#!/usr/bin/env python
# Script to fetch all domain PDL files referenced in browser_protocol.pdl

import argparse
import base64
import os
import re
import sys
import urllib.request
import urllib.error


def extract_includes(pdl_file):
    """
    Extract all include statements from the PDL file.

    Args:
        pdl_file: Path to the browser_protocol.pdl file

    Returns:
        List of included file paths (e.g., ["domains/Accessibility.pdl", "domains/Animation.pdl"])
    """
    includes = []

    with open(pdl_file, 'r') as f:
        for line in f:
            match = re.match(r'^\s*include\s+(.+\.pdl)\s*$', line)
            if match:
                includes.append(match.group(1))

    return includes


def fetch_domain_files(chrome_version, pdl_file, output_dir):
    """
    Fetch all domain PDL files from Chrome source repository.

    Args:
        chrome_version: Chrome version tag (e.g., "143.0.7499.40")
        pdl_file: Path to the browser_protocol.pdl file
        output_dir: Base directory where domain files will be saved
    """
    # Extract all include statements
    includes = extract_includes(pdl_file)

    if not includes:
        print("No include statements found in the PDL file.")
        return

    print(f"Found {len(includes)} domain files to fetch:")
    for include in includes:
        print(f"  - {include}")
    print()

    # Base URL for Chrome source (using gitiles raw API)
    # The ?format=TEXT parameter returns the file content as base64-encoded text
    base_url = f"https://chromium.googlesource.com/chromium/src/+/refs/tags/{chrome_version}/third_party/blink/public/devtools_protocol/"

    # Fetch each domain file
    success_count = 0
    failed_files = []

    for include_path in includes:
        # Construct the full URL with format=TEXT for base64 encoded raw content
        url = base_url + include_path + "?format=TEXT"

        # Construct the output path
        output_path = os.path.normpath(os.path.join(output_dir, include_path))
        output_directory = os.path.dirname(output_path)

        # Create the output directory if it doesn't exist
        os.makedirs(output_directory, exist_ok=True)

        print(f"Fetching {include_path} at url {url}...")

        try:
            # Fetch the file content
            with urllib.request.urlopen(url) as response:
                # The content is base64-encoded
                encoded_content = response.read()

                # Decode from base64
                decoded_content = base64.b64decode(encoded_content)

                # Write to file
                with open(output_path, 'wb') as f:
                    f.write(decoded_content)

                # Verify it's not HTML
                with open(output_path, 'r') as f:
                    first_line = f.readline()
                    if 'DOCTYPE html' in first_line or '<html' in first_line:
                        print(f"  ✗ Error: Received HTML instead of PDL file (file not found at URL)")
                        failed_files.append(include_path)
                        os.remove(output_path)
                    else:
                        print(f"  ✓ Success")
                        success_count += 1

        except urllib.error.HTTPError as e:
            print(f"  ✗ Error: HTTP {e.code} - {e.reason}")
            failed_files.append(include_path)
        except urllib.error.URLError as e:
            print(f"  ✗ Error: {e.reason}")
            failed_files.append(include_path)
        except Exception as e:
            print(f"  ✗ Error: {e}")
            failed_files.append(include_path)

    # Print summary
    print()
    print("=" * 60)
    print(f"Fetch complete: {success_count}/{len(includes)} files downloaded successfully")

    if failed_files:
        print()
        print("Failed to download:")
        for failed in failed_files:
            print(f"  - {failed}")
        print()
        print("This may indicate that the Chrome version doesn't exist or the file structure has changed.")
        sys.exit(1)


def main(argv):
    # Calculate default paths relative to this script's location
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_pdl_file = os.path.join(script_dir, "..", "CodeGeneration", "src", "main", "resources", "browser_protocol.pdl")
    default_output_dir = os.path.join(script_dir, "..", "CodeGeneration", "src", "main", "resources")

    parser = argparse.ArgumentParser(
        description="Fetch all domain PDL files referenced in browser_protocol.pdl from Chrome source repository.",
        epilog="""
Example usage:
  python fetch_domains.py --chrome_version 143.0.7499.40
        """
    )
    parser.add_argument(
        "--chrome_version",
        required=True,
        help="Chrome version tag (e.g., '143.0.7499.40')"
    )
    parser.add_argument(
        "--pdl_file",
        default=default_pdl_file,
        help=f"Path to the browser_protocol.pdl file (default: {default_pdl_file})"
    )
    parser.add_argument(
        "--output_dir",
        default=default_output_dir,
        help=f"Base directory where domain files will be saved (default: {default_output_dir})"
    )

    args = parser.parse_args(argv)

    # Normalize paths
    pdl_file = os.path.normpath(args.pdl_file)
    output_dir = os.path.normpath(args.output_dir)

    # Validate that the PDL file exists
    if not os.path.exists(pdl_file):
        print(f"Error: PDL file not found: {pdl_file}")
        sys.exit(1)

    # Fetch the domain files
    fetch_domain_files(args.chrome_version, pdl_file, output_dir)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
