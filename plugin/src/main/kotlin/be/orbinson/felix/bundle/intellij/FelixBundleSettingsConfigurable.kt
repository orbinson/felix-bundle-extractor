package be.orbinson.felix.bundle.intellij

import be.orbinson.felix.bundle.core.DecompilationBehavior
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.*

class FelixBundleSettingsConfigurable(private val project: Project) : Configurable {

    private var bundlesDirField: TextFieldWithBrowseButton? = null
    private var mavenRepositoryUrlField: JBTextField? = null
    private var includeGroupIdsField: JBTextField? = null
    private var excludeGroupIdsField: JBTextField? = null
    private var excludeArtifactIdsField: JBTextField? = null
    private var excludeNonMavenCheckbox: JBCheckBox? = null
    private var decompilationBehaviorCombo: JComboBox<DecompilationBehavior>? = null
    private var decompilationTimeoutSpinner: JSpinner? = null
    private var extractButton: JButton? = null

    override fun getDisplayName(): String = "Felix Bundle Extractor"

    override fun createComponent(): JComponent {
        val field = TextFieldWithBrowseButton()
        field.addBrowseFolderListener(
            "Felix Bundles Directory",
            "Select the Felix launchpad bundles directory (e.g. crx-quickstart/launchpad/felix)",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        bundlesDirField = field

        val mavenRepoField = JBTextField()
        mavenRepositoryUrlField = mavenRepoField

        val includeField = JBTextField()
        includeGroupIdsField = includeField

        val excludeGroupField = JBTextField()
        excludeGroupIdsField = excludeGroupField

        val excludeArtifactField = JBTextField()
        excludeArtifactIdsField = excludeArtifactField

        val excludeNonMaven = JBCheckBox("Exclude bundles without embedded pom.xml")
        excludeNonMavenCheckbox = excludeNonMaven

        val combo = JComboBox(DecompilationBehavior.entries.toTypedArray())
        combo.setRenderer { _, value, _, _, _ ->
            JBLabel(when (value) {
                DecompilationBehavior.ASK -> "Ask before decompiling"
                DecompilationBehavior.ALWAYS_DECOMPILE -> "Always decompile"
                DecompilationBehavior.NEVER_DECOMPILE -> "Never decompile"
                null -> ""
            })
        }
        decompilationBehaviorCombo = combo

        val timeoutSpinner = JSpinner(SpinnerNumberModel(60, 0, 3600, 10))
        decompilationTimeoutSpinner = timeoutSpinner

        val button = JButton("Extract / Refresh Bundles Now")
        button.addActionListener {
            applySettings()
            FelixBundleExtractor.extractAllInBackground(project)
        }
        extractButton = button

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Bundles directory:"), field, 1, false)
            .addLabeledComponent(JBLabel("Maven repository URL:"), mavenRepoField, 1, false)
            .addSeparator()
            .addLabeledComponent(JBLabel("Include groupIds (regex, leave blank for all):"), includeField, 1, false)
            .addLabeledComponent(JBLabel("Exclude groupIds (regex):"), excludeGroupField, 1, false)
            .addLabeledComponent(JBLabel("Exclude artifactIds (regex):"), excludeArtifactField, 1, false)
            .addComponent(excludeNonMaven)
            .addSeparator()
            .addLabeledComponent(JBLabel("When bundle sources cannot be found:"), combo, 1, false)
            .addLabeledComponent(JBLabel("Decompilation timeout (seconds, 0 = no limit):"), timeoutSpinner, 1, false)
            .addSeparator()
            .addComponent(button)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = FelixBundleSettings.getInstance(project)
        return bundlesDirField?.text != settings.bundlesDirectory ||
                mavenRepositoryUrlField?.text != settings.mavenRepositoryUrl ||
                includeGroupIdsField?.text != settings.includeGroupIds ||
                excludeGroupIdsField?.text != settings.excludeGroupIds ||
                excludeArtifactIdsField?.text != settings.excludeArtifactIds ||
                excludeNonMavenCheckbox?.isSelected != settings.excludeNonMavenArtifacts ||
                decompilationBehaviorCombo?.selectedItem != settings.decompilationBehavior ||
                (decompilationTimeoutSpinner?.value as? Int) != settings.decompilationTimeoutSeconds
    }

    override fun apply() { applySettings() }

    private fun applySettings() {
        val settings = FelixBundleSettings.getInstance(project)
        settings.bundlesDirectory = bundlesDirField?.text?.trim() ?: ""
        settings.mavenRepositoryUrl = mavenRepositoryUrlField?.text?.trim() ?: "https://repo1.maven.org/maven2"
        settings.includeGroupIds = includeGroupIdsField?.text?.trim() ?: ""
        settings.excludeGroupIds = excludeGroupIdsField?.text?.trim() ?: ""
        settings.excludeArtifactIds = excludeArtifactIdsField?.text?.trim() ?: ""
        settings.excludeNonMavenArtifacts = excludeNonMavenCheckbox?.isSelected ?: false
        settings.decompilationBehavior =
            (decompilationBehaviorCombo?.selectedItem as? DecompilationBehavior) ?: DecompilationBehavior.ASK
        settings.decompilationTimeoutSeconds = (decompilationTimeoutSpinner?.value as? Int) ?: 60
    }

    override fun reset() {
        val settings = FelixBundleSettings.getInstance(project)
        bundlesDirField?.text = settings.bundlesDirectory
        mavenRepositoryUrlField?.text = settings.mavenRepositoryUrl
        includeGroupIdsField?.text = settings.includeGroupIds
        excludeGroupIdsField?.text = settings.excludeGroupIds
        excludeArtifactIdsField?.text = settings.excludeArtifactIds
        excludeNonMavenCheckbox?.isSelected = settings.excludeNonMavenArtifacts
        decompilationBehaviorCombo?.selectedItem = settings.decompilationBehavior
        decompilationTimeoutSpinner?.value = settings.decompilationTimeoutSeconds
    }
}
