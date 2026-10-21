package com.example.roomfinder

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Issues a single login attempt against the configured portal and classifies
 * the response. Redirects are NOT followed automatically so that a 3xx can be
 * read as a distinct signal (many portals redirect on success).
 */
class PortalClient(private val config: PortalConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun attempt(lastName: String, room: String): AttemptResult {
        val start = System.nanoTime()
        return try {
            val request = buildRequest(lastName, room)
            client.newCall(request).execute().use { resp ->
                val latency = (System.nanoTime() - start) / 1_000_000
                val body = resp.body?.string().orEmpty()
                val classification = classify(resp.code, body)
                AttemptResult(
                    lastName = lastName,
                    room = room,
                    httpStatus = resp.code,
                    latencyMs = latency,
                    classification = classification,
                    note = if (classification == Classification.SUCCESS) "matched" else "",
                )
            }
        } catch (e: IOException) {
            val latency = (System.nanoTime() - start) / 1_000_000
            AttemptResult(
                lastName = lastName,
                room = room,
                httpStatus = -1,
                latencyMs = latency,
                classification = Classification.ERROR,
                note = e.message ?: "io error",
            )
        }
    }

    private fun buildRequest(lastName: String, room: String): Request {
        val extras = parseExtras(config.extraFields)

        if (config.method == HttpMethod.GET) {
            val base = config.url.toHttpUrlOrNull()
                ?: throw IOException("Invalid URL: ${config.url}")
            val builder = base.newBuilder()
                .addQueryParameter(config.lastNameField, lastName)
                .addQueryParameter(config.roomField, room)
            extras.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            return Request.Builder().url(builder.build()).get().build()
        }

        val bodyFields = LinkedHashMap<String, String>().apply {
            put(config.lastNameField, lastName)
            put(config.roomField, room)
            putAll(extras)
        }

        val body = when (config.contentType) {
            ContentType.JSON -> {
                val json = JSONObject()
                bodyFields.forEach { (k, v) -> json.put(k, v) }
                json.toString().toRequestBody(config.contentType.mime.toMediaType())
            }
            ContentType.FORM_URLENCODED -> {
                FormBody.Builder().apply {
                    bodyFields.forEach { (k, v) -> add(k, v) }
                }.build()
            }
        }
        return Request.Builder().url(config.url).post(body).build()
    }

    private fun classify(code: Int, body: String): Classification {
        if (code == 429) return Classification.RATE_LIMITED

        val lower = body.lowercase()
        if (LOCKOUT_HINTS.any { it in lower }) return Classification.LOCKED_OUT

        // Explicit markers win when provided.
        if (config.successMarker.isNotBlank() && config.successMarker in body) {
            return Classification.SUCCESS
        }
        if (config.failureMarker.isNotBlank() && config.failureMarker in body) {
            return Classification.FAILURE
        }
        if (config.successMarker.isNotBlank() || config.failureMarker.isNotBlank()) {
            // Markers configured but none matched: infer the opposite.
            return if (config.failureMarker.isNotBlank()) Classification.SUCCESS
            else Classification.FAILURE
        }

        // No markers: fall back to status-code heuristics.
        return when (code) {
            in 200..299 -> Classification.SUCCESS
            in 300..399 -> if (config.treatRedirectAsSuccess) Classification.SUCCESS
            else Classification.FAILURE
            else -> Classification.FAILURE
        }
    }

    companion object {
        private val LOCKOUT_HINTS = listOf(
            "too many attempts", "account locked", "temporarily blocked",
            "try again later", "rate limit",
        )
    }
}

private fun parseExtras(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.split('&')
        .mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else pair.substring(0, idx).trim() to pair.substring(idx + 1).trim()
        }
        .toMap()
}
