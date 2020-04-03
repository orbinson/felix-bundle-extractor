package be.idoneus.felix.bundle.extractor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Extracts the bundles from the felix launchpad directory given by bundles.dir
 */
@Component
public class BundleExtractorService {

    private Log log = LogFactory.getLog(BundleExtractorService.class);

    @Autowired
    private BundleExtractor extractor;

    @Value("${bundles.dir.input}")
    private String bundlesDirInput;

    @Value("${bundles.dir.output}")
    private String bundlesDirOutput;

    @Value("${separate.artifacts.and.sources}")
    private boolean separateArtifactsAndSources;

    @Value("${include.grep.in.jars}")
    private boolean includeGrepInJars;

    void run() {
        createOutputDirectory();
        extractBundles();
    }

    private void extractBundles() {
        List<CompletableFuture<Path>> futures = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(bundlesDirInput))) {
            for (Path path : stream) {
                futures.add(extractor.extract(path));
            }
        } catch (IOException e) {
            log.error("Could not open directory", e);
        }
        //wait for all the futures to be done
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Downloaded " + extractor.getDownloadCount() + ", decompiled " + extractor.getDecompiledCount() + " and unprocessed " + extractor.getUnprocessedCount() + " in total");
    }

    private void createOutputDirectory() {
        createDirectory(Paths.get(bundlesDirOutput));
        if (separateArtifactsAndSources) {
            createDirectory(Paths.get(bundlesDirOutput).resolve("artifacts"));
            createDirectory(Paths.get(bundlesDirOutput).resolve("sources"));
            if (includeGrepInJars) {
                try {
                    Files.copy(Paths.get(ClassLoader.getSystemResource("grep-in-jars.sh").toURI()), Paths.get(bundlesDirOutput).resolve("sources/grep-in-jars.sh"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException | URISyntaxException e) {
                    log.error("Could not copy grep-in-jars.sh to output directory", e);
                }
            }
        }
    }

    private void createDirectory(Path directory) {
        try {
            if (Files.notExists(directory)) {
                Files.createDirectory(directory);
            }
        } catch (IOException e) {
            log.error("Could not create directory", e);
        }
    }
}
