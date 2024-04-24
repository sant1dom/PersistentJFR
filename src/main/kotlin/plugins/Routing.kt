package plugins

import databaseConnection
import getAllEvents
import getColumnsForEvent
import io.ktor.http.*
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

        /***
         * This endpoint processes the JFR file and stores the data in the database.
         * The response status is 200 if the file is processed successfully.
         * The response status is 500 if there is an error connecting to the database.
         */
        post("/jfr") {
            val multipartData = call.receiveMultipart()
            val fileBytes: MutableList<ByteArray> = mutableListOf()
            var commitValue = ""
            var date = ""
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
                        if (part.name == "commitValue") {
                            commitValue = part.value
                        } else if (part.name == "date") {
                            date = part.value
                        }
                    }

                    else -> {
                        println("Part not recognized: ${part.name} = ${part.javaClass}")
                    }
                }
                part.dispose()
            }

            val connection = databaseConnection(args[0]) ?: return@post call.respondText("Error: could not connect to the database", status = HttpStatusCode.InternalServerError)

            fileBytes.forEachIndexed { index, file ->
                processFile(file, connection, commitValue, fileNames[index], date)
            }
            
            call.respondText("commit_value: $commitValue," +
                                    if (fileBytes.isNotEmpty()) " file_name: ${fileNames.joinToString()}" else "")
        }

        /***
         * This endpoint returns a welcome message.
         */
        get("/") {
            call.respondText("Welcome to PersistentJFR")
        }

        /***
         * This endpoint retrieves the statistics for a given event and column.
         * The statistics are returned as a JSON object.
         * The response status is 200 if the statistics are retrieved successfully.
         * The response status is 500 if there is an error connecting to the database.
         */
        post("/statistics") {
            val event = call.request.queryParameters["event"]?.replace(".", "_")
            val column = call.request.queryParameters["column"]

            if (event == null || column == null) return@post call.respondText("Error: missing query parameters", status = HttpStatusCode.BadRequest)

            if (event.isEmpty() || column.isEmpty()) return@post call.respondText("Error: empty query parameters", status = HttpStatusCode.BadRequest)

            val connection = databaseConnection(args[0]) ?: return@post call.respondText("Error: could not connect to the database", status = HttpStatusCode.InternalServerError)

            if (event !in getAllEvents(connection)) return@post call.respondText("Error: event not found", status = HttpStatusCode.NotFound)

            if (column !in getColumnsForEvent(connection, event)) return@post call.respondText("Error: column not found", status = HttpStatusCode.NotFound)

            val statement = connection.prepareStatement("SELECT id_pk, $column, commit_value FROM $event ORDER BY id_pk")

            val resultSet = statement.executeQuery()

            val sets = mutableMapOf<String, MutableList<Double>>()

            while (resultSet.next()) {
                val value = resultSet.getDouble(column)
                val commitValue = resultSet.getString("commit_value")

                if (sets[commitValue] == null) {
                    sets[commitValue] = mutableListOf()
                }

                sets[commitValue]!!.add(value)
            }

            val results = mutableMapOf<String, String>()

            sets.forEach { (k, set) ->
                val average = set.average()
                val percentile99 = set.sorted().dropLast((set.size * 0.01).toInt()).average()
                val max = set.maxOfOrNull { it }
                val min = set.minOfOrNull { it }
                val median = set.sorted()[set.size / 2]
                val q1 = set.sorted()[set.size / 4]
                val q3 = set.sorted()[set.size * 3 / 4]
                val iqr = q3 - q1

                results[k] = """
                    |{
                    |   "commitValue": "$k",
                    |   "average": $average,
                    |   "percentile99": $percentile99,
                    |   "max": $max,
                    |   "min": $min,
                    |   "median": $median,
                    |   "q1": $q1,
                    |   "q3": $q3,
                    |   "iqr": $iqr
                    |}
                """.trimMargin()
            }

            val response = """
                |{
                |   "event": "$event",
                |   "column": "$column",
                |   "results": ${results.values.joinToString(",", "[", "]")}
                |}
            """.trimMargin()
            call.respondText(response,
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK)
        }

        /***
         * This endpoint retrieves all the events in the database.
         * The events are returned as a JSON array.
         * The response status is 200 if the events are retrieved successfully.
         * The response status is 500 if there is an error connecting to the database.
         */
        get("/events") {
            val connection = databaseConnection(args[0]) ?: return@get call.respondText("Error: could not connect to the database")

            val events = getAllEvents(connection).map { "\"$it\"" }

            call.respondText(events.joinToString(", ", "[", "]"),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK)

            println("Events retrieved")
        }

        /***
         * This endpoint retrieves all the columns for a given event.
         * The columns are returned as a JSON array.
         * The response status is 200 if the columns are retrieved successfully.
         * The response status is 500 if there is an error connecting to the database.
         */
        get("/events/{event}") {
            val event = call.parameters["event"]?.replace(".", "_")
                ?: return@get call.respondText("Error: missing event parameter")

            val connection =
                databaseConnection(args[0]) ?: return@get call.respondText("Error: could not connect to the database", status = HttpStatusCode.InternalServerError)

            if (event !in getAllEvents(connection)) return@get call.respondText("Error: event not found", status = HttpStatusCode.NotFound)

            val columns = getColumnsForEvent(connection, event).map { "\"$it\"" }

            call.respondText(
                columns.joinToString(", ", "[", "]"),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )

            println("Columns for event $event retrieved")
        }
    }
}