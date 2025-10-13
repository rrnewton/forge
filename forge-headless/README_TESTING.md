# Testing the Forge TUI

## Running Tests

From the `forge-headless` directory, run:

```bash
./test_tui.py
```

This will run automated tests against the TUI using different agents.

## Test Structure

The test framework includes:

1. **pass_agent.py** - Always chooses option 0 (pass priority). Used to verify that passive play results in a loss.

2. **random_agent.py** - Makes random choices. Used to verify the TUI can handle various game paths.

3. **test_tui.py** - Main test runner that:
   - Spawns agents and TUI games
   - Parses game output
   - Verifies expected outcomes
   - Reports test results

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
