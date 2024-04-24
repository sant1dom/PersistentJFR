import sqlite3
import pandas as pd
import tkinter as tk
from tkinter import ttk
import plotly.express as px
import sys


# Function to extract data and plot the violin graph
def plot_graph(selected_table, selected_column, columns, c):
    # Execute query to get the data
    c.execute(f'SELECT * FROM {selected_table}')
    rows = c.fetchall()

    # Create DataFrame
    columns = ['id_pk', 'commit_value', 'file', 'date'] + columns
    df = pd.DataFrame(rows, columns=columns)

    if 'Time' in selected_column or 'time' in selected_column or 'duration' in selected_column:
        pass
    # Plot the graph
    fig = px.violin(df, x='commit_value', y=selected_column, box=True, points='all',
                    title=f'{selected_table}-{selected_column} per Commit Value', hover_data=df.columns,
                    color='commit_value')

    fig.show()


def plot_progression_graph(selected_table, selected_column, columns, c, statistic, commit_values):
    # Get the commits dates and check if they are sequential
    c.execute(f'SELECT date,commit_value FROM {selected_table} WHERE commit_value = "{commit_values[0]}"')
    date1 = c.fetchone()[0]
    c.execute(f'SELECT date,commit_value FROM {selected_table} WHERE commit_value = "{commit_values[1]}"')
    date2 = c.fetchone()[0]
    if date1 > date2:
        print('The commit values are not sequential')
        return

    c.execute(f'SELECT * FROM {selected_table} WHERE date BETWEEN "{date1}" AND "{date2}"')
    rows = c.fetchall()
    commits = list(set([row[1] for row in rows]))
    if len(commits) == 0:
        print('No data between the selected commits')
        return

    # Execute query to get the data
    c.execute(f'SELECT * FROM {selected_table} WHERE commit_value IN (' + ','.join(['?'] * len(commits)) + ')', commits)
    rows = c.fetchall()

    # Create DataFrame
    columns = ['id_pk', 'commit_value', 'file', 'date'] + columns
    df = pd.DataFrame(rows, columns=columns)
    # Sort the data by date
    df = df.sort_values(by='date')
    data = None
    if 'Time' in selected_column or 'time' in selected_column or 'duration' in selected_column:
        pass

    match statistic:
        case 'mean':
            data = df.groupby(['commit_value', 'date'])[selected_column].mean().reset_index()
        case 'median':
            data = df.groupby(['commit_value', 'date'])[selected_column].median().reset_index()
        case 'min':
            data = df.groupby(['commit_value', 'date'])[selected_column].min().reset_index()
        case 'max':
            data = df.groupby(['commit_value', 'date'])[selected_column].max().reset_index()
    data.sort_values(by='date', inplace=True)
    # Plot the graph
    fig = px.line(data, x='commit_value', y=selected_column,
                  title=f'{selected_table}-{selected_column} per Commit Value ({statistic})',
                  labels={'commit_value': 'Commit Value', selected_column: selected_column})

    fig.show()


# GUI
def update_columns_combobox(event, tables_combobox, columns_combobox, c):
    selected_table = tables_combobox.get()
    c.execute(f'PRAGMA table_info({selected_table})')
    columns = [row[1] for row in c.fetchall()][4:]  # Remove id, commit_value, and file columns
    columns_combobox['values'] = columns
    return columns


def plot_button_click(tables_combobox, columns_combobox, c, statistic=None, commit_values=None):
    selected_table = tables_combobox.get()
    selected_column = columns_combobox.get()
    columns = update_columns_combobox(None, tables_combobox, columns_combobox, c)
    if statistic is None:
        plot_graph(selected_table, selected_column, columns, c)
    else:
        plot_progression_graph(selected_table, selected_column, columns, c, statistic, commit_values)


def main():
    root = tk.Tk()
    window_height = 400
    window_width = 400

    screen_width = root.winfo_screenwidth()
    screen_height = root.winfo_screenheight()

    x_cordinate = int((screen_width / 2) - (window_width / 2))
    y_cordinate = int((screen_height / 2) - (window_height / 2))

    root.geometry("{}x{}+{}+{}".format(window_width, window_height, x_cordinate, y_cordinate))
    root.title("PersistentJFR Data Analysis")
    root.resizable(False, False)

    # Connect to the database and retrieve table names
    conn = sqlite3.connect("./databases/" + sys.argv[1] + ".db")
    c = conn.cursor()
    c.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [row[0] for row in c.fetchall()]
    tables.sort()
    # Tabs
    tab_control = ttk.Notebook(root)
    tab1(root, tables, tab_control, c)
    tab2(root, tables, tab_control, c)
    tab_control.pack(expand=1, fill="both")
    root.mainloop()


def tab1(root, tables, tab_control, c):
    tab1_var = ttk.Frame(tab_control)

    tab_control.add(tab1_var, text='Violin')

    # Title
    title = ttk.Label(tab1_var, text="Violin Plot", font=("Helvetica", 30))
    title.pack(pady=10)

    # Label
    label = ttk.Label(tab1_var, text="Select table and column to visualize")
    label.pack(pady=10)

    # Combobox to select tables
    tables_combobox = ttk.Combobox(tab1_var, values=tables)
    tables_combobox.pack(pady=10)
    tables_combobox.bind("<<ComboboxSelected>>",
                         lambda event: update_columns_combobox(event, tables_combobox, columns_combobox, c))

    # Combobox to select columns
    columns_combobox = ttk.Combobox(tab1_var)
    columns_combobox.pack(pady=5)

    # Button to draw the graph
    plot_button = ttk.Button(tab1_var, text="Draw Graph",
                             command=lambda: plot_button_click(tables_combobox, columns_combobox, c))
    plot_button.pack(pady=5)


def tab2(root, tables, tab_control, c):
    tab2_var = ttk.Frame(tab_control)
    tab_control.add(tab2_var, text='Progression')

    # Title
    title = ttk.Label(tab2_var, text="Progression Plot", font=("Helvetica", 30))
    title.pack(pady=10)

    # Label
    label = ttk.Label(tab2_var, text="Select table, column and value to visualize")
    label.pack(pady=10)

    # Combobox to select tables
    tables_combobox = ttk.Combobox(tab2_var, values=tables)
    tables_combobox.pack(pady=10)
    tables_combobox.bind("<<ComboboxSelected>>",
                         lambda event: update_columns_combobox(event, tables_combobox, columns_combobox, c))

    # Combobox to select columns
    columns_combobox = ttk.Combobox(tab2_var)
    columns_combobox.pack(pady=5)

    # Combobox to select the statistic
    statistic_combobox = ttk.Combobox(tab2_var, values=['mean', 'median', 'min', 'max'])
    statistic_combobox.pack(pady=5)

    # Combobox to select the starting commit value
    values = c.execute(f'SELECT DISTINCT commit_value FROM {tables[0]}').fetchall()
    values = [str(value[0]) for value in values]
    commit_value_combobox1 = ttk.Combobox(tab2_var, values=values)
    commit_value_combobox1.pack(pady=5)

    # Combobox to select the ending commit value
    commit_value_combobox2 = ttk.Combobox(tab2_var, values=values)
    commit_value_combobox2.pack(pady=5)

    # Button to draw the graph
    plot_button = ttk.Button(tab2_var,
                             text="Draw Graph",
                             command=lambda: plot_button_click(tables_combobox,
                                                               columns_combobox,
                                                               c,
                                                               statistic_combobox.get(),
                                                               (commit_value_combobox1.get(),
                                                                commit_value_combobox2.get())))

    plot_button.pack(pady=5)


if __name__ == '__main__':
    pd.options.display.float_format = '{:.2f}'.format
    main()
