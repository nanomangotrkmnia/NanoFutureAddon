# NanoFutureAddon

A Meteor Client addon for Minecraft 1.21.11. Adds a few exploit, movement, and utility modules.

## build

    ./gradlew build

The jar is in `build/libs/`.

## install

Put the jar in your mods folder next to Meteor Client for 1.21.11.

## modules

- `ai-assistant`: chat with a configured provider. Use `.ai <message>`; `.contextclear` resets context.
- `infinite-reach`: teleports to distant blocks, breaks or uses them, then teleports back.
- `instant-break`: sends the break packets and stops at the server's 0.7 finish point.
- `phase`: teleports through walls while your hitbox is inside a block.
- `reminder`: shows a toast with your text at a set interval.
- `time-shift`: sends move packet bursts to speed up regen and effects on 1.8 servers.

## notes

TimeShift only works on 1.8 servers.
Phase needs you to be inside a block first.
InfiniteReach is capped at 22 blocks on vanilla.

## commands

    .ai <message>
    .contextclear

## license

All rights reserved. See `LICENSE`.
