# UX review: Rikr: rationale

These are suggestions for the maintainers to consider, not a redesign. This document backs the deck
[UX review: Rikr suggestions 5, 8 and 9](https://claude.ai/artifact/1SgxqfDPDy4SQnVN6vWnM9); section numbers match
slide numbers.

## Context

- **Goal:** experienced reverse engineers open an obfuscated JAR, find code, understand it and rename it without
  losing work. Rikr is a work tool used for long sessions, so speed and error prevention matter, not persuasion.
- **Captured:** 30 October 2026, macOS, window 1440×900, dark theme, `gamepack.jar` loaded. Contrast was measured on
  rendered pixels.
- **Method:** each SUGGESTION image is the running app with only that slide's change applied to the live Swing
  components.
- **Not known:** there is no usage data or user research. Keyboard-only use and VoiceOver were not tested hands-on.
- **Standard:** WCAG 2.2. No specific accessibility law is assumed for a free desktop tool.

## Summary

| Slide | Suggestion | Severity | Evidence | Effort |
|---|---|---|---|---|
| 2 | Make the search field visible | 3 | Measured | S |
| 3 | A find bar that explains itself | 3 | Measured, observed | S |
| 4 | The same treatment for the tree's field | 3 | Measured | S |
| 5 | Icons for M, R, B and C | 2 | Measured, heuristic | S |
| 6 | Give the settings labels normal text colour | 2 | Measured, standard | S |

## 2. Make the search field visible

**Change:** give the Search workspace's field a 1 px border (#7a7e85) and the hint "Search names and instructions".
Keep the Cc and W icons, add accessible names and a visible on/off state.

**Rule or principle:** WCAG 2.2 SC 1.4.11 Non-text Contrast (AA): the boundary of an input needs 3:1 against what
is next to it; SC 3.3.2 Labels or Instructions (A). Nielsen heuristic 6, recognition rather than recall.

**Why it matters:** today the field (#242424) sits on a #2b2d30 panel at 1.12:1 with no hint, so the workspace looks
empty and people have to guess where to type. With a 3.4:1 border and a hint, the first thing to do is visible.

**Who benefits:** everyone opening the Search tab for the first time; low-vision users most.

**Severity · evidence:** 3 · measured (1.12:1 field to panel; suggested border 3.39:1 to the panel, 3.81:1 to the
field).

**How to validate:** ask new users to "find where getCodeBase is called" and time how long it takes them to start
typing; re-run the pixel contrast check.

## 3. A find bar that explains itself

**Change:** border and hint "Find in this class"; "No matches" and "3 of 12" instead of "0/0"; Previous and Next
instead of Up and Down, bound to Cmd+G and Shift+Cmd+G.

**Rule or principle:** WCAG 2.2 SC 1.4.11 (AA) and SC 3.3.2 (A); Nielsen heuristics 1 (visibility of system status)
and 4 (consistency and standards); Apple's macOS conventions for Find Next and Find Previous.

**Why it matters:** "0/0" reads as a score, not a match count, and Up and Down don't say what they move between.
Mac users press Cmd+G to find the next match in every other app; today that groups the class tree instead.

**Who benefits:** everyone searching within a class; keyboard users.

**Severity · evidence:** 3 · measured (same 1.12:1 field) and observed (labels and shortcut conflict).

**How to validate:** watch whether people press Cmd+G after a search; count how often "No matches" appears before
they change the query.

## 4. The same treatment for the tree's field

**Change:** border and the hint "Find in the tree" on the field above the project tree, so all three fields share
one style.

**Rule or principle:** WCAG 2.2 SC 1.4.11 (AA); Nielsen heuristic 4, consistency and standards.

**Why it matters:** the field reads as an empty strip with two stray buttons. With the same style as the other two
fields, people learn one search pattern for the whole app.

**Who benefits:** everyone browsing a large JAR.

**Severity · evidence:** 3 · measured (1.12:1).

**How to validate:** the same first-use task as slide 2, started from the Project tab.

## 5. Icons for M, R, B and C

**Change:** replace the letters with outline icons drawn in the folder and search icons' style (1.5 px stroke,
20 px grid, #c9ccd1, 2× for Retina): two classes with arrows (Cross-JAR mappings), arrows into a target
(References), a ribbon (Bookmarks), a block branching in two (Flow). Every rail button gets its tooltip as its
accessible name; tooltips stay.

**Rule or principle:** WCAG 2.2 SC 4.1.2 Name, Role, Value (A) and SC 1.1.1 Non-text Content (A) for the missing
names; Nielsen heuristic 6, recognition rather than recall. Heuristic: icons are recognised faster than letters only
when they are conventional, so the tooltips stay while people learn them.

**Why it matters:** letters have to be memorised, and the folder and search buttons announce nothing to a screen
reader today. Icons in one style make the rail read as a set.

**Who benefits:** new users; screen-reader users.

**Severity · evidence:** 2 · measured (empty accessible names on the folder and search buttons) and heuristic
(letters).

**How to validate:** ask people to "open the references for a class" without hovering; check the names with
VoiceOver.

**Effort and risk:** small. The icons are proposals; the team may prefer existing icon sets with the same metaphors.

## 6. Give the settings labels normal text colour

**Change:** labels use the theme's normal label colour (#cccccc) instead of `Theme.CONTENT_HIGHLIGHT_TEXT_COLOR`.

**Rule or principle:** WCAG 2.2 SC 1.4.3 Contrast (Minimum), AA: 4.5:1 for text; this also meets SC 1.4.6 Contrast
(Enhanced), AAA, at 7:1.

**Why it matters:** at 1.71:1 the labels are close to unreadable, so people have to guess what each control sets.
At 8.6:1 everyone can read them.

**Who benefits:** everyone; low-vision users and anyone on a bright display or in daylight most.

**Severity · evidence:** 2 · measured (#505050 on #2b2d30 = 1.71:1; #cccccc = 8.60:1) and standard.

**How to validate:** re-run the pixel contrast check on the Settings window.
