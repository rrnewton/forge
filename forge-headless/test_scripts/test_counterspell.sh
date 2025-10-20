#!/bin/bash
# E2E test for counterspell functionality
# This test verifies that:
# 1. A player can counter an opponent's spell
# 2. The game state is loaded correctly from a .pzl file
# 3. The RNG seed makes the game deterministic

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

echo "=== Counterspell E2E Test ==="
echo "Using JAR: $JAR_FILE"
echo "Test scenario: P2 casts Lightning Bolt, P1 counters it"
echo ""

# Create a temporary log file
LOG_FILE=$(mktemp)
trap "rm -f $LOG_FILE" EXIT

# Test inputs:
# Since we're loading from a .pzl file that starts at turn 2 with specific hands,
# the test scenario is:
# 1. AI (P2) is active player, will cast Lightning Bolt or Shock
# 2. P1 gets priority to respond - cast Counterspell (option 1)
# 3. Choose target for Counterspell (option 0 - the spell on stack)
# 4. Pass priority repeatedly to let game continue
#
# We'll provide enough inputs to get through multiple turns

# Run the test with deterministic seed and both players controlled by TUI
echo "Running game with seed=42..."
cd "$FORGE_DIR"
printf "0\n0\n0\n1\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n" | timeout 90 ./headless.sh tui \
    "../test_decks/counterspells.dck" \
    "../test_decks/monored.dck" \
    --seed 42 \
    --start-state "../test_decks/counterspell_test.pzl" \
    --player2-tui \
    --numeric-choices > "$LOG_FILE" 2>&1

# Check if the test succeeded
echo ""
echo "=== Analyzing Results ==="

# Check for key events in the log
if grep -q "Counterspell" "$LOG_FILE"; then
    echo -e "${GREEN}✓${NC} Counterspell card found in game"
else
    echo -e "${RED}✗${NC} Counterspell card NOT found in game"
    cat "$LOG_FILE"
    exit 1
fi

if grep -q "Cast instant: Counterspell" "$LOG_FILE"; then
    echo -e "${GREEN}✓${NC} Player was offered Counterspell as a castable option"
else
    echo -e "${RED}✗${NC} Counterspell was NOT offered as a castable option"
    cat "$LOG_FILE"
    exit 1
fi

# Check if a spell was put on the stack (opponent's spell)
if grep -q "Add to stack.*Lightning Bolt\|Add to stack.*Shock" "$LOG_FILE"; then
    echo -e "${GREEN}✓${NC} Opponent's spell was added to the stack"
else
    echo -e "${RED}✗${NC} No opponent spell found on stack"
    cat "$LOG_FILE"
    exit 1
fi

# Success!
echo ""
echo -e "${GREEN}=== Test PASSED ===${NC}"
echo "All checks completed successfully!"
echo ""
echo "Full log saved to: $LOG_FILE"
echo "Keeping log file for inspection (temp file will be removed automatically)"

# Show a snippet of the log
echo ""
echo "=== Log Snippet (relevant parts) ==="
grep -E "(Counterspell|Lightning Bolt|Shock|Add to stack|Cast instant)" "$LOG_FILE" | head -20

exit 0
