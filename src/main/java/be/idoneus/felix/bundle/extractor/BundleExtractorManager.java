package be.idoneus.felix.bundle.extractor;

import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Extracts the bundles from the felix launchpad directory given by bundles.dir
 */
public class BundleExtractorManager {

    private final BundleExtractor extractor;
    private final BundleExtractorConfig config;
    private final Log log = LogFactory.getLog(BundleExtractorManager.class);

    public BundleExtractorManager(BundleExtractorConfig config) {
        this.config = config;
        this.extractor = new BundleExtractor(config);
    }

    void run() {
        createOutputDirectory();
        extractBundles();
    }

    @SuppressWarnings("java:S2142")
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
        result.setBundleExtractionResults(
                bundleExtractionResults.stream().filter(r -> r.getArtifactId() != null).collect(Collectors.toList()));
        result.setDecompiledCount(bundleExtractionResults.stream().filter(BundleExtractionResult::isDecompiled).count());
        result.setDownloadCount(bundleExtractionResults.stream().filter(BundleExtractionResult::hasSources).count());
        result.setUnprocessedCount(bundleExtractionResults.stream().filter(r -> !r.getProcessed()).count());
        result.setExcludedCount(bundleExtractionResults.stream().filter(BundleExtractionResult::isExcluded).count());

        String jsonResult = new Gson().toJson(result);

        try {
            Files.write(Paths.get(config.getBundleOutputDir()).resolve(config.getOutputResultFileName()),
                    jsonResult.getBytes());
        } catch (IOException e) {
            log.error("Could not write to output file", e);
        }

        log.info("Downloaded " + result.getDownloadCount() + ", decompiled " + result.getDecompiledCount()
                + ", excluded " + result.getExcludedCount() + " and unprocessed " + result.getUnprocessedCount()
                + " in total");
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
