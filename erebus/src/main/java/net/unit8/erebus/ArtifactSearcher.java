package net.unit8.erebus;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * A Searcher for artifacts.
 *
 * Using search.maven.org REST API.
 *
 * @author kawasima
 */
public class ArtifactSearcher {
    private static final Logger LOG = LoggerFactory.getLogger(ArtifactSearcher.class);

    private XPath xpath = XPathFactory.newInstance().newXPath();
    private DocumentBuilder builder;

    public ArtifactSearcher() {
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Artifact> searchInternal(String query) throws IOException {
        LOG.debug("Query: {}", query);
        List<Artifact> artifacts = new ArrayList<>();
        URL url = URI.create("http://search.maven.org/solrsearch/select?rows=20&wt=xml&q=" + query).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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

    public List<Artifact> search(String coords) throws IOException{
        return searchInternal(coords);
    }

    private int countCharInStr(String s, char ch) {
        int i = 0, cnt = 0;
        while ((i = s.indexOf(ch, i) + 1) > 0) cnt++;
        return cnt;
    }

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
