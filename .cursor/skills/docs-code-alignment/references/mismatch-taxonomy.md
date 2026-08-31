# Mismatch Taxonomy

Use these stable categories when naming, comparing and merging findings in `docs/PodLoggerJunitDemoDocsCodeAllighment.md`.

The goal is not to force every finding into one rigid bucket, but to keep wording repeatable across runs so the skill can tell whether a finding is truly new or just phrased differently.

## Primary categories

### `missing canon`

A file or canon role is expected by the docs system but is absent from the working tree or missing entirely.

Typical cases:

- required module map missing;
- snapshot file missing when the skill expects an existing baseline.

### `broken link`

A canon link points to a path that no longer resolves correctly.

Typical cases:

- rename not propagated;
- doc references stale path under old directory layout.

### `duplicated authority`

Two documents act like competing normative sources for the same fact.

Typical cases:

- module doc restates project-level invariants differently from the PRD;
- commands doc starts prescribing acceptance.

### `stale operational example`

An example, shortcut or runbook snippet still exists but no longer accurately reflects the current code or current usage.

Typical cases:

- README example no longer matches a concrete IT usage and is not labeled as example;
- old command path shown as current.

### `wrong test inventory`

The documented test set, counts, names, display-name levels, or expected results no longer match the source tests.

Typical cases:

- method-level display name described as class-level;
- wrong parameterized-case count;
- designed failure documented as a defect.

### `incomplete API coverage`

The docs omit a real API field, parameter, attribute, dependency or transfer-relevant part of the implementation.

Typical cases:

- missing annotation attributes;
- incomplete package map;
- missing config/dependency note for reuse.

### `session artifact presented as canon`

A one-off observation, machine-specific path, or session diary appears as if it were stable canon.

Typical cases:

- historic command transcript inside Commands commons;
- Allure results from one run documented as hard rule;
- local absolute path described as universally required canon.

### `target-state presented as as-built`

A future plan, strategy or draft is treated as current implementation truth.

Typical cases:

- target-state story read as as-built contract;
- prompt history used as authority.

### `snapshot drift`

The snapshot itself no longer correctly represents the known state or fails to preserve continuity.

Typical cases:

- unchanged finding duplicated under a new ID;
- fixed finding removed instead of closed;
- changed finding not updated.

### `new artifact not classified`

A new file or code artifact appeared but has no clear place in the documentation system.

Typical cases:

- new MD file exists with unclear role;
- new test class exists but is absent from canon;
- helper material is not marked as reference/non-canonical.

## Choosing a category

Use the first category that best explains the root problem:

1. Is the file/path absent or unresolved? Use `missing canon` or `broken link`.
2. Is the wrongness caused by duplicate authority? Use `duplicated authority`.
3. Is the issue an outdated example or runbook snippet? Use `stale operational example`.
4. Is the issue about test names/counts/outcomes? Use `wrong test inventory`.
5. Is the issue about missing API/package/dependency coverage? Use `incomplete API coverage`.
6. Is the issue merely a session-specific observation dressed up as canon? Use `session artifact presented as canon`.
7. Is a future plan or draft being treated as current implementation? Use `target-state presented as as-built`.
8. Is the snapshot history itself mishandled? Use `snapshot drift`.
9. Did something new appear without a role? Use `new artifact not classified`.

## Merging rules

When comparing a newly observed issue to the existing snapshot:

1. Treat two findings as the same finding if they share the same root problem, the same affected artifact and the same criterion, even if the wording differs.
2. Prefer updating the existing finding over creating a new one.
3. Create a new finding only when at least one of these changed materially:
   - affected artifact;
   - criterion;
   - root problem category;
   - actual code/document discrepancy.
4. If the problem disappeared, keep the historical record and mark it `closed`.

## Mapping to criteria

- `missing canon`, `broken link` -> usually `C11`
- `duplicated authority` -> often `C10`
- `stale operational example` -> often `C3`, `C5`, `C12`
- `wrong test inventory` -> usually `C1`, `C2`
- `incomplete API coverage` -> usually `C3`, `C4`, `C8`
- `session artifact presented as canon` -> usually `C12`, `C13`
- `target-state presented as as-built` -> usually `C14`
- `snapshot drift` -> usually `C15`
- `new artifact not classified` -> usually `C16`

## Known local mappings

- mixed README `@PodLogger` API/example layers without clear labeling -> `stale operational example` plus `incomplete API coverage`
- `OpenshiftClientParseTest` display-name level mismatch -> `wrong test inventory`
- `junit-pod-logger.md` missing transfer-relevant structure -> `incomplete API coverage`
- session diary inside Commands -> `session artifact presented as canon`
- target-state Event docs competing with as-built -> `target-state presented as as-built`
- stale references to removed `docs/README.md` -> `broken link`
