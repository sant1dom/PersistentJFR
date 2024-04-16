# PersistentJFR - A tool for collecting Java Flight Recorder data

PersistentJFR is a tool for collecting Java Flight Recorder (JFR) data from a running JVM and storing it in a file. It is designed to be used in production environments where you want to collect JFR data over a long period of time without having to keep a large amount of data in memory.

## Features

- Collect JFR data from files uploaded
- Visualize JFR data in a web interface (violin plots for numeric data)
- Filter JFR data by event type, and specific event fields

## Getting started

### Prerequisites

- Python
- Java 8 or later

### Installation

1. Clone the repository
2. Install the required Python packages by running `pip install -r requirements.txt`
3. Compile the Kotlin code by running `.\gradlew.bat installDist` (Windows) or `./gradlew installDist` (Linux/Mac) 
4. Run the server by running `.\build\install\PersistentJFR\bin\PersistentJFR.bat <database>` (Windows) or `./build/install/PersistentJFR/bin/PersistentJFR <database>` (Linux/Mac) where the first argument is the name you want to assign to your database
4(optional). If you want instead to build the jar you can run `.\gradlew.bat buildFatJar` (Windows) or `./gradlew buildFatJar` (Linux/Mac) and run it using `java -jar build\libs\PersistentJFR.jar <database>` where the first argument is the name you want to assign to your database
5. Open the web interface by navigating to `http://localhost:8080/swagger` in your web browser
6. Upload one or more JFR files to import them into the database (remember to provide a commit value to group the files together)
7. Visualize the data by executing the Python script `python show.py <database>` where the first argument is the name of the database you want to read
8. Filter the data by event type and specific event fields
