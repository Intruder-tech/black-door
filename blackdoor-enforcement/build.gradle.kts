plugins {
    id("blackdoor.java-conventions")
}

description = "Decides when findings justify acting on a player (docs/FALSE_POSITIVE_POLICY.md)."

dependencies {
    api(project(":blackdoor-common"))
    api(project(":blackdoor-config"))
    api(project(":blackdoor-violations"))
    testImplementation(testFixtures(project(":blackdoor-common")))
}
