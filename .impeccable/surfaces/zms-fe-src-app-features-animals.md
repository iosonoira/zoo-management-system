---
version: 1
slug: "zms-fe-src-app-features-animals"
primary_target: "zms-fe/src/app/features/animals"
related_targets: []
---

# Surface brief: Animals (list, detail, status, transfer)

## Scope and mode

Operate. Routes `/animals` and `/animals/:id` in `zms-fe`. Keeper flow first (find, check, transfer), vet status change, role-aware actions. Registration form out of scope. Data from an in-memory mock behind the same contract as `animal-service` `/animals`, fictional demo data labelled as such, demo role switcher (keeper / vet / admin) standing in for Keycloak.

Redesign 2026-09-19: layout, composition and behaviour kept (enclosure group above its animals; detail with hero, Status, Location, Record; sheets; toast); the Enclosure Signage visual world is replaced. User rejected: heavy brown, double routed rules, Overpass, saturated filled status panels. Light theme primary plus a dark mode.

## Audience and job

Keeper on a phone outdoors in daylight, walking enclosure to enclosure; vet changing clinical status; admin reviewing roster; reviewer opening cold on desktop.

## Direction contract

THESIS: The zoo kept as a glasshouse register: each enclosure a bed under glass, each animal a staked label in that bed. Calm, cool and exact in daylight. Refuses both the old brown enamel signage and the neutral-grey-plus-indigo SaaS admin.

OWN-WORLD: Cool glass-white ground (faint green), clear white panes with 1px glazing rules in conservatory green-black, one deep conservatory green for primary action. Each habitat owns one full-strength hue (moss terrestrial, glass blue aquatic, verdigris amphibious) shown only as a solid stake tile. Every status owns a soft tint shown on its tile everywhere (green healthy, amber observation, clay treatment, stone deceased); user decision 2026-09-19: colour always indicates state. Red is danger only, as an outlined DANGER tab and a tinted band. Atkinson Hyperlegible Next 400/700/800 with Atkinson Hyperlegible Mono for tags, counts, dates. Dark mode is the glasshouse at night: neutral graphite ground, panes one step up, green only as accent.

STORY: The keeper scans a bed, sees which animals need attention because only they carry colour, taps one, reads its record, transfers it or (vet) changes status, and sees who did it.

FIRST VIEWPORT: Mobile: white top bar with hairline, "Animals" 34px/800 on the ground with count, 48px search pane, status filter chips, then the first enclosure pane: header row with habitat stake, name 17px/700, mono count, arrow; animal rows 68px inside the same pane divided by rules: tinted status tile, name 18px/700 with optional DANGER tab, status word and species, mono tag chip, chevron. Desktop: panes flow in columns; detail is two columns.

FORM: Glasshouse Register, position 7 on my ordered list, seed key c784738b. Raises: one full-strength hue per section (acetate tab manual); hierarchy by scale contrast, few weights (type specimen); one marker marks the active step on the status track (origami); one 4px rhythm and fixed row, tabular mono (oscilloscope). Signature interaction: transfer re-stakes the location label, old one slides out along its arrow, new one slides in (View Transitions, reduced-motion crossfade).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved

Enclosure naming lives only in the FE mock (backend has bare UUIDs). Search and filters are client-side over the full list.
