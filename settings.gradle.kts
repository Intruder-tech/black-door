pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    // Provisions the JDK 25 toolchain automatically (Velocity API is Java 25 bytecode).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        // Each third-party repository is restricted to the groups we expect from it
        // (mitigates dependency-confusion style substitution).
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
            content {
                includeGroupByRegex("io[.]papermc([.].*)?")
                includeGroup("com.velocitypowered")
                includeGroup("com.mojang")
                includeGroup("net.md-5")
            }
        }
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            name = "spigotmc"
            content {
                includeGroup("org.spigotmc")
                includeGroup("org.bukkit")
                includeGroup("net.md-5")
            }
        }
        maven("https://repo.codemc.io/repository/maven-releases/") {
            name = "codemc"
            content {
                includeGroup("com.github.retrooper")
            }
        }
    }
}

rootProject.name = "black-door"

include(
    "blackdoor-api",
    "blackdoor-common",
    "blackdoor-config",
    "blackdoor-protocol",
    "blackdoor-protocol-packetevents",
    "blackdoor-violations",
    "blackdoor-state",
    "blackdoor-packets",
    "blackdoor-core",
    "blackdoor-platform-bukkit",
    "blackdoor-platform-paper",
    "blackdoor-proxy-velocity",
    "blackdoor-proxy-bungee",
    "dist-bukkit",
    "blackdoor-benchmarks",
)
