# Erebus

Erebus is a wrapper for aether.


## Usage

Fetch an artifact with dependencies from Maven central or local repository, as follows:

```java
Erebus erebus = new Erebus.Builder().build();
List<File> artifacts = erebus.resolveAsFiles("commons-lang:commons-lang:2.6");
```
