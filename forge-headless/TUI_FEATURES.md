# Text UI (TUI) Interactive Features

## Help Command (`?`)

At any choice prompt, type `?` to display help information.

The help shows:
- Available commands
- How to play lands and cast spells
- Information about priority and game flow

### Example:
```
Enter choice (0-5, or ? for help): ?

=== HELP ===
Commands:
  0-9       - Select an action by number
  ?         - Show this help
  v         - View a card (see detailed card text)

During your turn, you can:
  - Play lands (if you haven't used your land drop)
  - Cast spells from your hand
  - Pass priority (0) to move to the next phase

You will be prompted repeatedly until you pass priority.
============
```

## Card Viewer (`v`)

At any choice prompt, type `v` to view detailed information about cards.

The viewer shows:
- All cards from your hand
- All cards from both players' battlefields
- Cards are sorted alphabetically
- Location tags show where each card is

### Example:
```
Enter choice (0-3, or ? for help): v

=== VIEW CARD ===
Select a card to view:
  0. Hired Claw [Hand]
  1. Lightning Strike [Hand]
  2. Mountain [Hand]
  3. Mountain [Battlefield - Player 1]
  4. Screaming Nemesis [Battlefield - AI-monored]
Enter card number (or press Enter to cancel): 0

============================================================
Hired Claw {R}
------------------------------------------------------------
Type: Creature - Lizard Mercenary
Power/Toughness: 1/2

Text:
Whenever you attack with one or more Lizards, Hired Claw deals 1 damage to target opponent.
{1}{R}: Put a +1/+1 counter on Hired Claw. Activate only if an opponent lost life this turn and only once each turn.
============================================================
```

### Card Details Displayed:

For all cards:
- Card name and mana cost
- Type line (e.g., "Creature - Goblin Warrior", "Instant", etc.)
- Oracle text (all abilities and rules text)

For creatures:
- Power/Toughness

For cards on the battlefield:
- Controller name
- Status flags (Tapped, Summoning Sickness, etc.)

## Usage Tips

- Use `v` frequently to check card text during gameplay
- The card viewer includes opponent's cards so you can read their abilities
- Press Enter (empty input) to cancel card viewing and return to your turn
- Both `?` and `v` can be used at any prompt without consuming your action
