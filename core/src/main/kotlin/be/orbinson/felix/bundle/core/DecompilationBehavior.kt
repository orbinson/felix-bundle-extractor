package be.orbinson.felix.bundle.core

/**
 * Controls how the extractor behaves when Maven sources cannot be downloaded.
 */
enum class DecompilationBehavior {
    /** Always attempt decompilation with Fernflower/Vineflower. */
    ALWAYS_DECOMPILE,

    /** Never decompile; the bundle will have no sources attached. */
    NEVER_DECOMPILE,

    /**
     * Ask the caller.  In the CLI this maps to ALWAYS_DECOMPILE.
     * In the plugin a dialog is shown.
     */
    ASK,
}
