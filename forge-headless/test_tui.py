#!/usr/bin/env python3
"""
Test framework for Forge TUI

This test suite runs automated tests against the TUI to verify:
1. Basic game flow works
2. Agents can interact with the TUI
3. Game outcomes are deterministic in simple cases
4. Choice tracking works correctly
"""

import subprocess
import sys
import re
import os
from pathlib import Path


class TUITestRunner:
    def __init__(self):
        self.forge_dir = Path(__file__).parent
        self.test_decks_dir = self.forge_dir / "test_decks"
        self.scripts_dir = self.forge_dir / "scripts"
        self.headless_script = self.forge_dir.parent.parent / "headless.sh"

    def run_game(self, agent_script, deck1, deck2, timeout=120):
        """
        Run a TUI game with an agent driving player 1.

        Returns:
            tuple: (returncode, stdout, stderr)
        """
        agent_path = self.scripts_dir / agent_script
        deck1_path = self.test_decks_dir / deck1
        deck2_path = self.test_decks_dir / deck2

        # Make agent executable
        os.chmod(agent_path, 0o755)

        # Convert to absolute paths
        deck1_abs = str(deck1_path.absolute())
        deck2_abs = str(deck2_path.absolute())

        # Run: agent_script | ./headless.sh tui deck1 deck2
        agent_proc = subprocess.Popen(
            [str(agent_path)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            stdin=subprocess.PIPE,
        )

        game_proc = subprocess.Popen(
            [str(self.headless_script), "tui", deck1_abs, deck2_abs],
            stdin=agent_proc.stdout,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

        # Close our handle on agent stdout so game can get EOF when agent exits
        agent_proc.stdout.close()

        try:
            game_stdout, game_stderr = game_proc.communicate(timeout=timeout)
            agent_stderr = agent_proc.stderr.read().decode('utf-8')
            return (game_proc.returncode, game_stdout, game_stderr + agent_stderr)
        except subprocess.TimeoutExpired:
            game_proc.kill()
            agent_proc.kill()
            return (-1, "", "Test timed out")

    def parse_game_result(self, output):
        """
        Parse the game output to extract winner and other info.

        Returns:
            dict with keys: winner, is_draw, choices_made, etc.
        """
        result = {
            'winner': None,
            'is_draw': False,
            'game_completed': False,
            'choices_made': 0,
            'choice_options': 0
        }

        # Look for winner in output
        winner_match = re.search(r'Winner: (.+)', output)
        if winner_match:
            result['winner'] = winner_match.group(1)
            result['game_completed'] = True

        # Look for draw
        if 'Result: Draw!' in output:
            result['is_draw'] = True
            result['game_completed'] = True

        # Count choices made (look for agent output)
        choice_pattern = re.compile(r'\[AGENT\] Chose: (\d+)')
        choices = choice_pattern.findall(output)
        result['choices_made'] = len(choices)

        # Count choice prompts to get total options
        prompt_pattern = re.compile(r'Enter choice \((\d+)-(\d+)\):')
        for match in prompt_pattern.finditer(output):
            min_choice = int(match.group(1))
            max_choice = int(match.group(2))
            result['choice_options'] += (max_choice - min_choice + 1)

        return result


def test_pass_agent_loses():
    """
    Test that a pass-only agent always loses against the AI.
    """
    print("Running test: pass_agent_loses...")
    runner = TUITestRunner()

    returncode, stdout, stderr = runner.run_game(
        "pass_agent.py",
        "monored.dck",
        "monored.dck"
    )

    # Print stderr for debugging
    if stderr:
        print("\n--- Agent/Game Output ---")
        print(stderr[:2000])  # First 2000 chars

    result = runner.parse_game_result(stderr + stdout)

    # Assertions
    assert result['game_completed'], "Game did not complete"
    assert not result['is_draw'], "Game should not be a draw"
    assert result['winner'] is not None, "There should be a winner"

    # The human player (who only passes) should lose
    # Winner should be "AI-monored" or similar
    assert 'AI' in result['winner'] or 'Ai' in result['winner'], \
        f"Expected AI to win, but winner was: {result['winner']}"

    print(f"✓ Test passed: AI won as expected")
    print(f"  - Winner: {result['winner']}")
    print(f"  - Choices made by agent: {result['choices_made']}")

    return True


def test_random_agent_completes():
    """
    Test that a random agent can complete a game.
    """
    print("\nRunning test: random_agent_completes...")
    runner = TUITestRunner()

    returncode, stdout, stderr = runner.run_game(
        "random_agent.py",
        "monored.dck",
        "monored.dck",
        timeout=180  # Longer timeout for random agent
    )

    result = runner.parse_game_result(stderr + stdout)

    # Assertions
    assert result['game_completed'], "Game did not complete"
    assert result['winner'] is not None or result['is_draw'], \
        "Game should have a winner or be a draw"

    print(f"✓ Test passed: Game completed")
    print(f"  - Winner: {result['winner']}")
    print(f"  - Draw: {result['is_draw']}")
    print(f"  - Choices made by agent: {result['choices_made']}")

    return True


def main():
    """
    Run all tests.
    """
    print("=" * 60)
    print("Forge TUI Test Suite")
    print("=" * 60)

    # Check if a specific test is requested
    if len(sys.argv) > 1:
        test_name = sys.argv[1]
        if test_name == "pass":
            tests = [test_pass_agent_loses]
        elif test_name == "random":
            tests = [test_random_agent_completes]
        else:
            print(f"Unknown test: {test_name}")
            print("Available tests: pass, random, all")
            return 1
    else:
        tests = [
            test_pass_agent_loses,
            test_random_agent_completes,
        ]

    passed = 0
    failed = 0

    for test_func in tests:
        try:
            test_func()
            passed += 1
        except AssertionError as e:
            print(f"✗ Test failed: {e}")
            failed += 1
        except Exception as e:
            print(f"✗ Test error: {e}")
            import traceback
            traceback.print_exc()
            failed += 1

    print("\n" + "=" * 60)
    print(f"Results: {passed} passed, {failed} failed")
    print("=" * 60)

    return 0 if failed == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
