package net.unit8.erebus.tryartifact.command;

import net.unit8.erebus.Erebus;
import net.unit8.erebus.tryartifact.tool.TryJShellTool;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.io.File;
import java.util.List;

/**
 * @author kawasima
 */
public class TryArtifactCommandRegister {
    public void register(TryJShellTool tool) {
        Erebus erebus = new Erebus.Builder().build();
        tool.registerCommand(new TryJShellTool.Command("/resolve", "<spec>",
                "resolve an artifact",
                "Resolve an artifact\n" +
                "   spec is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>",
                arg -> {
                    if (arg.isEmpty()) {
                        tool.hard("/resolve requires a maven spec argument");
                        return false;
                    } else {
                        try {
                            List<File> artifacts = erebus.resolveAsFiles(arg);
                            artifacts.stream().map(File::getPath)
                                    .forEach(path -> {
                                        tool.getState().addToClasspath(
                                                TryJShellTool.toPathResolvingUserHome(path).toString());
                                        tool.fluff("Path %s added to classpath", path);
                                    });
                            return true;
                        } catch (DependencyCollectionException | DependencyResolutionException e) {
                            tool.error("%s", e);
                            return false;
                        }
                    }
                },
                null,
                TryJShellTool.CommandKind.REPLAY));
    }
}
