# Testing the Forge TUI

## Running Tests

From the `forge-headless` directory, run:

```bash
python3 test_tui.py
```

Or to run a specific test:

```bash
python3 test_tui.py pass    # Run pass agent test
python3 test_tui.py random  # Run random agent test
```

This will run automated tests against the TUI using different agents.

## Test Structure

The test framework includes:

1. **scripts/pass_agent.py** - Always chooses option 0 (pass priority). Used to verify that passive play results in a loss.

2. **scripts/random_agent.py** - Makes random choices. Used to verify the TUI can handle various game paths.

3. **test_tui.py** - Main test runner that:
   - Spawns agents and TUI games via subprocess
   - Parses game output to extract metrics
   - Verifies expected outcomes with comprehensive assertions
   - Reports test results

## Test Invariants

The tests validate important game invariants:

**Pass Agent Test:**
- Game takes more than 2 turns (validates progression)
- Battlefield has permanents by end (AI plays lands/creatures)
- Basic lands in hand are offered as play options

**Random Agent Test:**
- Decks load successfully
- Game survives at least 3 turns
- No Java exceptions occur (validates mana filtering)
- No "payManaCost() cost was not paid" errors

## Adding New Tests

To add a new test, create a function in `test_tui.py`:

```python
def test_my_new_test():
    """
    Description of what this test verifies.
    """
    print("Running test: my_new_test...")
    runner = TUITestRunner()

    # Run a game
    returncode, stdout, stderr = runner.run_game(
        "agent_script.py",
        "deck1.dck",
        "deck2.dck"
    )

    # Parse results
    result = runner.parse_game_result(stderr + stdout)

    # Make assertions
    assert result['game_completed'], "Game should complete"
    # ... more assertions ...

    print("✓ Test passed")
    return True
```

Then add it to the `tests` list in `main()`.

## Creating New Agents

Agents should:
1. Read from stdin line by line
2. Echo lines to stderr for debugging
3. When they see "Enter choice (X-Y):", write a number to stdout
4. Flush stdout after each choice

See `pass_agent.py` and `random_agent.py` for examples.
