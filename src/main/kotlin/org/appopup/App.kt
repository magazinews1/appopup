package org.appopup

import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel


data class Home(val ignore: String = "ignored") : ViewModel

object Appoup {
    operator fun invoke(): HttpHandler {
        val renderer = HandlebarsTemplates().HotReload("src/main/resources")

        return routes("/" to GET bind { _: Request -> Response(OK).body(renderer(Home())) })
    }
}

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
//    val production = args.size ==2 && args[1] == "production"

    Appoup().asServer(Jetty(port)).startAndBlock()
}
