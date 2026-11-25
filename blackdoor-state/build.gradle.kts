plugins {
    id("blackdoor.java-conventions")
}

description = "Player state engine: per-connection sessions built from packets (single writer: the connection's network thread)."

dependencies {
    api(project(":blackdoor-protocol"))
    testImplementation(testFixtures(project(":blackdoor-common")))
}
