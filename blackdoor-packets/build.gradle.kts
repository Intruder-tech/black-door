plugins {
    id("blackdoor.java-conventions")
}

description = "Packet security engine: deterministic validation of inbound packets (docs/PROTOCOL_ARCHITECTURE.md section 4)."

dependencies {
    api(project(":blackdoor-state"))
    api(project(":blackdoor-violations"))
    api(project(":blackdoor-config"))
    testImplementation(testFixtures(project(":blackdoor-common")))
}
