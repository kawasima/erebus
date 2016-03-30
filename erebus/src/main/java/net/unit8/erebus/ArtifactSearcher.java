package net.unit8.erebus;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.text.html.parser.Parser;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
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
 * @author kawasima
 */
public class ArtifactSearcher {
    XPath xpath = XPathFactory.newInstance().newXPath();
    DocumentBuilder builder;

    public ArtifactSearcher() {
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Artifact> search(String artifactId) throws IOException, XMLStreamException {
        List<Artifact> artifacts = new ArrayList<>();
        URL url = URI.create("http://search.maven.org/solrsearch/select?wt=xml&q=" + artifactId).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try (InputStream in = conn.getInputStream()) {
            Document document = builder.parse(in);
            NodeList docs = (NodeList) xpath.evaluate("/response/result/doc", document, XPathConstants.NODESET);
            for (int i = 0; i < docs.getLength(); i++) {
                Node doc = docs.item(i);
                String a = (String) xpath.evaluate("str[@name='a']", doc, XPathConstants.STRING);
                String g = (String) xpath.evaluate("str[@name='g']", doc, XPathConstants.STRING);
                String p = (String) xpath.evaluate("str[@name='p']", doc, XPathConstants.STRING);
                String v = (String) xpath.evaluate("str[@name='latestVersion']", doc, XPathConstants.STRING);

                Artifact artifact = new DefaultArtifact(g, a, p, v);
                artifacts.add(artifact);
            }
        } catch(SAXException | XPathExpressionException e) {
        }

        return artifacts;
    }
}
