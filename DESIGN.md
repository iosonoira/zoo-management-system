---
name: Zoo Management System
description: The zoo kept as a glasshouse register, with each enclosure a bed under glass and each animal a staked label in it.
colors:
  ground: "oklch(0.972 0.005 165)"
  ground-deep: "oklch(0.945 0.007 165)"
  pane: "oklch(1 0 0)"
  pane-hover: "oklch(0.985 0.006 165)"
  rule: "oklch(0.26 0.03 170 / 0.12)"
  rule-strong: "oklch(0.26 0.03 170 / 0.24)"
  ink: "oklch(0.24 0.028 170)"
  ink-2: "oklch(0.45 0.02 170)"
  ink-3: "oklch(0.62 0.014 170)"
  brand: "oklch(0.4 0.07 172)"
  brand-hover: "oklch(0.34 0.065 172)"
  brand-ink: "oklch(0.99 0.004 165)"
  brand-tint: "oklch(0.94 0.03 170)"
  terrestrial: "oklch(0.56 0.12 125)"
  aquatic: "oklch(0.54 0.1 235)"
  amphibious: "oklch(0.54 0.09 180)"
  stake-ink: "oklch(0.995 0 0)"
  healthy: "oklch(0.48 0.09 160)"
  healthy-tint: "oklch(0.95 0.03 160)"
  observation: "oklch(0.47 0.1 75)"
  observation-tint: "oklch(0.955 0.06 90)"
  treatment: "oklch(0.5 0.14 40)"
  treatment-tint: "oklch(0.945 0.035 45)"
  deceased: "oklch(0.47 0.01 170)"
  deceased-tint: "oklch(0.935 0.004 170)"
  danger: "oklch(0.53 0.19 29)"
  danger-tint: "oklch(0.955 0.025 25)"
  scrim: "oklch(0.2 0.03 170 / 0.45)"
typography:
  display:
    fontFamily: "'Atkinson Hyperlegible Next Variable', 'Atkinson Hyperlegible Next', system-ui, sans-serif"
    fontSize: "2.75rem"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "-0.035em"
  headline:
    fontFamily: "'Atkinson Hyperlegible Next Variable', 'Atkinson Hyperlegible Next', system-ui, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 800
    lineHeight: 1.1
    letterSpacing: "-0.025em"
  title:
    fontFamily: "'Atkinson Hyperlegible Next Variable', 'Atkinson Hyperlegible Next', system-ui, sans-serif"
    fontSize: "1.375rem"
    fontWeight: 800
    lineHeight: 1.1
    letterSpacing: "-0.015em"
  name:
    fontFamily: "'Atkinson Hyperlegible Next Variable', 'Atkinson Hyperlegible Next', system-ui, sans-serif"
    fontSize: "1.1875rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.01em"
  body:
    fontFamily: "'Atkinson Hyperlegible Next Variable', 'Atkinson Hyperlegible Next', system-ui, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.45
  body-sm:
    fontFamily: "'Atkinson Hyperlegible Next Variable', 'Atkinson Hyperlegible Next', system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.3
  label:
    fontFamily: "'Atkinson Hyperlegible Next Variable', 'Atkinson Hyperlegible Next', system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 400
    lineHeight: 1.1
  hazard:
    fontFamily: "'Atkinson Hyperlegible Next Variable', 'Atkinson Hyperlegible Next', system-ui, sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 700
    lineHeight: 1.5
    letterSpacing: "0.07em"
  tag-code:
    fontFamily: "'Atkinson Hyperlegible Mono', ui-monospace, monospace"
    fontSize: "0.8125rem"
    fontWeight: 500
    fontFeature: "tnum"
rounded:
  sm: "0.375rem"
  md: "0.625rem"
  pane: "1rem"
  sheet: "1.25rem"
  full: "999px"
spacing:
  "1": "0.25rem"
  "2": "0.5rem"
  "3": "0.75rem"
  "4": "1rem"
  "5": "1.5rem"
  "6": "2rem"
  "7": "3rem"
  gutter: "1rem"
  gutter-wide: "2rem"
  target: "2.75rem"
components:
  button-primary:
    backgroundColor: "{colors.brand}"
    textColor: "{colors.brand-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "0 1.5rem"
    height: "3.25rem"
  button-primary-hover:
    backgroundColor: "{colors.brand-hover}"
    textColor: "{colors.brand-ink}"
  button-primary-disabled:
    backgroundColor: "{colors.ground-deep}"
    textColor: "{colors.ink-3}"
  button-secondary:
    backgroundColor: "{colors.pane}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "0 1.5rem"
    height: "3.25rem"
  button-secondary-hover:
    backgroundColor: "{colors.pane-hover}"
  button-grave:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.pane}"
    rounded: "{rounded.md}"
    padding: "0 1.5rem"
    height: "3.25rem"
  button-quiet:
    textColor: "{colors.ink-2}"
    rounded: "{rounded.md}"
    padding: "0 0.75rem"
    height: "{spacing.target}"
  button-quiet-hover:
    backgroundColor: "{colors.ground-deep}"
    textColor: "{colors.ink}"
  search-field:
    backgroundColor: "{colors.pane}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "0 1rem"
    height: "3rem"
  filter-chip:
    backgroundColor: "{colors.pane}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    padding: "0 0.75rem"
    height: "{spacing.target}"
  filter-chip-selected:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.pane}"
  pane:
    backgroundColor: "{colors.pane}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pane}"
  enclosure-sign:
    backgroundColor: "{colors.pane}"
    textColor: "{colors.ink}"
    padding: "0.75rem 1rem"
    height: "3.5rem"
  animal-plate:
    backgroundColor: "{colors.pane}"
    textColor: "{colors.ink}"
    padding: "0.75rem 0.75rem 0.75rem 1rem"
    height: "4.25rem"
  animal-plate-hover:
    backgroundColor: "{colors.pane-hover}"
  habitat-stake:
    textColor: "{colors.stake-ink}"
    rounded: "{rounded.sm}"
    size: "2rem"
  status-tile:
    rounded: "{rounded.md}"
    size: "2.25rem"
  danger-tab:
    textColor: "{colors.danger}"
    typography: "{typography.hazard}"
    rounded: "{rounded.sm}"
    padding: "0.1em 0.45em"
  tag-code:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.ink-2}"
    typography: "{typography.tag-code}"
    rounded: "{rounded.sm}"
    padding: "0.125rem 0.375rem"
  sheet-option:
    backgroundColor: "{colors.pane}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "0.75rem"
    height: "4rem"
  toast:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.pane}"
    rounded: "{rounded.md}"
    padding: "0.75rem 1rem"
---

# Design System: Zoo Management System

## Overview

**Creative North Star: "The Glasshouse Register"**

The zoo is kept as a conservatory ledger. Each enclosure is a bed under glass: one clear white pane on a cool, faintly green ground, framed by a single 1px glazing rule. Each animal is a staked label planted in that bed, a row inside the pane divided from its neighbours by the same hairline. The register is calm, cool and exact in daylight, built for a keeper reading a phone outdoors while walking from bed to bed.

Colour is scarce and therefore loud. One deep conservatory green carries action, selection and focus. Each habitat owns one full-strength hue that appears only as a solid stake tile. On the roster, a healthy animal carries no colour at all, so the animals that need attention are the only ones that do. Red means danger and nothing else. Hierarchy comes from scale contrast in a single hyperlegible family at three working weights, with a mono face reserved for tag codes.

At night the glasshouse goes dark rather than inverting: a neutral graphite ground (chroma near 0, cool hue 250), panes one step lighter, and green kept strictly as an accent (primary button, selection, focus, the selected-role check). Stakes and status hues brighten; white stake ink swaps for graphite ink. The world refuses the brown enamel signage it replaced (heavy brown, double routed rules, Overpass, saturated filled status panels) and the neutral-grey-plus-indigo SaaS admin.

**Key Characteristics:**
- Cool glass-white ground, white panes, 1px glazing rules drawn as box-shadow rings.
- One conservatory green for every action, selection and focus ring.
- One full-strength hue per habitat, shown only as a solid stake tile.
- Every status carries its own tint everywhere it appears (roster tile, detail pill, status track, status sheet): healthy green, observation amber, treatment clay, deceased stone.
- Red only for danger: an outlined DANGER tab on rows, a tinted band on the record.
- Atkinson Hyperlegible Next at 400/700/800; Atkinson Hyperlegible Mono only for tag codes.
- A 4px spacing rhythm, 44px minimum targets, tabular numerals for counts and dates.
- Motion is brief ease-out; the signature is the transfer that re-stakes the location label.

## Colors

A near-colourless glasshouse where hue is rationed to action (green), place (habitat stakes), clinical status (tints) and hazard (red).

### Primary
- **Conservatory Green** (brand): Primary buttons, the selected sheet option (2px inset ring and filled check mark), search focus ring, global focus outline, text selection, input caret, skip link, and the wordmark's glasshouse window. Hover deepens to **Potting-Shed Green** (brand-hover). **Glass Wash** (brand-tint) fills the wordmark's panes. **Pane Ink** (brand-ink) sets text on green.

### Secondary: habitat stakes
- **Moss** (terrestrial), **Glass Blue** (aquatic), **Verdigris** (amphibious): Full-strength fills for the habitat stake tile on enclosure headers, the large location sign, and destination options in the transfer sheet. Icon ink on top is **Stake White** (stake-ink) in light mode and graphite in dark mode.

### Tertiary: status and hazard
- **Leaf** (healthy, healthy-tint): Healthy text and tint. On roster rows, healthy uses neither; it gets an empty ringed tile in ink-3 and a regular-weight grey word. Where status is the subject (detail hero chip, status track lit cell, status sheet option), healthy shows its faint tint like every other status.
- **Amber** (observation, observation-tint), **Clay** (treatment, treatment-tint), **Stone** (deceased, deceased-tint): Status ink and soft tint pairs. Ink colours the icon and the bold status word; tint fills the status tile, the hero status chip and the lit track cell.
- **Signal Red** (danger, danger-tint): The outlined DANGER tab on plates and the tinted band across the top of the detail hero. Nothing else.

### Neutral
- **Glass Ground** (ground): Page background and the sheet body; also the fill of tag-code chips so they read as recessed.
- **Deep Ground** (ground-deep): Pressed rows, quiet-button hover, disabled primary buttons, the warning note, the deceased hero, and skeleton loaders.
- **Clear Pane** (pane) and **Pane Hover** (pane-hover): Every container surface, the top bar, the sheet footer; hover on rows, options and secondary buttons.
- **Glazing Rule** (rule) and **Strong Glazing** (rule-strong): Translucent green-black hairlines. Rule rings panes, divides rows and fact cells, and underlines the top bar; strong outlines inputs, secondary buttons and hovered chips.
- **Greenhouse Ink** (ink), **Ink 2** (ink-2), **Ink 3** (ink-3): Primary text; secondary text (species, meta, labels, dt); tertiary marks (chevrons, arrows, idle track icons, healthy tile icon). Ink doubles as the fill of the selected filter chip, the grave button and the toast.
- **Scrim** (scrim): Backdrop behind sheets.

### Named Rules
**The Status Colour Rule.** Every status shows its own tint on its tile, pill and track cell, healthy included, so colour always tells the animal's state. On roster rows the healthy status word stays grey regular weight, so exception words (700, in their status ink) still stand out.

**The Red Means Danger Rule.** Danger red appears only for dangerous animals. Errors are ink-outlined, not red; the irreversible deceased action is ink (grave), not red.

**The One Stake Rule.** A habitat hue appears only as a solid stake tile carrying its habitat icon. It never tints a pane, colours text, or becomes a border.

**The One Green Rule.** Conservatory green means "act here" or "this is selected". It is never decoration on content.

## Typography

**Display Font:** Atkinson Hyperlegible Next Variable (with Atkinson Hyperlegible Next, system-ui)
**Body Font:** Atkinson Hyperlegible Next Variable
**Label/Mono Font:** Atkinson Hyperlegible Mono 500/700 (with ui-monospace)

**Character:** A single legibility-first family does all the talking, and hierarchy comes from size jumps rather than a zoo of weights. The mono appears only where a keeper matches a physical tag.

### Hierarchy
- **Display** (800, 2.75rem mobile / 4rem from 48rem, line-height 1, -0.035em): The animal's name on the detail hero. Wraps anywhere rather than overflowing.
- **Headline** (800, 2.25rem mobile / 2.75rem from 48rem, 1.1, -0.025em): The page title ("Animals") above its count summary.
- **Title** (800, 1.375rem, 1.1, -0.015em): Section headings (Status, Location, Record), sheet titles, empty and error notices, the large location sign.
- **Name** (700, 1.1875rem, 1.2, -0.01em): Animal names on roster plates, single line with ellipsis. Enclosure names in pane headers step down to 700 at 1.0625rem.
- **Body** (400, 1.0625rem, 1.45): Default text, buttons (at 700), search input, ledes capped at 60ch.
- **Body small** (400, 0.9375rem, 1.3): Plate meta (status word and species), filter chips, sheet descriptions, notes.
- **Label** (400, 0.8125rem): Fact-cell terms, chip counts, the role label, track labels, the footer.
- **Hazard** (700, 0.6875rem, 0.07em, uppercase): The DANGER tab only.
- **Tag code** (Mono 500, 0.8125rem, tabular): Tag codes in their recessed chip; on the detail hero the code sits at 700 beside an uppercase "TAG" field label.

### Named Rules
**The Mono Is For Tags Rule.** Atkinson Hyperlegible Mono sets tag codes (and inline `code`) only. Counts and dates stay in the body face with tabular numerals.

**The Tabular Count Rule.** Every count, date and code uses tabular figures so columns of numbers line up.

**The Scale Over Weight Rule.** Working weights are 400, 700 and 800 (500 only for chips and tag codes). Build hierarchy with size first.

## Layout

A single-column register on mobile that becomes a flowing bed-by-bed wall on desktop. Spacing runs on a 4px rhythm (0.25, 0.5, 0.75, 1, 1.5, 2, 3rem). The page gutter is 1rem, widening to 2rem from 48rem. Every interactive control is at least 44px (target); primary and sheet buttons are 52px tall; plates are 68px; sheet options 64px.

- **Roster:** max 84rem. Headline and summary, then search (full width, max 28rem beside the filters from 64rem) and status filter chips that scroll horizontally on mobile, bleeding into the gutter, and wrap from 64rem. Enclosure panes stack with 1rem gaps; from 64rem they flow down CSS columns (24rem columns, 1.5rem gap, never split across columns) so short beds leave no holes. On mobile the enclosure header sticks to the top while its animals scroll under it.
- **Detail:** max 72rem. Quiet back link, hero pane (name and species on the left; status chip and tag on the right from 48rem, bottom-aligned), then Status and Location side by side from 60rem, with Record spanning both. Record facts are a hairline grid: 1 column, 2 from 40rem, 3 from 60rem.
- **Sheets:** bottom sheet on mobile (max 88dvh or 44rem, safe-area padding); centred dialog up to 34rem from 48rem. Head, scrolling body, pinned footer with full-width actions.
- **Toast:** fixed above the bottom edge, max 30rem, centred.

### Named Rules
**The Bed Rule.** An enclosure is one pane. Its animals are rows inside it, divided by glazing rules, never separate cards.

## Elevation & Depth

Almost flat. Depth comes from a white pane on a tinted ground plus a 1px ring, with a nearly invisible contact shadow. Real lift is reserved for things that float over the page: sheets and the toast. Lines are drawn with box-shadow rings (outer for panes, inset for controls) rather than borders, so they never shift layout.

### Shadow Vocabulary
- **Glazing ring** (`box-shadow: 0 0 0 1px var(--rule), var(--shadow-pane)`; shadow-pane is `0 1px 2px oklch(0.26 0.03 170 / 0.05)`): Every pane, the status track, the large location sign.
- **Control ring** (`box-shadow: inset 0 0 0 1px var(--rule-strong)`): Search, secondary buttons, sheet options on hover; 2px brand inset on focus or selection.
- **Hairline divider** (`box-shadow: 0 -1px 0 var(--rule)` between rows; `0 1px 0 var(--rule)` under the top bar and sticky headers).
- **Lift** (`box-shadow: 0 12px 32px -12px oklch(0.2 0.03 170 / 0.35)`): Sheets and toast only.

### Named Rules
**The Glass Not Paper Rule.** Panes do not float. If something casts a real shadow, it is a sheet or a toast.

## Shapes

Softly rounded glass. Three radii: 6px (sm) for small inline marks (tag chips, DANGER tab, list stakes, focus outline corners); 10px (md) for controls (buttons, inputs, chips, status tiles, sheet options, toast, large stakes); 16px (pane) for panes, the status track and skeletons. Sheets use 20px, top corners only on mobile. Round shapes are limited to the status dot, filter-chip dots and the option check mark. The wordmark is a glasshouse window drawn in CSS: arched top, two mullions, one transom, in brand green.

## Components

### Buttons
Solid and quiet, never outlined in colour.
- **Shape:** gently rounded (md), 52px tall, 1.5rem horizontal padding, 700 weight, 20px icon with 0.5rem gap.
- **Primary:** conservatory green with pane ink; hover deepens; disabled drops to deep ground with ink-3. Full width inside sections and sheet footers.
- **Secondary:** clear pane with a strong glazing ring; hover to pane-hover.
- **Grave:** ink fill with pane text, used only to confirm an irreversible record (deceased).
- **Quiet:** transparent, ink-2, 44px; hover fills deep ground. Back link, close, theme toggle.
- **Press:** every button nudges down 1px on active. Transitions are 140ms ease-out.

### Chips
- **Style:** pane with glazing ring, 44px, body-small at 500, optional status dot and a count in label size.
- **State:** hover strengthens the ring; selected (`aria-pressed`) inverts to ink fill with pane text at 700, ring removed, count at 72% opacity.

### Cards / Containers
- **Corner Style:** pane radius (16px).
- **Background:** clear pane; deep ground for the deceased hero.
- **Shadow Strategy:** glazing ring (see Elevation & Depth).
- **Internal Padding:** 1.5rem on mobile hero, 2rem from 48rem; 0.75rem/1rem for rows and fact cells.

### Inputs / Fields
- **Style:** search field is a pane with strong inset ring, 48px, leading search icon in ink-2.
- **Focus:** ring becomes 2px conservatory green; icon darkens to ink. Global focus is a 2px green outline at 3px offset.
- **Error:** form errors are an ink-outlined (1.5px) pane with an alert icon, not red.

### Navigation
- **Role select:** built on Angular Aria (Combobox + Listbox, CDK connected overlay, inline popover). Borderless trigger (700 label, chevron that turns on open, deep-ground hover and open state). The menu is a lifted pane (radius pane, 1px rule, shadow-lift) anchored to the trigger's right edge, 17rem min width; each option is 52px with the role name (700) and what the role does (xs, ink-2); active option gets deep-ground fill plus a strong-rule ring, the selected one a brand check. Keyboard: arrows, Enter/Space commit, Escape closes; outside click closes. Never a native `<select>`.
- **Top bar:** clear pane with a hairline beneath, 56px. Left: glasshouse-window wordmark, "ZMS" under 40rem and the full name above. Right: "Viewing as" label with the role select (see Role select) and a quiet sun/moon theme toggle. A skip link in brand green appears on focus.

### Enclosure pane with plates (signature)
The bed. A header row (habitat stake, enclosure name, tabular count, arrow) sits on the pane above a list of plates. Each plate is a four-part row: a 36px status tile (tinted for exceptions, empty ringed for healthy on the roster), the name with an optional DANGER tab, a meta line with the short status word (bold in status ink for exceptions, grey regular for healthy) and species, then the mono tag code in a recessed chip and a chevron that slides 2px on hover. Deceased rows grey the name and word.

### Location sign (large)
The same header in its own pane at 72px, with a 44px stake and a title-size name. It carries the view-transition name for the transfer: on move, the old sign slides out along its arrow (stake-out, 260ms) and the new one slides in (stake-in, 340ms, 90ms delay), each clipped to the pane radius.

### Status track
Four equal cells in clinical order inside one pane, divided by rules. The current cell is lit with its status tint, icon and bold label in status ink, and a single 7px dot in the corner. Idle cells are ink-3.

### Danger tab and danger band
The tab is a small uppercase hazard label outlined 1.5px in danger red with a warning icon, placed after the animal's name. On the detail hero, danger becomes a full-bleed tinted band across the top of the pane with a hairline in 30% danger.

### Sheet options
Radio rows styled as 64px panes: tile or stake, label and description, and a round check mark. Hover strengthens the ring; checked gets a 2px green ring and a filled green mark; the current value is disabled, transparent and loses its mark.

## Do's and Don'ts

### Do:
- **Do** draw every edge with a 1px glazing rule (box-shadow ring) on a white pane over the glass ground.
- **Do** give every status tile its tint, healthy green included; keep the healthy word grey on roster rows so exception words still lead.
- **Do** show habitat only as a solid full-strength stake tile with its icon.
- **Do** use conservatory green for the one primary action in a section, for selection and for focus.
- **Do** set tag codes in Atkinson Hyperlegible Mono in a recessed ground chip, and counts and dates with tabular numerals.
- **Do** keep controls at least 44px and primary actions at 52px, full width on mobile.
- **Do** pair every status colour with its icon and its word.
- **Do** keep motion to 140 to 220ms ease-out and replace view-transition movement with a 150ms crossfade under reduced motion.

### Don't:
- **Don't** use red for anything but a dangerous animal; errors are ink-outlined, the deceased confirmation is ink.
- **Don't** tint panes, text or borders with habitat hues.
- **Don't** split an enclosure into separate animal cards; rows live inside one pane.
- **Don't** cast real shadows from panes; lift is for sheets and the toast.
- **Don't** set counts, dates or prose in the mono face.
- **Don't** bring back heavy brown, double routed rules, Overpass, or saturated filled status panels.
- **Don't** drift into grey-plus-indigo admin styling; the neutrals carry a faint green and the only accent is conservatory green.

### Named Rule: Graphite Night

Dark mode grounds, panes, rules and inks are neutral graphite (chroma at most 0.008, hue 250). Green never tints a surface in dark mode; it appears only where it acts.
