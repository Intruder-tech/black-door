plugins {
    id("blackdoor.java-conventions")
}

description = "Paper 1.20+ and Folia adapter: region- and entity-aware scheduling."

dependencies {
    implementation(project(":blackdoor-core"))
    // Lowest Paper API with region schedulers; the same calls work on Folia.
    compileOnly(libs.paper.api)
}
