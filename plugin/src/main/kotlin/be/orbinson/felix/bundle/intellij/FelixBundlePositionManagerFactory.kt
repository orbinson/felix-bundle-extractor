package be.orbinson.felix.bundle.intellij

import com.intellij.debugger.NoDataException
import com.intellij.debugger.PositionManager
import com.intellij.debugger.PositionManagerFactory
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest

private val LOG = Logger.getInstance(FelixBundlePositionManagerFactory::class.java)

class FelixBundlePositionManagerFactory : PositionManagerFactory() {
    override fun createPositionManager(process: DebugProcess): PositionManager = FelixBundlePositionManager(process)
}

/**
 * The built-in debugger reads the decompiled line mapping eagerly: when resolving stop and stack-frame
 * lines, and when translating breakpoints (`CompoundPositionManager.locationsOfLine` reads it before any
 * position manager is asked). Custom position managers are consulted first, so this one installs the
 * mapping on the source file and then defers to the built-in manager.
 */
private class FelixBundlePositionManager(private val process: DebugProcess) : PositionManager {

    override fun getSourcePosition(location: Location?): SourcePosition? {
        location ?: throw NoDataException.INSTANCE
        val topLevelName = location.declaringType().name().substringBefore('$')
        installMapping(topLevelName) {
            val psiClass = JavaPsiFacade.getInstance(process.project).findClass(topLevelName, process.searchScope)
            psiClass?.navigationElement?.containingFile?.virtualFile.also {
                LOG.debug { "getSourcePosition($topLevelName): class=${psiClass != null}, source=${it?.path}" }
            }
        }
        throw NoDataException.INSTANCE
    }

    // Breakpoints: called for loaded classes before locationsOfLine.
    override fun getAllClasses(classPosition: SourcePosition): List<ReferenceType> {
        installMapping(classPosition)
        throw NoDataException.INSTANCE
    }

    override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> =
        throw NoDataException.INSTANCE

    // Breakpoints: called for classes that are not loaded yet; locationsOfLine follows on class prepare.
    override fun createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest? {
        installMapping(position)
        throw NoDataException.INSTANCE
    }

    private fun installMapping(position: SourcePosition) {
        installMapping(position.file.name) { position.file.virtualFile }
    }

    private fun installMapping(name: String, findSourceFile: () -> VirtualFile?) {
        try {
            ReadAction.run<RuntimeException> {
                findSourceFile()?.let { FelixBundleLineMapping.installIfDecompiled(process.project, it) }
            }
        } catch (e: ProcessCanceledException) {
            // The platform requires cancellation to propagate.
            throw e
        } catch (e: Exception) {
            // E.g. IndexNotReadyException while indexing; the built-in manager must still run.
            LOG.debug("Could not install line mapping for $name", e)
        }
    }
}
