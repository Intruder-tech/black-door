plugins {
    id("blackdoor.java-conventions")
}

description = "Composition root: lifecycle, module wiring, platform SPI, commands."

dependencies {
    api(project(":blackdoor-api"))
    api(project(":blackdoor-common"))
    api(project(":blackdoor-config"))
    api(project(":blackdoor-protocol"))
    api(project(":blackdoor-packets"))
    api(project(":blackdoor-network"))
    api(project(":blackdoor-auth"))
    api(project(":blackdoor-enforcement"))
    testImplementation(testFixtures(project(":blackdoor-common")))
}
