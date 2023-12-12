import os
import csv

def read_image_names(folder_path, type, map, folder_name = ''):
    data = []

    # Iterate through files in the folder
    for filename in os.listdir(folder_path):
        if filename.endswith(f"_{map}.png"):
            file_path = os.path.join(folder_path, filename)
            file_path = file_path.replace('\\','/')
            # Extract information from the file name
            filename_no_ext = filename.replace(f"_{map}.png", "")
            
            file_id = ''
            if (('aEP_b_') in file_path 
                or ('aEP_e_') in file_path
                or ('aEP_m_') in file_path):
                file_id = folder_name
            else:
                file_id = folder_name + '_' + filename_no_ext
            
            
            
            if file_id.endswith('01'): 
                file_id = file_id.replace('01','')
            if file_id.endswith('00'): 
                file_id = file_id.replace('00','')
            file_type = type
            file_frame = ""  # Assuming no specific frame information in the file name
            file_magnitude = 1
            file_map = map

            # Append data to the list
            data.append([file_id, file_type, file_frame, file_magnitude, file_map, file_path])

    return data

def process_folders(base_folder, type, map_type, csv_name):
    all_data = []

    for root, dirs, files in os.walk(base_folder):
        for folder in dirs:
            folder_path = os.path.join(root, folder)
            folder_path = folder_path.replace('\\','/')
            if folder.endswith(''):  # Check if it's a folder with images (adjust as needed)
                folder_data = read_image_names(folder_path, type, map_type, folder)
                all_data.extend(folder_data)
    write_to_csv(all_data, os.path.join(''), csv_name)

def write_to_csv(data, output_path, csv_name):
    # Define the CSV header
    header = ["id", "type", "frame", "magnitude", "map", "path"]

    # Write data to CSV file as "texture.csv" in the script's directory
    output_csv_path = os.path.join(output_path, f"texture_{csv_name}.csv")
    with open(output_csv_path, mode='w', newline='') as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(header)
        writer.writerows(data)

    return output_csv_path

if __name__ == "__main__":
     # Get the absolute path of the script
    script_path = os.path.abspath(__file__)
    
    # Get the absolute path of the script's directory (mod's root path)
    mod_root_path = os.path.dirname(script_path)
    
    # Change the working directory to the mod's directory
    os.chdir(mod_root_path)

    # Output CSV file path (within the current directory, named "texture.csv")
    #input_folder = os.path.join("graphics/shaders/normal/ships/")
    #write_to_csv(
    #    read_image_names(input_folder,'ship', 'normal'), os.path.join(''), "ships_noraml")
    #
    #input_folder = os.path.join("graphics/shaders/normal/fighters/")
    #write_to_csv(
    #    read_image_names(input_folder,'ship', 'normal'), os.path.join(''), "fighters_noraml")
    
    #input_folder = os.path.join("graphics/shaders/normal/stations/")
    #write_to_csv(
    #    read_image_names(input_folder,'ship', 'normal'), os.path.join(''), "stations_noraml")

    #
    #input_folder = os.path.join("graphics/shaders/material/ships/")
    #write_to_csv(
    #    read_image_names(input_folder,'ship', 'material'), os.path.join(''), "ships_material")
    #
    #input_folder = os.path.join("graphics/shaders/surface/ships/")
    #write_to_csv(
    #    read_image_names(input_folder,'ship', 'surface'), os.path.join(''), "ships_surface")

    input_folder = os.path.join("graphics/shaders/normal/weapons")
    output_csv_path = process_folders(input_folder, 'weapon', 'normal',"all_weapon")
    
    input_folder = os.path.join("graphics/shaders/material/weapons")
    output_csv_path = process_folders(input_folder, 'weapon', 'material',"all_weapon")
    
    input_folder = os.path.join("graphics/shaders/surface/weapons")
    output_csv_path = process_folders(input_folder, 'weapon', 'surface',"all_weapon")
    print(f"CSV file has been created with the extracted data in the script's directory.")
