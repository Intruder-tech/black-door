plugins {
    id("blackdoor.java-conventions")
    alias(libs.plugins.shadow)
}

description = "BungeeCord proxy adapter (Waterfall: best-effort)."

dependencies {
    implementation(project(":blackdoor-core"))
    compileOnly(libs.bungeecord.api)
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("bungee.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveBaseName = "BlackDoor-Bungee"
    archiveClassifier = ""
    relocate("org.yaml.snakeyaml", "io.github.intruder_tech.blackdoor.libs.snakeyaml")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class", "module-info.class")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
