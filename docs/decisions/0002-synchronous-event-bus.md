# 0002: Changes are announced on a synchronous event bus, project first

**Status:** accepted

## Context

A rename has to update the reference index, the decompiler cache, class mappings, bookmarks, the tree and the code
view. Calling each of these from the rename code tied renaming to every other feature, and the order mattered: the
UI read stale bookmarks if it refreshed before they were updated.

## Decision

Each `Project` has an `EventBus`. Feature services publish a `ProjectEvent` (a sealed interface: JARs loaded or
removed, a class removed, a rename step applied or undone, JARs saved, bookmarks changed) after changing state.
Delivery is synchronous, on the publishing thread. `Project` subscribes first and updates all derived state
(indexes, caches, mappings, bookmarks) in `Project.onEvent`, so later listeners such as the UI see a consistent
project.

## Consequences

- Derived state belongs in `Project.onEvent`, not in UI code.
- Listeners run on the thread that changed the project (see 0003), so they must be quick.
- The sealed event type makes every listener's `switch` say what it does for each kind of change.
