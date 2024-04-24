import jdk.jfr.EventType
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.concurrent.atomic.AtomicInteger

/***
 * This function processes a JFR file and inserts the data into the database.
 *
 * @param file ByteArray containing the JFR file
 * @param connection Database connection
 * @param commitValue Commit value
 * @param fileName File name
 */
fun processFile(file: ByteArray,
                connection: Connection,
                commitValue: String,
                fileName: String,
                date: String
) {
    // Create a temporary file to read the data
    val tempFile = File.createTempFile("recording", ".jfr")
    tempFile.writeBytes(file)

    // Get the event types present in the file
    val eventTypesLookUp = RecordingFile(tempFile.toPath()).readEventTypes()

    connection.autoCommit = false // Start transaction

    val insertStatements = mutableMapOf<String, PreparedStatement>()

    val validColumns= mutableMapOf<String, List<String>>()

    // For each event type, create the table and insertion query
    eventTypesLookUp.forEach { eventType ->
        createAndInsertStatements(eventType, connection, insertStatements, validColumns)
    }

    val idx = AtomicInteger(0)
    val batchSize = 10000
    // Read all events from the file and insert them into the database
    val events = RecordingFile.readAllEvents(tempFile.toPath())
    val totalEvents = events.size
    runBlocking {
        events.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            launch {
                batch.forEachIndexedParallel { index, event ->
                    val overallIndex = batchIndex * batchSize + index
                    val progress = (overallIndex + 1) * 100 / totalEvents
                    print("Processing event $overallIndex of $totalEvents ")
                    printProgressBar(progress)

                    processEvent(event, validColumns, insertStatements, commitValue, fileName, idx, connection, date)
                }
                insertStatements.values.forEach { statement ->
                    statement.executeBatch()
                }
                connection.commit()
            }
        }
    }

    insertStatements.values.forEach { statement ->
        statement.executeBatch()
        connection.commit()
    }

    connection.commit()
    connection.autoCommit = true // End transaction

    println("File processed successfully")
    println("-".repeat(50))
    tempFile.delete()

}

/***
 * This function prints a progress bar in the terminal to indicate the progress status.
 *
 * @param progress Progress to indicate
 */
fun printProgressBar(progress: Int) {
    val width = 20 // width of the bar
    val percent = (progress / 100.0 * width).toInt()
    val bar = "[" + "=".repeat(percent) + ">".repeat(if (percent < width) 1 else 0) + ".".repeat(width - percent) + "]"
    print("$bar ($progress%) \r")
}


/***
 * This function processes an event from the event list.
 *
 * @param event Event to process
 * @param validColumns Map containing valid columns for each event type
 * @param insertStatements Map containing insertion queries for each event type
 * @param commitValue Commit value
 * @param fileName File name
 * @param index Batch index
 * @param connection Database connection
 */
private fun processEvent(
    event: RecordedEvent,
    validColumns: MutableMap<String, List<String>>,
    insertStatements: MutableMap<String, PreparedStatement>,
    commitValue: String,
    fileName: String,
    index: AtomicInteger,
    connection: Connection,
    date: String
) {
    // Check if the event is among those of interest
    if (event.eventType.name !in validColumns.keys) {
        return
    }
    // Take only the numeric columns that were previously selected
    val columns =
        event.eventType.fields.filter { validColumns[event.eventType.name]?.contains(it.name) == true }.map { it.name }
    val tableName = event.eventType.name.replace(".", "_")
    val statement = insertStatements[tableName] ?: return

    statement.setString(1, commitValue)
    statement.setString(2, fileName)
    statement.setString(3, date)
    var i = 4
    for (columnName in columns) {
        statement.setObject(i++, event.getValue(columnName))
    }
    statement.addBatch()
    if (index.incrementAndGet() % 10000 == 0) {
        statement.executeBatch()
        connection.commit()
    }
}

/***
 * This function creates the table and insertion query for an event type.
 *
 * @param eventType Event type
 * @param connection Database connection
 * @param insertStatements Map containing insertion queries for each event type
 * @param validColumns Map containing valid columns for each event type
 */
private fun createAndInsertStatements(
    eventType: EventType,
    connection: Connection,
    insertStatements: MutableMap<String, PreparedStatement>,
    validColumns: MutableMap<String, List<String>>
) {
    // Select only numeric columns (those that allow analysis)
    val columnNames = eventType.fields
        .filter { it.name != "startTime" && it.typeName in setOf("double", "long", "int", "float", "short", "byte") }
    val columnNamesQuery = columnNames.joinToString(", ") { "${it.name} ${if (it.typeName == "int" || it.typeName == "short" || it.typeName == "byte") "INTEGER" else "REAL"}" }
    if (columnNames.isEmpty()) {
        return
    }
    // The replacement is necessary otherwise the table name is not valid because it would select the jdk database
    val tableName = eventType.name.replace(".", "_")

    // The replacement is necessary because the column name cannot be "index"
    val createTableQuery =
        "CREATE TABLE IF NOT EXISTS $tableName (id_pk INTEGER PRIMARY KEY, commit_value TEXT, file TEXT, date TEXT, ${
            columnNamesQuery.replace(
                "index",
                "idx"
            )
        })"
    connection.createStatement().executeUpdate(createTableQuery)
    val insertQuery = "INSERT INTO $tableName (commit_value, file, date,  ${
        columnNames.joinToString(", ") { it.name }.replace("index", "idx")
    }) VALUES (?, ?, ?, ${columnNames.joinToString { "?" }})"

    insertStatements[tableName] = connection.prepareStatement(insertQuery)
    validColumns[eventType.name] = columnNames.map { it.name }
}

/***
 * This function creates the connection to the database.
 *
 * @param databaseName Database name
 */
fun databaseConnection(databaseName: String): Connection? {
    // Create the databases directory if not exist
    val databasesDirectory = File("./databases")
    if (!databasesDirectory.exists()) {
        databasesDirectory.mkdirs()
    }

    val databaseFileName = "./databases/$databaseName.db"
    val url = "jdbc:sqlite:$databaseFileName"
    val connection: Connection?
    try {
        connection = DriverManager.getConnection(url)
        return connection
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

/***
 * This function retrieves all the events in the database.
 *
 * @param connection Database connection
 */
fun getAllEvents(connection: Connection): MutableList<String> {
    val statement =
        connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")

    val resultSet = statement.executeQuery()

    val events = mutableListOf<String>()

    while (resultSet.next()) {
        events.add(resultSet.getString("name"))
    }
    return events
}

/***
 * This function retrieves all the columns for a given event.
 *
 * @param connection Database connection
 * @param event Event name
 */
fun getColumnsForEvent(
    connection: Connection,
    event: String
): MutableList<String> {
    val statement = connection.prepareStatement("PRAGMA table_info($event)")

    val resultSet = statement.executeQuery()

    val columns = mutableListOf<String>()
    while (resultSet.next()) {
        val columnName = resultSet.getString("name")
        if (columnName != "id_pk" && columnName != "commit_value" && columnName != "file" && columnName != "date") {
            columns.add(columnName)
        }
    }
    return columns
}


/*** This function processes the events in parallel.
 *
 * @param action Action to perform
 */
suspend fun <T> Iterable<T>.forEachIndexedParallel(action: suspend (index: Int, T) -> Unit) {
    coroutineScope {
        mapIndexed { index, value ->
            async { action(index, value) }
        }.forEach { it.await() }
    }
}
