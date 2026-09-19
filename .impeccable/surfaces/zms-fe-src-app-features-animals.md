---
version: 1
slug: "zms-fe-src-app-features-animals"
primary_target: "zms-fe/src/app/features/animals"
related_targets: []
---

# Surface brief: Animals (list, detail, status, transfer)

## Scope and mode

Operate. Routes `/animals` and `/animals/:id` in `zms-fe`. Keeper flow first (find, check, transfer), vet status change, role-aware actions. Registration form out of scope. Data from an in-memory mock behind the same contract as `animal-service` `/animals`, fictional demo data labelled as such, demo role switcher (keeper / vet / admin) standing in for Keycloak.

## Audience and job

Keeper on a phone outdoors in daylight, walking enclosure to enclosure; vet changing clinical status; admin reviewing roster; reviewer opening cold on desktop.

## Direction contract

THESIS: The app is the zoo's own sign system. Animals are grouped under the enclosure plate you are standing at, each animal a routed sign plate. Refuses the admin table with status pills and a dark sidebar.

OWN-WORLD: Routed enamel plates: deep sign brown with white Overpass (Highway Gothic lineage), white plates with brown routed inset rule, rounded plate corners, authored pictograms in one stroke. Safety-sign colour law: green healthy, yellow observation, orange treatment, neutral grey retired (deceased); signal red reserved for the DANGER panel only. Neutral chroma-0 ground, never cream.

STORY: The keeper sees where each animal is and how it is, taps one, reads a full sign of its facts, moves it to another enclosure or (vet) changes its status, and sees who did it last. Forbidden actions explain why.

FIRST VIEWPORT: Mobile: brown header plate (title "Animals", count, demo role control), 52px search field, status filter row, then the first enclosure direction sign (name, habitat pictogram, arrow, animal count) with its animal plates: status symbol panel left, name 20px bold, species, short tag code right, DANGER panel when flagged. Desktop: enclosure groups flow in columns; detail is a two-column sign.

FORM: Enclosure signage, position 1 on my ordered list (chosen by the user as the pick card), seed key 3da4a227. Signature interaction: transfer re-hangs the location sign, the old enclosure plate slides out along its arrow and the new one slides in (View Transitions, reduced-motion crossfade).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved

Enclosure naming lives only in the FE mock (backend has bare UUIDs). Search and filters are client-side over the full list.
