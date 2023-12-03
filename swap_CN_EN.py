import pandas as pd
import copy as cp
import os
import csv
import json
from typing import Dict, List

def swap_file_csv(file_path: str, file_name_without_extension: str, swap_fields: list):
    # Define the file paths
    script_directory = os.path.dirname(os.path.abspath(__file__))

    # Change the working directory to the script's directory
    os.chdir(script_directory)

    EN_file_full_name = f"{file_name_without_extension}_EN.csv"
    CN_file_full_name = f"{file_name_without_extension}_CN.csv"

    file_path = os.path.join(file_path)
    file_path_EN = os.path.join(file_path.replace(file_name_without_extension, f"{file_name_without_extension}_EN"))
    file_path_CN = os.path.join(file_path.replace(file_name_without_extension, f"{file_name_without_extension}_CN"))

    # Read CSV files into dictionaries
    diction_rows_now: [List[Dict[str, str]]] = []
    diction_rows_other: [List[Dict[str, str]]] = []
    # by default is CN_to_EN, load _EN, save old data to _CN
    EN_to_CN = False

    try:
        with open(file_path, 'r', encoding='utf-8') as file:
            print(f'Swap Load {file_name_without_extension}')
            csv_reader = csv.DictReader(file)    
            # row is List[Dict[str, str]]
            for row in csv_reader:
                diction_rows_now.append(row)  
        with open(file_path_EN, 'r', encoding='utf-8') as file:
            # by default read _EN path
            csv_reader = csv.DictReader(file)    
            for row in csv_reader:
                diction_rows_other.append(row)             
    except FileNotFoundError as e:
        # if fail to read _EN path, go into EN_to_CN mode, load _CN, save old data to _EN
        try:
            if EN_file_full_name in str(e):
                EN_to_CN = True
                with open(file_path_CN, 'r', encoding='utf-8') as file:
                    csv_reader = csv.DictReader(file)    
                    for row in csv_reader:
                        diction_rows_other.append(row)  
        except Exception as e:
            print('Failed to load both EN/CN csv')
            return

    # find common ids  between two diction list
    common_ids = set(row['id'] for row in diction_rows_now).intersection(row['id'] for row in diction_rows_other)

    for common_id in [id for id in common_ids if id and not id.startswith('#')]:
        # find a row pair with the common 'id' from both dataframes
        # ignore padding rows with empty id
        # ignore comment rows start with "#"
        row_same_id = [row for row in diction_rows_now if row['id'] == common_id ]
        row_other_same_id = [row for row in diction_rows_other if row['id'] == common_id]

        # check and swap every specified fields 1 by 1
        for field in swap_fields:
            try:
                # Extract the values from the cell
                value_now = row_same_id[0][field]
                value_other = row_other_same_id[0][field]

                # Parse JSON if the value is not empty and can be loaded as JSON
                # both values contains '{' and '}'
                if value_now and value_other and '{' in value_now and '}' in value_now and '{' in value_other and '}' in value_other:
                    value_now = json.loads(value_now)
                    value_other = json.loads(value_other)

                # Swap value for this field between this row pair
                row_same_id[0][field] = value_other
                row_other_same_id[0][field] = value_now

            except Exception as e:
                print(f"Error swapping values for id {common_id} and field {field}: {e}")
                continue
        
    # Save the swaped diction_rows_now to the original CSV file
    with open(file_path, 'w', encoding='utf-8', newline='') as file:
        try:
            fieldnames = diction_rows_now[0].keys()
            csv_writer = csv.DictWriter(file, fieldnames=fieldnames)
            csv_writer.writeheader()

            for row in diction_rows_now:
                try:
                    csv_writer.writerow(row)
                except Exception as e:
                    print(f"Error write row{row} csv_to_now")
                    continue
        except Exception as e:
            print("Fail to Write now csv")      
            return
         
    # Save the swaped diction_rows_other to a new CSV file
    # EN_to_CN means load _CN, the old data save to _EN as backup 
    # if load _CN, save old data to _EN 
    if EN_to_CN:
        with open(file_path_EN, 'w', encoding='utf-8', newline='') as file:
            try:
                fieldnames_other = diction_rows_other[0].keys()
                csv_writer_other = csv.DictWriter(file, fieldnames=fieldnames_other)
                print(f'Swap Write {file_name_without_extension}')
                csv_writer_other.writeheader()

                for row_other in diction_rows_other:
                    try:
                        csv_writer_other.writerow(row_other)
                    except Exception as e:
                        print(f"Error write row{row_other} csv_to_backup")
                        continue
            except Exception as e:
                print("Fail to Write other csv")
        os.remove(file_path_CN)
    else:
        with open(file_path_CN, 'w', encoding='utf-8', newline='') as file:
            try:
                fieldnames_other = diction_rows_other[0].keys()
                csv_writer_other = csv.DictWriter(file, fieldnames=fieldnames_other)
                csv_writer_other.writeheader()
                print(f'Swap Write {file_name_without_extension}')

                for row_other in diction_rows_other:
                    try:
                        csv_writer_other.writerow(row_other)
                    except Exception as e:
                        print(f"Error write row{row_other} csv_to_backup")
                        continue
            except Exception as e:
                print("Fail to Write other csv")
        os.remove(file_path_EN)
    print(f'Swap Done {file_name_without_extension}')

if __name__ == "__main__":
    #swap_file_csv("data/campaign/submarkets.csv", "submarkets", ['name', 'desc'])
    #swap_file_csv("data/campaign/rules.csv", "rules", ['script','text','options'])
    #swap_file_csv("data/campaign/industries.csv", "industries", ['name','desc'])
    swap_file_csv("data/campaign/special_items.csv", "special23_items", ['name','tech/manufacturer','desc'])
    #swap_file_csv("data/strings/descriptions.csv", "descriptions", ['text1','text2','text3','text4'])