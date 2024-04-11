import jdk.jfr.EventType
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.concurrent.atomic.AtomicInteger

/***
 * Questa funzione si occupa di processare un file JFR e di inserire i dati nel database
 *
 * @param file ByteArray contenente il file JFR
 * @param connection Connessione al database
 * @param commitValue Valore del commit
 * @param fileName Nome del file
 */

fun processFile(file: ByteArray,
                connection: Connection,
                commitValue: String,
                fileName: String
                ) {
    // Creo un file temporaneo per poter leggere i dati
    val tempFile = File.createTempFile("recording", ".jfr")
    tempFile.writeBytes(file)

    // Ottengo i tipi di eventi presenti nel file
    val eventTypesLookUp = RecordingFile(tempFile.toPath()).readEventTypes()

    connection.autoCommit = false // Inizio transazione

    val insertStatements = mutableMapOf<String, PreparedStatement>()

    val validColumns= mutableMapOf<String, List<String>>()

    // Per ogni tipologia di evento creo la tabella e la query di inserimento
    eventTypesLookUp.forEach { eventType ->
        createAndInsertStatements(eventType, connection, insertStatements, validColumns)
    }

    val index = AtomicInteger(0)
    // Leggo tutti gli eventi dal file e li inserisco nel database
    val events = RecordingFile.readAllEvents(tempFile.toPath())
    events.forEachIndexed { i, event ->
        val progress = (i + 1) * 100 / events.size
        print("Processo evento $i di ${events.size} ")
        printProgressBar(progress)

        processEvent(event, validColumns, insertStatements, commitValue, fileName, index, connection)
    }
    insertStatements.values.forEach { it.executeBatch() }

    connection.commit()
    connection.autoCommit = true // Fine transazione

    println("File processato con successo")
    println("-".repeat(50))
    tempFile.delete()

}

/***
 * Questa funzione si occupa di stampare una progress bar a terminale per indicare lo stato di avanzamento
 *
 * @param progress Progresso da indicare
 */
fun printProgressBar(progress: Int) {
    val width = 20 // larghezza della barra
    val percent = (progress / 100.0 * width).toInt()
    val bar = "[" + "=".repeat(percent) + ">".repeat(if (percent < width) 1 else 0) + ".".repeat(width - percent) + "]"
    print("$bar ($progress%) \r")
}


/***
 * Questa funzione si occupa di processare un evento dalla lista di eventi
 *
 * @param event Evento da processare
 * @param validColumns Mappa che contiene le colonne valide per ogni tipologia di evento
 * @param insertStatements Mappa che contiene le query di inserimento per ogni tipologia di evento
 * @param commitValue Valore del commit
 * @param fileName Nome del file
 * @param index Indice per il batch
 * @param connection Connessione al database
 */
private fun processEvent(
    event: RecordedEvent,
    validColumns: MutableMap<String, List<String>>,
    insertStatements: MutableMap<String, PreparedStatement>,
    commitValue: String,
    fileName: String,
    index: AtomicInteger,
    connection: Connection
) {
    // Controllo che l'evento sia tra quelli che mi interessano
    if (event.eventType.name !in validColumns.keys) {
        return
    }
    // Prendo solo le colonne numeriche che ho precedentemente selezionato
    val columns =
        event.eventType.fields.filter { validColumns[event.eventType.name]?.contains(it.name) == true }.map { it.name }
    val tableName = event.eventType.name.replace(".", "_")
    val statement = insertStatements[tableName] ?: return

    statement.setString(1, commitValue)
    statement.setString(2, fileName)
    var i = 3
    for (columnName in columns) {
        statement.setObject(i++, event.getValue(columnName))
    }
    statement.addBatch()
    if (index.incrementAndGet() % 1000 == 0) {
        statement.executeBatch()
        connection.commit()
    }
}

/***
 * Questa funzione si occupa di creare la tabella e la query di inserimento per un tipo di evento
 *
 * @param eventType Tipo di evento
 * @param connection Connessione al database
 * @param insertStatements Mappa che contiene le query di inserimento per ogni tipologia di evento
 * @param validColumns Mappa che contiene le colonne valide per ogni tipologia di evento
 */
private fun createAndInsertStatements(
    eventType: EventType,
    connection: Connection,
    insertStatements: MutableMap<String, PreparedStatement>,
    validColumns: MutableMap<String, List<String>>
) {
    // Seleziono solo le colonne numeriche (che sono quelle che mi permettono di fare analisi)
    val columnNames = eventType.fields
        .filter { it.name != "startTime" && it.typeName in setOf("double", "long", "int", "float", "short", "byte") }
    val columnNamesQuery = columnNames.joinToString(", ") { "${it.name} ${if (it.typeName == "int" || it.typeName == "short" || it.typeName == "byte") "INTEGER" else "REAL"}" }
    if (columnNames.isNotEmpty()) {
        // La replace si rende necessaria altrimenti il nome della tabella non è valido perché selezionerebbe il database jdk
        val tableName = eventType.name.replace(".", "_")

        // La replace si rende necessaria perché il nome della colonna non può essere "index"
        val createTableQuery =
            "CREATE TABLE IF NOT EXISTS $tableName (id_pk INTEGER PRIMARY KEY, commit_value TEXT, file TEXT, ${
                columnNamesQuery.replace(
                    "index",
                    "idx"
                )
            })"
        connection.createStatement().executeUpdate(createTableQuery)
        val insertQuery = "INSERT INTO $tableName (commit_value, file, ${
            columnNames.joinToString(", ") { it.name }.replace("index", "idx")
        }) VALUES (?, ?, ${columnNames.joinToString { "?" }})"

        insertStatements[tableName] = connection.prepareStatement(insertQuery)
        validColumns[eventType.name] = columnNames.map { it.name }
    }
}

/***
 * Questa funzione si occupa di creare la connessione al database
 *
 * @param databaseName Nome del database
 */
fun databaseConnection(databaseName: String): Connection? {
    val databaseFileName = "/databases/$databaseName.db"
    val url = "jdbc:sqlite:$databaseFileName"
    var connection: Connection? = null
    try {
        connection = DriverManager.getConnection(url)
        println("Connessione al database avvenuta con successo")
        return connection
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
