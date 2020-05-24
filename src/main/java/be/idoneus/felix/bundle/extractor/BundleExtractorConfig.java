package be.idoneus.felix.bundle.extractor;

public class BundleExtractorConfig {

    private String bundleInputDir;
    private String bundleOutputDir;
    private String outputResultFileName;
    private int threadCount;
    private String excludeGroupIds;
    private String excludeArtifactIds;
    private boolean exludeNonMavenArtifacts = false;

    public String getBundleInputDir() {
        return bundleInputDir;
    }

    public boolean isExludeNonMavenArtifacts() {
        return exludeNonMavenArtifacts;
    }

    public void setExludeNonMavenArtifacts(boolean exludeNonMavenArtifacts) {
        this.exludeNonMavenArtifacts = exludeNonMavenArtifacts;
    }

    public String getExcludeArtifactIds() {
        return excludeArtifactIds;
    }

    public void setExcludeArtifactIds(String excludeArtifactIds) {
        this.excludeArtifactIds = excludeArtifactIds;
    }

    public String getExcludeGroupIds() {
        return excludeGroupIds;
    }

    public void setExcludeGroupIds(String excludeGroupIds) {
        this.excludeGroupIds = excludeGroupIds;
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