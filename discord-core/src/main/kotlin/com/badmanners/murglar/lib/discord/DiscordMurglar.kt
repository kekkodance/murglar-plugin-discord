package com.badmanners.murglar.lib.discord

import com.badmanners.murglar.lib.core.localization.DefaultMessages.Companion.DEFAULT
import com.badmanners.murglar.lib.core.localization.RussianMessages.Companion.RUSSIAN
import com.badmanners.murglar.lib.core.log.LoggerMiddleware
import com.badmanners.murglar.lib.core.model.node.Node
import com.badmanners.murglar.lib.core.model.tag.Lyrics
import com.badmanners.murglar.lib.core.model.tag.Tags
import com.badmanners.murglar.lib.core.model.track.source.Bitrate
import com.badmanners.murglar.lib.core.model.track.source.Extension
import com.badmanners.murglar.lib.core.model.track.source.Source
import com.badmanners.murglar.lib.core.network.NetworkMiddleware
import com.badmanners.murglar.lib.core.notification.NotificationMiddleware
import com.badmanners.murglar.lib.core.preference.EditPreference
import com.badmanners.murglar.lib.core.preference.Preference
import com.badmanners.murglar.lib.core.preference.PreferenceMiddleware
import com.badmanners.murglar.lib.core.preference.SwitchPreference
import com.badmanners.murglar.lib.core.service.BaseMurglar
import com.badmanners.murglar.lib.core.utils.getBoolean
import com.badmanners.murglar.lib.core.utils.getString
import com.badmanners.murglar.lib.discord.localization.DiscordDefaultMessages
import com.badmanners.murglar.lib.discord.localization.DiscordMessages
import com.badmanners.murglar.lib.discord.localization.DiscordRussianMessages
import com.badmanners.murglar.lib.core.login.LoginResolver
import com.badmanners.murglar.lib.core.node.*
import com.badmanners.murglar.lib.core.decrypt.Decryptor
import com.badmanners.murglar.lib.core.login.CredentialLoginStep
import com.badmanners.murglar.lib.core.login.CredentialsLoginVariant
import com.badmanners.murglar.lib.core.login.WebLoginVariant
import com.badmanners.murglar.lib.core.webview.WebViewProvider
import com.badmanners.murglar.lib.core.model.track.BaseTrack
import dev.firstdark.rpc.*
import dev.firstdark.rpc.models.DiscordRichPresence
import dev.firstdark.rpc.handlers.*
import dev.firstdark.rpc.models.User
import dev.firstdark.rpc.models.DiscordJoinRequest
import dev.firstdark.rpc.enums.*
import dev.codeman.smtc4j.SMTC4J
import dev.codeman.smtc4j.MediaInfo
import dev.codeman.smtc4j.PlaybackState
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.net.HttpURLConnection
import java.net.URL


class DiscordMurglar(
    id: String,
    preferences: PreferenceMiddleware,
    network: NetworkMiddleware,
    notifications: NotificationMiddleware,
    logger: LoggerMiddleware
) : BaseMurglar<DiscordTrack, DiscordMessages>(
    id, MESSAGES, preferences, network, notifications, logger, DiscordDecryptor(logger)
) {

    companion object {
        const val SERVICE_ID = "discord"

        private val MESSAGES = mapOf(
            DEFAULT to DiscordDefaultMessages,
            RUSSIAN to DiscordRussianMessages
        )
    }

    override val loginResolver = DiscordLoginResolver(preferences, network, notifications, this, messages)

    override val nodeResolver = DiscordNodeResolver(this, messages)

    override val murglarPreferences: List<Preference>
        get() = mutableListOf<Preference>().apply {
            this += SwitchPreference(
                id = "discord_enabled",
                title = messages.enableRichPresence,
                summary = null,
                getter = { preferences.getBoolean("discord_enabled", true) },
                setter = { preferences.setBoolean("discord_enabled", it) },
                refreshAllAfterChange = true
            )
            this += SwitchPreference(
                id = "discord_show_art",
                title = messages.showArt,
                summary = null,
                getter = { preferences.getBoolean("discord_show_art", true) },
                setter = { preferences.setBoolean("discord_show_art", it) }
            )
            this += EditPreference(
                id = "catbox_user_hash",
                title = messages.catboxHash,
                message = "Required for uploading to your personal Catbox account",
                getter = { preferences.getString("catbox_user_hash", "") },
                setter = { preferences.setString("catbox_user_hash", it) }
            )
            this += EditPreference(
                id = "discord_app_id",
                title = messages.appId,
                message = "Override the default application ID",
                getter = { preferences.getString("discord_app_id", applicationId) },
                setter = { preferences.setString("discord_app_id", it) }
            )
        }

    override val possibleFormats = listOf(
        Extension.UNKNOWN to Bitrate.B_UNKNOWN
    )

    private val rpc = DiscordRpc()
    private val applicationId = "1484288190998384791"
    private val pollExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "DiscordRPC-Poll").apply { isDaemon = true }
    }
    private val uploadExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "DiscordRPC-Upload").apply { isDaemon = true }
    }
    @Volatile private var uploadTask: ScheduledFuture<*>? = null

    private val rpcHandler = object : DiscordEventHandler {
        override fun ready(user: User?) {
            logger.i("DiscordRPC", "Discord RPC Ready: ${user?.username}")
        }
        override fun disconnected(errorCode: ErrorCode?, message: String?) {
            logger.w("DiscordRPC", "Discord RPC Disconnected: $errorCode - $message")
        }
        override fun errored(errorCode: ErrorCode?, message: String?) {
            logger.e("DiscordRPC", "Discord RPC Errored: $errorCode - $message")
        }
        override fun joinGame(joinSecret: String?) {}
        override fun spectateGame(spectateSecret: String?) {}
        override fun joinRequest(request: DiscordJoinRequest?) {}
    }

    override suspend fun onCreate() {
        val appId = preferences.getString("discord_app_id", applicationId).ifEmpty { applicationId }
        rpc.init(appId, rpcHandler, false)
        
        try {
            if (!SMTC4J.isLoaded()) {
                SMTC4J.load()
                
                pollExecutor.scheduleWithFixedDelay({
                    try {
                        val mediaInfo = SMTC4J.parsedMediaInfo()
                        val state = SMTC4J.parsedPlaybackState()
                        updatePresenceFromSmtc(mediaInfo, state)
                    } catch (e: Exception) {
                        logger.e("DiscordRPC", "SMTC Polling Error: ${e.message}")
                    }
                }, 0, 1000, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            logger.e("DiscordRPC", "SMTC Load Error: ${e.message}")
        }
    }

    private fun uploadToCatbox(base64: String?): String {
        if (base64.isNullOrEmpty() || !preferences.getBoolean("discord_show_art", true)) return "murglar"
        val userHash = preferences.getString("catbox_user_hash", "")
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://catbox.moe/user/api.php")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            val boundary = "---murglarPluginBoundary"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.doOutput = true
            
            val bytes = java.util.Base64.getDecoder().decode(base64)
            conn.outputStream.use { os -> 
                os.write(("--$boundary\r\n").toByteArray())
                os.write(("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n").toByteArray())
                if (userHash.isNotEmpty()) {
                    os.write(("--$boundary\r\n").toByteArray())
                    os.write(("Content-Disposition: form-data; name=\"userhash\"\r\n\r\n$userHash\r\n").toByteArray())
                }
                os.write(("--$boundary\r\n").toByteArray())
                os.write(("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"cover.jpg\"\r\n").toByteArray())
                os.write(("Content-Type: image/jpeg\r\n\r\n").toByteArray())
                os.write(bytes)
                os.write(("\r\n--$boundary--\r\n").toByteArray())
            }
            
            if (conn.responseCode == 200) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use {
                    logger.w("DiscordRPC", "Catbox returned ${conn.responseCode}: ${it.readText()}")
                }
            }
        } catch (e: Exception) {
            logger.e("DiscordRPC", "Catbox upload failed: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        return "murglar"
    }

    private fun clearActivity() {
        val emptyPresence = DiscordRichPresence.builder()
            .details(" ")
            .state(" ")
            .largeImageKey("")
            .largeImageText(" ")
            .smallImageKey("")
            .smallImageText(" ")
            .build()
        rpc.updatePresence(emptyPresence)
    }

    private var lastMediaTitle: String? = null
    private var lastStateCode: Int = -1
    @Volatile private var cachedArtUrl: String = "murglar"
    @Volatile private var artUpdated = false
    private var lastUpdateTimeMs: Long = 0
    private var lastPositionMs: Long = 0

    private fun updatePresenceFromSmtc(media: MediaInfo?, state: PlaybackState?) {
        if (!preferences.getBoolean("discord_enabled", true)) {
            clearActivity()
            return
        }
        if (media == null || state == null || media.title().isNullOrEmpty()) {
            if (lastMediaTitle != null) {
                clearActivity()
                lastMediaTitle = null
            }
            return
        }

        val isPlaying = state.getStateCode() == PlaybackState.StateCode.PLAYING
        val rawStateInt = state.stateCode()
        val currentPositionMs = (state.position() * 1000).toLong()

        val titleChanged = media.title() != lastMediaTitle
        val stateChanged = rawStateInt != lastStateCode
        val elapsedSinceLastUpdate = System.currentTimeMillis() - lastUpdateTimeMs
        val expectedPositionMs = lastPositionMs + elapsedSinceLastUpdate
        val positionDrift = Math.abs(currentPositionMs - expectedPositionMs)
        val seekedOrLooped = isPlaying && lastStateCode == rawStateInt && positionDrift > 3000

        val artChanged = artUpdated.also { if (it) artUpdated = false }

        if (!titleChanged && !stateChanged && !seekedOrLooped && !artChanged) {
            return
        }

        if (titleChanged) {
            uploadTask?.cancel(false)
            val thumbnail = media.thumbnailBase64()
            uploadTask = uploadExecutor.schedule({
                cachedArtUrl = uploadToCatbox(thumbnail)
                artUpdated = true
            }, 2, TimeUnit.SECONDS)
        }

        lastMediaTitle = media.title()
        lastStateCode = rawStateInt
        lastUpdateTimeMs = System.currentTimeMillis()
        lastPositionMs = currentPositionMs

        val safeTitle = if (media.title().isNullOrEmpty()) " " else media.title()
        val safeArtist = if (media.artist().isNullOrEmpty()) " " else media.artist()
        val safeAlbum = if (media.album().isNullOrEmpty()) " " else media.album()
        val safeKey = if (cachedArtUrl.isNullOrEmpty()) "murglar" else cachedArtUrl

        if (!isPlaying) {
            val builder = DiscordRichPresence.builder()
                .details(safeTitle)
                .state("Paused - $safeArtist")
                .largeImageKey(safeKey)
                .largeImageText(safeAlbum)
                .activityType(ActivityType.LISTENING)

            if (safeKey != "murglar") {
                builder.smallImageKey("murglar")
                builder.smallImageText("Murglar")
            }

            rpc.updatePresence(builder.build())
            return
        }

        val startTs = System.currentTimeMillis() - currentPositionMs

        val builder = DiscordRichPresence.builder()
            .details(safeTitle)
            .state(safeArtist)
            .largeImageKey(safeKey)
            .largeImageText(safeAlbum)
            .activityType(ActivityType.LISTENING)
            .startTimestamp(startTs)

        if (safeKey != "murglar") {
            builder.smallImageKey("murglar")
            builder.smallImageText("Murglar")
        }

        rpc.updatePresence(builder.build())
    }

    // Required Murglar interface implementations (Stubs as this is RPC only)

    override suspend fun resolveSourceForUrl(track: DiscordTrack, source: Source): Source = source

    override fun hasLyrics(track: DiscordTrack) = false

    override suspend fun getLyrics(track: DiscordTrack): Lyrics = Lyrics("")

    override suspend fun getTags(track: DiscordTrack, parent: Node?): Tags = Tags.Builder().apply {
        title = track.title
        artists = track.artistNames
    }.createTags()

    override suspend fun getTracksByMediaIds(mediaIds: List<String>): List<DiscordTrack> = emptyList()

    suspend fun loadUsername(): String? = "Discord User"
}

class DiscordTrack(
    id: String,
    title: String,
    artistIds: List<String>,
    artistNames: List<String>,
    durationMs: Long,
    serviceUrl: String
) : BaseTrack(
    id = id,
    title = title,
    artistIds = artistIds,
    artistNames = artistNames,
    durationMs = durationMs,
    sources = emptyList(),
    mediaId = id,
    serviceUrl = serviceUrl
)

class DiscordNodeResolver(
    murglar: DiscordMurglar,
    messages: DiscordMessages
) : BaseNodeResolver<DiscordMurglar, DiscordMessages>(murglar, messages) {
    override val configurations = emptyList<GenericConfiguration>()
    override suspend fun getTracksByMediaIds(mediaIds: List<String>): List<BaseTrack> = emptyList()
}

class DiscordLoginResolver(
    private val preferences: PreferenceMiddleware,
    private val network: NetworkMiddleware,
    private val notifications: NotificationMiddleware,
    private val murglar: DiscordMurglar,
    private val messages: DiscordMessages
) : LoginResolver {
    override val isLogged: Boolean get() = true
    override val loginInfo: String get() = "${messages.youAreLoggedIn}: Discord User"
    override val webLoginVariants = emptyList<WebLoginVariant>()
    override suspend fun webLogin(loginVariantId: String, webViewProvider: WebViewProvider): Boolean = false
    override val credentialsLoginVariants = emptyList<CredentialsLoginVariant>()
    override suspend fun credentialsLogin(loginVariantId: String, args: Map<String, String>): CredentialLoginStep = error("Not supported")
    override fun logout() {}
    suspend fun updateUser() {}
    fun checkLogged() {}
}

class DiscordDecryptor(private val logger: LoggerMiddleware) : Decryptor<DiscordTrack> {
    override val decryptionChunkSize = 4096
    override fun isEncrypted(track: DiscordTrack, source: Source) = false
    override suspend fun decrypt(content: ByteArray, offset: Int, length: Int, track: DiscordTrack, source: Source): ByteArray = content
}
