plugins {
    id("blackdoor.java-conventions")
}

description = "Movement simulation, replayed against recorded traces (docs/PHYSICS_ENGINE.md)."

dependencies {
    api(project(":blackdoor-protocol"))
}
