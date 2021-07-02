#!/usr/bin/env python
# Copyright 2017 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import argparse
import json
import os.path
import sys

import pdl

def main(argv):
    parser = argparse.ArgumentParser(description=("Converts from .pdl to .json by invoking the pdl Python module."))
    parser.add_argument("--pdl_file", help="The .pdl input file to parse.")
    parser.add_argument("--json_file", help="The .json output file write.")
    args = parser.parse_args(argv)
    file_name = os.path.normpath(args.pdl_file)
    input_file = open(file_name, "r")
    pdl_string = input_file.read()
    protocol = pdl.loads(pdl_string, file_name, True)
    input_file.close()

    output_file = open(os.path.normpath(args.json_file), 'w')
    json.dump(protocol, output_file, indent=4, separators=(',', ': '))
    output_file.close()


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))