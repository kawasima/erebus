# Erebus

A lightweight Java wrapper around [Maven Artifact Resolver (Aether)](https://maven.apache.org/resolver/) that lets you resolve, install, deploy, and search Maven artifacts programmatically—without embedding a full Maven runtime.

## Requirements

- Java 21+
- Maven 3.x (for building)

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>net.unit8.erebus</groupId>
    <artifactId>erebus</artifactId>
    <version>0.4.0</version>
</dependency>
```

## Quick Start

### Resolve dependencies as a classpath string

```java
Erebus erebus = new Erebus.Builder().build();

String classpath = erebus.resolveAsClasspath("org.apache.commons:commons-lang3:3.14.0");
// → "/home/user/.m2/repository/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar"
```

### Resolve dependencies as a list of files

```java
Erebus erebus = new Erebus.Builder().build();

List<File> jars = erebus.resolveAsFiles("com.google.guava:guava:33.2.1-jre");

URLClassLoader loader = new URLClassLoader(
    jars.stream()
        .map(f -> { try { return f.toURI().toURL(); } catch (Exception e) { throw new RuntimeException(e); } })
        .toArray(URL[]::new)
);
```

### Use a private repository

```java
RemoteRepository myRepo = new RemoteRepository.Builder(
        "my-repo", "default", "https://repo.example.com/maven2/").build();

Erebus erebus = new Erebus.Builder()
        .addRemote(myRepo)
        .build();
```

> **Note:** When at least one repository is added via `addRemote()`, Maven Central is **not** included automatically. Add it explicitly if needed.

### Install an artifact to the local repository

```java
Erebus erebus = new Erebus.Builder().build();

erebus.install("com.example:my-lib:1.0.0", new File("my-lib-1.0.0.jar"));
```

### Deploy an artifact to a remote repository

```java
erebus.deploy("com.example:my-lib:1.0.0", new File("my-lib-1.0.0.jar"));
```

## Artifact Search

`ArtifactSearcher` queries the Maven Central search API.

### Free-text search

```java
ArtifactSearcher searcher = new ArtifactSearcher();

List<Artifact> results = searcher.search("commons-lang");
```

### Incremental / autocomplete search

`searchIncremental` interprets a partial coordinate string and builds a structured query:

| Input | Behaviour |
|---|---|
| `"commons-lang"` | Free-text wildcard search |
| `"org.apache.commons:"` | All artifacts in the group |
| `"org.apache.commons:commons-lan"` | Artifact prefix within the group |
| `"org.apache.commons:commons-lang3:3."` | Version prefix |

```java
ArtifactSearcher searcher = new ArtifactSearcher();

// All artifacts under org.apache.commons
List<Artifact> byGroup = searcher.searchIncremental("org.apache.commons:");

// Autocomplete artifactId
List<Artifact> byArtifact = searcher.searchIncremental("org.apache.commons:commons-lan");

// Autocomplete version
List<Artifact> byVersion = searcher.searchIncremental("org.apache.commons:commons-lang3:3.");
```

## Coordinate Format

All artifact coordinates follow the standard Maven format:

```
<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
```

Examples:
- `commons-lang:commons-lang:2.6`
- `org.apache.commons:commons-lang3:3.14.0`
- `org.apache.commons:commons-lang3:jar:sources:3.14.0`

## Proxy Configuration

Proxy settings are read automatically from environment variables (first non-null wins):

| Variable | Description |
|---|---|
| `https_proxy` | HTTPS proxy URL (lowercase) |
| `HTTPS_PROXY` | HTTPS proxy URL (uppercase) |
| `http_proxy` | HTTP proxy URL (lowercase) |
| `HTTP_PROXY` | HTTP proxy URL (uppercase) |

Example:

```sh
export https_proxy=http://proxy.example.com:8080
```

## Local Repository

Artifacts are resolved to and installed in `~/.m2/repository` by default. This path is derived from the `user.home` system property at runtime.

## License

[Eclipse Public License (EPL), Version 1.0](http://www.eclipse.org/legal/epl-v10.html)
