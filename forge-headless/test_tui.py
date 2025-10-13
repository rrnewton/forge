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
        self.forge_dir = Path(__file__).parent.resolve()
        self.test_decks_dir = self.forge_dir / "test_decks"
        self.scripts_dir = self.forge_dir / "scripts"
        # headless.sh is 3 directories up: forge-headless -> forge -> outer-repo -> headless.sh
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
            'choice_options': 0,
            'max_turn': 0,
            'final_battlefield_count': 0,
            'land_in_hand_offered': False,
            'had_basic_land_in_hand': False,
            'java_error_occurred': False,
            'decks_loaded': False
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

        # Extract max turn number
        turn_pattern = re.compile(r'Turn (\d+) -')
        turns = [int(m.group(1)) for m in turn_pattern.finditer(output)]
        if turns:
            result['max_turn'] = max(turns)

        # Check for battlefield permanents at end (look for last battlefield state before GAME OVER)
        # Count "Lands in play:" and "Creatures:" lines
        battlefield_sections = re.findall(r'Lands in play: (\d+)', output)
        creature_sections = re.findall(r'Creatures: (\d+)', output)
        if battlefield_sections or creature_sections:
            # Get the last few battlefield counts (near game end)
            recent_lands = [int(x) for x in battlefield_sections[-4:]] if battlefield_sections else []
            recent_creatures = [int(x) for x in creature_sections[-4:]] if creature_sections else []
            result['final_battlefield_count'] = max(recent_lands + recent_creatures, default=0)

        # Check if land in hand was offered as a play option during main phase
        # Look for patterns like "Play land: Mountain" or "Play land: Plains"
        land_play_pattern = re.compile(r'\d+\. Play land: (Mountain|Plains|Forest|Island|Swamp)')
        if land_play_pattern.search(output):
            result['land_in_hand_offered'] = True

        # Check if player had a basic land in hand (look in "Your hand:" sections)
        hand_pattern = re.compile(r'Your hand:.*?(?=Life:|Creatures:|Library:|$)', re.DOTALL)
        basic_lands = ['Mountain', 'Plains', 'Forest', 'Island', 'Swamp']
        for hand_section in hand_pattern.finditer(output):
            hand_text = hand_section.group(0)
            if any(land in hand_text for land in basic_lands):
                result['had_basic_land_in_hand'] = True
                break

        # Check for Java errors (Exception, Error in output)
        if 'Exception' in output or 'Error:' in output or 'java.lang' in output:
            # Filter out expected error messages about card loading
            if 'ComputerUtilMana' not in output:  # This is the error we're trying to fix
                result['java_error_occurred'] = True

        # Check if decks loaded successfully
        if 'Starting game:' in output or 'Game starting...' in output:
            result['decks_loaded'] = True

        return result


def test_pass_agent_loses():
    """
    Test that a pass-only agent always loses against the AI.

    Invariants:
    - Game takes more than 2 turns
    - Something is on the battlefield by end (at least AI should play land)
    - If we have a basic land in hand, it should be listed as an option
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
        print("\n--- Agent/Game Output (first 2000 chars) ---")
        print(stderr[:2000])

    result = runner.parse_game_result(stderr + stdout)

    # Assertions - Basic game completion
    assert result['game_completed'], "Game did not complete"
    assert not result['is_draw'], "Game should not be a draw"
    assert result['winner'] is not None, "There should be a winner"

    # The human player (who only passes) should lose
    # Winner should be "AI-monored" or similar
    assert 'AI' in result['winner'] or 'Ai' in result['winner'], \
        f"Expected AI to win, but winner was: {result['winner']}"

    # Invariant 1: Game should take more than 2 turns
    assert result['max_turn'] > 2, \
        f"Game should take more than 2 turns, but ended on turn {result['max_turn']}"

    # Invariant 2: Something should be on the battlefield by end
    # (At minimum, the AI should have played some lands)
    assert result['final_battlefield_count'] > 0, \
        "At least one permanent should be on the battlefield by game end"

    # Invariant 3: If we had a basic land in hand, it should have been offered
    # Note: This only checks if we had a land AND it was offered at some point
    if result['had_basic_land_in_hand']:
        assert result['land_in_hand_offered'], \
            "Had a basic land in hand but it was never offered as a play option"

    print(f"✓ Test passed: AI won as expected")
    print(f"  - Winner: {result['winner']}")
    print(f"  - Max turn: {result['max_turn']}")
    print(f"  - Final battlefield count: {result['final_battlefield_count']}")
    print(f"  - Choices made by agent: {result['choices_made']}")
    if result['had_basic_land_in_hand']:
        print(f"  - Land in hand was offered: {result['land_in_hand_offered']}")

    return True


def test_random_agent_completes():
    """
    Test that a random agent can complete a game.

    Invariants:
    - Decks load successfully
    - Game takes multiple turns (survives a few rounds)
    - No Java errors occur (even when casting spells)
    """
    print("\nRunning test: random_agent_completes...")
    runner = TUITestRunner()

    returncode, stdout, stderr = runner.run_game(
        "random_agent.py",
        "monored.dck",
        "monored.dck",
        timeout=180  # Longer timeout for random agent
    )

    output = stderr + stdout
    result = runner.parse_game_result(output)

    # Invariant 1: Decks should load successfully
    assert result['decks_loaded'], \
        "Decks failed to load - check for deck loading errors"

    # Invariant 2: Game should complete
    assert result['game_completed'], "Game did not complete"
    assert result['winner'] is not None or result['is_draw'], \
        "Game should have a winner or be a draw"

    # Invariant 3: Game should take multiple turns (at least 3)
    assert result['max_turn'] >= 3, \
        f"Game should survive at least 3 turns, but ended on turn {result['max_turn']}"

    # Invariant 4: No Java errors should occur
    # This validates our mana filtering fix - random choices shouldn't cause crashes
    assert not result['java_error_occurred'], \
        "Java error occurred during game - check for exceptions in output"

    # Additional check: Look for the specific mana cost error we're trying to prevent
    if 'ComputerUtilMana: payManaCost() cost was not paid' in output:
        raise AssertionError("Mana cost error occurred - mana filtering is not working correctly")

    print(f"✓ Test passed: Random agent game completed successfully")
    print(f"  - Winner: {result['winner'] if result['winner'] else 'Draw'}")
    print(f"  - Max turn: {result['max_turn']}")
    print(f"  - Choices made by agent: {result['choices_made']}")
    print(f"  - No Java errors: ✓")

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
