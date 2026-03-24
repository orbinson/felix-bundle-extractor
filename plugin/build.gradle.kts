plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

val javaHome: String = (System.getenv("JAVA_HOME")
    ?: System.getProperty("java.home")
    ?: error("No Java home found. Set the JAVA_HOME environment variable."))

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core")) {
        // Exclude vineflower from core: at runtime inside IntelliJ the bundled
        // java-decompiler.jar (same package names) is already on the classpath.
        exclude(group = "org.vineflower", module = "vineflower")
    }

    intellijPlatform {
        create("IC", "2024.2")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.java.decompiler")
        bundledPlugin("org.jetbrains.idea.maven")
    }
}

intellijPlatform {
    pluginVerification {
        ides { ide("IC", "2024.2") }
    }
    pluginConfiguration {
        name = "Felix Bundle Extractor"
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
        changeNotes = """
            <h2>2.0.0</h2>
            <ul>
                <li>Rewritten as a native IntelliJ Platform plugin</li>
                <li>Automatically attaches AEM/Felix OSGi bundles as a searchable External Library</li>
                <li>Downloads sources from Maven Central or decompiles via IntelliJ's bundled Fernflower</li>
                <li>Editor banner to fetch sources on demand</li>
                <li>Felix Bundles search scope for Find in Files / Find Usages</li>
            </ul>
        """.trimIndent()
    }
}

// Force all tasks that spawn a JVM subprocess to use JAVA_HOME,
// not the JBR bundled with the downloaded IntelliJ IDE.
tasks {
    named<org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginProjectConfigurationTask>(
        "verifyPluginProjectConfiguration"
    ) { runtimeDirectory.set(file(javaHome)) }
    named<org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask>(
        "buildSearchableOptions"
    ) { runtimeDirectory.set(file(javaHome)) }
    named<org.jetbrains.intellij.platform.gradle.tasks.PrepareTestTask>("prepareTest") {
        runtimeDirectory.set(file(javaHome))
    }
    // instrumentCode requires downloading JetBrains GUI form compiler artifacts from a
    // geo-restricted CDN. We have no .form files, so disable it entirely.
    named("instrumentCode") { enabled = false }
    named("instrumentTestCode") { enabled = false }
}
