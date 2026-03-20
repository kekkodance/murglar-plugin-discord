package com.badmanners.murglar.lib.discord.localization

import com.badmanners.murglar.lib.core.localization.DefaultMessages


object DiscordDefaultMessages : DefaultMessages(), DiscordMessages {
    override val serviceName = "Discord"
    override val enableRichPresence = "Enable Discord Presence"
    override val showArt = "Show Album Art"
    override val catboxHash = "Catbox User Hash (Optional)"
    override val appId = "Discord Application ID"
}