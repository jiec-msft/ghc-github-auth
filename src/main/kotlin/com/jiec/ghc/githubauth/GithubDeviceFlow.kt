package com.jiec.ghc.githubauth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Minimal GitHub OAuth Device Flow client (https://docs.github.com/en/apps/oauth-apps/
 * building-oauth-apps/authorizing-oauth-apps#device-flow). Device flow needs only a
 * client id — no client secret — so users can bring an OAuth app their organization
 * has allow-listed.
 */
internal object GithubDeviceFlow {

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresInSec: Int,
        val intervalSec: Int,
    )

    private const val SCOPES = "repo gist workflow read:org"

    suspend fun requestDeviceCode(clientId: String): DeviceCode {
        val json = postForJson(
            "https://github.com/login/device/code",
            "client_id=${clientId.urlEncode()}&scope=${SCOPES.urlEncode()}"
        )
        return DeviceCode(
            deviceCode = json.stringOrThrow("device_code"),
            userCode = json.stringOrThrow("user_code"),
            verificationUri = json.stringOrThrow("verification_uri"),
            expiresInSec = json.get("expires_in")?.asInt ?: 900,
            intervalSec = json.get("interval")?.asInt ?: 5,
        )
    }

    /** Polls until the user approves, the code expires, or the coroutine is cancelled. */
    suspend fun pollForToken(clientId: String, code: DeviceCode): String {
        var intervalSec = code.intervalSec.coerceAtLeast(1)
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(intervalSec * 1000L)
            val json = postForJson(
                "https://github.com/login/oauth/access_token",
                "client_id=${clientId.urlEncode()}" +
                "&device_code=${code.deviceCode.urlEncode()}" +
                "&grant_type=urn:ietf:params:oauth:grant-type:device_code".urlEncodeGrantType()
            )
            json.get("access_token")?.asString?.let { return it }
            when (val error = json.get("error")?.asString) {
                "authorization_pending" -> Unit
                "slow_down" -> intervalSec = (json.get("interval")?.asInt ?: (intervalSec + 5)).coerceAtLeast(intervalSec + 5)
                "expired_token" -> throw IOException("The device code expired before the request was approved. Please try again.")
                "access_denied" -> throw IOException("The authorization request was denied.")
                else -> throw IOException("Device flow failed: $error — ${json.get("error_description")?.asString ?: "no details"}")
            }
        }
        throw IOException("The device code expired before the request was approved. Please try again.")
    }

    private suspend fun postForJson(url: String, body: String): JsonObject = withContext(Dispatchers.IO) {
        val raw = HttpRequests.post(url, "application/x-www-form-urlencoded")
            .tuner { it.setRequestProperty("Accept", "application/json") }
            .connect { request ->
                request.write(body)
                request.readString()
            }
        JsonParser.parseString(raw).asJsonObject
    }

    private fun JsonObject.stringOrThrow(key: String): String =
        get(key)?.asString ?: throw IOException("GitHub response is missing '$key': $this")

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

    // The grant_type value contains ':' which is safe in a form body; encode conservatively anyway.
    private fun String.urlEncodeGrantType(): String {
        val prefix = "&grant_type="
        val value = removePrefix(prefix)
        return prefix + value.urlEncode()
    }
}
