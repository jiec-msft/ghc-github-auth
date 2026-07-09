package com.jiec.ghc.githubauth

import com.google.gson.JsonParser
import com.intellij.openapi.components.service
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import java.io.IOException

/**
 * Validates a github.com token and stores it into the bundled GitHub plugin's
 * account store ([GHAccountManager]), creating or updating the matching account.
 */
internal object GithubTokenLogin {

    /** Returns the login of the authenticated user. */
    suspend fun login(token: String): String {
        val login = fetchLogin(token)
        val accountManager = service<GHAccountManager>()
        val existing = accountManager.accountsState.value.firstOrNull {
            it.server.isGithubDotCom && it.name == login
        }
        val account = existing ?: GHAccountManager.createAccount(login, GithubServerPath.DEFAULT_SERVER)
        accountManager.updateAccount(account, token)
        return login
    }

    private suspend fun fetchLogin(token: String): String = withContext(Dispatchers.IO) {
        val raw = HttpRequests.request("https://api.github.com/user")
            .tuner {
                it.setRequestProperty("Authorization", "Bearer $token")
                it.setRequestProperty("Accept", "application/vnd.github+json")
            }
            .readString()
        val login = JsonParser.parseString(raw).asJsonObject.get("login")?.asString
        if (login.isNullOrBlank()) throw IOException("GitHub /user response contains no 'login' — is the token valid?")
        login
    }
}
