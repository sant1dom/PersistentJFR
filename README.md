# PersistentJFR - A tool for collecting Java Flight Recorder data

PersistentJFR is a tool for collecting Java Flight Recorder (JFR) data from a running JVM and storing it in a file. It is designed to be used in production environments where you want to collect JFR data over a long period of time without having to keep a large amount of data in memory.

## Features

- Collect JFR data from files uploaded
- Visualize JFR data in a web interface (violin plots for numeric data)
- Filter JFR data by event type, and specific event fields

## Getting started

### Prerequisites

- Python
- Java 11 or later

### Installation

1. Clone the repository
2. Install the required Python packages by running `pip install -r requirements.txt`
3. Compile the Kotlin code by running `gradlew.bat installDist` (Windows) or `./gradlew installDist` (Linux/Mac) 
4. Run the server by running `./build/install/PersistentJFR/bin/PersistentJFR.bat` (Windows) or `./build/install/PersistentJFR/bin/PersistentJFR` (Linux/Mac) and followed by the name you want to assign to your database
5. Open the web interface by navigating to `http://localhost:8080/swagger` in your web browser
6. Upload one or more JFR files to import them into the database (remember to provide a commit value to group the files together)
7. Visualize the data by executing the python script `python show.py`
8. Filter the data by event type and specific event fields
