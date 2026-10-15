package com.anish1050.hires.data.api

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(val status: Int, message: String) : IOException(message)

// Thin OkHttp wrapper over the backend; every call is snake_case JSON in and
// out of the { success, data } envelope. A 401 signs the user out.
class HiResApi(private val baseUrl: String, private val auth: AuthStore) {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun absoluteUrl(path: String): String = if (path.startsWith("http")) path else "$baseUrl$path"

    suspend fun signInWithGoogle(idToken: String): AuthResult =
        send(AuthResult.serializer(), "POST", "/api/auth/google", jsonBody(buildJsonObject { put("id_token", idToken) }), authed = false)

    suspend fun me(): Me = send(Me.serializer(), "GET", "/api/me")

    suspend fun saveTaste(genres: List<String>, artistIds: List<String>) {
        val body = buildJsonObject {
            putJsonArray("genres") { genres.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("artist_ids") { artistIds.forEach { add(JsonPrimitive(it)) } }
        }
        sendRaw("PATCH", "/api/me/taste", jsonBody(body))
    }

    suspend fun deleteAccount() {
        sendRaw("DELETE", "/api/me")
    }

    suspend fun home(): Home = send(Home.serializer(), "GET", "/api/home")
    suspend fun discover(): Discover = send(Discover.serializer(), "GET", "/api/discover")
    suspend fun search(query: String): SearchResults = send(SearchResults.serializer(), "GET", "/api/search?q=${encode(query)}")
    suspend fun album(id: String): AlbumDetail = send(AlbumDetail.serializer(), "GET", "/api/albums/${encode(id)}")
    suspend fun artist(id: String): ArtistDetail = send(ArtistDetail.serializer(), "GET", "/api/artists/${encode(id)}")
    suspend fun stats(period: String): Stats = send(Stats.serializer(), "GET", "/api/stats?period=$period")
    suspend fun credits(): List<Credit> = withContext(Dispatchers.IO) {
        execute(request("GET", "/api/credits", authed = false)).jsonObject["credits"]!!.let { json.decodeFromJsonElement(ListSerializer(Credit.serializer()), it) }
    }

    suspend fun libraryTracks(): List<Track> = libraryItems("track", Track.serializer())
    suspend fun libraryAlbums(): List<AlbumRef> = libraryItems("album", AlbumRef.serializer())
    suspend fun libraryArtists(): List<ArtistRef> = libraryItems("artist", ArtistRef.serializer())

    suspend fun saveToLibrary(kind: String, itemId: String) {
        sendRaw("POST", "/api/library", jsonBody(buildJsonObject { put("kind", kind); put("item_id", itemId) }))
    }

    suspend fun removeFromLibrary(kind: String, itemId: String) {
        sendRaw("DELETE", "/api/library/$kind/${encode(itemId)}")
    }

    suspend fun postPlays(events: List<PlayEventDto>) {
        val body = buildJsonObject { put("events", json.encodeToJsonElement(ListSerializer(PlayEventDto.serializer()), events)) }
        sendRaw("POST", "/api/plays", jsonBody(body))
    }

    suspend fun recognize(pcm16Mono16k: ByteArray): Recognition =
        send(Recognition.serializer(), "POST", "/api/recognize", pcm16Mono16k.toRequestBody(OCTET_STREAM))

    suspend fun requestTrack(title: String, artist: String) {
        sendRaw("POST", "/api/recognize/request", jsonBody(buildJsonObject { put("title", title); put("artist", artist) }))
    }

    suspend fun verifyPurchase(purchaseToken: String): EntitlementDto =
        send(EntitlementDto.serializer(), "POST", "/api/billing/google/verify", jsonBody(buildJsonObject { put("purchase_token", purchaseToken) }))

    /** Blocking: called on ExoPlayer's loading thread when a track URI is resolved. */
    fun audioUrlBlocking(trackId: String, quality: String, purpose: String): AudioUrl =
        json.decodeFromJsonElement(AudioUrl.serializer(), execute(request("GET", "/api/tracks/${encode(trackId)}/$purpose?quality=$quality")))

    private suspend fun <T> libraryItems(kind: String, serializer: KSerializer<T>): List<T> = withContext(Dispatchers.IO) {
        val items = execute(request("GET", "/api/library?kind=$kind")).jsonObject["items"]!!.jsonArray
        items.map { json.decodeFromJsonElement(serializer, it) }
    }

    private suspend fun <T> send(serializer: KSerializer<T>, method: String, path: String, body: RequestBody? = null, authed: Boolean = true): T =
        withContext(Dispatchers.IO) { json.decodeFromJsonElement(serializer, execute(request(method, path, body, authed))) }

    private suspend fun sendRaw(method: String, path: String, body: RequestBody? = null) {
        withContext(Dispatchers.IO) { execute(request(method, path, body)) }
    }

    private fun request(method: String, path: String, body: RequestBody? = null, authed: Boolean = true): Request {
        val builder = Request.Builder().url("$baseUrl$path").method(method, body)
        if (authed) auth.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }

    private fun execute(request: Request): JsonElement = client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (response.code == 401) auth.clear()
        if (!response.isSuccessful) throw ApiException(response.code, text.take(200))
        json.parseToJsonElement(text).jsonObject["data"] ?: throw ApiException(response.code, "missing data")
    }

    private fun jsonBody(element: JsonElement) = element.toString().toRequestBody(JSON)
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
