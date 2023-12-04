import os
import csv

def read_image_names(folder_path, type, map):
    data = []

    # Iterate through files in the folder
    for filename in os.listdir(folder_path):
        if filename.endswith(f"_{map}.png"):
            file_path = os.path.join(folder_path, filename)

            # Extract information from the file name
            file_id = filename.replace(f"_{map}.png", "")
            file_type = type
            file_frame = ""  # Assuming no specific frame information in the file name
            file_magnitude = 1
            file_map = map

            # Append data to the list
            data.append([file_id, file_type, file_frame, file_magnitude, file_map, file_path])

    return data

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
    input_folder = os.path.join("graphics/shaders/normals/ships/")
    output_csv_path = write_to_csv(
        read_image_names(input_folder,'ship', 'normal'), os.path.join(''), "ships_noraml")
    
    input_folder = os.path.join("graphics/shaders/normals/fighters/")
    output_csv_path = write_to_csv(
        read_image_names(input_folder,'ship', 'normal'), os.path.join(''), "fighters_noraml")

    input_folder = os.path.join("graphics/shaders/normals/stations/")
    output_csv_path = write_to_csv(
        read_image_names(input_folder,'ship', 'normal'), os.path.join(''), "stations_noraml")

   
    input_folder = os.path.join("graphics/shaders/material/ships/")
    output_csv_path = write_to_csv(
        read_image_names(input_folder,'ship', 'material'), os.path.join(''), "ships_material")
    
    input_folder = os.path.join("graphics/shaders/surface/ships/")
    output_csv_path = write_to_csv(
        read_image_names(input_folder,'ship', 'surface'), os.path.join(''), "ships_surface")

    print(f"CSV file has been created with the extracted data in the script's directory.")
