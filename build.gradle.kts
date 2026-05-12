plugins {
    alias(libs.plugins.loom)
    alias(libs.plugins.mod.publish)
    `maven-publish`
}

val modVersion: String by project
val branchName: String by project
val slug: String by project
val compatibleVersions: String by project

version = "$modVersion+$branchName"

repositories {
    exclusiveContent {
        // Modrinth Maven - see: https://support.modrinth.com/en/articles/8801191-modrinth-maven
        // Mods may be pulled from here will this format: "maven.modrinth:<slug>:<version>"
        forRepositories(maven("https://api.modrinth.com/maven")).filter {
            includeGroup("maven.modrinth")
        }
    }

    maven("https://maven.nucleoid.xyz/")
    maven("https://jitpack.io/")
    mavenCentral()
}

loom.splitEnvironmentSourceSets()

dependencies {
    minecraft(libs.minecraft)
    api(libs.bundles.fabric)
    implementation(libs.plasmid)
    implementation(libs.permissions)
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.processResources {
    val user: String by project
    val authors: String by project
    val contributors: String by project

    val meta: Map<String, Any> = mapOf(
        "version" to project.version,
        "modId" to providers.gradleProperty("modId"),
        "modName" to providers.gradleProperty("modName"),
        "modDescription" to providers.gradleProperty("modDescription"),
        "homepage" to "https://modrinth.com/mod/$slug",
        "issues" to "https://github.com/$user/$slug/issues",
        "sources" to "https://github.com/$user/$slug",
        "license" to providers.gradleProperty("license"),
        "authors" to authors.split(", ").joinToString("\",\n    \""),
        "contributors" to contributors.split(", ").joinToString("\",\n    \""),
        "members" to "$authors${if (contributors.isEmpty()) "" else ". Contributions by $contributors."}",
        "minecraftVersion" to compatibleVersions.split(", ")[0],
        "fabricLoaderVersion" to libs.versions.fabric.loader,
        "fabricApiVersion" to libs.versions.fabric.api
    )

    inputs.properties(meta)

    filesMatching(listOf("*.mod.json", "META-INF/*mods.toml")) {
        // providers must be invoked manually or else you get stuff like "provider(?)" instead of "mod-id"
        expand(meta.mapValues { when (val value = it.value) {
            is Provider<*> -> value.get()
            else -> value
        }})
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

publishMods {
    file = tasks.jar.flatMap { it.archiveFile }
    additionalFiles.from(tasks.named("sourcesJar"))
    changelog = providers.environmentVariable("CHANGELOG")

    type = version.map { when {
        it.contains("alpha") -> ALPHA
        it.contains("beta") -> BETA
        else -> STABLE
    }}

    val modrinthId: String by project
    val compatibleLoaders: String by project
    val readme: RegularFile = rootProject.layout.projectDirectory.file("README.md")

    modrinth {
        projectId = modrinthId
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")

        minecraftVersions.addAll(compatibleVersions.split(", "))
        modLoaders.addAll(compatibleLoaders.split(", "))

        projectDescription = providers.fileContents(readme).asText.map {
            "<!--DO NOT EDIT MANUALLY: synced from gh readme-->\n$it"
        }

        requires {
            slug = "fabric-api"
            version = libs.versions.fabric.api
        }
    }
}
