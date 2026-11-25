plugins {
    id("blackdoor.java-conventions")
    alias(libs.plugins.shadow)
}

description = "Universal Bukkit-family jar: Bukkit, Spigot, Paper, Purpur, Folia (1.8.8 and newer)."

dependencies {
    implementation(project(":blackdoor-platform-bukkit"))
    implementation(project(":blackdoor-platform-paper"))
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveBaseName = "BlackDoor-Bukkit"
    archiveClassifier = ""
    // Never depend on the server's copy of shaded libraries (version skew, CVEs).
    relocate("org.yaml.snakeyaml", "io.github.intruder_tech.blackdoor.libs.snakeyaml")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class", "module-info.class")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
