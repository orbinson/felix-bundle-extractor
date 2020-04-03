package be.idoneus.felix.bundle.extractor;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface BundleExtractor {
    CompletableFuture<Path> extract(Path path);

    default int getDownloadCount() {
        return 0;
    }

    default int getDecompiledCount() {
        return 0;
    }

    default int getUnprocessedCount() {
        return 0;
    }
}
