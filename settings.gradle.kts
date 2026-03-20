rootProject.name = "murglar-plugin-discord"

include("discord-core")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven { url = java.net.URI.create("https://jitpack.io") }
    }
    resolutionStrategy {
        eachPlugin {
            // workaround for requesting plugins from plain maven repositories with new syntax
            val prefix = "murglar-gradle-plugin-"
            if (requested.id.id.startsWith(prefix)) {
                val artifactId = "${requested.id.id.substringAfter(prefix)}-plugin-gradle-plugin"
                useModule("com.github.badmannersteam.murglar-plugins:$artifactId:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("catalog") {
            version("murglar-plugins", "7.0")
            version("discord", "3")  // use a single number

            // for core module
            plugin("murglar-plugin-core", "murglar-gradle-plugin-core").versionRef("murglar-plugins")
        }
    }
}
