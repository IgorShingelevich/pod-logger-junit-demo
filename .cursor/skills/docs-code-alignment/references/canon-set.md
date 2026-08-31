# Canon Set

Use this file as the stable map of which documents belong to the docs-code alignment canon and what role each document plays.

## Canonical as-built documents

| File | Role | Must contain | Must not become |
| --- | --- | --- | --- |
| `docs/PodLoggerJunitDemoPRD.md` | Project charter | project purpose, modules, layers, invariants, document symmetry | feature-deep SQL details, command bodies, session findings |
| `docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md` | Store feature charter | SQLite/store contract | Events/health/fail-fast charter |
| `docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md` | Events feature charter | lifecycle Events, health, fail-fast, relevantEvents behavior | store schema or store query API |
| `docs/PodLoggerJunitDemoTest.md` | Global test catalog | test inventory, acceptance, verification, nuances, known issues | command bodies or operational command commons |
| `docs/PodLoggerJunitDemoCommands.md` | Commands commons | reusable commands grouped by scope | acceptance criteria, session diary, stale historical canon |
| `README.md` | Operational entrypoint | run flow, module overview, concise examples, Jenkins overview | full normative API surface or second PRD |
| `docs/README.md` | Docs index | navigation across docs canon | hidden or silently absent canon |
| `demo-app/demo-app.md` | Module map | SUT endpoints, logback, Docker image, jar expectations | second PRD or second test catalog |
| `demo-tests/demo-test.md` | Module map | infra classes, K3s/Testcontainers, why `fail()` exists | full feature charter |
| `junit-pod-logger/junit-pod-logger.md` | Module map | package map, transfer-relevant surface, key dependencies | operational demo runbook |
| `k8s/k8s.md` | Module map | manifest, probes, RBAC, YAML duplication contract | library internals |
| `docs/PodLoggerJunitDemoDocsCodeAllighment.md` | Cumulative snapshot | current alignments, open findings, closed findings | source-of-truth charter or second PRD |

## Context-only, not as-built canon

These files may be useful during analysis but must not outrank the as-built set:

- `docs/story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`
- `docs/story/OpenShiftEventHandlingStory/EventHandling2Story.md`
- `docs/propmtHistory/**`

## Canon invariants

1. One fact should have one primary normative home.
2. A module map is not a second PRD.
3. The test catalog is not a command manual.
4. The commands file is not an acceptance catalog.
5. The snapshot records status; it does not define the behavior charter.
6. Target-state/reference material must stay visibly separated from as-built canon.

## Known canon-sensitive cases

1. `docs/README.md` may matter even if it is missing from the working tree because other canon docs still reference it.
2. README examples may summarize API usage without mirroring a specific test class exactly.
3. A file can be historically present in git but absent on disk; treat that as a liveness/alignment case, not as a non-issue.
