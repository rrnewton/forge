#!/usr/bin/env python3
"""
Pass-Only Agent for Forge TUI Testing

This agent always passes priority (chooses option 0).
It's useful for testing that passive play always results in a loss.

Usage:
    ./pass_agent.py | ./headless.sh tui test_decks/monored.dck test_decks/monored.dck
"""

import sys
import re


def main():
    # Regular expression to match "Enter choice (X-Y):" prompts
    choice_pattern = re.compile(r'Enter choice \((\d+)-(\d+)\):')

    for line in sys.stdin:
        # Echo the line to stderr so we can see the game output
        print(line, end='', file=sys.stderr)

        # Check if this line is asking for a choice
        match = choice_pattern.search(line)
        if match:
            # Always choose 0 (pass priority)
            print(0, flush=True)
            print(f"[AGENT] Chose: 0 (pass)", file=sys.stderr, flush=True)


if __name__ == '__main__':
    main()
