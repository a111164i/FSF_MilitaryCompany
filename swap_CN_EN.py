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


    # check does file_path containing file_name_without_extension, it should have
    # if it does not, meaning the file_name_without_extension is incorrectly input
    file_path = os.path.join(file_path)
    file_path_EN = file_path
    file_path_CN = file_path
    if file_name_without_extension in file_path:
        file_path_EN = os.path.join(file_path.replace(file_name_without_extension, f"{file_name_without_extension}_EN"))
        file_path_CN = os.path.join(file_path.replace(file_name_without_extension, f"{file_name_without_extension}_CN"))
    assert file_path != file_path_EN, "Check file name and file path input"
    
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
                if (value_now and value_other 
                    and value_now.startswith("{") and value_now.endswith("}") 
                    and value_other.startswith("{") and value_other.endswith("}") ):
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

def swap_json(file_path: str, file_name_without_extension: str, extension: str = None):
    def read_json_with_comments(file_path):
        with open(file_path, 'r', encoding='utf-8') as file:
            lines = file.readlines()
        
        clean_content_parts = []
        for line in lines:
            # 以第一个#为分割点，拆分注释和有效内容（避免分割JSON字符串内的#）
            comment_split_result = line.split('#', 1)
            # 取分割后的第一部分（#之前的有效JSON内容）
            non_comment_part = comment_split_result[0]
            # 将有效内容添加到列表（保留原有缩进格式，不影响JSON解析）
            clean_content_parts.append(non_comment_part)
        
        # 拼接所有有效内容，形成无注释的JSON字符串
        clean_json_string = ''.join(clean_content_parts)
        # 解析JSON并返回
        return json.loads(clean_json_string)

    script_directory = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_directory)

    EN_file_full_name = f"{file_name_without_extension}_EN.json"
    CN_file_full_name = f"{file_name_without_extension}_CN.json"
    if(extension):
        EN_file_full_name = f"{file_name_without_extension}_EN.{extension}"
        CN_file_full_name = f"{file_name_without_extension}_CN.{extension}"

    file_path = os.path.join(file_path)
    file_path_EN = file_path
    file_path_CN = file_path
    if file_name_without_extension in file_path:
        file_path_EN = os.path.join(file_path.replace(file_name_without_extension, f"{file_name_without_extension}_EN"))
        file_path_CN = os.path.join(file_path.replace(file_name_without_extension, f"{file_name_without_extension}_CN"))

    EN_to_CN = False
    data1 = None
    data2 = None
    try:
        data1 = read_json_with_comments(file_path)
        data2 = read_json_with_comments(file_path_EN)            
    except FileNotFoundError as e:
        if EN_file_full_name in str(e):
            EN_to_CN = True
            data2 = read_json_with_comments(file_path_CN)
        else:
            print('Failed to load both EN/CN json')
            return

    def swap_nested_json_values(data1, data2):
        for key in data2:
            # If the value is a nested dictionary, call this function recursively
            if isinstance(data2[key], dict):
                swap_nested_json_values(data1[key], data2[key])
            # Swap values for non-dictionary items
            else:
                temp = data2[key]
                data2[key] = data1[key]
                data1[key] = temp
    swap_nested_json_values(data1, data2)

    with open(file_path, 'w', encoding='utf-8') as output:
        json.dump(data1, output, ensure_ascii=False, indent=2)

    if EN_to_CN:
        with open(file_path_EN, 'w', encoding='utf-8') as output:
            json.dump(data2, output, ensure_ascii=False, indent=2)
        print(f'Swap Done {file_name_without_extension}')
        os.remove(file_path_CN)
    else:
        with open(file_path_CN, 'w', encoding='utf-8') as output:
            json.dump(data2, output, ensure_ascii=False, indent=2)  
        print(f'Swap Done {file_name_without_extension}')
        os.remove(file_path_EN)
 
def swap_name(file_path: str, file_name_with_ext: str):
    # Split the file name and extension
    base_name, extension = os.path.splitext(file_name_with_ext)
    
    # Form the names for both "_CN.txt" and "_EN.txt"
    cn_file_name = f"{base_name}_CN{extension}"
    en_file_name = f"{base_name}_EN{extension}"
    
    # Get the directory of the file
    file_directory = os.path.dirname(file_path)
    
    # Check if the files exist in the directory
    cn_file_path = os.path.join(file_directory, cn_file_name)
    en_file_path = os.path.join(file_directory, en_file_name)
    
    if os.path.exists(cn_file_path):
        # Swap names
        os.rename(file_path, en_file_path)
        os.rename(cn_file_path, file_path)
        print(f"Swapped names: {file_name_with_ext} <-> {cn_file_name}")
    elif os.path.exists(en_file_path):
        # Swap names
        os.rename(file_path, cn_file_path)
        os.rename(en_file_path, file_path)
        print(f"Swapped names: {file_name_with_ext} <-> {en_file_name}")
    else:
        print(f"No corresponding {file_name_with_ext} found.")

def update_setting_in_json(file_path, key, new_value):

    script_directory = os.path.dirname(os.path.abspath(__file__))
    if not os.path.isabs(file_path):
        file_path = os.path.join(script_directory, file_path)
    try:
        # Open the settings file in read mode to load existing data
        with open(file_path, 'r',encoding='utf-8') as file:
            settings = json.load(file)
        
        # Check if the key exists in the JSON, if so, update the value
        if key in settings:
            if new_value is None:
                settings[key] = not settings[key] 
            else:
                settings[key] = new_value

            # Open the file in write mode to update the file with new data
            with open(file_path, 'w', encoding='utf-8') as file:
                json.dump(settings, file, indent=2, ensure_ascii=False)
            print(f"Value of '{key} in {file_path}' has been updated to {settings[key]}.")
        else:
            print(f"Key '{key}' not found in the settings file.")
    
    except FileNotFoundError:
        print(f"File '{file_path}' not found.")
    except json.JSONDecodeError:
        print(f"Error decoding JSON from file '{file_path}'.")
    except Exception as e:
        print(f"An unexpected error occurred: {e}")




      
if __name__ == "__main__":
    swap_file_csv("data/campaign/submarkets.csv", "submarkets", ['name', 'desc'])
    swap_file_csv("data/campaign/rules.csv", "rules", ['script','text','options'])
    swap_file_csv("data/campaign/industries.csv", "industries", ['name','desc'])
    swap_file_csv("data/campaign/special_items.csv", "special_items", ['name','tech/manufacturer','desc'])
    swap_file_csv("data/campaign/commodities.csv", "commodities", ['name'])
    swap_file_csv("data/campaign/market_conditions.csv", "market_conditions", ['name','desc'])
    swap_file_csv("data/strings/descriptions.csv", "descriptions", ['text1','text2','text3','text4','text5'])
    swap_file_csv("data/characters/skills/skill_data.csv", "skill_data", ['name','description','author'])
    swap_file_csv("data/shipsystems/ship_systems.csv", "ship_systems", ['name'])
    swap_file_csv("data/hulls/ship_data.csv", "ship_data", ['name','tech/manufacturer','designation'])
    swap_file_csv("data/hullmods/hull_mods.csv","hull_mods",['name','tech/manufacturer','uiTags','desc','short','sModDesc'])
    swap_file_csv("data/weapons/weapon_data.csv","weapon_data",['name','tech/manufacturer','primaryRoleStr','customPrimary'])
    swap_file_csv("data/config/LunaSettings.csv", "LunaSettings", ['fieldName','fieldDescription' ])
    swap_name("data/missions/aEP_eliminate_mission/descriptor.json", "descriptor.json")
    swap_name("data/missions/aEP_eliminate_mission/mission_text.txt", "mission_text.txt")
    swap_name("data/missions/aEP_first_contact/descriptor.json", "descriptor.json")
    swap_name("data/missions/aEP_first_contact/mission_text.txt", "mission_text.txt")
    swap_name("data/missions/aEP_planet_investigation/descriptor.json", "descriptor.json")
    swap_name("data/missions/aEP_planet_investigation/mission_text.txt", "mission_text.txt")
    swap_name("data/missions/aEP_assassination/descriptor.json", "descriptor.json")
    swap_name("data/missions/aEP_assassination/mission_text.txt", "mission_text.txt")
    swap_json("mod_info.json","mod_info")
    swap_json("data/config/modFiles/magicBounty_data.json", "magicBounty_data")
    swap_json("data/world/factions/aEP_FSF.faction", "aEP_FSF","faction")
    swap_json("data/world/factions/aEP_FSF_adv.faction", "aEP_FSF_adv","faction")
    update_setting_in_json("data/config/settings.json", 'aEP_UseEnString', None)
    