plugins {
    id("dev.kikugie.loom-back-compat")
}

group = property("maven_group") as String
version = "${property("mod_version")}+${property("minecraft_version")}"
base.archivesName = property("archives_base_name") as String

val archiveBaseName = property("archives_base_name") as String
val rootLicenseFile = rootProject.file("LICENSE")

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") {
        name = "Fabric"
    }
    maven("https://maven.isxander.dev/releases") {
        name = "Xander Maven"
    }
    maven("https://maven.terraformersmc.com/releases") {
        name = "TerraformersMC"
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("dev.isxander:yet-another-config-lib:${property("yacl_version")}")
    modImplementation("com.terraformersmc:modmenu:${property("modmenu_version")}")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 25
    }

    processResources {
        val props = mapOf(
            "id" to project.property("mod_id"),
            "name" to project.property("mod_name"),
            "version" to project.version,
            "description" to project.property("mod_description"),
            "minecraft_dependency" to project.property("minecraft_dependency"),
            "fabric_loader_version" to project.property("fabric_loader_version"),
            "fabric_api_version" to project.property("fabric_api_version"),
            "yacl_dependency_version" to project.property("yacl_dependency_version"),
            "modmenu_version" to project.property("modmenu_version"),
        )

        inputs.properties(props)
        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }

    jar {
        from(rootLicenseFile) {
            rename { "${it}_${archiveBaseName}" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the active target and copies jars into the root build/libs directory."

        dependsOn(named("build"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod_version")}"))
    }
}
