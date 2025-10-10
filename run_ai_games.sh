#!/bin/bash
# Script to run AI vs AI games in headless mode

set -e

# Configuration
N_GAMES=${1:-1}  # Number of games to run, default 1
JAR_PATH="forge-gui-desktop/target/forge-gui-desktop-2.0.07-SNAPSHOT-jar-with-dependencies.jar"
OUTPUT_DIR="./output"
DECK1="forge-gui/res/quest/precons/Air Razers.dck"
DECK2="forge-gui/res/quest/precons/Abzan Siege.dck"

# Java options for headless mode
JAVA_OPTS="-Xmx4096m \
  -Djava.awt.headless=true \
  -Dio.netty.tryReflectionSetAccessible=true \
  -Dfile.encoding=UTF-8 \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.time=ALL-UNNAMED \
  --add-opens java.base/java.text=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.util.regex=ALL-UNNAMED \
  --add-opens java.base/java.util.stream=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED"

echo "=== Forge AI vs AI Game Simulation ==="
echo "Number of games: $N_GAMES"
echo "Deck 1: $DECK1"
echo "Deck 2: $DECK2"
echo ""

# Check if JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: JAR file not found at $JAR_PATH"
    echo "Please run: mvn -pl forge-core,forge-game,forge-ai,forge-gui,forge-gui-desktop -am clean package -DskipTests -Dcheckstyle.skip=true"
    exit 1
fi

# Check if deck files exist
if [ ! -f "$DECK1" ]; then
    echo "ERROR: Deck 1 not found at $DECK1"
    exit 1
fi

if [ ! -f "$DECK2" ]; then
    echo "ERROR: Deck 2 not found at $DECK2"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Generate timestamp for log file
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$OUTPUT_DIR/game_${TIMESTAMP}.log"

echo "Running simulation..."
echo "Output will be saved to: $LOG_FILE"
echo ""

# Run the simulation
java $JAVA_OPTS -jar "$JAR_PATH" sim \
  -d "$DECK1" "$DECK2" \
  -n "$N_GAMES" \
  2>&1 | tee "$LOG_FILE"

# Extract and display summary
echo ""
echo "=== Game Summary ==="
echo "Log file: $LOG_FILE"

# Count wins for each player
AI1_WINS=$(grep -c "Ai(1).*has won" "$LOG_FILE" || true)
AI2_WINS=$(grep -c "Ai(2).*has won" "$LOG_FILE" || true)
DRAWS=$(grep -c "ended in a Draw" "$LOG_FILE" || true)

echo "AI(1) - $(basename "$DECK1" .dck): $AI1_WINS wins"
echo "AI(2) - $(basename "$DECK2" .dck): $AI2_WINS wins"
echo "Draws: $DRAWS"
echo ""
echo "Simulation complete!"
