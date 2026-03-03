package net.unit8.erebus;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * A thin wrapper around <a href="https://maven.apache.org/resolver/">Maven Artifact Resolver (Aether)</a>
 * that provides a simple API for resolving, installing, and deploying Maven artifacts.
 *
 * <p>Instances are created via the {@link Builder}:</p>
 *
 * <pre>{@code
 * Erebus erebus = new Erebus.Builder().build();
 *
 * // Resolve all transitive dependencies as a classpath string
 * String classpath = erebus.resolveAsClasspath("org.apache.commons:commons-lang3:3.14.0");
 *
 * // Or as a list of local files
 * List<File> jars = erebus.resolveAsFiles("org.apache.commons:commons-lang3:3.14.0");
 * }</pre>
 *
 * <p>By default, Maven Central ({@code https://repo1.maven.org/maven2/}) is used as the remote
 * repository. Additional repositories can be added via {@link Builder#addRemote(RemoteRepository)}.
 * The local repository defaults to {@code ~/.m2/repository}.</p>
 *
 * <p>HTTP/HTTPS proxy settings are read from the following environment variables (in order of
 * precedence): {@code https_proxy}, {@code HTTPS_PROXY}, {@code http_proxy}, {@code HTTP_PROXY}.</p>
 *
 * @author kawasima
 * @see Builder
 * @see ArtifactSearcher
 */
public class Erebus {
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> remoteRepositories;

    private Erebus(RepositorySystem repositorySystem, RepositorySystemSession session, List<RemoteRepository> remoteRepositories) {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.remoteRepositories = remoteRepositories;
    }

    private PreorderNodeListGenerator resolveInternal(String spec) throws DependencyCollectionException, DependencyResolutionException {
        Dependency dependency = new Dependency(new DefaultArtifact(spec), "compile");

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        for (RemoteRepository remote : remoteRepositories) {
            collectRequest.addRepository(remote);
        }
        DependencyNode node = repositorySystem.collectDependencies(session, collectRequest).getRoot();

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setRoot(node);

        repositorySystem.resolveDependencies(session, dependencyRequest);

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);
        return nlg;
    }

    /**
     * Resolves an artifact and all its transitive compile-scope dependencies, returning them
     * as an OS-specific classpath string (entries separated by {@link File#pathSeparator}).
     *
     * <p>Artifacts not already present in the local repository are downloaded from the
     * configured remote repositories.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * String cp = erebus.resolveAsClasspath("com.google.guava:guava:33.2.1-jre");
     * // "/home/user/.m2/repository/com/google/guava/guava/33.2.1-jre/guava-33.2.1-jre.jar:..."
     * }</pre>
     *
     * @param spec artifact coordinates in the form
     *             {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}
     * @return OS-specific classpath string containing the resolved artifact and all its dependencies
     * @throws DependencyCollectionException if the dependency tree could not be built
     * @throws DependencyResolutionException if any artifact in the dependency tree could not be resolved
     */
    public String resolveAsClasspath(String spec) throws DependencyCollectionException, DependencyResolutionException {
        return resolveInternal(spec).getClassPath();
    }

    /**
     * Resolves an artifact and all its transitive compile-scope dependencies, returning them
     * as a list of local {@link File} objects.
     *
     * <p>Artifacts not already present in the local repository are downloaded from the
     * configured remote repositories.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * List<File> jars = erebus.resolveAsFiles("com.google.guava:guava:33.2.1-jre");
     * URLClassLoader loader = new URLClassLoader(
     *     jars.stream().map(f -> { try { return f.toURI().toURL(); } catch (Exception e) { throw new RuntimeException(e); } }).toArray(URL[]::new)
     * );
     * }</pre>
     *
     * @param spec artifact coordinates in the form
     *             {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}
     * @return list of local JAR files for the artifact and all its dependencies
     * @throws DependencyCollectionException if the dependency tree could not be built
     * @throws DependencyResolutionException if any artifact in the dependency tree could not be resolved
     */
    public List<File> resolveAsFiles(String spec) throws DependencyCollectionException, DependencyResolutionException {
        return resolveInternal(spec).getFiles();
    }

    /**
     * Deploys an artifact file to the remote repository configured in the current
     * {@link RepositorySystemSession}.
     *
     * <p>The deployment target (URL, authentication, etc.) must be configured on the session's
     * repository. This method is typically used in release automation scripts.</p>
     *
     * @param spec artifact coordinates in the form
     *             {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}
     * @param file the artifact file to deploy (e.g., a JAR)
     * @throws DeploymentException if any artifact or metadata from the request could not be deployed
     */
    public void deploy(String spec, File file) throws DeploymentException {
        DeployRequest request = new DeployRequest();
        Artifact artifact = new DefaultArtifact(spec).setFile(file);
        request.setArtifacts(List.of(artifact));
        repositorySystem.deploy(session, request);
    }

    /**
     * Installs an artifact file into the local repository (typically {@code ~/.m2/repository}).
     *
     * <p>This is the programmatic equivalent of {@code mvn install:install-file}.
     * After installation, the artifact is available for local dependency resolution.</p>
     *
     * @param spec artifact coordinates in the form
     *             {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}
     * @param file the artifact file to install (e.g., a JAR)
     * @throws InstallationException if any artifact or metadata from the request could not be installed
     */
    public void install(String spec, File file) throws InstallationException {
        InstallRequest request = new InstallRequest();
        Artifact artifact = new DefaultArtifact(spec).setFile(file);
        request.setArtifacts(List.of(artifact));
        repositorySystem.install(session, request);
    }


    /**
     * Builder for creating {@link Erebus} instances.
     *
     * <p>Usage:</p>
     * <pre>{@code
     * // Minimal – uses Maven Central and ~/.m2/repository
     * Erebus erebus = new Erebus.Builder().build();
     *
     * // With an additional private repository
     * RemoteRepository myRepo = new RemoteRepository.Builder(
     *         "my-repo", "default", "https://repo.example.com/maven2/").build();
     * Erebus erebus = new Erebus.Builder()
     *         .addRemote(myRepo)
     *         .build();
     * }</pre>
     *
     * <p>Proxy settings are auto-detected from environment variables
     * ({@code https_proxy}, {@code HTTPS_PROXY}, {@code http_proxy}, {@code HTTP_PROXY}).</p>
     */
    public static final class Builder {
        private final List<RemoteRepository> remoteRepositories;
        private final RemoteRepository defaultRemoteRepository;

        /**
         * Creates a new {@code Builder} with Maven Central as the default remote repository.
         *
         * <p>Proxy settings are read from the environment: {@code https_proxy},
         * {@code HTTPS_PROXY}, {@code http_proxy}, {@code HTTP_PROXY} (first non-null wins).</p>
         */
        public Builder() {
            remoteRepositories = new ArrayList<>();
            RemoteRepository.Builder repoBuilder = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/");
            String proxyEnv = firstNonNull(
                    System.getenv("https_proxy"),
                    System.getenv("HTTPS_PROXY"),
                    System.getenv("http_proxy"),
                    System.getenv("HTTP_PROXY"));
            if (proxyEnv != null) {
                try {
                    URL proxyUrl = URI.create(proxyEnv).toURL();
                    repoBuilder.setProxy(new Proxy(proxyUrl.getProtocol(), proxyUrl.getHost(), proxyUrl.getPort()));
                } catch (MalformedURLException ignore) {
                }
            }
            defaultRemoteRepository = repoBuilder.build();
        }

        @SafeVarargs
        private static <T> T firstNonNull(T... values) {
            for (T v : values) {
                if (v != null) return v;
            }
            return null;
        }

        /**
         * Adds a remote repository to search when resolving artifacts.
         *
         * <p>If no remote repositories are added before {@link #build()} is called,
         * Maven Central is used automatically. If at least one repository is added here,
         * Maven Central is <em>not</em> included unless added explicitly.</p>
         *
         * @param repository the remote repository to add
         * @return this builder, for method chaining
         */
        public Builder addRemote(RemoteRepository repository) {
            remoteRepositories.add(repository);
            return this;
        }

        /**
         * Builds a new {@link Erebus} instance.
         *
         * <p>If no remote repositories were added, Maven Central is used as the sole remote.
         * The local repository is always {@code ~/.m2/repository}.</p>
         *
         * @return a configured {@link Erebus} instance
         */
        public Erebus build() {
            if (remoteRepositories.isEmpty()) {
                remoteRepositories.add(defaultRemoteRepository);
            }
            RepositorySystem system = newRepositorySystem();
            return new Erebus(system, newSession(system), remoteRepositories);
        }

        private static RepositorySystem newRepositorySystem() {
            RepositorySystemSupplier repositorySystemSupplier = new RepositorySystemSupplier();
            return repositorySystemSupplier.get();
        }

        private static RepositorySystemSession newSession(RepositorySystem system) {
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

            LocalRepository localRepo = new LocalRepository(System.getProperty("user.home") + "/.m2/repository");
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
            return session;
        }
    }
}
