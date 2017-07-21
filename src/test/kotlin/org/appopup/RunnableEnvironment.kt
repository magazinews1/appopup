import org.appopup.AppopupConfiguration
import org.appopup.AppopupServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.io.FileReader
import java.util.*

fun main(args: Array<String>) {
    val props = Properties()
    props.load(FileReader(".env.test"))

    val config = AppopupConfiguration.defaults
        .withProp(AppopupConfiguration.GITHUB_CLIENT_ID, props["GITHUB_CLIENT_ID"].toString())
        .withProp(AppopupConfiguration.GITHUB_CLIENT_SECRET, props["GITHUB_CLIENT_SECRET"].toString())
        .reify()

    AppopupServer(config).asServer(Jetty(config[AppopupConfiguration.PORT])).startAndBlock()
}