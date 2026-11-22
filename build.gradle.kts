plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = "QYS-UTILS"
    version = libs.versions.mod.version.get()
    group = "com.quinceyys.utils"
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    mavenCentral()
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_compat" to "~26.3"
        )
        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        withSourcesJar()
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    jar {
        archiveFileName.set("QYS-UTILS.jar")
    }
}