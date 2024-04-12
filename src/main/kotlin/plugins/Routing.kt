package plugins

import databaseConnection
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import processFile

fun Application.configureRouting(args: Array<String>) {
    routing {
        // Swagger UI to visualize the OpenAPI documentation in the browser at /swagger
        swaggerUI(
            path = "/swagger",
            swaggerFile = "openapi.yaml"
        )

        post("/jfr") {
            val multipartData = call.receiveMultipart()
            val fileBytes: MutableList<ByteArray> = mutableListOf()
            var commitValue: String = ""
            val fileNames: MutableList<String> = mutableListOf()

            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        println("File: ${part.originalFileName}")
                        val fileByte = part.streamProvider().readBytes()
                        val originalFileName = part.originalFileName!!
                        fileBytes.add(fileByte)
                        fileNames.add(originalFileName)
                    }

                    is PartData.FormItem -> {
                        println("Form field: ${part.name} = ${part.value}")
                        commitValue = part.value
                    }

                    else -> {
                        println("Parte non riconosciuta: ${part.name} = ${part.javaClass}")
                    }
                }
                part.dispose()
            }

            val connection = databaseConnection(args[0])
            if (connection == null) {
                call.respondText("Errore nella connessione al database")
                return@post
            }

            fileBytes.forEachIndexed { index, file ->
                processFile(file, connection, commitValue, fileNames[index])
            }
            
            call.respondText("commit_value: $commitValue," +
                                    if (fileBytes.isNotEmpty()) " file_name: ${fileNames.joinToString()}" else "")
        }

        get("/jfr") {
            call.respondText("Welcome to JFR Processor")
        }

    }
}
