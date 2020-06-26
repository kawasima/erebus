package net.unit8.erebus;

import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
public class ErebusTest {
    @Test
    public void resolveAsClasspath() throws DependencyCollectionException, DependencyResolutionException {
        Erebus erebus = new Erebus.Builder().build();
        String cp = erebus.resolveAsClasspath("commons-lang:commons-lang:2.4")
                .replace(File.separatorChar, '/');
        assertThat(cp).endsWith("/commons-lang/commons-lang/2.4/commons-lang-2.4.jar");
    }

    @Test
    public void resolveAsFiles() throws DependencyCollectionException, DependencyResolutionException {
        Erebus erebus = new Erebus.Builder().build();
        List<File> artifacts = erebus.resolveAsFiles("commons-lang:commons-lang:2.4");

        Assertions.assertNotNull(artifacts);
        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).getAbsolutePath().replace(File.separatorChar, '/'))
                .endsWith("/commons-lang/commons-lang/2.4/commons-lang-2.4.jar");
    }

}
