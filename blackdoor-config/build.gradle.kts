plugins {
    id("blackdoor.java-conventions")
}

description = "Configuration framework: safe YAML loading, validation, migrations, atomic reload."

dependencies {
    api(project(":blackdoor-common"))
    // Shaded and relocated in distribution jars (never use the server's copy).
    implementation(libs.snakeyaml)
    testImplementation(testFixtures(project(":blackdoor-common")))
}
