package net.unit8.erebus;

import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.Test;

/**
 * @author kawasima
 */
public class ErebusTest {
    @Test
    public void test() throws DependencyCollectionException, DependencyResolutionException {
        Erebus erebus = new Erebus.Builder().build();
        String cp = erebus.resolveAsClasspath("commons-lang:commons-lang:2.4");
        System.out.println(cp);
    }
}
