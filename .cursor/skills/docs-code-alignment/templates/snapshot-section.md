# Snapshot Section Template

Use this template when `docs/PodLoggerJunitDemoDocsCodeAllighment.md` needs a real update.

Do not rewrite the whole snapshot if there is no substantive delta.

## Top-level section order

Keep this order unless the existing snapshot has already evolved for a strong reason:

1. Title
2. Status / source meta
3. Context of the snapshot
4. Canon at the time of the snapshot
5. Confirmed alignments
6. Open findings
7. Closed findings
8. Conclusion about the MD structure
9. Rules for future runs of the skill
10. Related documents

## Meta block

Use a short header section like this:

```markdown
# Docs Code Allighment Snapshot

**Статус:** baseline snapshot | updated snapshot  
**Источник:** ручная сверка | обновлено skill `docs-code-alignment`  
**Story фичи:** [`docs/story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md`](story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md)  
**Главный устав проекта:** [`docs/PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md)
```

Guidelines:

- switch to `updated snapshot` once the skill is the writer;
- keep the story and PRD links stable;
- do not add chat-only artifacts, canvas paths or temporary scratch files.

## Context section

Use short bullets for context:

```markdown
## 1. Контекст снимка

- Snapshot built against current working tree.
- If no delta is found on the next run, this file must not be rewritten.
- New findings are appended to the single snapshot document.
```

Guidelines:

- mention git context only when it matters to the state;
- mention whether the snapshot was manual or skill-produced if that changed.

## Canon section

Use two subsections:

1. canonical documents
2. non-as-built/context-only materials

Recommended canonical table shape:

```markdown
| Группа | Файл | Состояние |
| --- | --- | --- |
| Главный устав | [`docs/PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md) | есть |
```

Guidelines:

- if a file exists in git HEAD but is missing in the working tree, say so explicitly in the status cell;
- keep context-only files out of the canonical table.

## Confirmed alignments section

Use a compact table:

```markdown
| ID | Тема | Подтверждение |
| --- | --- | --- |
| `C7` | CollectGate | `CollectGate.shouldCollect = !collectOnFailOnly \|\| failed` совпадает с PRD и README |
```

Guidelines:

- only keep alignments that help future runs avoid re-reporting the same non-issues;
- prefer durable code/document facts, not one-off runtime observations.

## Open findings section

Each open finding should use this shape:

```markdown
### `F-00X` — `Cnn` — short category label

- **Статус:** `open`
- **Категория:** `missing canon` | `broken link` | `stale operational example` | ...
- **Документный факт:** ...
- **Фактический факт:** ...
- **Почему это важно:** ...
- **Что должен делать skill в будущем:** ...   # optional when it helps future runs
```

Rules:

- preserve existing finding IDs whenever the same underlying issue remains true;
- create a new finding only if the affected artifact, criterion or root problem materially changed;
- do not split one issue into multiple findings unless the root causes are actually different.

## Closed findings section

When an open finding disappears, do not delete it silently. Convert it to a closed record:

```markdown
### `F-00X` — `Cnn` — short category label

- **Статус:** `closed`
- **Было:** ...
- **Стало:** ...
- **Что изменилось:** ...
```

Rules:

- closed findings preserve continuity;
- use one concise sentence for what changed;
- do not reopen under a new ID unless the issue is materially different.

## Conclusion section

Use a short numbered list:

```markdown
## 6. Вывод по структуре MD-файлов

1. Дополнительные модульные MD-файлы не нужны.
2. Проблема в точности и границах документов, а не в нехватке файлов.
```

Guidelines:

- keep this section structural;
- answer whether new MD files are needed or not;
- distinguish missing coverage from misallocated coverage.

## Rules for future runs section

Use a short rule list:

```markdown
## 7. Правила для будущего skill

1. Unchanged open findings are not rewritten.
2. Fixed findings become `closed`.
3. New materially distinct issues are appended.
4. No delta means no edit.
```

This section should reflect the current snapshot contract, not restate the entire skill.

## Related documents section

Keep direct links to the main story and canon:

```markdown
## 8. Связанные документы

- [`docs/story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md`](story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md)
- [`docs/PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md)
- [`docs/PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md)
- [`docs/PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md)
- [`README.md`](../README.md)
```

## Delta-only checklist

Before editing the snapshot, confirm all of the following:

- there is at least one new finding, closed finding, changed alignment or new artifact classification;
- the same issue is not already present under a slightly different wording;
- the update preserves section order and stable IDs;
- the update does not introduce a second result file;
- the update does not copy large chunks of skill logic into the snapshot.

If any answer is no, do not edit the snapshot yet.
