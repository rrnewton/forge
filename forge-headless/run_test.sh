#!/bin/bash
# Simple shell-based test runner for TUI
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Test 1: Pass agent always loses
echo "=========================================="
echo "Test 1: Pass agent should lose"
echo "=========================================="

OUTPUT=$(timeout 30 bash -c "yes 0 | ../../headless.sh tui \
    '$SCRIPT_DIR/test_decks/monored.dck' \
    '$SCRIPT_DIR/test_decks/monored.dck' 2>&1" || true)

if echo "$OUTPUT" | grep -q "Winner: AI-monored"; then
    echo "✓ PASSED: AI won as expected"
else
    echo "✗ FAILED: Expected AI to win"
    echo "Output: $OUTPUT" | tail -20
    exit 1
fi

echo ""
echo "=========================================="
echo "All tests passed!"
echo "=========================================="
