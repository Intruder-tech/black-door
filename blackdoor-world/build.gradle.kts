plugins {
    id("blackdoor.java-conventions")
}

description = "World view (ADR-017): shared position-keyed collision cache and per-player overlay."

dependencies {
    api(project(":blackdoor-protocol"))
    testImplementation(testFixtures(project(":blackdoor-common")))
}
