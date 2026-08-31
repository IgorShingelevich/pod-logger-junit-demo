# Story: Docs Code Allighment

**Статус:** planned.  
**Канонический документ фичи:** этот файл.  
**Снимок состояния:** [`docs/PodLoggerJunitDemoDocsCodeAllighment.md`](../../PodLoggerJunitDemoDocsCodeAllighment.md).  
**Главный устав проекта:** [`docs/PodLoggerJunitDemoPRD.md`](../../PodLoggerJunitDemoPRD.md).  
**Каталог тестов:** [`docs/PodLoggerJunitDemoTest.md`](../../PodLoggerJunitDemoTest.md).  
**Команды:** [`docs/PodLoggerJunitDemoCommands.md`](../../PodLoggerJunitDemoCommands.md).  

---

## 1. Цель

Нужен проектный skill, который после реализации новой функциональности, появления новых классов или изменения уставных документов выполняет одну и ту же повторяемую сверку:

1. строит инвентарь текущего кода и канонических MD-файлов;
2. проверяет, что факты кода и факты документации не противоречат друг другу;
3. проверяет, что распределение информации между MD-файлами остаётся единственным и непротиворечивым;
4. сравнивает новые результаты с уже существующим snapshot;
5. обновляет snapshot только тогда, когда найдено новое расхождение, изменился статус старого или закрылась старая проблема.

Этот story задаёт критерии сравнения и требования к будущему skill.  
Этот story **не** является snapshot и **не** заменяет правила самого skill.

---

## 2. Результат фичи

После реализации фичи в репозитории должен существовать проектный skill:

```text
.cursor/skills/docs-code-alignment/
  SKILL.md
  references/canon-set.md
  references/criteria-checklist.md
  references/mismatch-taxonomy.md
  templates/snapshot-section.md
```

Skill вызывается:

- явно, как project skill по имени `docs-code-alignment`;
- через slash-команду, если пользователь просит выровнять документацию с кодом;
- автоматически агентом после заметных изменений в коде или в уставных документах, если по описанию skill подходит к ситуации.

Skill читает уставные документы, code snapshot и кодовую базу, но пишет только в один snapshot-файл:

- [`docs/PodLoggerJunitDemoDocsCodeAllighment.md`](../../PodLoggerJunitDemoDocsCodeAllighment.md)

Отдельный `Docs Code Alignment Result` создавать не нужно.

---

## 3. Границы

### 3.1 В scope

- проверка консистентности уставных документов с фактическим кодом;
- проверка ссылок и ролей документов;
- проверка появления новых классов, тестов, модулей и новых MD-файлов;
- фиксация схождений и расхождений в snapshot;
- поддержание стабильной и бесстрастной процедуры сравнения.

### 3.2 Out of scope
Этот же скилл, после сопоставления вновь найденных артефактов с тем, что было найдено в docs.code_alignment.md, продолжает свою работу.

Его задача: описать весь появившийся код или описать все появившиеся нисхождения и расхождения. После того как они будут зафиксированы, после того как отработает метод сравнения внутри скилла, этот же скилл должен вызвать вопрос: выровнять ли код и документ.

Тогда этот же скилл продолжит проактивную самостоятельную работу по выявлению нисхождения между документами и кодом, укреплению корреляций и решению тех проблем, которые были зафиксированы в docs.code_alignment.md. Продолжение следует. 
-  исправление кода;
-  исправление всех документов без отдельной команды пользователя;
-Для персистентного слоя всех ошибок и нисхождений/расхождений между документами и кодом персистентный слой будет реализован только в рамках работы агента со скиллом и с документом. То есть у нас только один документ, в котором всегда только актуальная информация по соответствию кода и документов.
Не нужно составлять цепочку снэпшотов. Нужно обновлять информацию в одном большом документе или же вызывать этот же скилл с дополнительным указанием разобраться с какой-то из существующих в код alignment.md проблем.
Skill в этом случае нагружается всей контекстной информацией для анализа неконсистентности и для ее решения:
- если это именно неконсистентность на уровне документов/код, то есть если это ошибки на уровне кода, то это найти неконсистентность между кодом и документацией и решить ранее найденные неконсистентности;
- либо присовокупить вновь найденную Информацию в один большой сводный общмй для всех конфликтов  коков -кода в одном файле
---

## 4. Канон документов для сверки

Базовый канон проекта определяется уставом и текущей структурой репозитория.

### 4.1 Обязательные документы

| Группа | Файл | Роль |
| --- | --- | --- |
| Главный устав | [`docs/PodLoggerJunitDemoPRD.md`](../../PodLoggerJunitDemoPRD.md) | назначение проекта, модули, слои, инварианты |
| Устав фичи | [`docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md`](../PersistentLogStoreStory/PersistentLogStoreStory.md) | SQLite/store |
| Устав фичи | [`docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](../OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) | Events, health, fail-fast |
| Каталог тестов | [`docs/PodLoggerJunitDemoTest.md`](../../PodLoggerJunitDemoTest.md) | карточки тестов, критерии приёмки, проверки |
| Commons | [`docs/PodLoggerJunitDemoCommands.md`](../../PodLoggerJunitDemoCommands.md) | команды по скопам |
| Операции | [`README.md`](../../../README.md) | запуск, общее описание, Jenkins, сокращённые примеры |
| Оглавление docs | `docs/README.md` | карта канонического docs-набора |
| Карта модуля | [`demo-app/demo-app.md`](../../../demo-app/demo-app.md) | SUT-модуль |
| Карта модуля | [`demo-tests/demo-test.md`](../../../demo-tests/demo-test.md) | тестовый модуль |
| Карта модуля | [`junit-pod-logger/junit-pod-logger.md`](../../../junit-pod-logger/junit-pod-logger.md) | библиотека |
| Карта модуля | [`k8s/k8s.md`](../../../k8s/k8s.md) | манифест и RBAC |
| Snapshot | [`docs/PodLoggerJunitDemoDocsCodeAllighment.md`](../../PodLoggerJunitDemoDocsCodeAllighment.md) | текущее состояние выравнивания |

### 4.2 Не-as-built материалы

Эти файлы можно читать как контекст, но нельзя считать кодовым контрактом:

- [`docs/story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`](../OpenShiftEventHandlingStory/EventHandlingStrategies.md)
- [`docs/story/OpenShiftEventHandlingStory/EventHandling2Story.md`](../OpenShiftEventHandlingStory/EventHandling2Story.md)
- `docs/propmtHistory/**`

Skill обязан проверять, что они явно воспринимаются как target-state/reference, а не как источник истины по as-built.

---

## 5. Принципы приёмки skill

### 5.1 Единственность

Один факт должен иметь один главный канон. Повторное упоминание допускается только как краткая ссылка или сокращённый operational summary, но не как второй нормативный источник.

### 5.2 Непротиворечивость

Два документа не должны задавать разные ожидания для одного и того же поведения, класса, атрибута, артефакта или результата теста.

### 5.3 Полнота

Все актуальные модули, основные публичные классы, тестовые классы, артефакты запуска и канонические MD-файлы на момент вызова должны попасть в сравнение или быть явно помечены как вне канона.

### 5.4 Повторяемость

При одинаковом состоянии репозитория skill должен приходить к тем же выводам и не менять snapshot без содержательной дельты.

### 5.5 Бесстрастность

Skill должен разделять:

- кодовый контракт;
- operational summary;
- историческое наблюдение конкретной сессии;
- future/target-state материал;
- черновой материал.

---

## 6. Критерии сравнения

Ниже фиксируется обязательный набор критериев, которым должен следовать skill.  
Каждый критерий должен проверяться одинаково при каждом запуске.

| ID | Что проверяется | Как проверять | Что считается нарушением |
| --- | --- | --- | --- |
| `C1` | Инвентарь тестового Java-кода | все `*Test.java`, `*IT.java`, harness, nested, display names vs [`docs/PodLoggerJunitDemoTest.md`](../../PodLoggerJunitDemoTest.md) | тест, метод, display name, Docker-статус или expected Maven result не отражены или отражены неверно |
| `C2` | Счётчики тестов и expected outcomes | количество тестов/кейсов и expected PASSED/FAILED as designed | Test.md утверждает числа или outcome, которых нет в коде |
| `C3` | Публичный API `@PodLogger` | атрибуты аннотации, дефолты и смысл vs PRD/README/модульные MD | документ опускает значимый атрибут, трактует пример как фактическую аннотацию IT или описывает несуществующий API |
| `C4` | Карта пакетов и зависимостей библиотеки | `junit-pod-logger/src/main/**` и `pom.xml` vs [`junit-pod-logger.md`](../../../junit-pod-logger/junit-pod-logger.md) | модульная карта неполна настолько, что скрывает реальный переносимый слой |
| `C5` | Контракт SUT | `OrderController`, `Dockerfile`, jar-имя, HTTP и JSON vs [`demo-app/demo-app.md`](../../../demo-app/demo-app.md), README, Test.md | коды, message, jar, image, endpoint описаны неверно |
| `C6` | Тестовый стенд | `ClusterLifecycle`, `InfrastructureLoggingTest`, `k8s/demo-api.yaml`, classpath-копия YAML | K3s image, probes, selector, образ, копии YAML расходятся |
| `C7` | Runtime-инварианты логов и Events | `CollectGate`, `PodLoggerService`, event story, Test.md, README | документы путают gate логов, пустой Events-аттач, fail-ветку `afterEach`, persist-policy |
| `C8` | Версии зависимостей и плагинов | parent `pom.xml`, module POMs vs docs | документ утверждает неактуальную версию Fabric8, Allure или другой ключевой зависимости |
| `C9` | Jenkins/CI описание | `Jenkinsfile` vs README | pipeline stages, build/test behavior или UNSTABLE semantics описаны неверно |
| `C10` | Симметрия ролей документов | PRD §9 vs реальное содержание MD | модульный MD превращён во второй PRD, второй Test catalog или второй Commands |
| `C11` | Живость ссылок и файлов канона | наличие файлов на диске и в git HEAD, целостность ссылок | канонический файл удалён, переименован, потерян или на него ведут битые ссылки |
| `C12` | Чистота Commons/Commands | разделы-скопы vs сессионный журнал, абсолютные пути, мёртвые исторические команды | Commons засорён временными командами или ссылками на уже несуществующие пути как на канон |
| `C13` | Разделение контракта и наблюдения | договорённости документов vs конкретные артефакты последнего прогона | историческое наблюдение записано как обязательный контракт, хотя код его не ассертит |
| `C14` | Разделение as-built и target-state | target-state docs и `propmtHistory` | reference/черновики поданы как кодовый устав |
| `C15` | Сопоставление с предыдущим snapshot | сравнение новых находок со [`docs/PodLoggerJunitDemoDocsCodeAllighment.md`](../../PodLoggerJunitDemoDocsCodeAllighment.md) | skill дублирует старый open-пункт, удаляет историю статусов или не фиксирует изменение статуса |
| `C16` | Новые классы и новые MD | полный инвентарь репозитория на момент вызова | новый класс, модульный MD, story или тест появились, но не учтены в каноне или не помечены как вне канона |

---

## 7. Частные случаи, которые skill обязан знать

Эти кейсы уже были обнаружены ручной сверкой и должны стать частью встроенной базы знаний skill.

1. `docs/README.md` может существовать в git HEAD, но отсутствовать в рабочей копии; это отдельный alignment-case по `C11`, а не повод молча игнорировать файл.
2. Пример аннотации в README может быть полнее, чем фактическая аннотация `OrderErrorIT`; skill должен отличать пример API от конкретного использования по `C3`.
3. `PodLogger.java` содержит `standDownEventCodes()` и `standDownMessagePatterns()`; если документ их не отражает, это неполнота API по `C3`.
4. Display name теста может быть на методе, а не на классе; skill сравнивает реальное расположение аннотаций, а не только имена классов (`C1`).
5. `junit-pod-logger.md` может скрывать часть реальной структуры пакетов (`store.dto`, config, optional auto-config dependencies); это неполная карта переноса (`C4`).
6. Таблица результатов Allure/SQLite по `OrderErrorIT` может быть историческим наблюдением прошлого прогона, а не контрактом кода (`C13`).
7. `PodLoggerJunitDemoCommands.md` может содержать сессионный журнал и команды по уже неактуальным путям (`docs/prd/...`); это нарушение чистоты commons (`C12`).
8. `EventHandlingStrategies.md` и `EventHandling2Story.md` должны остаться target-state/reference и не конкурировать с as-built story (`C14`).
9. Файлы в `docs/propmtHistory/` не являются каноном, даже если содержат полезные наброски (`C14`).
10. При появлении нового MD skill должен определить его роль: устав, карта модуля, story, reference, snapshot или неканонический файл (`C16`).

---

## 8. Требования к будущему skill

### 8.1 Формат skill

Основной файл:

- `.cursor/skills/docs-code-alignment/SKILL.md`

Обязательная структура:

```markdown
---
name: docs-code-alignment
description: Validate the correlation between code and the project's canonical documentation set, update the docs-code snapshot when the status changes, and use the same comparison criteria on every run. Use when documentation must be checked against the current codebase, after implementing a feature, after adding tests or module docs, or when the user asks to align docs and code.
---
```

`disable-model-invocation: true` добавлять нельзя, потому что skill должен быть доступен не только по явному имени, но и для самостоятельного вызова агентом по ситуации.

### 8.2 Поведение skill

Skill обязан:

1. прочитать snapshot;
2. прочитать канонический docs-набор;
3. собрать инвентарь релевантного кода и MD-файлов;
4. выполнить сравнение по `C1–C16`;
5. сопоставить новые результаты со snapshot;
6. обновить snapshot только при содержательной дельте;
7. оставить snapshot без изменений, если состояние не поменялось.

Skill не должен:

- автоматически создавать новый result-файл;
- затирать историю старых расхождений;
- чинить код и docs без отдельной команды;
- считать target-state материалы as-built контрактом.

### 8.3 Требования к дополнительному контексту skill

Skill обязан иметь отдельные reference/template-файлы, чтобы основное `SKILL.md` оставалось коротким и повторяемым.

#### `references/canon-set.md`

Содержит:

- перечень канонических документов;
- краткое описание роли каждого документа;
- правило, что snapshot не является уставом поведения, а фиксирует текущее состояние.

#### `references/criteria-checklist.md`

Содержит:

- развёрнутую форму `C1–C16`;
- входы проверки;
- ожидаемый формат наблюдений;
- признаки нарушений;
- что считать схождением, а что расхождением.

#### `references/mismatch-taxonomy.md`

Содержит стабильные категории расхождений:

- missing canon;
- broken link;
- duplicated authority;
- stale operational example;
- wrong class/test inventory;
- incomplete API coverage;
- session artifact presented as canon;
- target-state presented as as-built;
- snapshot drift;
- new artifact not classified.

#### `templates/snapshot-section.md`

Содержит шаблон записи snapshot:

- мета;
- канон на момент снимка;
- подтверждённые схождения;
- open findings;
- closed findings;
- вывод по нужности новых MD;
- правило, что unchanged state не переписывается заново.

---

## 9. Правила обновления snapshot

Skill должен использовать один и тот же алгоритм записи.

1. Если находка уже есть в snapshot и её статус не изменился, skill не дублирует и не переформулирует её.
2. Если находка была `open`, а сейчас устранена, skill меняет её на `closed` и кратко фиксирует, что изменилось.
3. Если появился новый фактор, skill добавляет новый пункт.
4. Если появился новый класс, новый MD или новая связка между кодом и документом, skill обязан их классифицировать.
5. Если не появилось ни одной содержательной дельты, skill не редактирует snapshot.

---

## 10. Критерии приёмки реализации skill

Skill считается реализованным, если:

1. он использует единые критерии `C1–C16` при каждом запуске;
2. по одному и тому же состоянию репозитория он даёт одинаковый результат;
3. он обновляет только [`docs/PodLoggerJunitDemoDocsCodeAllighment.md`](../../PodLoggerJunitDemoDocsCodeAllighment.md);
4. он не создаёт отдельный result-файл;
5. он различает as-built, target-state, historical observation и черновики;
6. он умеет фиксировать как схождения, так и расхождения;
7. он не пишет в snapshot ничего, если изменений состояния нет;
8. он учитывает частные случаи из раздела 7;
9. он остаётся работоспособным после появления новых классов и новых MD-файлов.

---

## 11. Связанные документы

- [`docs/PodLoggerJunitDemoPRD.md`](../../PodLoggerJunitDemoPRD.md)
- [`docs/PodLoggerJunitDemoTest.md`](../../PodLoggerJunitDemoTest.md)
- [`docs/PodLoggerJunitDemoCommands.md`](../../PodLoggerJunitDemoCommands.md)
- [`docs/PodLoggerJunitDemoDocsCodeAllighment.md`](../../PodLoggerJunitDemoDocsCodeAllighment.md)
- [`README.md`](../../../README.md)
