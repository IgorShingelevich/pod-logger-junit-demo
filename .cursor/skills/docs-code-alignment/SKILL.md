---
name: docs-code-alignment
description: Validate the correlation between code and the project's canonical documentation set, detect documentation drift, compare newly found mismatches with the existing docs-code snapshot, and update the single snapshot document only when the status changes. Use when the user asks to align docs and code, after implementing a feature, after adding tests or module docs, or when new findings must be merged into the existing alignment history.
---

# Docs Code Alignment

Use this skill to compare the current codebase with the project's canonical documentation set and keep one cumulative snapshot of matches and mismatches in `docs/PodLoggerJunitDemoDocsCodeAllighment.md`.

This skill is for **state assessment**, not for auto-fixing docs or code.

## What this skill updates

Update only:

- `docs/PodLoggerJunitDemoDocsCodeAllighment.md`

Read as inputs:

- `docs/story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md`
- `docs/PodLoggerJunitDemoPRD.md`
- `docs/PodLoggerJunitDemoTest.md`
- `docs/PodLoggerJunitDemoCommands.md`
- `README.md`
- `demo-app/demo-app.md`
- `demo-tests/demo-test.md`
- `junit-pod-logger/junit-pod-logger.md`
- `k8s/k8s.md`
- `docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md`
- `docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`
- `docs/story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`
- `docs/story/OpenShiftEventHandlingStory/EventHandling2Story.md`

Also inspect the relevant code, tests, YAML, POM and Jenkins files needed to validate the current claims.

After reading this file, also read:

- `references/canon-set.md`
- `references/criteria-checklist.md`
- `references/mismatch-taxonomy.md`
- `templates/snapshot-section.md`

## When to use

Use this skill when any of the following is true:

- the user asks to align documentation and code;
- the user asks whether docs are synchronized with code;
- a feature has just been implemented and docs may have drifted;
- a new module document, story, test class, public API or operational command was added;
- the user wants a fresh assessment of correlation between docs and code;
- an existing mismatch may have been fixed and the snapshot must reflect the new status.

## Required behavior

1. Read the existing snapshot before making any judgment.
2. Re-run the same comparison process every time.
3. Distinguish between:
   - as-built contract;
   - operational summary;
   - historical observation;
   - target-state/reference material;
   - non-canonical draft material.
4. Add newly found mismatches to the existing single snapshot document instead of replacing history.
5. If a previously open mismatch is still present, keep it and do not rewrite it gratuitously.
6. If a previously open mismatch is fixed, mark it `closed` and record what changed.
7. If nothing changed, do not edit the snapshot.

## Canonical document roles

Treat these roles as normative:

- `docs/PodLoggerJunitDemoPRD.md`: project charter, modules, layers, invariants.
- `docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md`: as-built store feature contract.
- `docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`: as-built Events, health and fail-fast contract.
- `docs/PodLoggerJunitDemoTest.md`: global test catalog, acceptance, verification, known issues.
- `docs/PodLoggerJunitDemoCommands.md`: commands by scope only; not acceptance criteria.
- `README.md`: operational entrypoint, launch flow, concise examples.
- `demo-app/demo-app.md`, `demo-tests/demo-test.md`, `junit-pod-logger/junit-pod-logger.md`, `k8s/k8s.md`: module-local maps, not second PRDs.
- `docs/PodLoggerJunitDemoDocsCodeAllighment.md`: cumulative alignment snapshot, not behavior charter.

Treat these as **not as-built canon**:

- `docs/story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`
- `docs/story/OpenShiftEventHandlingStory/EventHandling2Story.md`
- `docs/propmtHistory/**`

These may be read as context, but must not outrank the as-built PRD/story set.

## Decision principles

Apply these principles in order:

### 1. Single source of truth

One fact should have one primary home. Repetition is allowed only as a summary or link, not as a competing normative source.

### 2. Non-contradiction

If two documents describe the same behavior, they must not prescribe different outcomes, counts, API fields, or responsibilities.

### 3. Completeness

All relevant current modules, tests, public APIs, manifests, and operational artifacts must either be covered by the canonical docs or explicitly treated as out of canon/reference material.

### 4. Repeatability

Given the same repo state, this skill should reach the same findings and should not churn the snapshot wording.

### 5. Delta-only writing

The snapshot is cumulative. Write only when there is a substantive delta.

## Comparison workflow

Follow this sequence every time.

### Step 1. Read the existing snapshot

Read `docs/PodLoggerJunitDemoDocsCodeAllighment.md` first.

Build an in-memory list of:

- existing open findings;
- existing closed findings;
- known confirmed alignments;
- explicit rules already recorded for future runs.

Do not delete historical findings merely because a shorter rewrite looks cleaner.

### Step 2. Build the current canon inventory

Enumerate the current canonical docs and verify which files:

- exist on disk;
- are referenced by other canon documents;
- are present in git history/HEAD when that distinction matters;
- are clearly marked as as-built vs target-state.

If a file is expected by canon but absent on disk, treat that as a possible `missing canon` or `broken link` finding.

### Step 3. Build the current code inventory

Inspect the implementation artifacts needed to validate the docs:

- root `pom.xml` and relevant module `pom.xml` files;
- `Jenkinsfile`;
- `demo-app` controllers, resources and Dockerfile;
- `demo-tests` cluster lifecycle, infra tests and ITs;
- `junit-pod-logger` public annotation, extension, services, parser, store and event classes;
- `k8s/demo-api.yaml` and the classpath copy in `demo-tests/src/test/resources/k8s/demo-api.yaml`;
- all relevant `*Test.java` and `*IT.java` files.

### Step 4. Compare using the fixed criteria

Use the criteria below on every run.

#### `C1` Test inventory fidelity

Compare all `*Test.java`, `*IT.java`, harness classes, nested tests and display names to `docs/PodLoggerJunitDemoTest.md`.

Flag a mismatch when:

- a class is undocumented;
- a documented class no longer exists;
- method-level and class-level display names are conflated;
- Docker/K3s requirements are wrong;
- expected Maven result is wrong.

#### `C2` Test counts and expected outcomes

Validate documented counts and pass/fail expectations against the source tests.

Flag a mismatch when counts, designed failures, or pass/fail semantics drift.

#### `C3` Public API coverage for `@PodLogger`

Compare `PodLogger.java` and related runtime behavior to PRD, README and module docs.

Flag a mismatch when:

- a real annotation attribute is missing from the canonical description;
- a document presents an example as if it were the actual annotation used by a concrete test;
- defaults or semantics are wrong;
- docs invent an API that code does not have.

#### `C4` Library transfer map completeness

Compare `junit-pod-logger` docs to the actual package and dependency surface.

Flag a mismatch when the module map hides material transfer-relevant structure such as DTO packages, config classes, store layers or required dependencies.

#### `C5` SUT contract fidelity

Compare `demo-app` behavior to docs:

- endpoints;
- HTTP status;
- response JSON;
- error codes and messages;
- Dockerfile and jar name.

#### `C6` Test environment fidelity

Compare cluster/test-infra docs to code and YAML:

- K3s image;
- image tag;
- selector;
- probes;
- YAML duplication between `k8s/` and `demo-tests/resources`.

#### `C7` Runtime log and Events invariants

Validate:

- `CollectGate`;
- fail-path sequencing;
- when Events are read;
- when Events are attached;
- when logs persist;
- empty Events attachment prohibition.

Do not confuse runtime log gating with Event collection rules.

#### `C8` Version fidelity

Compare key documented versions to POMs and plugin declarations.

At minimum validate:

- Fabric8 client;
- Allure Maven plugin;
- other versions whose drift would change documented behavior.

#### `C9` Jenkins and CI fidelity

Compare `Jenkinsfile` to operational docs.

Flag a mismatch when build stages, Docker expectations, Allure behavior or `UNSTABLE` semantics drift.

#### `C10` Document role symmetry

Validate the division of responsibility from the main PRD.

Flag a mismatch when:

- a module map becomes a second PRD;
- `Commands.md` becomes a second test catalog;
- the snapshot becomes a behavior charter;
- a story becomes a general project README.

#### `C11` Link and file liveness

Check whether canon links point to files that actually exist and whether an expected file exists on disk vs only in git history.

Flag a mismatch when:

- canon references a file missing from the working tree;
- a renamed file leaves stale references;
- a document path is kept alive only historically.

#### `C12` Commands commons hygiene

Ensure `docs/PodLoggerJunitDemoCommands.md` remains a scoped command reference.

Flag a mismatch when it accumulates:

- one-off session diaries;
- stale paths;
- environment-specific noise presented as canon;
- acceptance criteria that belong in the test catalog.

#### `C13` Contract vs observation separation

Distinguish hard contract from a result observed in one run.

Flag a mismatch when a session-specific observation is documented as if the code or tests guarantee it.

#### `C14` As-built vs target-state separation

Ensure future-state and prompt-history material is not treated as current implementation truth.

Flag a mismatch when target-state docs or drafts compete with current as-built docs.

#### `C15` Snapshot continuity

Compare current findings to the existing snapshot.

Flag a mismatch in the snapshot process itself when:

- an existing open finding is duplicated instead of updated;
- a closed finding disappears without explanation;
- a known issue changes status but the snapshot does not record that change.

#### `C16` New artifact classification

Inspect whether newly added classes, tests, stories, module docs or canon-adjacent files have been classified correctly.

Flag a mismatch when a new artifact has no clear role in the document system.

### Step 5. Classify each observation

Classify each result as one of:

- confirmed alignment;
- open finding;
- closed finding;
- non-canonical context note.

Use stable mismatch categories where helpful:

- `missing canon`
- `broken link`
- `duplicated authority`
- `stale operational example`
- `wrong test inventory`
- `incomplete API coverage`
- `session artifact presented as canon`
- `target-state presented as as-built`
- `snapshot drift`
- `new artifact not classified`

### Step 6. Compare against the existing snapshot

For every newly observed mismatch:

1. Search the current snapshot for an equivalent finding.
2. If the finding already exists and remains true, keep the existing item.
3. If the finding already exists but its condition changed, update that item rather than creating a duplicate.
4. If the finding is truly new, append it in the open findings section.

For every previously open finding:

1. Re-check whether it is still true.
2. If fixed, move it to the closed findings section or mark it `closed` in place according to the snapshot structure already in use.
3. Record what changed in one concise sentence.

### Step 7. Update the snapshot only if needed

Do not edit the snapshot if:

- no new findings appeared;
- no open finding changed status;
- no confirmed alignment meaningfully changed;
- no new artifact needs classification.

Edit the snapshot if at least one of those changed.

## Snapshot writing rules

When the snapshot needs an update:

1. Preserve the single-document model. All findings live in the same file.
2. Keep stable IDs for findings when possible.
3. Each finding must include:
   - criterion ID;
   - status;
   - document-side fact;
   - code-side fact;
   - why it matters.
4. Keep open and closed findings separate.
5. Keep confirmed alignments separate from mismatches.
6. Record enough detail for the next run to detect whether the state changed.

Do not:

- rewrite the whole snapshot for style only;
- drop older findings without status resolution;
- create a second output file;
- move rule logic out of this skill into the snapshot.

## Known project-specific cases

This repository already has known alignment cases that must be handled carefully:

1. `docs/README.md` may exist in git history/HEAD while being absent in the working tree.
2. The README `@PodLogger` block may be an API example, not the exact annotation used by `OrderErrorIT`.
3. `PodLogger.java` includes `standDownEventCodes()` and `standDownMessagePatterns()`, which may be missing from shorter docs.
4. `OpenshiftClientParseTest` has a method-level display name; do not assume it is class-level.
5. `junit-pod-logger.md` may omit transfer-relevant structure such as `store.dto`, config or dependency nuances.
6. `docs/PodLoggerJunitDemoTest.md` may contain session-specific Allure observations that are not asserted by code.
7. `docs/PodLoggerJunitDemoCommands.md` may contain stale historical commands or session diary content.
8. `EventHandlingStrategies.md` and `EventHandling2Story.md` are target-state/reference, not as-built canon.
9. `docs/propmtHistory/**` is non-canonical draft history.
10. New MD files must be classified before they are treated as canon.

## How to report in chat

When you use this skill, lead with the result:

- whether the snapshot changed or not;
- how many new findings were added;
- whether any findings were closed;
- whether any previously known findings remain open.

If you updated the snapshot, summarize only the delta. Do not dump the whole file unless asked.

## Companion files

Use these files as the first level of progressive disclosure:

- `references/canon-set.md`
- `references/criteria-checklist.md`
- `references/mismatch-taxonomy.md`
- `templates/snapshot-section.md`

Use the template file to preserve snapshot section order, finding fields and delta-only writing discipline.

## Example triggers

- "Align docs and code"
- "Run docs code alignment"
- "Check whether the PRD still matches the implementation"
- "We added a new test class; update the docs/code snapshot"
- "Re-check old mismatches and close the fixed ones"
