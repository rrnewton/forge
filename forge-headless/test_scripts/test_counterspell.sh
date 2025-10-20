#!/bin/bash
# E2E test for counterspell functionality
# This test verifies that:
# 1. A player can counter an opponent's spell
# 2. Instants can be cast in response to opponent spells
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
echo "Test scenario: Deterministic game where counterspell opportunities arise"
echo ""

# Create a temporary log file
LOG_FILE=$(mktemp)
trap "rm -f $LOG_FILE" EXIT

# Test inputs:
# With seed=42, the game is deterministic. We need to:
# 1. Keep starting hands for both players (no mulligan) - input "0" twice
# 2. P1's turn: Pass priority on their turn by repeatedly entering "0"
# 3. P2's turn: P2 will cast spells, P1 gets priority to respond
# 4. When P2 casts a spell, P1 should be offered chance to cast instant (Counterspell)
# 5. We'll provide many "0" inputs to pass priority and let the game progress

# Run the test with deterministic seed
echo "Running game with seed=42..."
cd "$FORGE_DIR"

# Use absolute paths since headless.sh changes directory
DECK1="$TEST_DECKS/counterspells.dck"
DECK2="$TEST_DECKS/monored.dck"

# Inputs:
# - "0": Keep starting hand (no mulligan) for P1
# - "1": Play land (Island) on turns 1, 2, 3 (we'll need mana to cast Counterspell)
# - "0": Pass priority most of the time
# - "1": Cast counterspell when offered (after AI casts a spell)
# - "0": Choose target for counterspell (the spell on stack)
# P2 is controlled by AI, so it will actually play spells that P1 can counter
# The test will timeout after 90s or when game ends
#
# Input sequence:
# - 0: Keep hand
# - 1,0,1,0,1,0: Play land on first 3 turns and pass otherwise
# - Then many 1,0 pairs: when AI casts, we get offered counterspell (choose 1), then target (choose 0)
# - Fallback to all 0s at the end
printf "0\n1\n0\n1\n0\n1\n0\n1\n0\n1\n0\n1\n0\n1\n0\n1\n0\n1\n0\n1\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n0\n" | timeout 90 ./headless.sh tui \
    "$DECK1" \
    "$DECK2" \
    --seed 42 \
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
# The monored deck contains various burn spells and creatures
if grep -q "Add to stack: AI-monored cast" "$LOG_FILE"; then
    echo -e "${GREEN}✓${NC} Opponent's spell was added to the stack"
else
    echo -e "${RED}✗${NC} No opponent spell found on stack"
    cat "$LOG_FILE"
    exit 1
fi

# Check if counterspell was actually cast (not just offered)
if grep -q "Add to stack: Player 1 cast Counterspell" "$LOG_FILE"; then
    echo -e "${GREEN}✓${NC} Player successfully cast Counterspell"
else
    echo -e "${RED}✗${NC} Counterspell was not cast"
    cat "$LOG_FILE"
    exit 1
fi

# Check if the counterspell resolved
if grep -q "Resolve stack: Counterspell.*Counter" "$LOG_FILE"; then
    echo -e "${GREEN}✓${NC} Counterspell successfully countered a spell"
else
    echo -e "${RED}✗${NC} Counterspell did not counter anything"
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
echo ""
echo "Opponent spells cast:"
grep "Add to stack: AI-monored cast" "$LOG_FILE" | head -5
echo ""
echo "Counterspells cast:"
grep "Add to stack: Player 1 cast Counterspell" "$LOG_FILE"
echo ""
echo "Counterspell resolutions:"
grep "Resolve stack: Counterspell.*Counter" "$LOG_FILE"

exit 0
