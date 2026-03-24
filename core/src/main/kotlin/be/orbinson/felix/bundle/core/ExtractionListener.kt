package be.orbinson.felix.bundle.core

/**
 * Progress and log callbacks from [BundleExtractor] to its caller.
 *
 * Both the CLI and the IntelliJ plugin implement this interface so that
 * [BundleExtractor] remains free of any UI or IntelliJ API dependencies.
 */
interface ExtractionListener {
    /** Called once at the start with the total number of bundle directories to process. */
    fun onStart(total: Int) {}

    /** Called before each bundle is processed. [index] is 0-based. */
    fun onProgress(index: Int, total: Int, bundleName: String) {}

    /** Called when the extraction has been cancelled externally. */
    fun isCancelled(): Boolean = false

    fun info(message: String) {}
    fun warn(message: String) {}
    fun debug(message: String) {}
    fun error(message: String, cause: Throwable? = null) {}
}
