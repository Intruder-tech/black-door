plugins {
    id("blackdoor.java-conventions")
}

description = "In-game login system: /signup, /signin, hashed passwords and 24-hour sessions (docs/AUTHENTICATION.md)."

dependencies {
    api(project(":blackdoor-common"))
    api(project(":blackdoor-config"))
    testImplementation(testFixtures(project(":blackdoor-common")))
}
