#!/bin/bash
# Auto-pass script: outputs "0" repeatedly for testing TUI
# Usage: ./auto_pass.sh | ./headless.sh tui a.dck a.dck

for i in {1..1000}; do
  echo "0"
  sleep 0.1
done
