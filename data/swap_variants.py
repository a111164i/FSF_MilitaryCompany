import os

def swap_variants():
    # Get the directory where the script is located
    script_directory = os.path.dirname(os.path.abspath(__file__))

    # Define the paths
    variants_path = os.path.join(script_directory, 'variants')
    variants_li_path = os.path.join(script_directory, 'variants_li')
    variants_org_path = os.path.join(script_directory, 'variants_org')

    # Print out the paths for debugging
    print(f"Script directory: {script_directory}")
    print(f"Variants path: {variants_path}")
    print(f"Variants_li path: {variants_li_path}")
    print(f"Variants_org path: {variants_org_path}")
    
    # Check existence of directories
    print(f"Does 'variants' exist? {'Yes' if os.path.exists(variants_path) else 'No'}")
    print(f"Does 'variants_li' exist? {'Yes' if os.path.exists(variants_li_path) else 'No'}")
    print(f"Does 'variants_org' exist? {'Yes' if os.path.exists(variants_org_path) else 'No'}")

    # Check if the current backup is "variants_li"
    if os.path.exists(variants_li_path) and not os.path.exists(variants_org_path):
        # Rename "variants" to "variants_org"
        os.rename(variants_path, variants_org_path)
        # Rename "variants_li" to "variants"
        os.rename(variants_li_path, variants_path)
        print('Swapped "variants_li" with "variants".')

    # Check if the current backup is "variants_org"
    elif os.path.exists(variants_org_path) and not os.path.exists(variants_li_path):
        # Rename "variants" to "variants_li"
        os.rename(variants_path, variants_li_path)
        # Rename "variants_org" to "variants"
        os.rename(variants_org_path, variants_path)
        print('Swapped "variants_org" with "variants".')

    else:
        print('No swap performed. Please ensure the directories exist and try again.')

# Call the function to perform the swap
swap_variants()
