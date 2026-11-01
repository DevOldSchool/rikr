# 0014: Auto-match keeps to its minimum and replaces only its own matches

**Status:** accepted

## Context

Auto-match runs in two phases: classes whose best candidate is unique and at least the minimum similarity in
shape, then more classes from the members of those matched (`ClassVoting`). Only the first phase used the minimum.
The second accepted every class its votes found, shown with its share of the votes as its similarity, so a run at
90% could add matches at 65%. A class already matched was never looked at again, so running Auto-match once more
with a higher minimum left the weaker matches of an earlier run in place. People raising the minimum expected the
classes below it to be left unmatched.

## Decision

A `ClassMappingDecision` records whether Auto-match accepted it (`automatic`). Auto-match, in the GUI and as
`auto_match_classes`, first forgets its own matches below the minimum, then accepts in both phases only matches at
or above it: a similarity in shape, or a share of the votes. Matches a person accepted, rejected or cleared are never
touched by Auto-match, whatever their similarity. The project file saves the flag from format version 3; version 2
files are read with every match a person's, since nothing recorded which were automatic.

Finding candidates reads the classes in the background; dropping and accepting matches happen on the thread that
changes the project (decision 0003), which announces them with `MappingsChanged`.

## Consequences

- The minimum means the same for every match Auto-match makes, and can be raised and run again to tighten an earlier
  run.
- A match accepted by hand from the candidate list is safe from Auto-match, even below the minimum.
- Matches from version 2 project files count as a person's, so raising the minimum does not drop them; clearing them
  by hand, or starting the mapping again, does.
