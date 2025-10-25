#!/bin/bash
set -euo pipefail

# Get the directory where this script is located
script_dir=$(cd "$(dirname "$0")" && pwd)

# Convert any relative deck paths to absolute paths
# Relative paths are resolved relative to the script's location, not the caller's pwd
args=()
for arg in "$@"; do
    # Check if this looks like a deck file path (ends in .dck and doesn't start with -)
    if [[ "$arg" == *.dck ]] && [[ "$arg" != -* ]]; then
        # If it's a relative path (doesn't start with /), resolve relative to script directory
        if [[ "$arg" != /* ]]; then
            arg="$script_dir/$arg"
        fi
    fi
    args+=("$arg")
done

cd "$script_dir/forge-headless/target" && time java -jar forge-headless-*-SNAPSHOT-jar-with-dependencies.jar "${args[@]}"
