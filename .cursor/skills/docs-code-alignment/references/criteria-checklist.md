# Criteria Checklist

Use this checklist when running docs-code alignment. Apply every criterion on every run.

## `C1` Test inventory fidelity

Inputs:

- `docs/PodLoggerJunitDemoTest.md`
- all relevant `*Test.java`, `*IT.java`, harness classes

Check:

- every current test class is represented;
- documented methods and display names map to the correct level;
- Docker/K3s requirements are correct;
- expected Maven result is correct.

Violation examples:

- missing test class card;
- removed class still documented;
- method-level display name described as class-level;
- wrong designed-failure expectation.

## `C2` Test counts and expected outcomes

Inputs:

- `docs/PodLoggerJunitDemoTest.md`
- source tests

Check:

- per-class counts;
- total library/demo counts;
- `FAILED as designed` vs `PASSED` semantics.

Violation examples:

- wrong count of parameterized cases;
- demo failure documented as defect instead of designed behavior.

## `C3` Public API coverage for `@PodLogger`

Inputs:

- `docs/PodLoggerJunitDemoPRD.md`
- `README.md`
- `junit-pod-logger/junit-pod-logger.md`
- `junit-pod-logger/src/main/java/com/example/podlogger/PodLogger.java`
- related runtime classes when semantics depend on implementation

Check:

- all real annotation attributes are represented in canon;
- defaults and meaning are correct;
- API example vs concrete IT usage is distinguished.

Violation examples:

- missing `standDownEventCodes()` or `standDownMessagePatterns()`;
- README example presented as exact `OrderErrorIT` annotation;
- invented attribute that does not exist in code.

## `C4` Library transfer map completeness

Inputs:

- `junit-pod-logger/junit-pod-logger.md`
- `junit-pod-logger/src/main/**`
- `junit-pod-logger/pom.xml`

Check:

- package map covers transfer-relevant layers;
- dependency list does not hide required pieces;
- config and DTO layers are not omitted if they affect transfer.

Violation examples:

- undocumented `store.dto`;
- undocumented config class;
- omitted dependency that matters for reuse.

## `C5` SUT contract fidelity

Inputs:

- `demo-app/demo-app.md`
- `README.md`
- `docs/PodLoggerJunitDemoTest.md`
- `demo-app` source and Dockerfile

Check:

- endpoints, HTTP status, JSON body, error codes and messages;
- jar name and Dockerfile assumptions.

Violation examples:

- wrong endpoint path;
- wrong error message;
- wrong jar filename.

## `C6` Test environment fidelity

Inputs:

- `demo-tests/demo-test.md`
- `k8s/k8s.md`
- `docs/PodLoggerJunitDemoTest.md`
- `ClusterLifecycle.java`
- infra tests
- both `demo-api.yaml` copies

Check:

- K3s image version;
- image tag;
- selectors;
- probes;
- both YAML copies still aligned.

Violation examples:

- K3s version drift;
- manifest copy drift;
- wrong readiness/liveness expectations.

## `C7` Runtime log and Events invariants

Inputs:

- `CollectGate.java`
- `PodLoggerService.java`
- as-built story docs
- `README.md`
- `docs/PodLoggerJunitDemoTest.md`

Check:

- log gate logic;
- fail-path ordering;
- empty Events attachment prohibition;
- persist-only-if-available behavior;
- separation of log collection and Event collection rules.

Violation examples:

- doc says Events on passed tests;
- doc says persist on unavailable pod;
- doc merges Events into the log gate incorrectly.

## `C8` Version fidelity

Inputs:

- root `pom.xml`
- module POMs
- docs that mention versions

Check:

- Fabric8 version;
- Allure Maven plugin version;
- any other version that changes described behavior.

Violation examples:

- docs mention stale dependency version;
- plugin version drift not reflected in commands or README.

## `C9` Jenkins and CI fidelity

Inputs:

- `Jenkinsfile`
- `README.md`

Check:

- stages;
- Docker expectation;
- Allure integration;
- `UNSTABLE` handling.

Violation examples:

- docs say pipeline runs one module but Jenkins runs another;
- docs hide `UNSTABLE` designed behavior.

## `C10` Document role symmetry

Inputs:

- `docs/PodLoggerJunitDemoPRD.md`
- all canon MD files

Check:

- each file stays within its documented role;
- no duplicate authorities emerge.

Violation examples:

- module map becoming a second PRD;
- commands file holding test acceptance;
- snapshot becoming the contract source.

## `C11` Link and file liveness

Inputs:

- canon files and their links
- working tree
- git-aware checks when needed

Check:

- canon files referenced by docs are present and reachable;
- paths are current;
- working-tree absence vs git-history presence is understood correctly.

Violation examples:

- stale reference to removed `docs/README.md`;
- stale path after rename;
- file exists only historically.

## `C12` Commands commons hygiene

Inputs:

- `docs/PodLoggerJunitDemoCommands.md`
- `docs/PodLoggerJunitDemoTest.md`
- current docs structure

Check:

- commands are grouped by scope;
- acceptance criteria stayed in Test.md;
- session diary material does not masquerade as canon.

Violation examples:

- one-off command log embedded as normative content;
- stale `docs/prd/...` path shown as current;
- absolute machine-specific path treated as canonical when not needed.

## `C13` Contract vs observation separation

Inputs:

- snapshot and test catalog claims
- source tests
- operational evidence only when clearly marked as such

Check:

- session observations remain observations;
- only asserted behavior is documented as hard requirement.

Violation examples:

- Allure artifact pattern from one run documented as guaranteed;
- incidental ordering documented as invariant.

## `C14` As-built vs target-state separation

Inputs:

- as-built story docs
- target-state docs
- draft history

Check:

- as-built docs remain primary;
- target-state docs are clearly labeled;
- drafts are not treated as canon.

Violation examples:

- future plan presented as current implementation;
- prompt-history file used as authoritative source.

## `C15` Snapshot continuity

Inputs:

- existing `docs/PodLoggerJunitDemoDocsCodeAllighment.md`
- newly observed findings

Check:

- unchanged open findings are not duplicated;
- closed findings keep status history;
- changed findings are updated, not re-added.

Violation examples:

- same mismatch added twice under different wording;
- fixed issue silently removed;
- open issue still true but rewritten as a new ID.

## `C16` New artifact classification

Inputs:

- current repo inventory
- canon set

Check:

- new classes, tests, stories, module docs or helpers are classified as canon, reference, snapshot or non-canonical context.

Violation examples:

- new MD file exists with no role;
- new test class added but not reflected anywhere;
- helper doc accidentally treated as charter.

## Output discipline

For every run, decide per observation:

- `confirmed alignment`
- `open finding`
- `closed finding`
- `non-canonical context note`

Write only the delta to `docs/PodLoggerJunitDemoDocsCodeAllighment.md`.
