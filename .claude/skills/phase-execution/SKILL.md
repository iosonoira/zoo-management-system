---
name: phase-execution
description: >
  Plan a phase of zoo-management-system in plan mode, then execute it with
  subagents routed by role and model to save tokens: Haiku for mechanical
  tasks, Sonnet for delicate framework APIs and docs, Opus only for tasks that
  meet explicit escalation criteria, and the orchestrator (whatever model this
  session runs on) for review and all commits. Use when Matteo asks to plan
  and/or execute a phase, a feature branch or a multi-task change
  ("pianifichiamo la fase", "esegui il piano", "modalità ibrida", "scegli tu i
  modelli", /phase-execution). Not for one-file fixes or questions.
---

# Phase execution: plan, route by role, review, commit

The orchestrator (this session) plans, reviews every task, reruns tests and
makes every commit. Subagents write code. This is the schema used in phases
9, 10 and 11, generalised so the orchestrator can be Opus or Sonnet.

**Never use `fable`** for the session or for a subagent: it costs extra
credits.

## 0. Check the orchestrator model

State in one line which model this session runs on.

- **Opus**: proceed.
- **Sonnet** (or anything else): suggest once, without insisting, switching
  to Opus, or to `opusplan` (Opus in plan mode, Sonnet while executing) from
  the model picker. If Matteo keeps Sonnet, proceed and apply the Sonnet
  column below. Do not ask again in the same session.

## 1. Plan (plan mode)

Enter plan mode (`EnterPlanMode`) if not already in it. Nothing is written
until Matteo approves.

- **Read the code before planning.** The code is the only source of truth,
  even for small details (phase 9 planned an `AnimalStatus.SICK` that does
  not exist). Read `docs/STATE.md`, `docs/decisions.md`, the relevant
  `CLAUDE.md` and the precedent classes the new code will copy.
- The plan contains: scope, domain/contract, infrastructure, **model per
  task** table, critical files (new, modified, **precedents to copy** with
  paths), verification, what Matteo must do by hand (`.env`, Keycloak
  recreate, push).
- Model table columns: `# | Task | Model | Risk | Why`. The "Why" cites a
  rule below or a past lesson, not a guess. **Risk** is `high` when the task
  meets an escalation criterion (section 2), otherwise `normal`.
- **Group contiguous tasks for the same agent** so it loads context once, and
  mark them "resumed from task N" (resumed with `SendMessage`).
- Fix **exact signatures** (records, ports, method names) in the plan, so
  later tasks agree with earlier ones.
- A new entry in `docs/decisions.md` is a task for the orchestrator: the
  rationale comes from Matteo. Ask him; if he gives none, write
  `Rationale: not recorded`.
- Include the estimated cost: Haiku ~50–110k tokens per task, Sonnet
  ~100–230k. Opus subagents cost more per token: list them separately.

## 2. Routing by role

| Role | Orchestrator = Opus | Orchestrator = Sonnet |
|---|---|---|
| Mechanical: code that copies an existing pattern with fixed signatures (domain model, ports, services modelled on a sibling, Mockito tests, compose/realm/CI blocks) | Haiku subagent | Haiku subagent |
| Delicate framework APIs (module scaffold, `mvnw` `100755`, JPA/Flyway/converters, REST + MapStruct + Bean Validation, SmallRye Kafka/DLQ/outbox, Angular Signal Forms) and **all documentation** | Sonnet subagent | Sonnet subagent (keeps the session's context clean) |
| Plan, review, test reruns, commits, decision entries, fixes too small to justify a subagent | orchestrator | orchestrator |
| Tasks meeting an escalation criterion | orchestrator, or Opus subagent if large enough to flood the session's context | **Opus subagent** |
| Review of a `high` risk task | orchestrator | **Opus subagent, read-only**, then the orchestrator |

Why: Haiku is reliable when it has the pattern in front of it (phase 9
tasks needed no fixes), but invented facts in phase 10 docs ("fase 8", "6
endpoint") and regressed `’` into `\'`. The subtlest Sonnet errors (401/403,
`EntityManager`) were caught by an Opus review.

### Escalation criteria (Opus)

Escalate a task to Opus only if at least one holds:

- concurrency, transactions, locking (like the advisory lock + `SELECT FOR
  UPDATE` fix, `0cec3ae`);
- security and authorisation (roles matrix, OIDC, exception mappers for
  401/403);
- design changes spanning more than one module;
- the task failed review **twice** with the model it was routed to;
- a subagent's diagnosis contradicts code already in the repo and the
  orchestrator cannot settle it by reading that code.

When escalating, **say so to Matteo in one line with the criterion**, before
launching the agent.

Never plan the "strong model writes full code in the plan, Haiku pastes it"
variant: the code gets written twice on the most expensive tokens, and full
code plans in this project still contained errors.

## 3. Execute

- **Sequential, same working tree.** Never two implementers in parallel: they
  share the git index (phase 8, a `git add` landed in the other agent's
  commit). Parallel only with `isolation: "worktree"`. Read-only reviewers
  may run in parallel.
- Launch with `Agent` and `model: "haiku" | "sonnet" | "opus"`. Resume the
  same agent for the next contiguous task with `SendMessage`.
- **Subagents never commit.** The brief says so explicitly.
- Brief template:
  1. Goal of the task, and what is out of scope.
  2. Precedent files to read and copy (exact paths), and the rule that the
     precedent wins over the agent's own idea.
  3. Exact signatures to produce or consume.
  4. Constraints: the relevant `CLAUDE.md` rules, do not touch other files,
     do not commit, do not start Docker stacks or services.
  5. Verification command (`.\mvnw.cmd verify` in the module, or the FE
     test/build command) and the request to paste **the real output lines**
     (tests run/failures), not a summary.
  6. Report: files changed, deviations from the brief and why, doubts.

## 4. Review after every task

For a `high` risk task with a non-Opus orchestrator, first launch a
read-only Opus reviewer on the task's diff (brief: the plan's task, the
precedent files, the checklist below; it reports findings, it edits
nothing). Then the orchestrator reviews as usual and decides.

Read the diff, do not trust the report. Check:

- **Deviations from the precedent pattern** (phase 10: Sonnet split an adapter
  and extended Panache instead of the `EntityManager` precedent).
- **Plausible explanations** ("it is impossible because Quarkus...") against
  the existing repo code, which often already has the answer (phase 11: Sonnet
  removed 401/403 JSON assertions; the fix already existed in animal-service,
  `f446251`).
- **Weakened tests**: removed or loosened assertions, skipped tests.
- **Stubs or scaffolding** created just to compile: never committed.
- **Unrelated edits** and regressions in untouched strings or files.
- **Docs**: every sentence must be backed by a class, file or commit;
  remove editorial phrasing and invented facts.

A failed review sends the task back to the same agent with `SendMessage`.
The second failure is an escalation criterion.

Then rerun the tests yourself (at least after the tasks the plan marks, and
always at the end), and commit.

## 5. Commit

- One commit per task, conventional style (`feat(feeding): ...`,
  `fix(...)`, `docs: ...`), with an explicit pathspec.
- **No `Co-Authored-By` line.**
- `git commit -- <pathspec>` does not record a prior
  `git update-index --chmod=+x`: for a new `mvnw`, commit without pathspec
  after staging, and check with `git ls-files -s` that it is `100755`.
- Before stating that something is done, check it on disk (`git status`,
  `git log`), not from the conversation.

## 6. End of phase

1. `verify` on every module whose parent POM or shared file changed.
2. `/code-review` on the branch range before the merge; fix the findings,
   routing each fix with the rules above.
3. Merge `--no-ff` into `main`. **Never push**: Matteo pushes.
4. Update `docs/STATE.md` if the plan says so, and the `project_state`
   memory.
5. Propose (do not do on your own) saving the session note and ADR through
   `claude-obsidian:save`, with a model table of what each task used, which
   tasks were escalated and why, and what review corrected.
6. Runtime E2E with `/verify` only if Matteo asks.
7. List what Matteo must do by hand for live mode (new `.env` variables,
   Keycloak recreate).
