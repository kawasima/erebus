package net.unit8.erebus;

import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.List;

/**
 * @author kawasima
 */
public class ErebusTest {
    @Test
    public void resolveAsClasspath() throws DependencyCollectionException, DependencyResolutionException {
        Erebus erebus = new Erebus.Builder().build();
        String cp = erebus.resolveAsClasspath("commons-lang:commons-lang:2.4");
        assertTrue(cp.endsWith("/commons-lang/commons-lang/2.4/commons-lang-2.4.jar"));
    }

    @Test
    public void resolveAsFiles() throws DependencyCollectionException, DependencyResolutionException {
        Erebus erebus = new Erebus.Builder().build();
        List<File> artifacts = erebus.resolveAsFiles("commons-lang:commons-lang:2.4");

        assertNotNull(artifacts);
        assertEquals(1, artifacts.size());
        assertTrue(artifacts.get(0).getAbsolutePath()
                .endsWith("/commons-lang/commons-lang/2.4/commons-lang-2.4.jar"));
    }

}
