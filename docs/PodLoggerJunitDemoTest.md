# Каталог тестов: `pod-logger-junit-demo`

**Роль:** генеральный документ по тестированию всех модулей.  
**Канон поведения:** код + [`PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md) + story-PRD.  
**Этот файл не заменяет PRD.** Он фиксирует: список тестов, ожидания, критерии приёмки, критерии проверки, тонкости и известные ошибки.

Команды (все скопы, не только тесты): [`PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md). Скоп прогона тестов: [Test Commands](PodLoggerJunitDemoCommands.md#test-commands).

Контракты фич (актуальная структура `docs/`):

- Store: [`story/PersistentLogStoreStory/PersistentLogStoreStory.md`](story/PersistentLogStoreStory/PersistentLogStoreStory.md)
- Events as-built: [`story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md)
- Events target-state (не as-built): [`story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`](story/OpenShiftEventHandlingStory/EventHandlingStrategies.md), [`story/OpenShiftEventHandlingStory/EventHandling2Story.md`](story/OpenShiftEventHandlingStory/EventHandling2Story.md)

Модульные MD (специфика дерева, не второй каталог тестов): [`demo-tests/demo-test.md`](../demo-tests/demo-test.md), [`junit-pod-logger/junit-pod-logger.md`](../junit-pod-logger/junit-pod-logger.md) (пакетные карты — из этой карты модуля), [`demo-app/demo-app.md`](../demo-app/demo-app.md), [`k8s/k8s.md`](../k8s/k8s.md).

---

## Структура `docs/` (после реорганизации)

| Путь сейчас | Было | Роль |
| --- | --- | --- |
| [`PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md) | `docs/prd/podLoggerJunitDemoPRD.md` | общий as-built PRD |
| **этот файл** `PodLoggerJunitDemoTest.md` | заглушка в `docs/` | каталог тестов |
| [`PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md) | — | справочник команд (commons) |
| [`story/PersistentLogStoreStory/PersistentLogStoreStory.md`](story/PersistentLogStoreStory/PersistentLogStoreStory.md) | `docs/feature/PersistentLogStore/PersistentLogStorePRD.md` | store PRD |
| [`story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) | `docs/feature/OpenShiftEventHandling/OpenShiftEventHandlingPRD.md` | Events as-built |
| [`story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`](story/OpenShiftEventHandlingStory/EventHandlingStrategies.md) | `docs/prd/EventHandlingPRD.md` | forward-looking guide |
| [`story/OpenShiftEventHandlingStory/EventHandling2Story.md`](story/OpenShiftEventHandlingStory/EventHandling2Story.md) | `docs/EventHandling.PRD.md` | расширенный target-state |

Папок `docs/prd/` и `docs/feature/` больше нет. Источник истины по тестам — **этот файл** + код.

---

## Как добавить новый тест

При появлении нового теста **обязательно** дописать сюда отдельную карточку:

1. Идентификатор (класс / метод / display name).
2. Модуль и нужен ли Docker/K3s.
3. **Критерии приёмки** — единственные, полные, без противоречий с PRD.
4. **Критерии проверки** — что смотреть в Surefire / SQLite / Allure / Docker.
5. **Список необходимых команд** в карточке (ссылка + точечный `-Dtest`) и **обязательно** в [`PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md), раздел [Test Commands](PodLoggerJunitDemoCommands.md#test-commands).
6. **Тонкости** (ожидаемый fail, пустой Events-аттач, window skew, …).
7. **Известные ошибки** и способ решения.

Нельзя: два разных ожидания на один и тот же исход (например «тест должен быть зелёным» и «тест должен быть failed as designed»).

---

## Инварианты приёмки (все модули)

1. Library-тесты (`junit-pod-logger`) **не требуют Docker**. Кластер не нужен.
2. `demo-tests` требуют JDK, Maven, **запущенный Docker Desktop**, собранный `demo-app/target/demo-app.jar`.
3. Ошибки collect/Allure/SQLite **не** краснят текущий product-тест. Ошибка `startTestRun` в `beforeAll` — fail-fast.
4. Events на **passed** invocation не читаются и не аттачатся.
5. Пустой список Events → **нет** аттача `pod-events-*` (запрещён пустой Events-аттач).
6. `relevantEvents` в Allure JSON логов на fail; в SQLite колонки нет; `podName` в log DTO может быть `null` (парсер stdout его не ставит).
7. Fail-fast прогона только при `standDownEventPresent`. Красный health без stand-down Event прогон **не** abort'ит.
8. `OrderErrorIT` **ожидаемо красный** (4 parameterized fail после HTTP 400). Это не дефект.
9. Отдельного Allure-аттача `pod-state-*` в v1 **нет**.

---

## Сводка классов

| Модуль | Класс | Кол-во | Docker | Ожидаемый Maven-результат |
| --- | --- | ---: | --- | --- |
| `junit-pod-logger` | `OpenshiftClientParseTest` | 1 | нет | все PASSED |
| `junit-pod-logger` | `OpenshiftEventHandlingTest` | 11 (+ nested) | нет | все PASSED |
| `junit-pod-logger` | `PersistentLogStoreTest` | 14 | нет | все PASSED |
| `demo-tests` | `InfrastructureLoggingTest` | 3 | да | все PASSED |
| `demo-tests` | `OrderErrorIT` | 4 | да | 4 FAILED as designed |
| `demo-app` | unit-тестов нет | 0 | нет | package only |

Итого library: **26 PASSED**. Demo: **3 PASSED + 4 FAILED as designed**. Maven `demo-tests` из‑за Surefire даёт **BUILD FAILURE** — ожидаемо.

---

## 1. `OpenshiftClientParseTest`

**Файл:** `junit-pod-logger/src/test/java/com/example/podlogger/client/OpenshiftClientParseTest.java`  
**Модуль:** `junit-pod-logger`. Docker: нет.  
**Display name метода:** `проверка парсинга JSON строк и пропуска шумов`.

### 1.1 `parsesJsonLinesAndSkipsNoise`

**Ожидание:** смешанный dump (kube preamble + JSON + `not json`) даёт ровно 2 `PodLogDto`.

**Критерии приёмки:**

- Строка `"some kube preamble"` не становится DTO.
- `"not json"` не становится DTO.
- Первая JSON-строка: `level=ERROR`, `message=Unknown SKU`, timestamp `2026-08-29T17:01:02.123`.
- Вторая JSON-строка: message содержит `started`.
- Размер списка = 2.

**Критерии проверки:** Surefire `Tests run: 1, Failures: 0`. Allure/SQLite/кластер не требуются.

**Команды:** [`PodLoggerJunitDemoCommands.md` — Test Commands](PodLoggerJunitDemoCommands.md#test-commands). Точечно: `mvn -pl junit-pod-logger -am test -Dtest=OpenshiftClientParseTest`.

**Тонкости:** тест бьёт в `JsonLogParser`, не в живой `OpenshiftClient.getLog()`. Кластер не поднимается.

**Известные ошибки:**

| Ошибка | Решение |
| --- | --- |
| Неверный timestamp (наносекунды vs millis) | Jackson `JavaTimeModule`; ожидание `123_000_000` nanos |
| Parser тянет Fabric8 | Не должен: только `ObjectMapper` + dump-строка |

---

## 2. `OpenshiftEventHandlingTest`

**Файл:** `junit-pod-logger/src/test/java/com/example/podlogger/event/OpenshiftEventHandlingTest.java`  
**Harness:** `OpenshiftEventHandlingHarness` (in-memory client, capturing `AllureSink`).  
**Контракт:** [`OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md), сценарии 1–5. Docker: нет.  
**Display name класса:** `OpenShift Event Handling Test`.

### 2.1 Nested `1. Publish/get с кодами`

| Метод | Display name | Критерии приёмки |
| --- | --- | --- |
| `publishThenGetReturnsCode` | publishPodEvent затем getEvents возвращает тот же code | После `publishPodEvent("Warning","Maintenance","stand down")` `getEvents()` содержит 1 элемент, `code=reason=Maintenance` |
| `mapperCopiesReasonAsCode` | G1: mapper code равен Event.reason | `PodEventMapper.toDto` копирует k8s `reason` в `code` и `reason` |
| `windowExcludesEventsOutsideRange` | G2: окно from..to отсекает события вне интервала | Событие «сейчас» входит в `now±1m`, не входит в окно «два дня назад» |

**Критерии проверки:** 3 PASSED в Surefire nested-классе. Кластер не нужен.

### 2.2 Nested `2. Fail + Events → Allure`

`failedTestAttachesEventsWithCode` — `упавший тест аттачит pod-events с code и relevantEvents в логах`.

**Критерии приёмки (A1/A3/P1/P2):**

- Упавший sample получает Allure-аттач `pod-events-*` с `"code":"Maintenance"`.
- Если логи непустые — `pod-logs-*` содержит `relevantEvents` с тем же кодом.
- Ровно два lifecycle publish: `TestRunStarted` и `TestRunFinished` (`total=`, `passed=`, `failed=` в message).
- Per-test Event не публикуется.

**Критерии проверки:** EngineTestKit + capturing sink `OpenshiftEventHandlingHarness.ATTACHMENTS` / `PUBLISHED`.

### 2.3 Nested `3. Fail + нет Events → нет аттача`

`failedTestWithoutEventsSkipsEventsAttachment` — `пустой getEvents не создаёт pod-events аттач`.

**Критерии приёмки (A2):** mock `getEvents` = `[]` → нет аттача `pod-events-*`; `pod-logs-*` при непустых логах есть.

### 2.4 Nested `4. Stand-down → fail-fast`

`standDownAbortsRemainingTests` — `stand-down Event останавливает второй тест, первый остаётся исходным fail`.

**Критерии приёмки (F1/F2):**

- Первый тест остаётся `AssertionError` (product), не затирается stand-down.
- Второй метод **не** доходит до тела (`SECOND_RAN=false`).
- Сообщение второго: содержит `Stand unavailable`.
- Allure: и Events, и логи (если хвост лога непустой).

**Тонкость:** из `afterEach` exception **не** бросают; abort следующих — в `beforeEach`.

### 2.5 Nested `5. Health-only red → нет fail-fast`

`healthRedDoesNotFailFastAndSkipsPersist` — `красный health без stand-down Event не abort'ит прогон и не persist'ит invocation`.

**Критерии приёмки (F3/H4):**

- `available=false`, `standDownEventPresent=false` → второй тест **выполняется** (PASSED).
- Persist `log_entry` для `firstFails` отсутствует.
- Events-аттача нет, если список пустой.

### 2.6 Nested `StandDownEventMatcher`

| Метод | Display name | Критерии приёмки |
| --- | --- | --- |
| `standDownCodesMatch` | G4: Maintenance / StandUnavailable / Evicted — match | коды матчятся |
| `lifecycleAndNoiseDoNotMatch` | G3: TestRunStarted / TestRunFinished / Pulled — не stand-down | lifecycle и `Pulled` **не** stand-down |
| `messagePatternMatches` | pattern maintenance в message | Подстрока `maintenance` в message при коде `Custom` → match |

### 2.7 Nested `PodLogDto.relevantEvents JSON`

`relevantEventsAppearInJson` — `поле relevantEvents сериализуется`. Jackson сериализует поле `relevantEvents` и код Event в JSON лога.

**Команды:** [`PodLoggerJunitDemoCommands.md` — Test Commands](PodLoggerJunitDemoCommands.md#test-commands). Точечно: `mvn -pl junit-pod-logger -am test -Dtest=OpenshiftEventHandlingTest`.

**Известные ошибки:**

| Ошибка | Решение |
| --- | --- |
| EngineTestKit не видит Spring beans | Harness: `@SpringBootTest` + `@Primary` stub `OpenshiftClient` + `AllureSink` |
| Static-состояние harness течёт между nested | `@BeforeEach resetHarness()` |
| Probe в `beforeAll` съедает первый `availability` | Harness: `startAvailability` на `PROBE_COUNT==0`, дальше `availability` |
| Surefire ERROR `Failed to delete temp directory` после зелёного тела теста | Windows lock WAL SQLite vs JUnit `@TempDir`. Assertions уже прошли. Повтор `mvn -pl junit-pod-logger -am test` часто `26 PASSED`. Не считать дефектом сценариев 1–5. |

---

## 3. `PersistentLogStoreTest`

**Файл:** `junit-pod-logger/src/test/java/com/example/podlogger/store/PersistentLogStoreTest.java`  
**Display name класса:** `persistent log store test`. Docker: нет.  
**Контракт:** [`PersistentLogStoreStory.md`](story/PersistentLogStoreStory/PersistentLogStoreStory.md).

| Метод | Display name | Критерии приёмки |
| --- | --- | --- |
| `shouldStartAndFinishTestRun` | запись и чтение: start/finish test run | `STARTED` без `finishedAt`; после finish — `FINISHED` и `finishedAt` |
| `shouldSaveLogsAndGetByTestRunId` | запись и чтение: saveLogs / getLogs(testRunId) | 2 строки ST; контекст run (имя, environment) на DTO |
| `shouldFilterByTimeRange` | фильтр по времени | Закрытый интервал `[T1,T2]` не отдаёт записи после `to` |
| `shouldFilterByEnvironment` | фильтр по environment | ST vs DEV; mismatch `stRunId+DEV` = пусто |
| `shouldFilterBySuiteAndEnvironment` | фильтр suite + environment | suite `orders-suite` + ST → 2 |
| `shouldFilterByRunNameSuiteEnvironment` | фильтр runName + suite + environment | Совпадение 2; чужой env — пусто |
| `shouldFilterByRelatedTestClassAndMethod` | фильтр relatedTestClass / relatedTestMethod | `OrderErrorIT` + `apiErrorIsLoggedOnPod` → 2 |
| `shouldFilterByLogQueryCombined` | комбинированный LogQuery | ST + ERROR + messageContains SKU → 1 «Unknown SKU» |
| `shouldGetLogsForWholeRun` | getLogsForWholeRun | Кардинальность = getLogs(testRunId) |
| `shouldDeduplicateOnResave` | повторный save не создаёт дубли | Unique `(testRunId, timestamp, fingerprint)` |
| `shouldRejectSaveWithoutTestRunId` | save без testRunId отклоняется | `IllegalArgumentException` |
| `shouldDeleteOlderThanRemovesClosedRunsOnly` | deleteOlderThan удаляет только закрытые старые run | Удаляет 1 закрытый backdated; открытый старый и свежий живы |
| `shouldSkipPersistWhenCollectOnFailOnlyAndPassed` | зелёный класс с collectOnFailOnly не пишет log_entry | `test_run` есть, `log_entry` нет |
| `extensionOnFailedClassPersistsLogsThatCanBeRead` | упавший класс с @PodLogger пишет строки, которые читаются из SQLite | ERROR Unknown SKU, `testFailed`, related class/method |

**Критерии проверки:** Surefire 14 PASSED; БД в `@TempDir`, не `demo-tests/target/pod-logger-store.sqlite`.

**Команды:** [`PodLoggerJunitDemoCommands.md` — Test Commands](PodLoggerJunitDemoCommands.md#test-commands). Точечно: `mvn -pl junit-pod-logger -am test -Dtest=PersistentLogStoreTest`.

**Тонкости:** `@TempDir` на каждый метод; `relevantEvents` не в INSERT; fingerprint без Events.

**Известные ошибки:**

| Ошибка | Решение |
| --- | --- |
| Windows file lock SQLite | Отдельный файл на `@TempDir`; не шарить одну DB между JVM |
| `Failed to delete temp directory` на teardown | То же, что Events: lock WAL после PASSED. Критерий — тело метода, не cleanup `@TempDir`. |
| `deleteOlderThan` снёс открытый run | PRD: `finished_at IS NULL` не трогать |

---

## 4. `InfrastructureLoggingTest`

**Файл:** `demo-tests/src/test/java/com/example/demotest/InfrastructureLoggingTest.java`  
**Display name:** `Infrastructure Logging Test`. Docker: **да**. `@PodLogger` нет.

### 4.1 `dockerIsReachable`

Display: `step 1 - docker is reachable for local infrastructure tests`.

**Критерии приёмки:** `docker info` exit code 0, stdout не пустой.

**Критерии проверки:** шаг 1/3 в логе; без этого шага 2–3 бессмысленны.

### 4.2 `demoApiImageCanBeBuilt`

Display: `step 2 - demo api image can be built from the packaged application`.

**Критерии приёмки:**

- Существует `demo-app/target/demo-app.jar` (иначе fail с текстом `mvn -pl demo-app -am package -DskipTests`).
- `docker build -t demo-api:local .` из `demo-app` exit 0.
- В выводе есть маркер успеха (`Successfully` / `naming to docker.io/library/demo-api:local` / `writing image`).

### 4.3 `deploymentBecomesReachable`

Display: `step 3 - local cluster deployment becomes reachable`.

**Критерии приёмки:**

- `ClusterLifecycle.start()` инициализирует Fabric8 client и port-forward (`localPort > 0`).
- `GET http://127.0.0.1:{port}/health` → 200, тело содержит `UP`.

**Команды:** [`PodLoggerJunitDemoCommands.md` — Test Commands](PodLoggerJunitDemoCommands.md#test-commands). Точечно: `mvn -pl demo-tests -am test -Dtest=InfrastructureLoggingTest` (нужны jar и Docker — там же).

**Тонкости:**

- `DOCKER_HOST` на Windows часто пустой: Docker Desktop named pipe достаточен. Тест **не** требует `tcp://127.0.0.1:2375`, хотя javadoc это упоминает.
- K3s image: `rancher/k3s:v1.31.5-k3s1`. Первый запуск качает образ — минуты.
- `@PodLogger` нет → в Allure **нет** `pod-logs-*` / `pod-events-*`. Это не дыра приёмки этого класса.
- CTL кластера в демо — **Docker + Testcontainers K3s**, не `kubectl`/`oc`. Fabric8 ходит в API K3s-контейнера сам.

**Известные ошибки:**

| Ошибка | Решение |
| --- | --- |
| Docker is not reachable | Запустить Docker Desktop, дождаться `docker info` = 0 |
| Missing demo-app.jar | `mvn -pl demo-app -am package -DskipTests` |
| K3s timeout 2 min на Ready | Проверить Docker resources; `docker ps`; логи Testcontainers `k3s` |
| `/health` не UP | Port-forward / под не Ready; смотреть `ClusterLifecycle` wait |

---

## 5. `OrderErrorIT` (Test Run Demo App)

**Файл:** `demo-tests/src/test/java/com/example/demotest/OrderErrorIT.java`  
**Аннотация:** `@PodLogger(collectOnFailOnly = true)`. Docker/K3s: **да**.

Static-блок вызывает `ClusterLifecycle.start()` (build image, K3s, import, manifest, wait Ready, port-forward).

Parameterized: `UNKNOWN_SKU`, `OUT_OF_STOCK`, `PAYMENT_DECLINED`, `USER_BLOCKED`.

### Общие ожидания каждого кейса

1. RestAssured `GET /api/orders/{code}` → **HTTP 400**.
2. Тело: `code` и `message` совпадают с таблицей API.
3. Затем **обязательный** `Assertions.fail(...)` — иначе при `collectOnFailOnly=true` не будет Allure/SQLite логов.
4. Maven Surefire помечает кейс **FAILED**. Это **единственный** ожидаемый статус кейса.
5. `@PodLogger` afterEach: `getEvents(window)` → Allure Events **если список непустой** → probe → логи окна → persist если под available.

### Критерии приёмки по кодам

| Кейс | HTTP | `code` | `message` | JUnit | Allure logs | Allure events | SQLite |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UNKNOWN_SKU | 400 | UNKNOWN_SKU | Unknown SKU | FAILED as designed | `pod-logs-UNKNOWN_SKU` | `pod-events-*` **только если** Events окна непусты | строка ERROR Unknown SKU |
| OUT_OF_STOCK | 400 | OUT_OF_STOCK | Item is out of stock | FAILED as designed | `pod-logs-OUT_OF_STOCK` | то же правило | строка Item is out of stock |
| PAYMENT_DECLINED | 400 | PAYMENT_DECLINED | Payment was declined | FAILED as designed | `pod-logs-PAYMENT_DECLINED` | то же правило | строка Payment was declined |
| USER_BLOCKED | 400 | USER_BLOCKED | User is blocked | FAILED as designed | `pod-logs-USER_BLOCKED` | то же правило | строка User is blocked |

Lifecycle Events на поде (не per-test): `TestRunStarted` в `beforeAll`, `TestRunFinished` в `afterAll`.

### Критерии проверки

1. Surefire: 4 failures, 0 errors; сообщение содержит `collectOnFailOnly demo: force failure after expected API error`.
2. Не 500 и не assertion на status 400 — иначе это **не** as-designed fail (см. известные ошибки).
3. SQLite `demo-tests/target/pod-logger-store.sqlite`: `test_run` FINISHED; `log_entry` по кейсам; `pod_name` может быть NULL.
4. Allure: у каждого failed кейса есть `pod-logs-{CODE}`. `pod-events-{CODE}` — только при непустом LIST в окне invocation ±2s.
5. `afterAll` публикует `TestRunFinished` даже при красных тестах.

**Команды:** [`PodLoggerJunitDemoCommands.md` — Test Commands](PodLoggerJunitDemoCommands.md#test-commands) (`mvn -pl demo-tests -am test`, Allure, sqlite).

**Тонкости (обязательно учитывать при приёмке Allure):**

- Окно Events = `[testStartUtc - 2s, testEnd + 2s]`. `TestRunStarted` часто попадает **только в первый** parameterized кейс. Последующие кейсы без Events в окне **не** получают `pod-events-*`. Это as-built, не дефект v1.
- Отдельного аттача «под/pod-state» нет.
- Skew 2s может подмешать лог предыдущего кейса в следующий `pod-logs-*`.
- `podName` в log JSON часто `null`; имя поды есть в Event DTO (`involvedObject`).
- Класс-уровень Allure `OrderErrorIT` может быть `broken` (Spring/extension), это не четвёртый product-кейс.

**Известные ошибки:**

| Ошибка | Решение |
| --- | --- |
| `Expected status code <400> but was <500>` | Под/приложение не готовы; сначала `InfrastructureLoggingTest` / health UP |
| Нет `demo-app.jar` | `mvn -pl demo-app -am package -DskipTests` |
| Нет `pod-events-*` на 2–4 кейсах | Окно LIST; Events вне интервала. Не требовать Events на каждый кейс в v1 |
| Maven BUILD FAILURE на demo-tests | Ожидаемо из‑за 4 fail; не чинить Surefire ignore без отдельного решения |
| `allure:report` prefix not found | `mvn -pl demo-tests io.qameta.allure:allure-maven:2.15.0:report` |
| Allure `file://` не грузит JSON | HTTP: `python -m http.server` в каталоге report |
| Смешанные старые `allure-results` | Очистить `demo-tests/target/allure-results` перед чистым прогоном |

---

## 6. `demo-app`

Unit-тестов нет. Приёмка артефакта:

- `mvn -pl demo-app -am package -DskipTests` → `demo-app/target/demo-app.jar`.
- Dockerfile копирует этот jar. Без него `ClusterLifecycle` / шаг 2 infra падают.

---

## Матрица «тест → артефакты» (факт последнего полного прогона диалога)

| Тест | Surefire | Allure logs | Allure events | SQLite |
| --- | --- | --- | --- | --- |
| Parse / EventHandling / PersistentLogStore | 26 PASSED | harness sink, не demo report | — | TempDir |
| Infra step 1–3 | 3 PASSED | нет | нет | нет (нет @PodLogger) |
| OrderErrorIT UNKNOWN_SKU | FAILED designed | да | да (`TestRunStarted` в окне) | да |
| OrderErrorIT OUT_OF_STOCK | FAILED designed | да | нет (пустое окно) | да |
| OrderErrorIT PAYMENT_DECLINED | FAILED designed | да | нет | да |
| OrderErrorIT USER_BLOCKED | FAILED designed | да | нет | да |

---

Команды прогона, Allure, SQLite и Docker/CTL — в [`PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md), раздел [Test Commands](PodLoggerJunitDemoCommands.md#test-commands).

---

## Ссылки

- Операции: [`README.md`](../README.md)
- Команды: [`PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md)
- Приёмка Events (сценарии 1–5): `OpenshiftEventHandlingTest`
- Приёмка store: `PersistentLogStoreTest`

## Риски миграции

| Риск | Симптом | Первый тест для проверки | Где искать решение |
| --- | --- | --- | --- |
| Новый stdout-формат логов не совпадает с `PodLogDto` | `OrderErrorIT` падает as designed, но `pod-logs-*` пустой или логи в SQLite не те | `OpenshiftClientParseTest` | `parser/logParser.md`, `demo-app/demo-app.md` |
| Platform Events/RBAC/stand-down отличаются от демо | fail-fast не срабатывает или срабатывает неожиданно | `OpenshiftEventHandlingTest` | `event/event.md`, `client/openshiftClient.md`, `k8s/k8s.md` |
| Store/SQLite ведут себя странно после адаптации parser | дедуп, query или persist дают неожиданный результат | `PersistentLogStoreTest` | `store/store.md`, `store/sqlite/sqlLite.md` |
| Проблема только в consumer wiring и bootstrap | library unit tests зелёные, а интеграционный demo path падает | `InfrastructureLoggingTest`, затем `OrderErrorIT` | `demo-tests/demo-test.md`, `README.md` |

### Профит для агента в новом контуре

- Этот раздел связывает тип симптома с первым тестом, который должен доказать или опровергнуть гипотезу.
- Агент может не читать весь каталог, а сразу запускать нужный тест и переходить в соответствующий MD-файл с рисками миграции.
