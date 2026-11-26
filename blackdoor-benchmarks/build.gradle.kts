plugins {
    id("blackdoor.java-conventions")
}

description = "Measurement harnesses (never shipped). Milestone 2.3: world-view memory spike (OD-3)."

/** Usage: ./gradlew :blackdoor-benchmarks:worldViewSpike -Pregions=<path to region folder> */
tasks.register<JavaExec>("worldViewSpike") {
    group = "verification"
    description = "Measures memory and build cost of candidate world-view representations on real region files."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.intruder_tech.blackdoor.benchmarks.worldview.WorldViewSpike"
    jvmArgs("-Xms2g", "-Xmx2g", "-XX:+UseG1GC")
    args(providers.gradleProperty("regions").getOrElse("missing"))
}

/** Usage: ./gradlew :blackdoor-benchmarks:networkOverhead */
tasks.register<JavaExec>("networkOverhead") {
    group = "verification"
    description = "Times the connection guard per new connection."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.intruder_tech.blackdoor.benchmarks.network.NetworkOverhead"
}

dependencies {
    implementation(project(":blackdoor-network"))
}
