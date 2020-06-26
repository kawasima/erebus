package net.unit8.erebus;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
public class ArtifactSearcherTest {
    @Test
    public void search() throws IOException {
        ArtifactSearcher searcher = new ArtifactSearcher();
        assertThat(searcher.search("commons-lang"))
                .allMatch(artifact -> artifact.getGroupId().contains("commons-lang")
                        || artifact.getArtifactId().contains("commons-lang"));
    }

    @Test
    public void searchIncremental() throws IOException {
        ArtifactSearcher searcher = new ArtifactSearcher();

        assertThat(searcher.searchIncremental("org.apache.commons:"))
                .allMatch(artifact -> artifact.getGroupId().contains("org.apache.commons"));
        assertThat(searcher.searchIncremental("org.apache.commons:commons-lan"))
                .allMatch(artifact -> artifact.getGroupId().contains("org.apache.commons")
                        && artifact.getArtifactId().contains("commons-lan"));
    }

    @Test
    public void searchIncrementalForVersion() throws IOException {
        String groupId = "org.apache.commons";
        String artifactId = "commons-lang";
        ArtifactSearcher searcher = new ArtifactSearcher();
        assertThat(searcher.searchIncremental(groupId + ":" + artifactId + ":3."))
                .allMatch(artifact -> artifact.getGroupId().equals(groupId)
                        && artifact.getArtifactId().equals(artifactId)
                        && artifact.getVersion().startsWith("3."));
    }
}
