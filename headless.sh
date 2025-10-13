#!/bin/bash
set -xeuo pipefail

dir=$(dirname "$0")

cd $dir/forge-headless/target && time java -jar forge-headless-*-SNAPSHOT-jar-with-dependencies.jar "$@"
