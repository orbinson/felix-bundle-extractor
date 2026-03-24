plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "be.orbinson.felix.bundle.cli.MainKt"
    }
}

// Make the standard `assemble` task also build the fat JAR
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
