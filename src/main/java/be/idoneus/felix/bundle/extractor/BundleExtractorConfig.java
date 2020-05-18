package be.idoneus.felix.bundle.extractor;

import java.util.ArrayList;
import java.util.List;

public class BundleExtractorConfig {

    private String bundleInputDir;
    private String bundleOutputDir;
    private String outputResultFileName;
    private int threadCount;
    private List<String> excludedDecompilation = new ArrayList<>();

    public String getBundleInputDir() {
        return bundleInputDir;
    }

    public List<String> getExcludedDecompilation() {
        return excludedDecompilation;
    }

    public void setExcludedDecompilation(List<String> excludedDecompilation) {
        this.excludedDecompilation = excludedDecompilation;
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

    public void setOutputResultFileName(String outputResultFileName) {
        this.outputResultFileName =outputResultFileName;
    }

    public String getOutputResultFileName() {
        return outputResultFileName;
    }

}