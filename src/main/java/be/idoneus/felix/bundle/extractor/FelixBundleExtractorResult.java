package be.idoneus.felix.bundle.extractor;

import java.util.ArrayList;
import java.util.List;

public class FelixBundleExtractorResult {

    private List<BundleExtractionResult> bundleExtractionResults = new ArrayList<>();
    private long decompiledCount = 0;
    private long downloadCount = 0;
    private long unprocessedCount = 0;
    private long excludedCount = 0;

    public long getDecompiledCount() {
        return decompiledCount;
    }

    public long getExcludedCount() {
        return excludedCount;
    }

    public void setExcludedCount(long excludedCount) {
        this.excludedCount = excludedCount;
    }

    public List<BundleExtractionResult> getBundleExtractionResults() {
        return bundleExtractionResults;
    }

    public void setBundleExtractionResults(List<BundleExtractionResult> bundleExtractionResults) {
        this.bundleExtractionResults = bundleExtractionResults;
    }

    public long getUnprocessedCount() {
        return unprocessedCount;
    }

    public void setUnprocessedCount(long unprocessedCount) {
        this.unprocessedCount = unprocessedCount;
    }

    public long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(long downloadCount) {
        this.downloadCount = downloadCount;
    }

    public void setDecompiledCount(long decompiledCount) {
        this.decompiledCount = decompiledCount;
    }

}