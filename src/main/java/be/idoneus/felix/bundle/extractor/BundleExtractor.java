package be.idoneus.felix.bundle.extractor;

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

public class BundleExtractor {

    private Log log = LogFactory.getLog(BundleExtractor.class);

    private final BundleExtractorConfig config;

    public BundleExtractor(BundleExtractorConfig config) {
        this.config = config;
    }

    public BundleExtractionResult extract(Path path) {
        BundleExtractionResult result = new BundleExtractionResult();
        long startTime = System.nanoTime();
        result.setPath(path.toString());
        try {
            Path versionPath = getVersionDirectory(path);
            if (versionPath != null) {
                Path bundlePath = versionPath.resolve("bundle.jar");
                if (bundlePath.toFile().exists()) {
                    extractJarFile(result, path, bundlePath);
                    result.setProcessed(true);
                } else {
                    log.info("Could not find bundle jar" + path.toString());
                    result.setProcessed(false);
                }
            } else {
                log.info("Could not find version directory" + path.toString());
                result.setProcessed(false);
            }
        } catch (Exception e) {
            log.error("Could not extract bundle", e);
            result.setProcessed(false);
        }
        long elapsedTime = System.nanoTime() - startTime;
        log.debug("Extraction of " + path.toString() + " took " + elapsedTime / 1_000_000_000 + " seconds");
        return result;
    }

    private void extractJarFile(BundleExtractionResult result, Path path, Path bundlePath)
            throws XmlPullParserException {
        try (JarFile jarFile = new JarFile(bundlePath.toFile())) {
            String embeddedDependencies = getManifestAttribute(jarFile, "Embed-Dependency");
            Enumeration<JarEntry> enumeration = jarFile.entries();
            boolean extracted = false;
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                if (entry.getName().endsWith("pom.xml")) {
                    extracted = extractPom(result, path, bundlePath, jarFile, entry, embeddedDependencies);
                    if (extracted) {
                        break;
                    }
                }
            }
            if (!extracted) {
                if (!config.isExludeNonMavenArtifacts()) {
                    log.info(
                            "Could not get a pom.xml for bundle " + path.toString() + " , defaulting back to manifest");
                    extractFromManifest(result, path, bundlePath, jarFile);
                } else {
                    log.info("Could not get a pom.xml for bundle " + path.toString()
                            + " and manifest extraction disabled, skipping");
                }
            }
        } catch (IOException e) {
            log.error("Could not get jarfile", e);
        }
    }

    private void extractFromManifest(BundleExtractionResult result, Path path, Path bundlePath, JarFile jarFile)
            throws IOException {
        String groupId = getManifestAttribute(jarFile, "Implementation-Vendor-Id");
        if (groupId.equals("")) {
            // defaulting to com.adobe.manifest
            groupId = "com.adobe.manifest";
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

        result.setFromManifest(true);
        result.setArtifactId(artifactId);
        result.setGroupId(groupId);
        result.setVersion(version);

        if (isExcludedGroupId(groupId)) {
            log.info("Not extracting manifest because the group id " + groupId + " is excluded");
            result.setExcluded(true);
            return;
        } else if (isExcludedArtifactId(artifactId)) {
            log.info("Not extracting manifest because the artifact id " + artifactId + " is excluded");
            result.setExcluded(true);
            return;
        }

        Path buildDirectory = path.resolve(artifactId + "-extracted");
        if (Files.exists(buildDirectory)) {
            deleteDirectoryStream(buildDirectory);
        }
        Files.createDirectory(buildDirectory);

        Files.copy(bundlePath, buildDirectory.resolve(artifactId + "-" + version + ".jar"));
        createSourceJar(buildDirectory, artifactId, version);
        result.setDecompiled(true);
        moveToOutputFolder(buildDirectory, groupId, artifactId, version);
        FileUtils.deleteDirectory(buildDirectory.toFile());
        log.info("Extracted and renamed with manifest.mf for " + groupId + ":" + artifactId);
    }

    private boolean isExcludedArtifactId(String artifactId) {
        if (config.getExcludeArtifactIds() == null || config.getExcludeArtifactIds().equals("")) {
            return false;
        } else {
            return artifactId.matches(config.getExcludeArtifactIds());
        }
    }

    private boolean isExcludedGroupId(String groupId) {
        if (config.getExcludeGroupIds() == null || config.getExcludeGroupIds().equals("")) {
            return false;
        } else {
            return groupId.matches(config.getExcludeGroupIds());
        }
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

    private boolean extractPom(BundleExtractionResult result, Path path, Path bundlePath, JarFile jarFile,
            JarEntry entry, String embeddedDependencies) throws IOException, XmlPullParserException {
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

            result.setFromPom(true);
            result.setArtifactId(artifactId);
            result.setGroupId(groupId);
            result.setVersion(version);

            if (isExcludedGroupId(groupId)) {
                log.info("Not extracting pom because the group id " + groupId + " is excluded");
                result.setExcluded(true);
                return true;
            } else if (isExcludedArtifactId(artifactId)) {
                log.info("Not extracting pom because the artifact id " + artifactId + " is excluded");
                result.setExcluded(true);
                return true;
            }

            InputStream entryStream = jarFile.getInputStream(entry);

            Path buildDirectory = path.resolve(artifactId + "-extracted");
            if (Files.exists(buildDirectory)) {
                deleteDirectoryStream(buildDirectory);
            }
            Files.createDirectory(buildDirectory);

            Files.copy(entryStream, buildDirectory.resolve("pom.xml"));
            Files.copy(bundlePath, buildDirectory.resolve(artifactId + "-" + version + ".jar"));
            boolean sourcesFound = downloadSources(buildDirectory, groupId, artifactId, version);
            if (!sourcesFound) {
                createSourceJar(buildDirectory, artifactId, version);
                result.setDecompiled(true);
            } else {
                result.setHasSources(true);
            }
            moveToOutputFolder(buildDirectory, groupId, artifactId, version);
            FileUtils.deleteDirectory(buildDirectory.toFile());
            log.info("Extracted and renamed for " + groupId + ":" + artifactId);

            return true;
        } else {
            log.info("Found a pom.xml with artifact id " + artifactId
                    + " that is not of this bundle (probably an embedded or inlined resource) for " + path.toString());
            return false;
        }
    }

    private void deleteDirectoryStream(Path path) throws IOException {
        Files.walk(path).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }

    private void moveToOutputFolder(Path buildDirectory, String groupId, String artifactId, String version)
            throws IOException {
        Path outputPath = Paths.get(config.getBundleOutputDir());
        Path artifact = buildDirectory.resolve(artifactId + "-" + version + ".jar");
        Path sources = buildDirectory.resolve(artifactId + "-" + version + "-sources.jar");
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
        long startTime = System.nanoTime();
        Map<String, Object> options = new HashMap<>();
        options.put(IFernflowerPreferences.LOG_LEVEL, IFernflowerLogger.Severity.ERROR.toString());
        ConsoleDecompiler decompiler = new ConsoleDecompiler(directory.resolve("sources").toFile(), options);
        decompiler.addSpace(directory.resolve(artifactId + "-" + version + ".jar").toFile(), true);
        decompiler.decompileContext();
        Files.move(directory.resolve("sources").resolve(artifactId + "-" + version + ".jar"),
                directory.resolve(artifactId + "-" + version + "-sources.jar"));
        deleteDirectory(directory.resolve("sources"));
        long elapsedTime = System.nanoTime() - startTime;
        log.debug("Decompilation of " + artifactId + " took " + elapsedTime / 1_000_000_000 + " seconds");
    }
}
