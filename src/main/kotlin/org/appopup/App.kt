package org.appopup

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.konfigur8.Configuration
import io.github.konfigur8.ConfigurationTemplate
import io.github.konfigur8.Property
import org.http4k.client.OkHttp
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.body.form
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import org.http4k.format.Jackson.auto
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel

data class Home(val ignore: String = "ignored") : ViewModel

object Appoup {
    operator fun invoke(github: HttpHandler, config: Configuration): HttpHandler {

        val githubClient = GithubClient(github, GithubAppConfig(config[AppopupConfiguration.GITHUB_CLIENT_ID], config[AppopupConfiguration.GITHUB_CLIENT_SECRET]))

        val renderer = HandlebarsTemplates().HotReload("src/main/resources")

        return routes(
            "/github-auth-callback" to GET bind { request: Request ->
                val token = githubClient.retrieveAccessToken(GithubOauthCode(request.query("code")!!))
                Response(OK).body("Welcome!")
            },
            "/" to GET bind { _: Request -> Response(OK).body(renderer(Home())) }
        )
    }
}

data class GithubAppConfig(val clientId: String, val clientSecret: String)
data class GithubOauthCode(val value: String)
data class GithubAccessToken(val value: String)

class GithubClient(val client: HttpHandler, val config: GithubAppConfig) {

    data class TokenResponse(@JsonProperty("access_token") val accessToken: String?,
                             val error: String?,
                             @JsonProperty("error_description") val errorDescription: String?,
                             val scope: String?)

    private val tokenResponseLens = Body.auto<TokenResponse>().toLens()

    fun retrieveAccessToken(code: GithubOauthCode): GithubAccessToken {
        val response = client(Request(POST, "https://github.com/login/oauth/access_token")
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
}

object AppopupServer {
    operator fun invoke(config: Configuration): HttpHandler {
        val client = DebuggingFilters.PrintRequestAndResponse().then(OkHttp())
        val server = DebuggingFilters.PrintRequestAndResponse().then(Appoup(client, config))
        return server
    }
}

object AppopupConfiguration {
    val PORT = Property.int("PORT")
    val GITHUB_CLIENT_ID = Property.string("GITHUB_CLIENT_ID")
    val GITHUB_CLIENT_SECRET = Property.string("GITHUB_CLIENT_SECRET")
    val defaults = ConfigurationTemplate()
        .withProp(GITHUB_CLIENT_ID, "unset")
        .withProp(GITHUB_CLIENT_SECRET, "unset")
        .withProp(PORT, 5000)
}

fun main(args: Array<String>) {
    val config = AppopupConfiguration.defaults.reify()
    AppopupServer(config).asServer(Jetty(config[AppopupConfiguration.PORT])).startAndBlock()
}
