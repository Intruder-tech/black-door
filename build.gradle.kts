// Root build: architecture and source-rule verification (docs/ARCHITECTURE.md §2.1, SECURITY_REQUIREMENTS.md).
plugins {
    alias(libs.plugins.shadow) apply false
}

/*
 * Module layering. A module may only depend on modules in a strictly lower layer.
 * Keep in sync with docs/ARCHITECTURE.md §3.
 */
val moduleLayers = mapOf(
    "blackdoor-api" to 0,
    "blackdoor-common" to 1,
    "blackdoor-config" to 2,
    "blackdoor-protocol" to 2,
    "blackdoor-violations" to 2,
    "blackdoor-protocol-packetevents" to 3,
    "blackdoor-state" to 3,
    "blackdoor-world" to 3,
    "blackdoor-packets" to 4,
    "blackdoor-network" to 4,
    "blackdoor-core" to 5,
    "blackdoor-platform-bukkit" to 6,
    "blackdoor-platform-paper" to 6,
    "blackdoor-proxy-velocity" to 6,
    "blackdoor-proxy-bungee" to 6,
    "dist-bukkit" to 7,
    "blackdoor-benchmarks" to 8,
)

/** Modules allowed to depend on the PacketEvents adapter (ADR-003). */
val packetEventsAdapterConsumers = setOf("blackdoor-platform-bukkit", "dist-bukkit")

/** Project-to-project dependency edges, collected once every project is configured. */
val moduleEdges = sortedMapOf<String, Set<String>>()
gradle.projectsEvaluated {
    subprojects.forEach { p ->
        moduleEdges[p.name] = p.configurations
            .filter { it.name in setOf("api", "implementation", "compileOnly", "runtimeOnly", "compileOnlyApi") }
            .flatMap { it.dependencies.withType<ProjectDependency>() }
            .map { it.path.removePrefix(":") }
            .toSortedSet()
    }
}

val verifyModuleGraph by tasks.registering {
    group = "verification"
    description = "Fails if a module depends on a module in the same or a higher layer."
    val layers = moduleLayers
    val consumers = packetEventsAdapterConsumers
    val edges = moduleEdges
    doLast {
        val problems = mutableListOf<String>()
        edges.forEach { (module, depsRaw) ->
            val own = layers[module]
            if (own == null) {
                problems += "Module '$module' has no layer in moduleLayers"
                return@forEach
            }
            depsRaw.map { it.toString() }.forEach { dep ->
                val depLayer = layers[dep]
                when {
                    depLayer == null -> problems += "'$module' -> '$dep': dependency has no layer"
                    depLayer >= own -> problems += "'$module' (layer $own) -> '$dep' (layer $depLayer): must depend downward only"
                }
                if (dep == "blackdoor-protocol-packetevents" && module !in consumers) {
                    problems += "'$module' must not depend on the PacketEvents adapter"
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Module graph violations:\n  " + problems.joinToString("\n  "))
        }
        logger.lifecycle("Module graph OK (${edges.size} modules).")
    }
}

/*
 * Static source rules (SR-31, SR-80, ADR-003, ADR-010). Simple, fast, and deliberately strict.
 */
data class SourceRule(val id: String, val regex: Regex, val allowedModules: Set<String> = emptySet())

val sourceRules = listOf(
    SourceRule("SR-80 no process execution", Regex("""\bRuntime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec\b|\bnew\s+ProcessBuilder\b""")),
    SourceRule("SR-31 no Java native deserialization", Regex("""\bObjectInputStream\b""")),
    SourceRule("ADR-010 no server internals", Regex("""^\s*import\s+(net\.minecraft|org\.bukkit\.craftbukkit|com\.mojang\.authlib)\.""", RegexOption.MULTILINE)),
    SourceRule("ADR-003 PacketEvents only in adapter", Regex("""^\s*import\s+com\.github\.retrooper\.""", RegexOption.MULTILINE), setOf("blackdoor-protocol-packetevents")),
    SourceRule("No System.exit", Regex("""\bSystem\s*\.\s*exit\s*\(""")),
)

val verifySourceRules by tasks.registering {
    group = "verification"
    description = "Scans main sources for forbidden APIs."
    val roots = subprojects.associate { it.name to it.layout.projectDirectory.dir("src/main/java").asFile }
    inputs.files(roots.values.filter { it.exists() })
    doLast {
        val problems = mutableListOf<String>()
        roots.forEach { (module, root) ->
            if (!root.exists()) return@forEach
            root.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { file ->
                val text = file.readText()
                sourceRules.forEach { rule ->
                    if (module !in rule.allowedModules && rule.regex.containsMatchIn(text)) {
                        problems += "[${rule.id}] ${file.relativeTo(rootDir)}"
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Source rule violations:\n  " + problems.joinToString("\n  "))
        }
        logger.lifecycle("Source rules OK.")
    }
}

tasks.register("check") {
    group = "verification"
    dependsOn(verifyModuleGraph, verifySourceRules)
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.register("build") {
    group = "build"
    dependsOn("check")
    dependsOn(subprojects.map { "${it.path}:build" })
}
