# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Zoo staff working with live animals, plus the people who evaluate this project as a portfolio piece. Access is role-based (Keycloak realm roles), and each role works the same data for a different job:

- **Keepers (`zoo-keeper`)**: primary user. On the move between enclosures, phone in hand, often outdoors in bright light, sometimes gloved or with one hand busy. Needs to find an animal fast, see its status at a glance, and transfer it to another enclosure. Short, frequent sessions.
- **Veterinarians (`zoo-vet`)**: update an animal's clinical status. Need precision, certainty about what a status change will do, and visibility of who changed what.
- **Administrators (`zoo-admin`)**: register new animals and manage the full roster. More often at a desk, in longer sessions, working through forms and lists.
- **Reviewers / recruiters**: open the app cold to judge the quality of the work. Must grasp the domain and the craft within seconds, without a guided tour.

## Product Purpose

An internal zoo management system for the staff who care for the animals: one trusted, audited record of who each animal is, where it lives and how it is doing, so a keeper at an enclosure and a vet in the clinic see the same truth instead of relying on scattered notes and memory.

It is also a portfolio project, and the goal is both at once: it must work as a realistic tool a real team could use every day (roles, workflows, edge cases, error states), and its visual and interaction quality must stand out when a reviewer opens it.

Success: a keeper finds an animal and acts on it in a few taps on a phone; a vet changes a status with no doubt about the outcome; an admin registers an animal without friction; a reviewer reads it immediately as a considered product, not a template.

## Positioning

Not a generic admin panel over a CRUD table: the UI is shaped by the zoo's actual domain rules and roles. Status transitions are real rules (a deceased animal is terminal), permissions follow the job (keepers transfer, vets change clinical status, admins register), and every write carries its author. The interface makes those rules visible, backed by a strict hexagonal Quarkus backend.

## Operating Context

- **Keeper in the field**: mobile phone, outdoors, variable and often bright light, moving between enclosures, one-handed or gloved use, interruptions. This is the primary design scenario (mobile-first).
- **Vet in the clinic**: phone or desktop; changes are deliberate and must be traceable.
- **Admin at a desk**: desktop, longer sessions, data entry and roster review.
- **Reviewer**: desktop browser, first visit, no context, a few minutes of attention.
- Authentication through Keycloak (OIDC); the signed-in user's role decides which actions exist.

## Capabilities and Constraints

**Available today** (`zms-be/animal-service`, REST under `/animals`):

| Capability | Endpoint | Roles |
|---|---|---|
| List animals | `GET /animals` | admin, vet, keeper |
| Animal detail | `GET /animals/{id}` | admin, vet, keeper |
| Register animal | `POST /animals` | admin |
| Update clinical status | `PUT /animals/{id}/status` | vet, admin |
| Transfer to another enclosure | `PUT /animals/{id}/transfer` | keeper, admin |

**Domain terminology** (use these terms consistently in the UI):

- Animal: `name`, `species`, `dangerous` (boolean flag), `habitat`, `enclosureId`, `arrivalDate`, `status`, `createdBy`, `updatedBy`.
- Habitat: `TERRESTRIAL`, `AQUATIC`, `AMPHIBIOUS`.
- Status: `HEALTHY`, `UNDER_OBSERVATION`, `IN_TREATMENT`, `DECEASED`. `DECEASED` is terminal: no further status changes and no transfers. Transition to the same status is rejected.
- Errors: 400 invalid data, 403 forbidden for the role, 404 animal not found, 422 invalid status transition.

**Technical constraints**:

- Frontend: Angular 22 with SSR (`zms-fe/`), standalone components, signals, Signal Forms, each component split in `.ts` / `.html` / `.scss`; only official angular.dev documentation as source (binding rules in `zms-fe/CLAUDE.md`).
- No list filtering, search or pagination exists in the API yet; enclosures are bare UUIDs with no enclosure entity or name.

**Planned, not built** (do not show as working features or empty placeholders): `health-service` (clinical records), `feeding-service` (feeding plans), `notification-service` (Kafka-driven notifications). The navigation should leave room for them.

**Open decisions**: enclosure naming/representation, search and filtering, dashboard/home content.

## Brand Commitments

- **Name**: Zoo Management System (ZMS). No logo or brand assets exist yet.
- **Personality (confirmed)**: natural, warm, crafted. The domain of living animals and habitats gives the product its character; warmth is carried with restraint and precision, never decoration.
- **References (named by the user)**: Linear, for speed, clear states and controlled density; Stripe Dashboard, for readable tables, well-structured entity detail pages and sober status badges.
- **Anti-references (named by the user)**: kids' zoo aesthetic (cartoons, mascots, random saturated colors, playful rounded fonts); AI SaaS look (purple gradients, glassmorphism, gradient text, hero-metric cards, identical card grids); 2000s enterprise software (gray on gray, endless forms, unreadable tables).
- **Voice**: plain, direct, kind. Short concrete labels, verbs on actions ("Transfer", "Update status"). Illness, danger and death stated plainly and respectfully, never playfully.
- **Language**: English UI by default, prepared for Italian via Angular i18n.

## Evidence on Hand

- Real backend contract: `zms-be/animal-service` (REST resource, DTOs, domain rules, OpenAPI with bearer scheme).
- Keycloak dev realm with the three roles: `zms-be/infrastructure/keycloak/`.
- No real animal data, photos, enclosure maps, staff, testimonials or metrics exist. Demo data must be clearly fictional and plausible; do not fabricate usage statistics or claims.

## Product Principles

1. **Status at a glance.** An animal's health status, danger flag and location read in under a second, on a phone, outdoors.
2. **Built for the hand in the field.** Mobile-first: the keeper's flow (find, check, transfer) is designed first; desktop expands it for admins and vets.
3. **Consequences visible before they happen.** Domain rules and permissions are shown, not discovered through errors: what an action will do, what is not allowed and why, and who did what afterwards.
4. **Respect the living subject.** Animals are individuals with names and histories; sensitive states are handled with care.
5. **Real product, portfolio craft.** Every screen must survive both a real shift and a reviewer's scrutiny: complete states (loading, empty, error, forbidden), plausible data, nothing placeholder.

## Accessibility & Inclusion

- WCAG 2.2 AA minimum and all AXE checks passing (binding rule in `zms-fe/CLAUDE.md`).
- Outdoor phone use: contrast for status and primary text well above minimums; touch targets at least 44×44px, usable gloved or in a hurry.
- Status and danger never conveyed by color alone (always text and/or icon too).
- Full keyboard support and visible focus on desktop; correct focus management in dialogs and after actions.
- `prefers-reduced-motion` honored everywhere.
- Layouts tolerate longer Italian strings.
- Role-aware UI: actions unavailable to a role are hidden or explained, never left to fail with a raw 403.
