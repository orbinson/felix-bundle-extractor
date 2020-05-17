package be.idoneus.felix.bundle.extractor;

import java.util.ArrayList;
import java.util.List;

public class BundleExtractorConfig {

    private String bundleInputDir;
    private String bundleOutputDir;
    private int threadCount;
    private List<String> excludedArtifacts = new ArrayList<>();

    public String getBundleInputDir() {
        return bundleInputDir;
    }

    public List<String> getExcludedArtifacts() {
        return excludedArtifacts;
    }

    public void setExcludedArtifacts(List<String> excludedArtifacts) {
        this.excludedArtifacts = excludedArtifacts;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public String getBundleOutputDir() {
        return bundleOutputDir;
    }

    public void setBundleOutputDir(String bundleOutputDir) {
        this.bundleOutputDir = bundleOutputDir;
    }

    public void setBundleInputDir(String bundleInputDir) {
        this.bundleInputDir = bundleInputDir;
    }

}