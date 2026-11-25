plugins {
    id("blackdoor.java-conventions")
    alias(libs.plugins.shadow)
}

description = "Velocity proxy adapter (Velocity API 4.x, Java 25)."

dependencies {
    implementation(project(":blackdoor-core"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}

// Velocity API 4.x is Java 25 bytecode, so this module must target 25.
tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

// Compile-time constants for the @Plugin annotation.
val generateBuildConstants by tasks.registering(Copy::class) {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/templates/java"))
    expand("version" to pluginVersion)
}
sourceSets.main {
    java.srcDir(generateBuildConstants.map { it.destinationDir })
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveBaseName = "BlackDoor-Velocity"
    archiveClassifier = ""
    relocate("org.yaml.snakeyaml", "io.github.intruder_tech.blackdoor.libs.snakeyaml")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class", "module-info.class")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
