package be.idoneus.felix.bundle.extractor;

public class BundleExtractionResult {

    private String path;
    private String groupId;
    private String artifactId;
    private String version;
    private boolean hasSources;
    private boolean decompiled;
    private boolean processed;
    private boolean fromPom;
    private boolean fromManifest;
    private boolean excluded = false;

    public String getPath() {
        return path;
    }

    public boolean isfromManifest() {
        return fromManifest;
    }

    public void setFromManifest(boolean fromManifest) {
        this.fromManifest = fromManifest;
    }

    public boolean isFromPom() {
        return fromPom;
    }

    public void setFromPom(boolean fromPom) {
        this.fromPom = fromPom;
    }

    public boolean isDecompiled() {
        return decompiled;
    }

    public void setDecompiled(boolean decompiled) {
        this.decompiled = decompiled;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean getProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public boolean hasSources() {
        return hasSources;
    }

    public void setHasSources(boolean hasSources) {
        this.hasSources = hasSources;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public boolean isExcluded() {
        return excluded;
    }

}