package be.idoneus.felix.bundle.extractor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import be.idoneus.felix.bundle.extractor.impl.DefaultBundleExtractor;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Extracts the bundles from the felix launchpad directory given by bundles.dir
 */
public class BundleExtractorService {

    private Log log = LogFactory.getLog(BundleExtractorService.class);

    private final BundleExtractor extractor;
    private final String bundlesInputDir;
    private final String bundlesOutputDir;

    public BundleExtractorService(String bundlesInputDir, String bundlesOutputDir) {
        this.bundlesInputDir = bundlesInputDir;
        this.bundlesOutputDir = bundlesOutputDir;
        this.extractor = new DefaultBundleExtractor(bundlesOutputDir);
    }

    void run() {
        createOutputDirectory();
        extractBundles();
    }

    private void extractBundles() {
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        List<CompletableFuture<Path>> futures = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(bundlesInputDir))) {
            // loop over all bundle jars
            for (Path path : stream) {
                CompletableFuture<Path> cf = CompletableFuture.supplyAsync(() -> extractor.extract(path),
                        executorService);
                futures.add(cf);
            }
        } catch (IOException e) {
            log.error("Could not open directory", e);
        }

        // wait for all the futures to be done
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // close executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        log.info("Downloaded " + extractor.getDownloadCount() + ", decompiled " + extractor.getDecompiledCount()
                + " and unprocessed " + extractor.getUnprocessedCount() + " in total");
    }

    private void createOutputDirectory() {
        createDirectory(Paths.get(bundlesOutputDir));
        createDirectory(Paths.get(bundlesOutputDir).resolve("artifacts"));
        createDirectory(Paths.get(bundlesOutputDir).resolve("sources"));
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
