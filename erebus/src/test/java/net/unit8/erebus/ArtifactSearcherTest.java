package net.unit8.erebus;

import org.junit.Test;

import java.io.IOException;

/**
 * @author kawasima
 */
public class ArtifactSearcherTest {
    @Test
    public void search() throws IOException {
        ArtifactSearcher searcher = new ArtifactSearcher();
        System.out.println(searcher.search("commons-lang"));
    }

    @Test
    public void searchIncremental() throws IOException {
        ArtifactSearcher searcher = new ArtifactSearcher();
        System.out.println(searcher.searchIncremental("org.apache.commons"));
        System.out.println(searcher.searchIncremental("commons-lang"));
        System.out.println(searcher.searchIncremental("org.apache.commons:commons-lan"));
    }

    @Test
    public void searchIncrementalForVersion() throws IOException {
        ArtifactSearcher searcher = new ArtifactSearcher();
        System.out.println(searcher.searchIncremental("org.apache.commons:commons-lang3:3."));
    }
}
