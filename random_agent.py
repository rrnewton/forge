#!/usr/bin/env python3
"""
Random Agent for Forge TUI

Reads the TUI output from stdin and responds with random choices
when prompted. This allows testing the TUI with an agent that makes
random moves rather than always passing priority.

Usage:
    ./random_agent.py | ./headless.sh tui a.dck a.dck
"""

import sys
import re
import random


def main():
    # Regular expression to match "Enter choice (X-Y):" prompts
    choice_pattern = re.compile(r'Enter choice \((\d+)-(\d+)\):')

    for line in sys.stdin:
        # Echo the line to stderr so we can see the game output
        print(line, end='', file=sys.stderr)

        # Check if this line is asking for a choice
        match = choice_pattern.search(line)
        if match:
            min_choice = int(match.group(1))
            max_choice = int(match.group(2))

            # Generate a random choice in the valid range
            choice = random.randint(min_choice, max_choice)

            # Output the choice to stdout (which goes to the TUI's stdin)
            print(choice, flush=True)

            # Log what we chose to stderr
            print(f"[AGENT] Chose: {choice} (from range {min_choice}-{max_choice})",
                  file=sys.stderr, flush=True)


if __name__ == '__main__':
    main()
