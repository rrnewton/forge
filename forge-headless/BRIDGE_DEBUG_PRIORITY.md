# Debug priority protocol, 2026-09-20

`debug.priority.v2` extends the local observer protocol; ordinary bridge games and
`debug.observed_state.v1` retain their behavior. Enable with both `game_start`
booleans `debug_state: true` and `debug_priority_v2: true` after checking the hello
capability. The observer sees hidden state; neither gameplay policy receives it.

Every actual priority callback, for either seat and with any stack depth, publishes
one frozen `debug_checkpoint`. Forge performs state-based actions and puts pending
triggers on the stack before that callback. No callback is silently passed to align
the engines. Legal phase skipping remains the rules engine's responsibility.

Nonterminal snapshots additionally require:

- `priority_seat`: one-based seat receiving priority.
- `transaction_id`: positive increasing integer, beginning at 1 for this game.
- `stack`: top-first array of `{identity, controller, kind}`, where `identity` is
  the initialized catalog identity, `controller` is a one-based seat and `kind` is
  `spell`, `activated`, or `triggered`.

The v2 phase vocabulary is `untap`, `upkeep`, `draw`, `main1`, `begincombat`,
`declareattackers`, `declareblockers`, `firststrikedamage`, `combatdamage`,
`endcombat`, `main2`, `end`, `cleanup`. Only actual priority callbacks produce
stops; this vocabulary does not create priority in untap or ordinary cleanup.
Initial positions and terminal snapshots omit the three additional fields.

A `decision` of kind `priority`, or an `opponent_action` containing a priority
command, must echo `transaction_id`, `priority_seat`, `turn`, `phase`, and
`active_seat` in `context`. The addressed seat must be the pending priority seat.
One transaction admits exactly one command. Missing, stale, wrong-seat, and
duplicate commands fail without releasing the game thread. A `spell_targets`
continuation echoes the same context and is admitted only after its priority
command and before the next priority callback. Combat and discard callbacks
retain their separate existing protocol.

Failures on the game thread wake all debug rendezvous waiters and surface the
original cause. Malformed protocol commands produce a fatal error notification
and a request error where applicable.

The initial stack descriptors are observations, not target handles. Duplicate
same-name spells/abilities still need stable object/ability/stack handles before
general stack targeting is claimed. This increment does not assert full choice
or card coverage; every reachable unsupported choice must remain explicit.
