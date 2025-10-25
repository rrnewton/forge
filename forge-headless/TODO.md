# Forge TUI - TODO and Status

## Current Status (2025-10-20)

### ✅ Completed Features

#### Counterspell Support
- Fixed `hasValidTargets()` to check spells on the stack
- Added stack spell targeting in `chooseTargetsFor()`
- Added `SpellAbility` formatting in `formatTarget()`
- Players are now correctly prompted to counter opponent spells

#### UI Improvements
- Removed redundant game state display on opponent's turn
- Only show full game state on player's own turn
- Added `b` command to view battlefield/game state on demand
- Added `s` command to view current stack contents
- Simplified error messages

#### Testing Infrastructure
- Added `--seed <long>` flag for deterministic RNG
- Fixed Makefile to only test TUI-related modules (forge-core, forge-game, forge-ai, forge-headless)
- Restored headless.sh wrapper script for proper JAR execution
- Unit tests pass successfully (6 tests in forge-headless)

### ⚠️ In Progress

#### Puzzle State Loading (.pzl files)
**Status**: Blocked by integration complexity

**Problem**: The `--start-state` flag to load .pzl files hits a fundamental issue:
```
java.lang.IllegalStateException: Turns already started, call this only once per game
```

**Root Cause**: Forge's puzzle system expects to be loaded at a specific point in the game lifecycle, but the TUI game loop and puzzle system have conflicting initialization sequences.

**What Works**:
- Parsing .pzl files ✅
- Creating GameState objects ✅
- `--seed` flag for deterministic games ✅

**What Doesn't Work**:
- Applying puzzle state after game creation ❌
- Running game loop with pre-loaded state ❌

**Next Steps**:
1. Investigate Forge's `PhaseHandler.setupFirstTurn()` to understand the initialization sequence
2. Explore alternative approaches:
   - Option A: Load state before `match.createGame()`
   - Option B: Use a custom game initialization path for TUI
   - Option C: Modify puzzle loading to work with already-started games
3. Consider whether we need .pzl files or if deterministic seeds are sufficient for testing

#### End-to-End Testing
**Status**: Design phase (waiting on .pzl fix)

**Plan**: Create `forge-headless/test_scripts/test_counterspell.sh` that:
- Uses `--seed 42` for deterministic testing
- Uses `--p2=tui` to control both players
- Uses `--numeric-choices` for predictable input
- Feeds deterministic input sequence via stdin
- Greps logs for expected events:
  - "Counterspell" card present
  - "Cast instant: Counterspell" option offered
  - Opponent spell on stack

**Alternative**: If .pzl loading proves too complex, we can:
- Rely on `--seed` alone for reproducibility
- Use longer input sequences to reach desired game states
- Accept that tests start from turn 1

### 📋 Backlog

#### High Priority
- [ ] Resolve .pzl file loading issue OR decide to skip it
- [ ] Complete E2E test for counterspell functionality
- [ ] Add `make validate` to CI/documentation workflow

#### Medium Priority
- [ ] Add graveyard viewer command (similar to 'v' for cards)
- [ ] Improve stack display formatting
- [ ] Add color support for mana symbols in card text
- [ ] Show card counts in zones (e.g., "Hand: 5 cards" → "Hand: 5 cards (2x Mountain, ...)")

#### Low Priority
- [ ] Add replay/undo functionality
- [ ] Save game state to .pzl file
- [ ] Add keybindings customization
- [ ] Support for multiplayer (3+ players)

## Known Issues

### Critical
None

### Minor
1. Some cards may not display correctly in card viewer if they have complex formatting
2. Very long card names might wrap awkwardly in menus
3. Stack display doesn't show parent/child spell relationships clearly

## Testing Notes

**Before Committing**:
Always run `make validate` which includes:
- Unit tests (`mvn test` for TUI modules only)
- Build verification
- E2E tests (when implemented)

**Current Test Status**:
- Unit tests: ✅ 6/6 passing
- E2E tests: ⚠️ Not yet implemented (blocked by .pzl)
- Manual testing: ✅ Counterspell works correctly

## Recent Changes

### Commit 016b8fd6c2 - Fix Makefile and restore headless.sh wrapper script
- Updated test target to avoid GUI module failures
- Restored headless.sh from git history
- Documented .pzl loading issue

### Commit ba45b0239b - Add counterspell support and streamline opponent turn display
- Check spells on stack as valid targets
- Add stack spell targeting in chooseTargetsFor()
- Format SpellAbility objects for target display
- Only show full game state on player's own turn

### Commit (uncommitted) - Add view battlefield and stack commands
- Added 'b' command to display game state on demand
- Added 's' command to view current stack
- Updated help text

## Development Workflow

1. Make changes to Java files in `forge/forge-headless/src/`
2. Build: `make build` (or `mvn -pl forge-core,forge-game,forge-ai,forge-headless -am package -DskipTests -Dcheckstyle.skip=true`)
3. Test manually: `./headless.sh tui deck1.dck deck2.dck [--seed 42]`
4. Run validation: `make validate` (unit tests + build)
5. Commit with validation results in commit message

## Contact/Questions

See `/workspace/CLAUDE.md` for detailed development notes and architecture.
