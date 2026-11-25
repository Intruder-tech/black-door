// Shared conventions for every Black Door Java module.
plugins {
    `java-library`
}

group = "io.github.intruder-tech"

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        // One JDK builds everything; bytecode level is set per module via `options.release`.
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Default bytecode level: Java 17 (ADR-002). Modules may raise it (e.g. Velocity: 25).
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial,-options", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj").get())
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Tests must be runnable on the minimum supported runtime too; CI adds a Java 17 run.
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Pin every resolved dependency version (SR-100). Regenerate with: ./gradlew build --write-locks
dependencyLocking {
    lockAllConfigurations()
}
