import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "com.jiec.ghc"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.1.7")
        // The bundled GitHub plugin whose account store we log in to.
        bundledPlugin("org.jetbrains.plugins.github")
        // AccountManager / AccountManagerBase live in this V2 platform module.
        bundledModule("intellij.platform.collaborationTools")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.7")
        }
    }
}

kotlin {
    jvmToolchain(21)
}
