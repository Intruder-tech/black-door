plugins {
    id("blackdoor.java-conventions")
}

description = "Findings and the violation ledger. Phase 2: counting and rate-limited alerts; full correlation and decay arrive in Phase 5."

dependencies {
    api(project(":blackdoor-common"))
    testImplementation(testFixtures(project(":blackdoor-common")))
}
