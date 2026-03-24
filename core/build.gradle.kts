plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // Parse embedded pom.xml (groupId / artifactId / version)
    implementation("org.apache.maven:maven-model:3.9.6")

    // Fernflower-compatible decompiler available on Maven Central.
    // Vineflower is a maintained fork that uses the same package and class names
    // (org.jetbrains.java.decompiler.*) so core code compiles against it.
    // Inside the plugin the bundled java-decompiler.jar takes precedence at runtime.
    implementation("org.vineflower:vineflower:1.11.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}
