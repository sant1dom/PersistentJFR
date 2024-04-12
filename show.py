import sqlite3
import pandas as pd
import tkinter as tk
from tkinter import ttk
import plotly.express as px
import sys

# Function to extract data and plot the graph
def plot_graph(selected_table, selected_column, columns):
    # Execute query to get the data
    c.execute(f'SELECT * FROM {selected_table}')
    rows = c.fetchall()

    # Create DataFrame
    columns = ['id_pk', 'commit_value', 'file'] + columns
    df = pd.DataFrame(rows, columns=columns)

    # Plot the graph
    fig = px.violin(df, x='commit_value', y=selected_column, box=True, points='all',
                    title=f'{selected_table}-{selected_column} per Commit Value', hover_data=df.columns, color='commit_value')

    fig.show()

    # Close connection
    conn.close()

# GUI
def update_columns_combobox(event):
    selected_table = tables_combobox.get()
    c.execute(f'PRAGMA table_info({selected_table})')
    columns = [row[1] for row in c.fetchall()][3:]  # Remove id, commit_value, and file columns
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
root.title("PersistentJFR Data Analysis")
root.resizable(False, False)


# Connect to the database and retrieve table names
conn = sqlite3.connect("./databases/"+sys.argv[1] + ".db")
c = conn.cursor()
c.execute("SELECT name FROM sqlite_master WHERE type='table'")
tables = [row[0] for row in c.fetchall()]
tables.sort()

# Label
label = ttk.Label(root, text="Select table and column to visualize")
label.pack(pady=10)

# Combobox to select tables
tables_combobox = ttk.Combobox(root, values=tables)
tables_combobox.pack(pady=10)
tables_combobox.bind("<<ComboboxSelected>>", update_columns_combobox)

# Combobox to select columns
columns_combobox = ttk.Combobox(root)
columns_combobox.pack(pady=5)

# Button to draw the graph
plot_button = ttk.Button(root, text="Draw Graph", command=plot_button_click)
plot_button.pack(pady=5)

root.mainloop()
