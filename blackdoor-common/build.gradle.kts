plugins {
    id("blackdoor.java-conventions")
    `java-test-fixtures`
}

description = "Shared primitives: time sources, rate limiters, logging, bounded executors."

dependencies {
    api(project(":blackdoor-api"))
}
