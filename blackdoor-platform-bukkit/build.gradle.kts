plugins {
    id("blackdoor.java-conventions")
}

description = "Bukkit/Spigot adapter (1.8.8 and newer). Also the entry point of the universal Bukkit-family jar."

dependencies {
    implementation(project(":blackdoor-core"))
    implementation(project(":blackdoor-protocol-packetevents"))
    // Lowest API we support; everything used here must exist in 1.8.8.
    compileOnly(libs.spigot.api.legacy)
    // The server ships Log4j; used only to keep passwords out of its log.
    compileOnly(libs.log4j.core)
}
