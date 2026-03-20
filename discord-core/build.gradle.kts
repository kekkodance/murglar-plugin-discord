plugins {
    alias(catalog.plugins.murglar.plugin.core)
}

murglarPlugin {
    id = "discord"
    name = "Discord Rich Presence"
    version = catalog.versions.discord.map(String::toInt)
    entryPointClass = "com.badmanners.murglar.lib.discord.DiscordMurglar"
}

tasks.jar {
    enabled = false
}

repositories {
    mavenCentral()
    maven("https://maven.firstdark.dev/releases")
}

dependencies {
    implementation("dev.firstdark.discordrpc:discord-rpc:1.0.4")
    implementation("com.google.code.gson:gson:2.10.1")
}
