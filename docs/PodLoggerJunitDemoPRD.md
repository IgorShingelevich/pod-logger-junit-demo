# Общий PRD: `pod-logger-junit-demo`

**Статус:** as-built, источник истины по назначению проекта.  
**Канонический путь:** `docs/PodLoggerJunitDemoPRD.md`.  
**Операции и запуск:** [`README.md`](../README.md).  
**Каталог тестов:** [`PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md).  
**Команды:** [`PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md).  
**Фичи:**  
- [`Persistent Log Store`](story/PersistentLogStoreStory/PersistentLogStoreStory.md)  
- [`OpenShift Event Handling`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md)

Этот документ описывает **весь** проект. Фичевые PRD не повторяют модули и запуск — они детализируют свой слой. Черновики в `docs/propmtHistory` не являются источником истины.

---

## 1. Назначение

Библиотека JUnit 5 `@PodLogger`: на тестовый класс ставится аннотация, каждый invocation (включая каждый кейс `@ParameterizedTest`) фиксирует UTC-окно, ходит в Kubernetes API через Fabric8 **OpenShiftClient** и:

1. парсит JSON-строки stdout поды в `PodLogDto`;
2. прикладывает срез окна в **Allure**;
3. сохраняет тот же срез логов в локальный **SQLite** (`PodStoreService`);
4. на **упавшем** invocation читает Kubernetes Events, аттачит их отдельно и умеет fail-fast прогона, если на поде есть stand-down Event.

Это **не** полноценный OpenShift (CRC/OKD). Локальный кластер демо — **K3s в Docker (Testcontainers)**. API `pods/log` и core/v1 Events те же.

Проект нужен, чтобы в закрытом контуре перенести библиотеку `junit-pod-logger` к своим тестам: аннотация, extension, store, Events — без зависимости от `demo-app`/`demo-tests`.

---

## 2. Модули

| Модуль | Роль | Docker |
| --- | --- | --- |
| `demo-app` | Spring Boot SUT: `GET /health`, `GET /api/orders/{code}` → 400 + ERROR JSON в stdout. Локально: [`demo-app/demo-app.md`](../demo-app/demo-app.md) | только как образ для K3s |
| `junit-pod-logger` | Библиотека: `@PodLogger`, extension, runtime, Allure, SQLite, Events. Локально: [`junit-pod-logger/junit-pod-logger.md`](../junit-pod-logger/junit-pod-logger.md) | не нужен |
| `demo-tests` | Потребитель: K3s, деплой, RestAssured, 4 ожидаемо красных parameterized кейса. Локально: [`demo-tests/demo-test.md`](../demo-tests/demo-test.md) | нужен |

---

## 3. Слои библиотеки

```text
JUnit lifecycle                 runtime                         persistence              output
─────────────────               ───────                         ────────────             ──────
PodLoggerExtension  ──►  PodLoggerService  ──► OpenshiftClient
        │                        │                    │
        │                        ├── LogParser         ├── getLog
        │                        ├── PodStoreService   ├── getEvents / publishPodEvent
        │                        └── LogAllure…        └── probePodAvailability
        │
        └── TestRunStore
```

| Компонент | Делает | Не делает |
| --- | --- | --- |
| `PodLoggerExtension` | хуки, test run, счётчики, stand-down flag | SQL, parse, Fabric8 |
| `PodLoggerService` | окно, persist, Allure, Events, merge | schema, query builder |
| `OpenshiftClient` | logs, events, health | SQLite, Allure, JUnit |
| `PodStoreService` | save/get логов | Kubernetes |
| `TestRunStore` | lifecycle прогона | чтение логов |
| `LogAllureAttachmentService` | JSON-аттачи | persist |

`GetUniqueLogs` / `GetRelevantLogs` в v1 **нет** (будущий `LogAnalysisService` поверх store).

---

## 4. Инварианты, общие для всех фич

1. В stdout поды из фреймворка **не пишем**. Маркеры прогона — только Kubernetes Events.
2. Один флаг `collectOnFailOnly` на **оба** выхода логов (Allure + SQLite). Отдельного `persist` нет.
3. `shouldCollect = !collectOnFailOnly \|\| failed`. Для failed CollectGate всегда true.
4. Ошибки save/Allure/collect **не** краснят текущий тест. Ошибка `startTestRun` в `beforeAll` — fail-fast.
5. `testRunName` может повторяться; уникален `testRunId` (UUID).
6. Прогоны `DEV` / `ST` / `FT` / `LOCAL` живут в **одной** SQLite.
7. Events на passed-тесте не читаются и не аттачатся.
8. `relevantEvents` в SQLite **не** пишутся.
9. `podName` в log DTO парсер stdout не ставит (`null`, если поля не было в JSON строки).

---

## 5. Lifecycle (сводка)

```text
beforeAll   startTestRun → publish TestRunStarted → probe; stand-down → throw
beforeEach  если STAND_UNAVAILABLE → throw; иначе запомнить start UTC
afterEach   passed → CollectGate логов; failed → Events + availability + persist gate
afterAll    merge логов прогона → publish TestRunFinished → finishTestRun
```

Подробности логов: [`PersistentLogStoreStory.md`](story/PersistentLogStoreStory/PersistentLogStoreStory.md).  
Подробности Events/health/fail-fast: [`OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md).

---

## 6. Публичная аннотация

```java
@PodLogger(
    collectOnFailOnly = true,
    namespace = "default",
    podLabelSelector = "app=demo-api",
    testRunName = "order-error-demo",
    testSuiteName = "com.example.demotest.OrderErrorIT",
    environmentType = EnvironmentType.LOCAL,  // DEV | ST | FT | LOCAL
    serviceType = "demo-api",
    publishLifecycleEvents = true,
    failFastOnStandDownEvent = true,
    healthCheckUrl = "",
    standDownEventCodes = {},
    standDownMessagePatterns = {})
```

Пустые массивы stand-down = дефолт библиотеки, не «матчить ничего».

---

## 7. Приёмка

| Что | Где | Docker |
| --- | --- | --- |
| Parser | `OpenshiftClientParseTest` | нет |
| Store | `PersistentLogStoreTest` (display name **persistent log store test**) | нет |
| Events | `OpenshiftEventHandlingTest` (сценарии 1–5) | нет |
| Infra стенда | `InfrastructureLoggingTest` в `demo-tests` | да |
| Демо на поде | `OrderErrorIT` в `demo-tests` | да |

```bash
mvn -pl junit-pod-logger -am test
mvn -pl demo-app -am package -DskipTests && docker build -t demo-api:local demo-app
mvn -pl demo-tests -am test
```

---

## 8. Вне v1 (весь проект)

- `LogAnalysisService` (`GetUniqueLogs` / `GetRelevantLogs`)
- Watch / `events.k8s.io` / Events Node
- Отдельная таблица events в SQLite
- Per-test publish `TestFailed`
- Export JSON store
- CI-artifact файла БД (не требуется)

---

## 9. Симметрия документов

| Документ | Содержит | Не содержит |
| --- | --- | --- |
| Этот файл | назначение, модули, слои, общие инварианты, указатели на фичи | SQL-схему, таблицу persist vs Allure Events |
| [`PersistentLogStoreStory.md`](story/PersistentLogStoreStory/PersistentLogStoreStory.md) | SQLite, API store, fingerprint, retention | Events, fail-fast, health |
| [`OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) | publish/get Events, health, Allure Events, fail-fast | query API store, schema |
| [`PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md) | каталог тестов, приёмка, известные ошибки | тела команд (ссылка на Commands) |
| [`PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md) | команды по скопам | критерии приёмки |
| README | как запускать, аннотация, путь к БД, Jenkins | полный SQL, полный контракт хуков Events |
| `demo-app.md` / `demo-test.md` / `junit-pod-logger.md` / `k8s.md` | специфика дерева модуля | второй каталог тестов, второй PRD |
| Пакетные MD `junit-pod-logger` (`podLogger.md`, `openshiftClient.md`, `logParser.md`, `store.md`, `repository.md`, `sqlLite.md`, `allure.md`, `event.md`) | карта классов пакета, границы, указатель на приёмку | SQL-схема, полный контракт хуков, карточки тестов |

Пакетные MD — дети [`junit-pod-logger.md`](../junit-pod-logger/junit-pod-logger.md), не отдельные уставы фич. Индекс и ссылки на них держит карта модуля. Fail-fast и probe остаются в Event story; [`event.md`](../junit-pod-logger/src/main/java/com/example/podlogger/event/event.md) — только matcher и константы кодов.
