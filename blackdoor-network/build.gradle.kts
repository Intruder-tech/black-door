plugins {
    id("blackdoor.java-conventions")
}

description = "Connection-level protection: handshake, ping and login rate limits (docs/NETWORK_SECURITY.md section 2)."

dependencies {
    api(project(":blackdoor-config"))
    api(project(":blackdoor-violations"))
    testImplementation(testFixtures(project(":blackdoor-common")))
}
