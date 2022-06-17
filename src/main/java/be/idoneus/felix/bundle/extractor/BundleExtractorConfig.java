package be.idoneus.felix.bundle.extractor;

public class BundleExtractorConfig {

    private String bundleInputDir;
    private String bundleOutputDir;
    private String outputResultFileName;
    private int threadCount;
    private String includeGroupIds;
    private String excludeGroupIds;
    private String excludeArtifactIds;
    private boolean excludeNonMavenArtifacts = false;

    public String getBundleInputDir() {
        return bundleInputDir;
    }

    public void setBundleInputDir(String bundleInputDir) {
        this.bundleInputDir = bundleInputDir;
    }

    public boolean isExcludeNonMavenArtifacts() {
        return excludeNonMavenArtifacts;
    }

    public void setExcludeNonMavenArtifacts(boolean excludeNonMavenArtifacts) {
        this.excludeNonMavenArtifacts = excludeNonMavenArtifacts;
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

    public String getOutputResultFileName() {
        return outputResultFileName;
    }

    public void setOutputResultFileName(String outputResultFileName) {
        this.outputResultFileName = outputResultFileName;
    }

    public String getIncludeGroupIds() {
        return includeGroupIds;
    }

    public void setIncludeGroupIds(String includeGroupIds) {
        this.includeGroupIds = includeGroupIds;
    }
}
