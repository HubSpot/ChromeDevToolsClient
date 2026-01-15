#!/usr/bin/env python
# Copyright 2017 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import argparse
import json
import os.path
import re
import sys

import pdl

def resolve_includes(pdl_string, base_dir, processed_files=None):
    """
    Recursively resolve include statements in PDL files.

    Args:
        pdl_string: The PDL content as a string
        base_dir: The directory containing the PDL file (for resolving relative includes)
        processed_files: Set of already processed files to prevent circular includes

    Returns:
        The PDL content with all includes resolved
    """
    if processed_files is None:
        processed_files = set()

    lines = pdl_string.split('\n')
    result_lines = []

    for line in lines:
        # Match include statements like: include domains/Accessibility.pdl
        match = re.match(r'^\s*include\s+(.+\.pdl)\s*$', line)
        if match:
            include_path = match.group(1)
            # Resolve the include path relative to the base directory
            full_include_path = os.path.normpath(os.path.join(base_dir, include_path))

            # Prevent circular includes
            if full_include_path in processed_files:
                print(f'Warning: Circular include detected for {full_include_path}, skipping', file=sys.stderr)
                continue

            # Check if the included file exists
            if not os.path.exists(full_include_path):
                print(f'Error: Included file not found: {full_include_path}', file=sys.stderr)
                sys.exit(1)

            processed_files.add(full_include_path)

            # Read the included file
            with open(full_include_path, 'r') as include_file:
                included_content = include_file.read()

            # Recursively resolve includes in the included file
            included_dir = os.path.dirname(full_include_path)
            resolved_content = resolve_includes(included_content, included_dir, processed_files)

            # Add the resolved content (without the include line itself)
            result_lines.append(f'# Included from: {include_path}')
            result_lines.append(resolved_content)
        else:
            result_lines.append(line)

    return '\n'.join(result_lines)

def main(argv):
    parser = argparse.ArgumentParser(description=("Converts from .pdl to .json by invoking the pdl Python module."))
    parser.add_argument("--pdl_file", help="The .pdl input file to parse.")
    parser.add_argument("--json_file", help="The .json output file write.")
    args = parser.parse_args(argv)

    file_name = os.path.normpath(args.pdl_file)
    base_dir = os.path.dirname(file_name)

    input_file = open(file_name, "r")
    pdl_string = input_file.read()
    input_file.close()

    pdl_string = resolve_includes(pdl_string, base_dir, set([file_name]))

    protocol = pdl.loads(pdl_string, file_name, True)

    output_file = open(os.path.normpath(args.json_file), 'w')
    json.dump(protocol, output_file, indent=4, separators=(',', ': '))
    output_file.close()


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
