import pandas as pd
import copy as cp
import os
import csv
import json

def swap_file_csv(file_path: str, file_name_without_extension: str, swap_fields: list):
    # Define the file paths
    script_directory = os.path.dirname(os.path.abspath(__file__))

    # Change the working directory to the script's directory
    os.chdir(script_directory)

    EN_file_full_name = f"{file_name_without_extension}_EN.csv"
    CN_file_full_name = f"{file_name_without_extension}_CN.csv"

    file_path_submarket = os.path.join(file_path)
    file_path_submarket_EN = os.path.join(file_path.replace(file_name_without_extension, f"{file_name_without_extension}_EN"))
    file_path_submarket_CN = os.path.join(file_path.replace(file_name_without_extension, f"{file_name_without_extension}_CN"))

    # Read CSV files into dictionaries
    df_submarket, df_submarket_other, EN_to_CN = None, None, False

    try:
        with open(file_path_submarket, 'r', encoding='utf-8') as file:
            csv_reader = csv.DictReader(file)
            df_submarket = [row for row in csv_reader]

        with open(file_path_submarket_EN, 'r', encoding='utf-8') as file:
            csv_reader = csv.DictReader(file)
            df_submarket_other = [row for row in csv_reader]

    except FileNotFoundError as e:
        # Check which file is not found
        if EN_file_full_name in str(e):
            with open(file_path_submarket_CN, 'r', encoding='utf-8') as file:
                csv_reader = csv.DictReader(file)
                df_submarket_other = [row for row in csv_reader]
                EN_to_CN = True

    if df_submarket is None:
        raise FileNotFoundError(f"{file_path_submarket} not found")

    if df_submarket_other is None:
        raise FileNotFoundError(f"Other file not found: EN_to_CN = {EN_to_CN}")

    # Swap values between the dataframes for the same 'id'
    common_ids = set(row['id'] for row in df_submarket).intersection(row['id'] for row in df_submarket_other)

    for common_id in common_ids:
        # find the row pair with the common 'id' from both dataframes
        rows_submarket = [row for row in df_submarket if row['id'] == common_id]
        rows_other = [row for row in df_submarket_other if row['id'] == common_id]

        # Swap values for the specified fields in dataframe A
        for field in swap_fields:
            try:
                # Extract the values from the cells
                value_submarket = rows_submarket[0][field]
                value_other = rows_other[0][field]

                # Skip if both values are None or both are empty strings
                if not value_submarket and not value_other:
                    continue

                # Parse JSON if the value is not empty and can be loaded as JSON
                if value_submarket and value_other and '{' in value_submarket and '}' in value_submarket and '{' in value_other and '}' in value_other:
                    value_submarket = json.loads(value_submarket)
                    value_other = json.loads(value_other)

                # Swap values only if both cells are not empty
                rows_submarket[0][field] = value_other
                rows_other[0][field] = value_submarket

            except Exception as e:
                print(f"Error swapping values for id {common_id} and field {field}: {e}")
                continue
        
    # Save the updated dataframe A to a new CSV file
    with open(file_path_submarket, 'w', encoding='utf-8', newline='') as file:
        fieldnames = df_submarket[0].keys()
        csv_writer = csv.DictWriter(file, fieldnames=fieldnames)
        csv_writer.writeheader()

        for row in df_submarket:
            # Replace None values with an empty string before writing to CSV
            row_no_none = {key: '' if value is None else value for key, value in row.items()}
            
            # Filter keys to only include those present in fieldnames
            row_no_none_filtered = {key: value for key, value in row_no_none.items() if key in fieldnames}

            csv_writer.writerow(row_no_none_filtered)

    # Save the updated dataframe B to a new CSV file
    if EN_to_CN:
        with open(file_path_submarket_EN, 'w', encoding='utf-8', newline='') as file:
            fieldnames_other = df_submarket_other[0].keys()
            csv_writer_other = csv.DictWriter(file, fieldnames=fieldnames_other)
            csv_writer_other.writeheader()

            for row_other in df_submarket_other:
                # Replace None values with an empty string before writing to CSV
                row_no_none_other = {key: '' if value is None else value for key, value in row_other.items()}

                # Filter keys to only include those present in fieldnames_other
                row_no_none_filtered_other = {key: value for key, value in row_no_none_other.items() if key in fieldnames_other}

                csv_writer_other.writerow(row_no_none_filtered_other)
        os.remove(file_path_submarket_CN)
    else:
        with open(file_path_submarket_CN, 'w', encoding='utf-8', newline='') as file:
            fieldnames_other = df_submarket_other[0].keys()
            csv_writer_other = csv.DictWriter(file, fieldnames=fieldnames_other)
            csv_writer_other.writeheader()

            for row_other in df_submarket_other:
                # Replace None values with an empty string before writing to CSV
                row_no_none_other = {key: '' if value is None else value for key, value in row_other.items()}

                # Filter keys to only include those present in fieldnames_other
                row_no_none_filtered_other = {key: value for key, value in row_no_none_other.items() if key in fieldnames_other}

                csv_writer_other.writerow(row_no_none_filtered_other)
        os.remove(file_path_submarket_EN)

if __name__ == "__main__":
    #swap_file_csv("data/campaign/submarkets.csv", "submarkets", ['name', 'desc'])
    #swap_file_csv("data/campaign/rules.csv", "rules", ['script','text','options'])
    swap_file_csv("data/campaign/industries.csv", "industries", ['name','desc'])