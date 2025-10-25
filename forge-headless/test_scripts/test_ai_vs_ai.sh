#!/bin/bash
# E2E test for AI vs AI gameplay
# This test verifies that:
# 1. AI agents work correctly
# 2. Games can run without user input
# 3. The game completes successfully

set -e  # Exit on any error

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FORGE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
HEADLESS_DIR="$FORGE_DIR/forge-headless"
TARGET_DIR="$HEADLESS_DIR/target"
TEST_DECKS="$HEADLESS_DIR/test_decks"

# Find the jar file
JAR_FILE=$(ls -t "$TARGET_DIR"/forge-headless-*-SNAPSHOT-jar-with-dependencies.jar 2>/dev/null | head -1)

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}ERROR: forge-headless jar not found. Please run 'mvn package' first.${NC}"
    exit 1
fi

echo "=== AI vs AI E2E Test ==="
echo "Using JAR: $JAR_FILE"
echo "Test scenario: Two AI players play against each other"
echo ""

# Create a temporary log file
LOG_FILE=$(mktemp)
trap "rm -f $LOG_FILE" EXIT

# Run the test with both AI agents
echo "Running AI vs AI game with seed=42..."
cd "$FORGE_DIR"

# Use absolute paths since headless.sh changes directory
DECK1="$TEST_DECKS/monored.dck"
DECK2="$TEST_DECKS/monored.dck"

timeout 30 ./headless.sh tui \
    "$DECK1" \
    "$DECK2" \
    --seed 42 \
    --p1 ai \
    --p2 ai > "$LOG_FILE" 2>&1

EXIT_CODE=$?

# Check if the test succeeded
echo ""
echo "=== Analyzing Results ==="

# Check exit code
if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Game completed successfully (exit code 0)"
else
    echo -e "${RED}✗${NC} Game failed with exit code: $EXIT_CODE"
    cat "$LOG_FILE"
    exit 1
fi

# Check that both players are AI
if grep -q "Starting game:.*AI.*vs.*AI" "$LOG_FILE"; then
    echo -e "${GREEN}✓${NC} Both players are AI"
else
    echo -e "${RED}✗${NC} Not all players are AI"
    cat "$LOG_FILE"
    exit 1
fi

# Check that no TUI prompts were shown (game ran autonomously)
if grep -q "Enter choice" "$LOG_FILE"; then
    echo -e "${RED}✗${NC} TUI prompts found (AI not working correctly)"
    cat "$LOG_FILE"
    exit 1
else
    echo -e "${GREEN}✓${NC} No TUI prompts (game ran autonomously)"
fi

# Check that game completed
if grep -q "GAME OVER" "$LOG_FILE"; then
    echo -e "${GREEN}✓${NC} Game completed"
else
    echo -e "${RED}✗${NC} Game did not complete"
    cat "$LOG_FILE"
    exit 1
fi

# Check that there was a winner
if grep -q "Winner:" "$LOG_FILE"; then
    WINNER=$(grep "Winner:" "$LOG_FILE" | head -1)
    echo -e "${GREEN}✓${NC} $WINNER"
else
    echo -e "${RED}✗${NC} No winner found"
    cat "$LOG_FILE"
    exit 1
fi

# Success!
echo ""
echo -e "${GREEN}=== Test PASSED ===${NC}"
echo "All checks completed successfully!"

exit 0
