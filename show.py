import sqlite3
import pandas as pd
import tkinter as tk
from tkinter import ttk
import plotly.express as px
import sys

# Funzione per estrarre i dati e disegnare il grafico
def plot_graph(selected_table, selected_column, columns):
    # Esegue la query per ottenere i dati
    c.execute(f'SELECT * FROM {selected_table}')
    rows = c.fetchall()

    # Crea il DataFrame
    columns = ['id_pk', 'commit_value', 'file'] + columns
    df = pd.DataFrame(rows, columns=columns)

    # Disegna il grafico
    fig = px.violin(df, x='commit_value', y=selected_column, box=True, points='all',
                    title=f'{selected_table}-{selected_column} per Commit Value', hover_data=df.columns, color='commit_value')

    fig.show()

    # Chiude la connessione
    conn.close()

# GUI
def update_columns_combobox(event):
    selected_table = tables_combobox.get()
    c.execute(f'PRAGMA table_info({selected_table})')
    columns = [row[1] for row in c.fetchall()][3:]  # Rimuove le colonne id, commit_value e file
    columns_combobox['values'] = columns
    return columns

def plot_button_click():
    selected_table = tables_combobox.get()
    selected_column = columns_combobox.get()
    columns = update_columns_combobox(None)
    plot_graph(selected_table, selected_column, columns)

root = tk.Tk()
window_height = 200
window_width = 400

screen_width = root.winfo_screenwidth()
screen_height = root.winfo_screenheight()

x_cordinate = int((screen_width/2) - (window_width/2))
y_cordinate = int((screen_height/2) - (window_height/2))

root.geometry("{}x{}+{}+{}".format(window_width, window_height, x_cordinate, y_cordinate))
root.title("PersistentJFR Analisi dei Dati ")
root.resizable(False, False)


# Connessione al database e recupero dei nomi delle tabelle
conn = sqlite3.connect("./databases/"+sys.argv[1] + ".db")
c = conn.cursor()
c.execute("SELECT name FROM sqlite_master WHERE type='table'")
tables = [row[0] for row in c.fetchall()]
tables.sort()

# Etichetta
label = ttk.Label(root, text="Seleziona la tabella e la colonna da visualizzare")
label.pack(pady=10)

# Combobox per selezionare le tabelle
tables_combobox = ttk.Combobox(root, values=tables)
tables_combobox.pack(pady=10)
tables_combobox.bind("<<ComboboxSelected>>", update_columns_combobox)

# Combobox per selezionare le colonne
columns_combobox = ttk.Combobox(root)
columns_combobox.pack(pady=5)

# Bottone per disegnare il grafico
plot_button = ttk.Button(root, text="Disegna Grafico", command=plot_button_click)
plot_button.pack(pady=5)

root.mainloop()
