plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("org.jetbrains.intellij.platform") version "2.10.5" apply false
}

subprojects {
    group = "be.orbinson.felix"
    version = "2.0.0-SNAPSHOT"

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }
}
