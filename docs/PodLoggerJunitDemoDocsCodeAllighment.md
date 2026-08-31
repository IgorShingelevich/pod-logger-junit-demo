# Docs Code Allighment Snapshot

**Статус:** updated snapshot.  
**Источник:** обновлено skill `docs-code-alignment` после наполнения [`event.md`](../junit-pod-logger/src/main/java/com/example/podlogger/event/event.md).  
**Story фичи:** [`docs/story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md`](story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md).  
**Главный устав проекта:** [`docs/PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md).  

Этот файл не описывает процедуру работы skill и не заменяет его правила.  
Этот файл фиксирует текущее состояние совпадений и расхождений между кодом и документацией.

---

## 1. Контекст снимка

- Снимок собран после локального fast-forward `EventPolicy` -> `master`.
- Snapshot отражает состояние после первой реальной валидации skill и последующего появления пакетных MD в `junit-pod-logger`.
- Если при следующем прогоне skill состояние не меняется, этот файл не должен переписываться заново.
- Если состояние изменится, новые находки или закрытие старых должны фиксироваться в этом же файле.

---

## 2. Канон на момент снимка

### 2.1 Канонические документы

| Группа | Файл | Состояние |
| --- | --- | --- |
| Главный устав | [`docs/PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md) | есть |
| Устав фичи | [`docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md`](story/PersistentLogStoreStory/PersistentLogStoreStory.md) | есть |
| Устав фичи | [`docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) | есть |
| Каталог тестов | [`docs/PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md) | есть |
| Commons | [`docs/PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md) | есть |
| Операции | [`README.md`](../README.md) | есть |
| Карта модуля | [`demo-app/demo-app.md`](../demo-app/demo-app.md) | есть |
| Карта модуля | [`demo-tests/demo-test.md`](../demo-tests/demo-test.md) | есть |
| Карта модуля | [`junit-pod-logger/junit-pod-logger.md`](../junit-pod-logger/junit-pod-logger.md) | есть; индекс пакетных MD |
| Карта модуля | [`k8s/k8s.md`](../k8s/k8s.md) | есть |
| Пакетные карты (дети модуля) | `podLogger.md`, `openshiftClient.md`, `logParser.md`, `store.md`, `repository.md`, `sqlLite.md`, `allure.md`, `event.md` | есть; не отдельные уставы |
| Snapshot | этот файл | есть |

### 2.2 Не-as-built материалы

Эти файлы существуют, но не являются кодовым контрактом:

- [`docs/story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`](story/OpenShiftEventHandlingStory/EventHandlingStrategies.md)
- [`docs/story/OpenShiftEventHandlingStory/EventHandling2Story.md`](story/OpenShiftEventHandlingStory/EventHandling2Story.md)
- `docs/propmtHistory/**`

---

## 3. Подтверждённые схождения

Ниже перечислены факты, которые ручная сверка подтвердила как согласованные между кодом и документами.

| ID | Тема | Подтверждение |
| --- | --- | --- |
| `C2` | Maven-модули | parent `pom.xml` содержит `demo-app`, `junit-pod-logger`, `demo-tests`; это совпадает с PRD и README |
| `C5` | Контракт SUT | `OrderController` подтверждает `GET /health`, `GET /api/orders/{code}`, 4 кода ошибок и fallback `Unknown error code: {code}`; это согласовано с README, `demo-app.md`, Test.md |
| `C5` | Dockerfile и jar | `demo-app/Dockerfile` копирует `target/demo-app.jar`, что совпадает с `demo-app.md` и README |
| `C6` | Локальный стенд | `ClusterLifecycle` использует `rancher/k3s:v1.31.5-k3s1`, `demo-api:local`, import образа и port-forward; это совпадает с `demo-test.md` и Test.md |
| `C6` | Манифесты | `k8s/demo-api.yaml` и `demo-tests/src/test/resources/k8s/demo-api.yaml` содержат один и тот же Deployment/Service контракт: `app=demo-api`, `/health`, port `8080` |
| `C1` | Набор тестовых классов | `OpenshiftClientParseTest`, `OpenshiftEventHandlingTest`, `PersistentLogStoreTest`, `InfrastructureLoggingTest`, `OrderErrorIT` отражены в Test.md |
| `C1` | Parse test display name | `docs/PodLoggerJunitDemoTest.md` теперь явно фиксирует, что `проверка парсинга JSON строк и пропуска шумов` — display name метода, а не класса |
| `C2` | Счётчики тестов | Test.md корректно фиксирует 1 parser test, 11 event tests, 14 store tests, 3 infra tests и 4 parameterized `OrderErrorIT` cases |
| `C7` | CollectGate | `CollectGate.shouldCollect = !collectOnFailOnly \|\| failed` совпадает с PRD, README и story-документами |
| `C7` | Fail-ветка `afterEach` | `PodLoggerService.handleFailedInvocation()` подтверждает порядок `getEvents -> attach events if non-empty -> probe -> collect logs -> persist only if available`; это согласовано с README и Event story |
| `C7` | Пустой Events-аттач | документы верно говорят, что пустой `pod-events-*` не создаётся |
| `C8` | Версии | `fabric8.version=6.13.4` и `allure-maven=2.15.0` согласованы между POM и документацией |
| `C9` | Jenkins | `Jenkinsfile` подтверждает build, docker build, `demo-tests` с `UNSTABLE`, Allure; это совпадает с README |
| `C10` | Роли документов | PRD §9 разделяет устав, story, каталог тестов, commands, README, карты модулей и пакетные MD `junit-pod-logger` как детей карты модуля |
| `C10` | Пакетные MD не второй PRD | частные MD держат инвентарь классов и границы пакета; SQL, хуки Events и карточки тестов остаются в story / Test.md |
| `C11` | Единая входная точка docs | Общим оглавлением проекта считается только корневой `README.md`; ссылки на `docs/README.md` больше не входят в активный канон |
| `C12` | Commands commons hygiene | `PodLoggerJunitDemoCommands.md` теперь содержит только reusable команды по скопам; session diary, stale path и machine-specific canon убраны |
| `C3` | README `@PodLogger` слои | README теперь отдельно показывает фактическое использование в `OrderErrorIT` и полный API аннотации, включая `standDownEventCodes` и `standDownMessagePatterns` |
| `C4` | Карта переноса библиотеки | `junit-pod-logger.md` индексирует пакеты и зависимости; `store.dto`, `PodLoggerConfiguration`, optional `spring-boot-autoconfigure` и `sqlite-jdbc` 3.47.1.0 отражены в иерархии карт |
| `C14` | Target-state пометки | README и Test.md явно маркируют `EventHandlingStrategies.md` и `EventHandling2Story.md` как не-as-built |
| `C16` | Skill support package | `.cursor/skills/docs-code-alignment/` теперь содержит `SKILL.md`, `references/*` и `templates/snapshot-section.md`; состав совпадает с story и не конкурирует с каноническими MD проекта |
| `C16` | Пакетные MD `junit-pod-logger` | восемь файлов классифицированы как дети модульной карты, включая [`event.md`](../junit-pod-logger/src/main/java/com/example/podlogger/event/event.md); fail-fast остаётся в Event story |

---

## 4. Open findings

Ниже зафиксированы расхождения и некорреляции, обнаруженные ручной сверкой.  
Каждый пункт имеет стабильный критерий, статус и короткий диагноз.

### `F-005` — `C13` — session observation is presented too close to contract

- **Статус:** `open`
- **Категория:** `session artifact presented as canon`
- **Документный факт:** Test.md содержит матрицу `OrderErrorIT` с конкретным распределением Allure Events/SQLite по кейсам как факт последнего прогона.
- **Фактический факт:** `OrderErrorIT` в коде ассертит только HTTP 400 + `fail()`. Он не ассертит, что Events окажутся только у первого кейса или что snapshot Allure будет именно таким на любом прогоне.
- **Почему это важно:** историческое наблюдение сессии нужно явно отделять от обязательного кода-контракта, иначе документ кажется более жёстким, чем тест.

### `F-007` — `C14` — target-state Event docs are adjacent to as-built canon

- **Статус:** `open`
- **Категория:** `target-state presented as as-built`
- **Документный факт:** `EventHandlingStrategies.md` и `EventHandling2Story.md` лежат рядом с as-built Event story и упомянуты в каноне как reference/target-state.
- **Фактический факт:** сами файлы не являются кодовым контрактом, а часть их содержания выходит за пределы текущего as-built.
- **Почему это важно:** skill должен уметь проверять, что эти документы не конкурируют с [`OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) за роль источника истины.

### `F-008` — `C14` — prompt history remains non-canonical but visible

- **Статус:** `open`
- **Категория:** `target-state presented as as-built`
- **Документный факт:** PRD и story-материалы справедливо считают `docs/propmtHistory/**` черновиками.
- **Фактический факт:** в дереве остаются файлы вроде `_patch_prd_tail.py`, которые легко перепутать с частью активного docs-process, если нет строгого правила классификации.
- **Почему это важно:** будущий skill должен явно относить такие файлы к non-canonical context и не пытаться выравнивать их как устав.

---

## 5. Closed findings

### `F-001` — `C11` — missing canon on disk

- **Статус:** `closed`
- **Категория:** `broken link`
- **Было:** активный канон и часть документов считали `docs/README.md` обязательным оглавлением docs, хотя в рабочей копии файла не было.
- **Стало:** активный канон опирается только на корневой `README.md`; ссылки на `docs/README.md` убраны из уставных и support-документов skill.
- **Что изменилось:** finding закрыт сменой правила канона, а не восстановлением старого файла.

### `F-002` — `C3` — README example is not the actual IT annotation

- **Статус:** `closed`
- **Категория:** `stale operational example`
- **Было:** README смешивал полный пример `@PodLogger(...)` и фактическое использование в `OrderErrorIT`, при этом не показывал `standDownEventCodes` и `standDownMessagePatterns`.
- **Стало:** README отдельно показывает реальную аннотацию `OrderErrorIT` и полный API-пример `@PodLogger`, включая оба stand-down массива.
- **Что изменилось:** finding закрыт явным разделением operational example и полного API слоя.

### `F-003` — `C1` — parser display name mapped to the wrong level

- **Статус:** `closed`
- **Категория:** `wrong test inventory`
- **Было:** Test.md описывал `проверка парсинга JSON строк и пропуска шумов` как display name тестового класса.
- **Стало:** Test.md явно фиксирует, что это display name метода `parsesJsonLinesAndSkipsNoise`.
- **Что изменилось:** finding закрыт точечной правкой каталога тестов без изменения исходного тестового кода.

### `F-006` — `C12` — Commands commons contains session diary and stale paths

- **Статус:** `closed`
- **Категория:** `session artifact presented as canon`
- **Было:** `PodLoggerJunitDemoCommands.md` смешивал reusable команды с развёрнутым журналом сессии, абсолютными локальными путями и историческими командами по уже несуществующим путям вроде `docs/prd/...`.
- **Стало:** `PodLoggerJunitDemoCommands.md` оставляет только reusable команды по скопам из корня репозитория; session diary, stale path и ссылки на устаревшие документы удалены.
- **Что изменилось:** finding закрыт чисткой commons до стабильного command canon и синхронизацией ссылок на него в связанных документах.

### `F-004` — `C4` — incomplete transfer map for `junit-pod-logger`

- **Статус:** `closed`
- **Категория:** `incomplete API coverage`
- **Было:** `junit-pod-logger.md` скрывал `store.dto`, `PodLoggerConfiguration`, optional `spring-boot-autoconfigure` и нюансы POM.
- **Стало:** карта модуля индексирует пакетные MD; `store.md` покрывает `store.dto`, `podLogger.md` — config, модульный MD — optional autoconfigure и `sqlite-jdbc` 3.47.1.0.
- **Что изменилось:** finding закрыт наполнением пакетных карт и дополнением transfer-слоя, а не сменой кода библиотеки.

### `F-009` — `C11` — stale path to `OpenshiftEventHandlingTest`

- **Статус:** `closed`
- **Категория:** `broken link`
- **Было:** Test.md и README указывали `.../podlogger/OpenshiftEventHandlingTest.java`; класс лежит в пакете `event`.
- **Стало:** оба документа ссылаются на `.../podlogger/event/OpenshiftEventHandlingTest.java`.
- **Что изменилось:** finding закрыт правкой путей при согласовании пакетных MD с каталогом тестов.

---

## 6. Вывод по структуре MD-файлов

1. Пакетные MD внутри `junit-pod-logger` нужны и уже есть: они дети [`junit-pod-logger.md`](../junit-pod-logger/junit-pod-logger.md), не вторые PRD.
2. Устав проекта и уставы фич не выпотрошены: SQL, хуки Events и API store остаются в story; пакетные MD держат инвентарь классов и границы.
3. `demo-up.md` как отдельный документ не требуется; его роль уже выполняет [`demo-app/demo-app.md`](../demo-app/demo-app.md).
4. Пакет `event` теперь имеет [`event.md`](../junit-pod-logger/src/main/java/com/example/podlogger/event/event.md): matcher и константы кодов; abort прогона по-прежнему описывает Event story.
5. Открытые проблемы теперь не в нехватке карт библиотеки, а в отделении session observation (`F-005`) и в соседстве target-state/prompt-history с as-built (`F-007`, `F-008`).

---

## 7. Правила для следующих прогонов skill

Эти правила зафиксированы здесь как контракт поведения snapshot, а не как полная инструкция реализации:

1. Если старый finding всё ещё `open` и состояние не изменилось, skill не должен переписывать его заново другими словами.
2. Если finding исправлен, skill должен сменить статус на `closed` и кратко добавить, что именно изменилось.
3. Если найден новый артефакт, новый класс, новый MD или новое расхождение, skill должен добавить новый пункт.
4. Если дельты нет, skill не должен менять файл.

---

## 8. Связанные документы

- [`docs/story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md`](story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md)
- [`docs/PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md)
- [`docs/PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md)
- [`docs/PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md)
- [`README.md`](../README.md)
- [`junit-pod-logger/junit-pod-logger.md`](../junit-pod-logger/junit-pod-logger.md)
