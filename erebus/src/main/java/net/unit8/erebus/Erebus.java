package net.unit8.erebus;

import com.google.common.collect.ImmutableList;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper of aether.
 *
 * @author kawasima
 */
public class Erebus {
    private RepositorySystem repositorySystem;
    private RepositorySystemSession session;
    private List<RemoteRepository> remoteRepositories;

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
        dependencyRequest.setRoot( node );

        repositorySystem.resolveDependencies(session, dependencyRequest);

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);
        return nlg;
    }

    /**
     * Resolves artifact dependencies represented as CLASSPATH.
     *
     * @param spec &lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]:&lt;version&gt;
     * @return A classpath string
     * @throws DependencyCollectionException
     * @throws DependencyResolutionException
     */
    public String resolveAsClasspath(String spec) throws DependencyCollectionException, DependencyResolutionException {
        return resolveInternal(spec).getClassPath();
    }

    /**
     * Resolves artifact dependencies represented as a list of files.
     *
     * @param spec &lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]:&lt;version&gt;
     * @return A list of artifact files.
     * @throws DependencyCollectionException
     * @throws DependencyResolutionException
     */
    public List<File> resolveAsFiles(String spec) throws DependencyCollectionException, DependencyResolutionException {
        return resolveInternal(spec).getFiles();
    }

    /**
     * Deploys a file as artifact to a remote repository.
     *
     * @param spec &lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]:&lt;version&gt;
     * @param file artifact file
     * @throws DeploymentException
     */
    public void deploy(String spec, File file) throws DeploymentException {
        DeployRequest request = new DeployRequest();
        Artifact artifact = new DefaultArtifact(spec);
        artifact.setFile(file);
        request.setArtifacts(ImmutableList.of(artifact));
        repositorySystem.deploy(session, request);
    }

    /**
     * Installs a file as artifact to a local repository.
     *
     * @param spec &lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]:&lt;version&gt;
     * @param file artifact file
     * @throws InstallationException
     */
    public void install(String spec, File file) throws InstallationException {
        InstallRequest request = new InstallRequest();
        Artifact artifact = new DefaultArtifact(spec).setFile(file);
        request.setArtifacts(ImmutableList.of(artifact));
        repositorySystem.install(session, request);
    }


    public static final class Builder {
        private List<RemoteRepository> remoteRepositories;
        private RemoteRepository defaultRemoteRepository;

        public Builder() {
            remoteRepositories = new ArrayList<>();
            RemoteRepository.Builder repoBuilder = new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/");
            String httpProxy = System.getenv("http_proxy");
            if (httpProxy != null) {
                try {
                    URL proxyUrl = new URL(httpProxy);
                    repoBuilder.setProxy(new Proxy(proxyUrl.getProtocol(), proxyUrl.getHost(), proxyUrl.getPort()));
                } catch (MalformedURLException ignore) {

                }
            }
            defaultRemoteRepository = repoBuilder.build();
        }

        public Builder addRemote(RemoteRepository repository) {
            remoteRepositories.add(repository);
            return this;
        }
        public Erebus build() {
            if (remoteRepositories.isEmpty()) {
                remoteRepositories.add(defaultRemoteRepository);
            }
            RepositorySystem system = newRepositorySystem();
            return new Erebus(system, newSession(system), remoteRepositories);
        }

        private static RepositorySystem newRepositorySystem() {
            DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
            locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class );
            locator.addService(TransporterFactory.class, FileTransporterFactory.class );
            locator.addService(TransporterFactory.class, HttpTransporterFactory.class );

            return locator.getService( RepositorySystem.class );
        }

        private static RepositorySystemSession newSession(RepositorySystem system) {
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

            LocalRepository localRepo = new LocalRepository(System.getProperty("user.home") + "/.m2/repository");
            session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo ) );
            return session;
        }

    }
}
