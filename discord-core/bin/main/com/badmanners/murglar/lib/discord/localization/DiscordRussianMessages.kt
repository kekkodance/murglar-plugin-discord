package com.badmanners.murglar.lib.discord.localization

import com.badmanners.murglar.lib.core.localization.RussianMessages


object DiscordRussianMessages : RussianMessages(), DiscordMessages {
    override val serviceName = "Discord"
    override val enableRichPresence = "Включить статус Discord"
    override val showArt = "Показывать обложки"
    override val catboxHash = "User Hash для Catbox (необязательно)"
    override val appId = "ID приложения Discord"
}