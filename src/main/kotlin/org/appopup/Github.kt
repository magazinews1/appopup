package org.appopup

import com.fasterxml.jackson.annotation.JsonProperty
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.body.form
import org.http4k.format.Jackson.auto

data class GithubAppConfig(val clientId: String, val clientSecret: String)
data class GithubOauthLink(val value:String)
data class GithubOauthCode(val value: String)
data class GithubAccessToken(val value: String)
data class GithubUser(val name: String)

class GithubClient(val client: HttpHandler, val config: GithubAppConfig) {

    data class TokenResponse(@JsonProperty("access_token") val accessToken: String?,
                             val error: String?,
                             @JsonProperty("error_description") val errorDescription: String?,
                             val scope: String?)

    data class UserResponse(val login: String)

    private val tokenResponseLens = Body.auto<TokenResponse>().toLens()
    private val userResponseLens = Body.auto<UserResponse>().toLens()

    fun createOauthLink(): GithubOauthLink = GithubOauthLink("https://github.com/login/oauth/authorize?client_id=${config.clientId}&scope=repo&")

    fun retrieveAccessToken(code: GithubOauthCode): GithubAccessToken {
        val response = client(Request(Method.POST, "https://github.com/login/oauth/access_token")
            .header("Accept", "application/json")
            .form("client_id", config.clientId)
            .form("client_secret", config.clientSecret)
            .form("code", code.value)
        )
        if (!response.status.successful) {
            throw RuntimeException(response.bodyString())
        }
        val token = tokenResponseLens.extract(response)
        if (token.error != null) {
            throw RuntimeException(token.errorDescription ?: "Unknown")
        }
        return GithubAccessToken(token.accessToken.orEmpty())
    }

    fun userInfo(token: GithubAccessToken): GithubUser? {
        val response = client(Request(Method.GET, "https://api.github.com/user").header("Authorization", "token ${token.value}"))
        val user = userResponseLens.extract(response)
        return GithubUser(user.login)
    }
}