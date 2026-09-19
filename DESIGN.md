---
name: Zoo Management System
description: The zoo's own sign system, rendered as routed enamel plates for keepers, vets and admins.
colors:
  ground: "oklch(0.955 0 0)"
  ground-deep: "oklch(0.915 0.003 60)"
  plate: "oklch(0.995 0 0)"
  sign: "oklch(0.31 0.048 52)"
  sign-raised: "oklch(0.38 0.052 52)"
  sign-ink: "oklch(0.99 0.004 80)"
  sign-ink-2: "oklch(0.86 0.02 70)"
  ink: "oklch(0.22 0.018 52)"
  ink-2: "oklch(0.43 0.016 52)"
  ink-3: "oklch(0.62 0.01 52)"
  rule: "oklch(0.84 0.006 52)"
  healthy: "oklch(0.5 0.12 152)"
  healthy-ink: "oklch(0.99 0 0)"
  observation: "oklch(0.86 0.165 92)"
  observation-ink: "oklch(0.24 0.03 80)"
  treatment: "oklch(0.7 0.17 52)"
  treatment-ink: "oklch(0.2 0.03 45)"
  deceased: "oklch(0.52 0.004 52)"
  deceased-ink: "oklch(0.99 0 0)"
  danger: "oklch(0.52 0.2 27)"
  danger-ink: "oklch(0.99 0 0)"
typography:
  display:
    fontFamily: "'Overpass Variable', 'Overpass', system-ui, sans-serif"
    fontSize: "2.625rem"
    fontWeight: 900
    lineHeight: 1.1
    letterSpacing: "-0.015em"
  headline:
    fontFamily: "'Overpass Variable', 'Overpass', system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 850
    lineHeight: 1.1
    letterSpacing: "-0.01em"
  title:
    fontFamily: "'Overpass Variable', 'Overpass', system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 800
    lineHeight: 1.15
  body:
    fontFamily: "'Overpass Variable', 'Overpass', system-ui, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.45
  body-sm:
    fontFamily: "'Overpass Variable', 'Overpass', system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.3
  label:
    fontFamily: "'Overpass Variable', 'Overpass', system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 700
    lineHeight: 1.1
  label-caps:
    fontFamily: "'Overpass Variable', 'Overpass', system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 800
    lineHeight: 1.1
    letterSpacing: "0.06em"
  code:
    fontFamily: "'Overpass Variable', 'Overpass', system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 800
    letterSpacing: "0.02em"
    fontFeature: "'tnum' 1"
rounded:
  tab: "0.3rem"
  panel: "0.5rem"
  plate: "0.75rem"
  sheet: "1rem"
  pill: "999px"
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
    backgroundColor: "{colors.sign}"
    textColor: "{colors.sign-ink}"
    rounded: "{rounded.panel}"
    padding: "0.25rem 1.5rem 0"
    height: "3.25rem"
    typography: "{typography.body}"
  button-primary-hover:
    backgroundColor: "{colors.sign-raised}"
    textColor: "{colors.sign-ink}"
  button-primary-disabled:
    backgroundColor: "{colors.ground-deep}"
    textColor: "{colors.ink-3}"
  button-secondary:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
    padding: "0.25rem 1.5rem 0"
    height: "3.25rem"
  button-secondary-hover:
    backgroundColor: "{colors.ground}"
  button-grave:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.plate}"
    rounded: "{rounded.panel}"
    padding: "0.25rem 1.5rem 0"
    height: "3.25rem"
  button-quiet:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
    padding: "0.25rem 0.75rem 0"
    height: "{spacing.target}"
  button-quiet-hover:
    backgroundColor: "{colors.ground-deep}"
  chip-filter:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "0.2rem 0.75rem 0 0.5rem"
    height: "{spacing.target}"
    typography: "{typography.body-sm}"
  chip-filter-pressed:
    backgroundColor: "{colors.sign}"
    textColor: "{colors.sign-ink}"
  search-field:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
    padding: "0 1rem"
    height: "3.25rem"
  sign-plate:
    backgroundColor: "{colors.sign}"
    textColor: "{colors.sign-ink}"
    rounded: "{rounded.plate}"
  white-plate:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.plate}"
  enclosure-sign:
    backgroundColor: "{colors.sign}"
    textColor: "{colors.sign-ink}"
    rounded: "{rounded.plate}"
    padding: "0.5rem 1rem 0.5rem 0.5rem"
    height: "3.5rem"
    typography: "{typography.title}"
  animal-plate:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.plate}"
    padding: "0.75rem 1rem 0.75rem 0.75rem"
    height: "4.75rem"
  animal-plate-deceased:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.ink-2}"
  status-panel-healthy:
    backgroundColor: "{colors.healthy}"
    textColor: "{colors.healthy-ink}"
    rounded: "{rounded.panel}"
    size: "3.25rem"
  status-panel-observation:
    backgroundColor: "{colors.observation}"
    textColor: "{colors.observation-ink}"
    rounded: "{rounded.panel}"
    size: "3.25rem"
  status-panel-treatment:
    backgroundColor: "{colors.treatment}"
    textColor: "{colors.treatment-ink}"
    rounded: "{rounded.panel}"
    size: "3.25rem"
  status-panel-deceased:
    backgroundColor: "{colors.deceased}"
    textColor: "{colors.deceased-ink}"
    rounded: "{rounded.panel}"
    size: "3.25rem"
  danger-panel:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.danger-ink}"
    rounded: "{rounded.tab}"
    padding: "0.2em 0.5em 0.1em"
    typography: "{typography.label-caps}"
  tag-code:
    backgroundColor: "{colors.ground-deep}"
    textColor: "{colors.ink}"
    rounded: "{rounded.tab}"
    padding: "0.2rem 0.4rem 0.05rem"
    typography: "{typography.code}"
  option:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.plate}"
    padding: "0.75rem"
    height: "4rem"
  sheet:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sheet}"
  note:
    backgroundColor: "{colors.ground-deep}"
    textColor: "{colors.ink-2}"
    rounded: "{rounded.panel}"
    padding: "0.5rem 0.75rem"
    height: "3.25rem"
  form-error:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
    padding: "0.75rem"
  toast:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.plate}"
    rounded: "{rounded.panel}"
    padding: "0.75rem 1rem"
  top-bar:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.sign-ink}"
    height: "3.5rem"
---

# Design System: Zoo Management System

## Overview

**Creative North Star: "Enclosure Signage"**

The interface is the zoo's own wayfinding system. Every surface is a routed enamel plate: deep sign brown carrying white Overpass lettering (the Highway Gothic lineage of park and road signs), or a white plate framed by a thin brown routed rule set in from its edge. Animals hang under the enclosure sign where they live, each one its own plate: status symbol panel left, name, status word and species, short tag code right. A keeper reads it the way they read the signs on the path, in under a second, in daylight.

Status speaks the safety-sign colour law, not a dashboard palette. Green is healthy, yellow is observation, orange is treatment, neutral grey is deceased, and signal red exists for one thing only: the DANGER panel. Colour never stands alone; every status panel carries an authored pictogram and every status is also written out in words. The ground is a true neutral grey at chroma 0, so the brown plates and the status colours are the only warmth on screen.

Density is controlled and tactile: 44px minimum targets, 52px primary controls, plates at least 76px tall. Depth is physical and shallow: plates sit on the ground with a short soft shadow, sheets rise from the bottom edge. Motion is short and ease-out, and has one signature: on transfer the location sign is taken down and re-hung, sliding out along its arrow and the new one sliding in.

**Key Characteristics:**
- Two plate materials only: sign brown with white lettering, white plate with a brown routed rule.
- One variable face, Overpass, from 400 to 900; heavy weights carry hierarchy.
- Status as safety signage: colour plus pictogram plus word, always.
- Red is reserved for danger; errors and warnings are ink, not red.
- Chroma-0 neutral ground; warmth lives in the brown plates, never in a cream background.
- Authored one-stroke pictograms on a 24px grid.
- Mobile-first stack; desktop flows enclosure groups into columns and turns the detail into a two-column sign.

## Colors

A chroma-0 grey ground, a single deep sign brown with its white lettering, and a strict five-colour safety law for status and danger.

### Primary
- **Sign Brown** (sign): the enamel of every direction sign, the list and detail header plates, the primary button, the pressed filter chip, the selected option ring and mark, and the text caret. It is the brand; nothing else on screen is brown at this depth.
- **Raised Sign Brown** (sign-raised): hover state of the primary button only.
- **Sign White** (sign-ink): lettering and pictograms on brown, the pictogram tile on enclosure signs, the detail tag chip.
- **Routed Rule Cream** (sign-ink-2): the routed inset line on brown plates and secondary text on brown (species, counts, "Viewing as").

### Status law (safety-sign colours)
- **Healthy Green** (healthy on healthy-ink): check pictogram, white glyph.
- **Observation Yellow** (observation on observation-ink): eye pictogram, dark glyph. Also the text-selection colour and the skip link, where it acts as the highlighter.
- **Treatment Orange** (treatment on treatment-ink): cross pictogram, dark glyph.
- **Retired Grey** (deceased on deceased-ink): ribbon pictogram, white glyph. Grey, not black and not red: death is stated plainly and quietly.
- **Signal Red** (danger on danger-ink): the DANGER panel on animal plates and the danger band on the detail hero. Nothing else.

### Neutral
- **Neutral Ground** (ground): page background, sheet background, deceased plate fill, secondary-button hover. Chroma 0.
- **Deep Ground** (ground-deep): notes, tag-code chips, filter count badges, quiet-button hover, skeleton base, disabled primary buttons.
- **White Plate** (plate): animal plates, record plate, search field, options, sheet footer, secondary button.
- **Sign Ink** (ink): body text, the top bar, toasts, the grave button, the focus ring, the form-error ring.
- **Secondary Ink** (ink-2): meta text, species, labels, lede copy, footer.
- **Tertiary Ink** (ink-3): hover rings, unselected radio marks, disabled pictogram tiles, scrollbar thumb. Not for body text.
- **Rule Grey** (rule): hairline outlines of white plates, chips, options and unlit status cells; the gap colour between record cells.

### Named Rules
**The Red Is Danger Rule.** Signal red appears only on the DANGER panel and the dangerous-animal band. Form errors are an ink-ringed white panel with an alert pictogram; warnings are deep-ground notes with a lock. If red shows up anywhere else, it is a bug.

**The Colour Law Rule.** Each status owns exactly one colour, one pictogram and one word: healthy/green/check, observation/yellow/eye, treatment/orange/cross, deceased/grey/ribbon. Colour is never the only carrier; a status panel is always paired with its label on the same plate or in the same row.

**The Chroma-Zero Ground Rule.** The page ground is true neutral grey. Warmth comes from the brown plates, never from tinting the background toward cream or beige.

## Typography

**Display Font:** Overpass Variable (with Overpass, system-ui, sans-serif)
**Body Font:** Overpass Variable, same family
**Label/Mono Font:** Overpass with tabular numerals for tag codes, counts and dates

**Character:** One signage face used the way park signs use it: very heavy for names you read from a distance, plain and open for everything you read up close. There is no second family; hierarchy comes from weight (400 to 900) and a fixed rem scale at a ~1.2 ratio.

### Hierarchy
- **Display** (900, 2.625rem, line-height 1.1, -0.015em): page titles on brown header plates ("Animals") and the animal name on the detail hero, which grows to 4rem at -0.025em from 48rem up.
- **Headline** (850, 1.5rem, 1.1): section headings (Status, Location, Record) and sheet titles; 2rem at 850 for full-page not-found and error plates.
- **Title** (800, 1.25rem, 1.15): animal name on its plate (single line, ellipsis), enclosure name on its sign (1.5rem on the large location sign), hero status word.
- **Body** (400, 1.0625rem, 1.45): running copy, buttons (at 750), inputs, header summary (at 600). Ledes cap at 60ch, the footer at 70ch.
- **Body small** (400 to 700, 0.9375rem, 1.3): plate meta (status word at 700 in ink, species in ink-2), chips, notes, option descriptions, sheet subtitles.
- **Label** (700, 0.8125rem): record field labels, status-track cell labels, "Viewing as", footer.
- **Label caps** (800, 0.8125rem, 0.06em, uppercase): the DANGER panel and the detail TAG chip only, the two places the physical signage world uses capitals.

### Named Rules
**The Optical Baseline Rule.** Overpass sits high in its box, so every filled control nudges its label down: buttons pad 0.25rem top and 0 bottom, chips 0.2rem top, small tabs 0.2rem top and 0.05rem bottom, the search input 0.2rem top. New controls with text in a filled shape must carry the same correction.

**The Tabular Code Rule.** Tag codes, counts and dates use tabular numerals with 0.02em tracking, so columns of codes line up down the plate stack.

## Layout

Mobile-first single column inside a page gutter of 1rem, widening to 2rem from 48rem. The list page caps at 84rem, the detail page at 72rem, both centred. Vertical rhythm is a 4px-based scale (0.25, 0.5, 0.75, 1, 1.5, 2, 3rem); plates in a stack sit 0.5rem apart, enclosure groups 2rem apart, page blocks 1rem apart.

- **Touch targets:** 2.75rem minimum for every tappable thing; primary buttons, the search field and notes are 3.25rem.
- **List:** header plate, then tools (search and a horizontally scrolling filter row that bleeds to the screen edge on mobile). Each enclosure sign is sticky at the top while its plates scroll beneath it, with a ground-coloured fade behind it. From 64rem the tools sit on one row (search 20 to 28rem wide) and enclosure groups flow into auto-fill columns of at least 24rem; signs stop being sticky.
- **Detail:** hero plate full width; from 48rem the hero splits into identity left and status/tag right, aligned to the bottom. From 60rem Status and Location sit side by side and the Record plate spans both. Record facts go 1, 2 then 3 columns at 40rem and 60rem.
- **Sheets:** full-width bottom sheets on mobile (max 88dvh or 44rem), centred 34rem dialogs from 48rem, footer padded for the safe area.
- **Breakpoints:** 40rem, 48rem, 60rem, 64rem.
- **Layers:** sticky signs 10, header 20, toast and skip link 40.

## Elevation & Depth

Hybrid and physical. Plates are objects mounted on the ground: they carry a short, soft, brown-tinted shadow, never a hard offset. Most depth comes from material contrast (brown against grey, white against grey) and from the routed inset line, which is drawn with inset box-shadows so it follows the plate's corners exactly. Only sheets and toasts get a lifted shadow, because only they float above the page.

### Shadow Vocabulary
- **Brown plate mount** (`0 1px 2px oklch(0.2 0.02 52 / 0.18), 0 4px 12px -4px oklch(0.2 0.02 52 / 0.2)`): every sign plate.
- **White plate mount** (`0 0 0 1px var(--rule), 0 1px 2px oklch(0.2 0.02 52 / 0.1)`): hairline edge plus contact shadow for white plates.
- **Sheet lift** (`0 -8px 32px -8px oklch(0.2 0.02 52 / 0.35)`, backdrop `oklch(0.2 0.02 52 / 0.5)`): dialogs rising from the bottom.
- **Toast lift** (`0 8px 24px -8px oklch(0.2 0.02 52 / 0.5)`): the confirmation toast.

### Named Rules
**The Routed Plate Rule.** A plate's rule is routed, not drawn on: a fill-coloured inset band of 0.3125rem, then a 1.5px line inside it. Brown plates route in Routed Rule Cream; white plates route in Sign Brown (grey ink-3 when deceased). Anything nested inside a routed white plate insets by 0.3125rem + 1.5px and reduces its radius by the same amount.

**The Brown-Tinted Shadow Rule.** Every shadow and scrim is tinted at hue 52 (the sign brown), never neutral black.

## Shapes

Softly rounded rectangles, like cut enamel plates. Plates, options and skeletons use 0.75rem; buttons, fields, status panels, notes and the danger band use 0.5rem; small tabs on plates (tag codes, DANGER panel, the ZMS wordmark) use 0.3rem; sheets use 1rem (top corners only on mobile). Pills (999px) are reserved for filter chips, their count badges, filter status dots, radio marks and the count ring on enclosure signs. Outlines are inset box-shadows rather than borders (1.5px rule at rest, 2px ink-3 on hover, 3px sign brown when selected or focused within) so they never shift layout. Unlit status-track cells carry a fine -45deg hatch, like an unlit panel on a physical sign.

## Components

### Buttons
Heavy, square-shouldered sign buttons.
- **Shape:** gently rounded (0.5rem), 3.25rem tall, label at 750 weight with a 1.25rem pictogram and 0.5rem gap.
- **Primary:** Sign Brown with Sign White lettering; hover lifts to Raised Sign Brown; press drops 1px; disabled goes Deep Ground with ink-3 text and a not-allowed cursor.
- **Secondary:** White plate with a 2px inset Sign Brown rule; hover fills Neutral Ground. Used for recovery actions such as "Clear search and filters".
- **Grave:** Sign Ink fill with white lettering, for irreversible acts (recording a death). Not red: death is not danger.
- **Quiet:** transparent, 2.75rem tall, Deep Ground on hover; back links and sheet close buttons.
- **Focus:** 3px ink outline, 3px offset, on every focusable element.
- **Labels:** verb plus object with the consequence named ("Move to Big Cat Ridge", "Record Bruno as deceased").

### Chips
- **Style:** white pill, 1.5px rule inset, 0.9375rem at 700, a status dot (1.625rem pill status panel) and a tabular count badge on Deep Ground.
- **State:** hover rings in ink-3; pressed (aria-pressed) fills Sign Brown with white text and a translucent white count badge.

### Cards / Containers
- **Corner Style:** 0.75rem plates.
- **Background:** Sign Brown for signs and header plates; White Plate for content; Neutral Ground for deceased.
- **Shadow Strategy:** see the plate mounts in Elevation & Depth.
- **Border:** routed inset rule, never a CSS border.
- **Internal Padding:** 1.5rem on header plates (2rem on the desktop hero); 0.75 to 1rem on list plates.

### Inputs / Fields
- **Style:** the search field is a 3.25rem white panel (0.5rem radius) with a 2px ink-3 inset ring, search pictogram, ink-2 placeholder.
- **Focus:** the ring thickens to 3px Sign Brown and the text goes to full ink; the caret is Sign Brown.
- **Options (radio rows in sheets):** 4rem white plates with a 1.5px rule; hover 2px ink-3; checked 3px Sign Brown ring and a filled brown round mark with a white check; keyboard focus draws the 3px ink outline around the row; disabled (the current value) drops to Neutral Ground with no ring.
- **Error:** a white panel with a 2px ink ring, alert pictogram and 700-weight message, announced with role alert. Never red.

### Navigation
- **Top bar:** 3.5rem, Sign Ink (near-black brown), white lettering. Left: the ZMS wordmark tab (white 0.3rem tab, 900 weight, 0.08em tracking) with the full name from 40rem. Right: "Viewing as" label and the demo role select on a darker brown panel with an authored chevron.
- **Back link:** quiet button with arrow-left pictogram.
- **Skip link:** Observation Yellow tab that drops in on focus.

### Enclosure Sign (signature)
The brown direction sign that heads each group: habitat pictogram on a white 2.5rem tile (0.4rem radius), enclosure name at title weight, a tabular count in a cream-ringed pill, and a right arrow. A large variant (5rem tall, 3.25rem tile, 1.5rem name) is the "you are here" location sign on the detail page.

### Animal Plate (signature)
A routed white plate linking to the animal: 3.25rem status panel left, name (title) with an optional DANGER panel, then status word in ink and species in ink-2, and on the right the tag code on a Deep Ground tab and a chevron. Hover tints the plate very slightly warm; press scales to 0.99. Deceased plates drop to Neutral Ground with a grey routed rule and ink-2 name.

### Status Track
Four equal cells in clinical order, each with pictogram and short label. The current cell is lit in its status colour with heavier stroke and label weight; the others are hatched and ringed in rule grey.

### Sheets, Notes, Toast
- **Sheet:** native dialog with a head (headline and ink-2 subtitle, quiet close), scrolling body of options, and a white footer with a full-width action. Enters by rising 1.5rem and fading over 220ms.
- **Note:** Deep Ground panel with a lock pictogram explaining why an action is absent for this role or state. Warning notes use full ink text.
- **Toast:** ink panel at the bottom, check pictogram, rises 0.75rem and fades in over 220ms; announced politely.
- **Skeleton:** plate-radius blocks shaped like the real sign and plates, with a slow 1.4s linear shimmer on Deep Ground.

### Pictograms
Authored set on a 24px grid, 2px round stroke by default (2.2 to 3 when small or on a filled panel), sized through a 1.5rem default. Every icon is decorative (aria-hidden) with a text label beside it.

### Motion
Ease-out cubic-bezier(0.22, 1, 0.36, 1), 150ms for state changes, 220ms for entrances. Signature: on transfer the location sign is re-hung through a view transition: the old sign slides 40% right and clips away along its arrow over 260ms, the new one slides in from the left over 320ms after 90ms. The animal name morphs between plate and hero. Under reduced motion all animations collapse to 1ms and view transitions become a 150ms crossfade.

## Do's and Don'ts

### Do:
- **Do** build every surface from the two plate materials: Sign Brown with Sign White lettering, or White Plate with a routed brown rule (0.3125rem inset, 1.5px line).
- **Do** pair every status colour with its pictogram and its word: green/check/Healthy, yellow/eye/Under observation, orange/cross/In treatment, grey/ribbon/Deceased.
- **Do** keep every tappable target at least 2.75rem and primary controls at 3.25rem.
- **Do** draw outlines as inset box-shadows (1.5px rest, 2px hover, 3px selected) and focus as a 3px ink outline with 3px offset.
- **Do** explain an absent action with a Deep Ground note and a lock pictogram instead of hiding it silently or letting it fail.
- **Do** tint shadows and scrims with the sign brown hue (52).
- **Do** use tabular numerals for tag codes, counts and dates.
- **Do** carry the Overpass optical-baseline nudge (extra top padding) on any filled control with text.

### Don't:
- **Don't** use signal red for anything but the DANGER panel and the dangerous-animal band; errors are ink-ringed, the irreversible action is ink.
- **Don't** tint the ground toward cream or beige; it stays chroma 0.
- **Don't** convey status or danger by colour alone.
- **Don't** introduce a second typeface or a light display weight; hierarchy is Overpass weight.
- **Don't** use hard offset shadows or neutral black shadows.
- **Don't** fall back to an admin table with status pills and a dark sidebar; animals are plates under their enclosure sign.
- **Don't** use stock icon sets; draw new pictograms on the same 24px, 2px round-stroke grid.
- **Don't** use purple gradients, glassmorphism, gradient text or cartoon mascots.
