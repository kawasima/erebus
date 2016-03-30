package net.unit8.erebus;

import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

/**
 * @author kawasima
 */
public class ArtifactSearcherTest {
    @Test
    public void test() throws IOException, XMLStreamException {
        ArtifactSearcher searcher = new ArtifactSearcher();
        System.out.println(searcher.search("commons-lang"));
    }
}
