#!/bin/bash
set -euo pipefail

dir=$(dirname "$0")

cd $dir/forge-headless/target && java -jar forge-headless-*-SNAPSHOT-jar-with-dependencies.jar "$@"
