package org.appopup

import io.github.konfigur8.Configuration
import io.github.konfigur8.ConfigurationTemplate
import io.github.konfigur8.Property
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel

data class Home(val user: String? = null, val loginUrl: String = "ignored") : ViewModel

object Appopup {

    operator fun invoke(github: HttpHandler, config: Configuration): HttpHandler {
        val sessionStorage = Sessions()

        val renderer = HandlebarsTemplates().HotReload("src/main/resources")
        val githubAppConfig = GithubAppConfig(config[AppopupConfiguration.GITHUB_CLIENT_ID], config[AppopupConfiguration.GITHUB_CLIENT_SECRET])
        val githubClient = GithubClient(github, githubAppConfig)

        return Session(sessionStorage)
            .then(routes(
                "/github-auth-callback" to GET bind { request: Request ->
                    val token = githubClient.retrieveAccessToken(GithubOauthCode(request.query("code")!!))
                    request.storeInSession(sessionStorage, token)
                    Response(FOUND).header("Location", "/")
                },
                "/logout" to GET bind { request: Request ->
                    request.destroySession(sessionStorage)
                    Response(FOUND).header("Location", "/")
                },
                "/" to GET bind { request: Request ->
                    val token: GithubAccessToken? = request.retrieveKeyFromSession(sessionStorage)
                    val user = token?.let { githubClient.userInfo(it) }
                    Response(OK).body(renderer(Home(user?.name, githubAppConfig.loginUrl)))
                })
            )
    }
}

object AppopupServer {
    operator fun invoke(config: Configuration): HttpHandler {
        val client = DebuggingFilters.PrintRequestAndResponse().then(OkHttp())
        val server = DebuggingFilters.PrintRequestAndResponse().then(Appopup(client, config))
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
