package net.unit8.erebus;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches for Maven artifacts via the
 * <a href="https://central.sonatype.com/search">Maven Central</a> Solr REST API
 * ({@code https://search.maven.org/solrsearch/select}).
 *
 * <p>Two search modes are provided:</p>
 * <ul>
 *   <li>{@link #search(String)} – free-text search by keyword.</li>
 *   <li>{@link #searchIncremental(String)} – coordinate-aware search that parses a partial
 *       {@code groupId:artifactId:version} string and builds a structured Solr query,
 *       making it suitable for incremental / autocomplete use-cases.</li>
 * </ul>
 *
 * <p>Example – keyword search:</p>
 * <pre>{@code
 * ArtifactSearcher searcher = new ArtifactSearcher();
 * List<Artifact> results = searcher.search("commons-lang");
 * }</pre>
 *
 * <p>Example – incremental search:</p>
 * <pre>{@code
 * // Returns artifacts whose groupId starts with "org.apache.commons"
 * List<Artifact> byGroup = searcher.searchIncremental("org.apache.commons:");
 *
 * // Returns artifacts matching groupId + partial artifactId
 * List<Artifact> byGroupAndArtifact = searcher.searchIncremental("org.apache.commons:commons-lan");
 *
 * // Returns artifacts matching all three coordinates with a partial version
 * List<Artifact> byVersion = searcher.searchIncremental("org.apache.commons:commons-lang3:3.");
 * }</pre>
 *
 * <p>Proxy settings are read from the following environment variables (first non-null wins):
 * {@code https_proxy}, {@code HTTPS_PROXY}, {@code http_proxy}, {@code HTTP_PROXY}.</p>
 *
 * <p>Results are limited to 20 entries per query (the API default).</p>
 *
 * @author kawasima
 * @see Erebus
 */
public class ArtifactSearcher {
    private static final Logger LOG = LoggerFactory.getLogger(ArtifactSearcher.class);

    private final XPath xpath = XPathFactory.newInstance().newXPath();
    private final DocumentBuilder builder;
    private Proxy proxy;

    /**
     * Creates a new {@code ArtifactSearcher}.
     *
     * <p>Proxy settings are auto-detected from the environment variables
     * {@code https_proxy}, {@code HTTPS_PROXY}, {@code http_proxy}, {@code HTTP_PROXY}
     * (first non-null wins). If none are set, connections are made directly.</p>
     *
     * @throws IllegalStateException if the underlying XML parser cannot be configured
     */
    public ArtifactSearcher() {
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }

        String proxyEnv = firstNonNull(
                System.getenv("https_proxy"),
                System.getenv("HTTPS_PROXY"),
                System.getenv("http_proxy"),
                System.getenv("HTTP_PROXY"));
        if (proxyEnv != null) {
            try {
                URL proxyUrl = URI.create(proxyEnv).toURL();
                proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUrl.getHost(), proxyUrl.getPort()));
            } catch (MalformedURLException ignore) {
            }
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private List<Artifact> searchInternal(String query) throws IOException {
        LOG.debug("Query: {}", query);
        List<Artifact> artifacts = new ArrayList<>();
        URL url = URI.create("https://search.maven.org/solrsearch/select?rows=20&wt=xml&q=" + query).toURL();

        HttpsURLConnection conn = proxy == null ?
                (HttpsURLConnection) url.openConnection() : (HttpsURLConnection) url.openConnection(proxy);

        conn.setConnectTimeout(500);
        conn.setReadTimeout(1000);

        try (InputStream in = conn.getInputStream()) {
            Document document = builder.parse(in);
            NodeList docs = (NodeList) xpath.evaluate("/response/result/doc", document, XPathConstants.NODESET);
            for (int i = 0; i < docs.getLength(); i++) {
                Node doc = docs.item(i);
                String a = (String) xpath.evaluate("str[@name='a']", doc, XPathConstants.STRING);
                String g = (String) xpath.evaluate("str[@name='g']", doc, XPathConstants.STRING);
                String p = (String) xpath.evaluate("str[@name='p']", doc, XPathConstants.STRING);
                String v = (String) xpath.evaluate("str[@name='v']", doc, XPathConstants.STRING);
                if (v.isEmpty()) {
                    v = (String) xpath.evaluate("str[@name='latestVersion']", doc, XPathConstants.STRING);
                }

                Artifact artifact = new DefaultArtifact(g, a, p, v);
                artifacts.add(artifact);
            }
        } catch(SAXException | XPathExpressionException e) {
            throw new IOException(e);
        }

        return artifacts;
    }

    /**
     * Searches Maven Central for artifacts matching the given free-text keyword.
     *
     * <p>The {@code coords} string is passed directly as the Solr {@code q} parameter,
     * so Solr query syntax is supported (e.g., {@code "commons-lang"}, {@code "guava"}).</p>
     *
     * @param coords free-text search keyword or Solr query expression
     * @return up to 20 matching artifacts; never {@code null}
     * @throws IOException if the search API cannot be reached or returns an unparseable response
     */
    public List<Artifact> search(String coords) throws IOException {
        return searchInternal(coords);
    }

    private int countCharInStr(String s, char ch) {
        int i = 0, cnt = 0;
        while ((i = s.indexOf(ch, i) + 1) > 0) cnt++;
        return cnt;
    }

    /**
     * Searches Maven Central using a partial Maven coordinate string, suitable for
     * incremental / autocomplete scenarios.
     *
     * <p>The {@code coords} argument is interpreted according to the number of
     * {@code ':'} separators present:</p>
     *
     * <table border="1">
     *   <caption>Coordinate parsing rules</caption>
     *   <tr><th>Input example</th><th>Solr query generated</th></tr>
     *   <tr><td>{@code "commons-lang"}</td>
     *       <td>Free-text wildcard: {@code commons-lang~}</td></tr>
     *   <tr><td>{@code "org.apache.commons:"}</td>
     *       <td>Group filter: {@code g:org.apache.commons AND a:*}</td></tr>
     *   <tr><td>{@code "org.apache.commons:commons-lan"}</td>
     *       <td>Group + artifact prefix: {@code g:org.apache.commons AND a:commons-lan*}</td></tr>
     *   <tr><td>{@code "org.apache.commons:commons-lang3:3."}</td>
     *       <td>Group + artifact + version prefix: {@code g:... AND a:... AND v:3.*}</td></tr>
     * </table>
     *
     * <p>A trailing {@code ':'} is stripped before parsing.</p>
     *
     * @param coords partial Maven coordinate ({@code groupId}, {@code groupId:artifactId},
     *               or {@code groupId:artifactId:version}), optionally ending with {@code ':'}
     * @return up to 20 matching artifacts; never {@code null}
     * @throws IOException if the search API cannot be reached or returns an unparseable response
     */
    public List<Artifact> searchIncremental(String coords) throws IOException {
        if (coords.endsWith(":"))
            coords = coords.substring(0, coords.length() - 1);
        int cnt = countCharInStr(coords, ':');
        switch (cnt) {
            case 0: return search(coords + "~");
            case 1: coords += ":_";  break;
        }

        Artifact artifact = new DefaultArtifact(coords);
        StringBuilder query = new StringBuilder();
        query.append("g:").append(artifact.getGroupId())
                .append("+AND+")
                .append("a:").append(artifact.getArtifactId());
        if (artifact.getExtension() != null && cnt > 1) {
            query.append("+AND+p:").append(artifact.getExtension());
        }

        if (!artifact.getVersion().equals("_")) {
            query.append("+AND+v:").append(artifact.getVersion());
        }
        return searchInternal(query.append("*").toString());
    }
}
