package be.idoneus.felix.bundle.extractor;

import java.nio.file.Path;

@FunctionalInterface
public interface BundleExtractor {
    Path extract(Path path);

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
