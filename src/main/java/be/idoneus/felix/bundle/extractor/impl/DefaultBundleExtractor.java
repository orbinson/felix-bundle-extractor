package be.idoneus.felix.bundle.extractor.impl;

import be.idoneus.felix.bundle.extractor.BundleExtractor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class DefaultBundleExtractor implements BundleExtractor {

    private Log log = LogFactory.getLog(DefaultBundleExtractor.class);

    private String bundlesOutputDir;

    private int decompiledCount = 0;
    private int downloadCount = 0;
    private int unprocessedCount = 0;

    public DefaultBundleExtractor(String bundlesOutputDir) {
        this.bundlesOutputDir = bundlesOutputDir;
	}

	public int getDecompiledCount() {
        return decompiledCount;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public int getUnprocessedCount() {
        return unprocessedCount;
    }

    @Override
    public Path extract(Path path) {
        try {
            Path versionPath = getVersionDirectory(path);
            if (versionPath != null) {
                Path bundlePath = versionPath.resolve("bundle.jar");
                if (bundlePath.toFile().exists()) {
                    extractJarFile(path, bundlePath);
                } else {
                    log.info("Could not find bundle jar" + path.toString());
                    unprocessedCount++;
                }
            } else {
                log.info("Could not find version directory" + path.toString());
                unprocessedCount++;
            }
        } catch (Exception e) {
            log.error("Could not extract bundle", e);
        }
        return path;
    }

    private void extractJarFile(Path path, Path bundlePath) throws XmlPullParserException {
        try (JarFile jarFile = new JarFile(bundlePath.toFile())) {
            String embeddedDependencies = getManifestAttribute(jarFile, "Embed-Dependency");
            Enumeration<JarEntry> enumeration = jarFile.entries();
            boolean extracted = false;
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                if (entry.getName().endsWith("pom.xml")) {
                    extracted = extractPom(path, bundlePath, jarFile, entry, embeddedDependencies);
                    if (extracted) {
                        break;
                    }
                }
            }
            if (!extracted) {
                log.info("Could not get a pom.xml for bundle " + path.toString() + " , defaulting back to manifest");
                extractFromManifest(path, bundlePath, jarFile);
            }
        } catch (IOException e) {
            log.error("Could not get jarfile", e);
        }
    }

    private void extractFromManifest(Path path, Path bundlePath, JarFile jarFile) throws IOException {
        String groupId = getManifestAttribute(jarFile, "Implementation-Vendor-Id");
        if (groupId.equals("")) {
            // defaulting to com.adobe
            groupId = "com.adobe";
        }
        String artifactId = getManifestAttribute(jarFile, "Bundle-SymbolicName");
        if (artifactId.equals("")) {
            artifactId = getManifestAttribute(jarFile, "Implementation-Title");
        }
        if (artifactId.equals("")) {
            log.info("Could not extract bundle because nothing of relevant data is present for bundle "
                    + path.toString());
        }

        String version = getManifestAttribute(jarFile, "Implementation-Version");
        Files.createDirectory(path.resolve(artifactId));
        Files.copy(bundlePath, path.resolve(artifactId).resolve(artifactId + "-" + version + ".jar"));
        createSourceJar(path.resolve(artifactId), artifactId, version);
        moveToOutputFolder(path, groupId, artifactId, version);
        FileUtils.deleteDirectory(path.resolve(artifactId).toFile());
        log.info("Extracted and renamed with manifest.mf for " + groupId + ":" + artifactId);
    }

    private void deleteDirectory(Path path) throws IOException {
        try (Stream<Path> paths = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private String getManifestAttribute(JarFile jarFile, String manifestAttributeName) throws IOException {
        Enumeration<JarEntry> enumeration = jarFile.entries();
        while (enumeration.hasMoreElements()) {
            JarEntry entry = enumeration.nextElement();
            if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                Manifest manifest = new Manifest(jarFile.getInputStream(entry));
                String embeddedDependencies = manifest.getMainAttributes().getValue(manifestAttributeName);
                if (embeddedDependencies != null) {
                    return embeddedDependencies;
                }
                return "";
            }
        }
        return "";
    }

    private boolean extractPom(Path path, Path bundlePath, JarFile jarFile, JarEntry entry, String embeddedDependencies)
            throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(jarFile.getInputStream(entry));
        String groupId = model.getGroupId();
        if (groupId == null) {
            groupId = model.getParent().getGroupId();
        }
        String artifactId = model.getArtifactId();
        if (!embeddedDependencies.contains(artifactId)) {
            String version = model.getVersion();
            if (version == null) {
                version = model.getParent().getVersion();
            }
            InputStream entryStream = jarFile.getInputStream(entry);
            Files.createDirectory(path.resolve(artifactId));
            Files.copy(entryStream, path.resolve(artifactId).resolve("pom.xml"));
            Files.copy(bundlePath, path.resolve(artifactId).resolve(artifactId + "-" + version + ".jar"));
            boolean sourcesFound = downloadSources(path.resolve(artifactId), groupId, artifactId, version);
            if (!sourcesFound) {
                createSourceJar(path.resolve(artifactId), artifactId, version);
            }
            moveToOutputFolder(path, groupId, artifactId, version);
            FileUtils.deleteDirectory(path.resolve(artifactId).toFile());
            log.info("Extracted and renamed for " + groupId + ":" + artifactId);
            return true;
        } else {
            log.info("Found a pom.xml with artifact id " + artifactId
                    + " that is not of this bundle (probably an embedded or inlined resource) for " + path.toString());
            return false;
        }
    }

    private void moveToOutputFolder(Path path, String groupId, String artifactId, String version) throws IOException {
        Path outputPath = Paths.get(bundlesOutputDir);
        Path artifact = path.resolve(artifactId).resolve(artifactId + "-" + version + ".jar");
        Path sources = path.resolve(artifactId).resolve(artifactId + "-" + version + "-sources.jar");
        Files.move(artifact, outputPath.resolve("artifacts").resolve(artifactId + "-" + version + ".jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.move(sources, outputPath.resolve("sources").resolve(artifactId + "-" + version + "-sources.jar"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean downloadSources(Path directory, String groupId, String artifectId, String version) {
        try {
            String sourcesJarName = artifectId + "-" + version + "-sources.jar";
            URL website = new URL("https://repo1.maven.org/maven2/" + groupId.replaceAll("\\.", "/") + "/" + artifectId
                    + "/" + version + "/" + sourcesJarName);
            HttpURLConnection huc = (HttpURLConnection) website.openConnection();
            huc.setRequestMethod("HEAD");
            huc.connect();
            int code = huc.getResponseCode();
            if (code == 200) {
                try (ReadableByteChannel rbc = Channels.newChannel(website.openStream())) {
                    String sourcesFile = directory.toString() + "/" + sourcesJarName;
                    try (FileOutputStream fos = new FileOutputStream(sourcesFile)) {
                        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                        downloadCount++;
                        return true;
                    }
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            log.debug("No file found", e);
        }
        return false;
    }

    private Path getVersionDirectory(Path path) throws IOException {
        final Path[] result = { null };
        try (Stream<Path> paths = Files.walk(path)) {
            paths.forEach(filePath -> {
                if (filePath.toString().contains("version") && result[0] == null) {
                    result[0] = filePath;
                }
            });
        }
        return result[0];
    }

    private void createSourceJar(Path directory, String artifactId, String version) throws IOException {
        Map<String, Object> options = new HashMap<>();
        options.put(IFernflowerPreferences.LOG_LEVEL, IFernflowerLogger.Severity.ERROR.toString());
        ConsoleDecompiler decompiler = new ConsoleDecompiler(directory.resolve("sources").toFile(), options);
        decompiler.addSpace(directory.resolve(artifactId + "-" + version + ".jar").toFile(), true);
        decompiler.decompileContext();
        Files.move(directory.resolve("sources").resolve(artifactId + "-" + version + ".jar"),
                directory.resolve(artifactId + "-" + version + "-sources.jar"));
        deleteDirectory(directory.resolve("sources"));
        decompiledCount++;
    }
}
