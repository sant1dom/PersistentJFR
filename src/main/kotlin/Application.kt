import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import plugins.configureRouting

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Provide the name of the database file as the first argument")
        return
    }
    val server = embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        watchPaths = listOf("classes", "resources", "src"),
    ) {
        module(args)
    }
    server.start(wait = true)
}

fun Application.module(args: Array<String>) {
    configureRouting(args)
}
