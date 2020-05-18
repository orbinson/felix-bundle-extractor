package be.idoneus.felix.bundle.extractor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.Gson;

/**
 * Extracts the bundles from the felix launchpad directory given by bundles.dir
 */
public class BundleExtractorManager {

    private Log log = LogFactory.getLog(BundleExtractorManager.class);

    private final BundleExtractor extractor;
    private final BundleExtractorConfig config;

    public BundleExtractorManager(BundleExtractorConfig config) {
        this.config = config;
        this.extractor = new BundleExtractor(config);
    }

    void run() {
        createOutputDirectory();
        extractBundles();
    }

    private void extractBundles() {
        ExecutorService executorService = Executors.newFixedThreadPool(config.getThreadCount());

        List<CompletableFuture<BundleExtractionResult>> futures = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(config.getBundleInputDir()))) {
            // loop over all bundle jars
            for (Path path : stream) {
                CompletableFuture<BundleExtractionResult> cf = CompletableFuture
                        .supplyAsync(() -> extractor.extract(path), executorService);
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

        // create the result list
        List<BundleExtractionResult> bundleExtractionResults = new ArrayList<>();
        for (CompletableFuture<BundleExtractionResult> future : futures) {
            try {
                bundleExtractionResults.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                log.error("Could not get future", e);
            }
        }

        createOutputResult(bundleExtractionResults);
    }

    private void createOutputResult(List<BundleExtractionResult> bundleExtractionResults) {
        FelixBundleExtractorResult result = new FelixBundleExtractorResult();
        result.setBundleExtractionResults(bundleExtractionResults);
        result.setDecompiledCount(bundleExtractionResults.stream().filter(r -> r.isDecompiled()).count());
        result.setDownloadCount(bundleExtractionResults.stream().filter(r -> r.hasSources()).count());
        result.setUnprocessedCount(bundleExtractionResults.stream().filter(r -> !r.getProcessed()).count());

        String jsonResult = new Gson().toJson(result);

        try {
            Files.write(Paths.get(config.getBundleOutputDir()).resolveSibling(config.getOutputResultFileName()),
                    jsonResult.getBytes());
        } catch (IOException e) {
            log.error("Could not write to output file", e);
        }

        log.info("Downloaded " + result.getDownloadCount() + ", decompiled " + result.getDecompiledCount()
                + " and unprocessed " + result.getUnprocessedCount() + " in total");
    }

    private void createOutputDirectory() {
        createDirectory(Paths.get(config.getBundleOutputDir()));
        createDirectory(Paths.get(config.getBundleOutputDir()).resolve("artifacts"));
        createDirectory(Paths.get(config.getBundleOutputDir()).resolve("sources"));
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
