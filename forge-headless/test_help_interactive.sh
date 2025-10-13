#!/bin/bash
# Interactive test for help and card viewing features
# This script demonstrates the new features

echo "=================================="
echo "TUI Help & Card Viewing Test"
echo "=================================="
echo ""
echo "This test will show you the new features:"
echo "1. Type '?' at any prompt to see help"
echo "2. Type 'v' at any prompt to view card details"
echo ""
echo "Let's start a game. When prompted, try:"
echo "  - Type '?' to see help"
echo "  - Type 'v' to view a card"
echo "  - Type '0' to pass and move forward"
echo ""
read -p "Press Enter to start the game..."

cd "$(dirname "$0")"
../headless.sh tui '../test_decks/monored.dck' '../test_decks/monored.dck'
