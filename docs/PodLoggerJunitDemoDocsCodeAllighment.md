# Docs Code Allighment Snapshot

**Статус:** baseline snapshot, составлен вручную до реализации skill.  
**Источник:** ручная сверка кода и канонических MD-файлов.  
**Story фичи:** [`docs/story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md`](story/DocsCodeAllighmentStory/DocsCodeAllighmentStory.md).  
**Главный устав проекта:** [`docs/PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md).  

Этот файл не описывает процедуру работы skill и не заменяет его правила.  
Этот файл фиксирует текущее состояние совпадений и расхождений между кодом и документацией.

---

## 1. Контекст снимка

- Снимок собран после локального fast-forward `EventPolicy` -> `master`.
- Snapshot отражает состояние, найденное до автоматизации skill.
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
| Оглавление docs | `docs/README.md` | есть в git HEAD, отсутствует в рабочей копии |
| Карта модуля | [`demo-app/demo-app.md`](../demo-app/demo-app.md) | есть |
| Карта модуля | [`demo-tests/demo-test.md`](../demo-tests/demo-test.md) | есть |
| Карта модуля | [`junit-pod-logger/junit-pod-logger.md`](../junit-pod-logger/junit-pod-logger.md) | есть |
| Карта модуля | [`k8s/k8s.md`](../k8s/k8s.md) | есть |

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
| `C2` | Счётчики тестов | Test.md корректно фиксирует 1 parser test, 11 event tests, 14 store tests, 3 infra tests и 4 parameterized `OrderErrorIT` cases |
| `C7` | CollectGate | `CollectGate.shouldCollect = !collectOnFailOnly \|\| failed` совпадает с PRD, README и story-документами |
| `C7` | Fail-ветка `afterEach` | `PodLoggerService.handleFailedInvocation()` подтверждает порядок `getEvents -> attach events if non-empty -> probe -> collect logs -> persist only if available`; это согласовано с README и Event story |
| `C7` | Пустой Events-аттач | документы верно говорят, что пустой `pod-events-*` не создаётся |
| `C8` | Версии | `fabric8.version=6.13.4` и `allure-maven=2.15.0` согласованы между POM и документацией |
| `C9` | Jenkins | `Jenkinsfile` подтверждает build, docker build, `demo-tests` с `UNSTABLE`, Allure; это совпадает с README |
| `C10` | Роли документов | PRD §9 и модульные MD согласованно разделяют устав, тестовый каталог, commands и карты модулей |
| `C14` | Target-state пометки | README и Test.md явно маркируют `EventHandlingStrategies.md` и `EventHandling2Story.md` как не-as-built |

---

## 4. Open findings

Ниже зафиксированы расхождения и некорреляции, обнаруженные ручной сверкой.  
Каждый пункт имеет стабильный критерий, статус и короткий диагноз.

### `F-001` — `C11` — missing canon on disk

- **Статус:** `open`
- **Документный факт:** корневой README и другие канонические документы ссылаются на `docs/README.md` как на оглавление docs.
- **Фактический факт:** `docs/README.md` присутствует в git HEAD, но отсутствует в рабочей копии.
- **Почему это важно:** ссылка на карту канона бита в реальном состоянии дерева, поэтому читатель не получает полный вход в docs-набор.
- **Что должен делать skill в будущем:** отличать отсутствие на диске от отсутствия в HEAD и не считать файл «несуществующим вообще».

### `F-002` — `C3` — README example is not the actual IT annotation

- **Статус:** `open`
- **Документный факт:** README показывает расширенный пример `@PodLogger(...)` с `testRunName`, `testSuiteName`, `environmentType`, `serviceType`, `publishLifecycleEvents`, `failFastOnStandDownEvent`, `healthCheckUrl`.
- **Фактический факт:** `demo-tests/src/test/java/com/example/demotest/OrderErrorIT.java` использует только `@PodLogger(collectOnFailOnly = true)`.
- **Дополнительный частный случай:** `PodLogger.java` содержит ещё `standDownEventCodes()` и `standDownMessagePatterns()`, но README их не показывает, тогда как PRD показывает.
- **Почему это важно:** без явного различения «пример API» и «фактическое использование в IT» документ вводит в заблуждение, а API coverage в README остаётся неполным.

### `F-003` — `C1` — parser display name mapped to the wrong level

- **Статус:** `open`
- **Документный факт:** Test.md описывает display name парсера как идентификатор тестового класса.
- **Фактический факт:** у `OpenshiftClientParseTest` `@DisplayName` стоит на методе `parsesJsonLinesAndSkipsNoise`, а не на классе.
- **Почему это важно:** skill должен сравнивать не только имена классов, но и реальное размещение JUnit-аннотаций.

### `F-004` — `C4` — incomplete transfer map for `junit-pod-logger`

- **Статус:** `open`
- **Документный факт:** `junit-pod-logger.md` даёт укрупнённую карту пакетов и зависимостей, достаточную только частично.
- **Фактический факт:** модуль содержит как минимум дополнительные элементы, которые документ не отражает как часть реального переносимого слоя: `store.dto`, `PodLoggerConfiguration`, optional `spring-boot-autoconfigure`, а также реальные dependency nuances из POM.
- **Почему это важно:** карта «что переносить» должна покрывать фактическую структуру библиотеки, иначе перенос в закрытый контур может быть выполнен по неполному списку.

### `F-005` — `C13` — session observation is presented too close to contract

- **Статус:** `open`
- **Документный факт:** Test.md содержит матрицу `OrderErrorIT` с конкретным распределением Allure Events/SQLite по кейсам как факт последнего прогона.
- **Фактический факт:** `OrderErrorIT` в коде ассертит только HTTP 400 + `fail()`. Он не ассертит, что Events окажутся только у первого кейса или что snapshot Allure будет именно таким на любом прогоне.
- **Почему это важно:** историческое наблюдение сессии нужно явно отделять от обязательного кода-контракта, иначе документ кажется более жёстким, чем тест.

### `F-006` — `C12` — Commands commons contains session diary and stale paths

- **Статус:** `open`
- **Документный факт:** `PodLoggerJunitDemoCommands.md` должен быть commons-справочником по скопам.
- **Фактический факт:** в нём присутствует развёрнутый журнал конкретной сессии, абсолютные локальные пути и команды по уже несуществующим путям вроде `docs/prd/...`.
- **Почему это важно:** commons начинает смешивать канон команд и исторический лог, а stale path выглядит как часть актуального contract surface.

### `F-007` — `C14` — target-state Event docs are adjacent to as-built canon

- **Статус:** `open`
- **Документный факт:** `EventHandlingStrategies.md` и `EventHandling2Story.md` лежат рядом с as-built Event story и упомянуты в каноне как reference/target-state.
- **Фактический факт:** сами файлы не являются кодовым контрактом, а часть их содержания выходит за пределы текущего as-built.
- **Почему это важно:** skill должен уметь проверять, что эти документы не конкурируют с [`OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) за роль источника истины.

### `F-008` — `C14` — prompt history remains non-canonical but visible

- **Статус:** `open`
- **Документный факт:** PRD и story-материалы справедливо считают `docs/propmtHistory/**` черновиками.
- **Фактический факт:** в дереве остаются файлы вроде `_patch_prd_tail.py`, которые легко перепутать с частью активного docs-process, если нет строгого правила классификации.
- **Почему это важно:** будущий skill должен явно относить такие файлы к non-canonical context и не пытаться выравнивать их как устав.

---

## 5. Closed findings

Пока пусто.  
После реализации skill закрытые пункты должны оставаться в этом файле со статусом `closed` и короткой записью о том, что изменилось.

---

## 6. Вывод по структуре MD-файлов

1. Дополнительные модульные MD-файлы сейчас не нужны.
2. `demo-up.md` как отдельный документ не требуется; его роль уже выполняет [`demo-app/demo-app.md`](../demo-app/demo-app.md).
3. Канон уже разбит по правильным слоям: устав проекта, уставы фич, каталог тестов, commands commons, operational README и карты модулей.
4. Основная проблема сейчас не в нехватке документов, а в точности их границ, полноте отдельных карт и чистоте snapshot/commons.

---

## 7. Правила для будущего skill

Эти правила зафиксированы здесь как ожидание к поведению snapshot, а не как полная инструкция реализации:

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
