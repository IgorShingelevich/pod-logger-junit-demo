# JUnit `@PodLogger` — логи поды в Allure и SQLite

Демонстрация кастомного JUnit 5 extension: на тестовый класс ставится `@PodLogger`. Каждый invocation (в том числе каждый кейс `@ParameterizedTest`) фиксирует UTC-окно, ходит в Kubernetes API через Fabric8 **OpenShiftClient**, парсит JSON-строки лога поды в `PodLogDto` и:

- прикладывает срез в **Allure**;
- сохраняет тот же срез в локальный **SQLite** (`PodStoreService`);
- на **упавшем** invocation читает Kubernetes Events, аттачит непустой список отдельно (`pod-events-*`) и при stand-down Event останавливает оставшиеся тесты класса.

**Один флаг** `collectOnFailOnly` управляет обоими выходами *логов*: что ушло в Allure — то же пишется в store (если под доступна). Events на passed-тесте не снимаются.

Это **не** полноценный OpenShift (CRC/OKD). Локальный кластер демо — **K3s в Docker (Testcontainers)**. API `pods/log` и core/v1 Events те же.

## Документация (канон)

| Документ | Содержание |
| --- | --- |
| [`docs/PodLoggerJunitDemoPRD.md`](docs/PodLoggerJunitDemoPRD.md) | Общий PRD: назначение, модули, слои, инварианты |
| [`docs/PodLoggerJunitDemoTest.md`](docs/PodLoggerJunitDemoTest.md) | Каталог тестов: приёмка, проверка, известные ошибки |
| [`docs/PodLoggerJunitDemoCommands.md`](docs/PodLoggerJunitDemoCommands.md) | Справочник команд по скопам (Test Commands и другие) |
| [`docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md`](docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md) | SQLite store |
| [`docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) | Events, health, fail-fast |
| [`docs/README.md`](docs/README.md) | Оглавление docs |

JavaDoc каждого класса и метода — в модуле `junit-pod-logger`.

Дополнительные архитектурные материалы (не as-built):

- [`docs/story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`](docs/story/OpenShiftEventHandlingStory/EventHandlingStrategies.md) — forward-looking guide по возможной эволюции event management; не заменяет текущий as-built контракт.
- [`docs/story/OpenShiftEventHandlingStory/EventHandling2Story.md`](docs/story/OpenShiftEventHandlingStory/EventHandling2Story.md) — расширенная версия того же guide; использовать как reference для следующей итерации.

## Требования

- JDK 17
- Maven 3.9+
- Docker Desktop (только для `OrderErrorIT` / `InfrastructureLoggingTest` в `demo-tests`)

## Модули

| Модуль | Назначение |
| --- | --- |
| `demo-app` | Spring Boot API: `GET /health`, `GET /api/orders/{code}` — 400 + ERROR JSON в stdout |
| `junit-pod-logger` | `@PodLogger`, extension, runtime-сбор, Allure, **SQLite store**, **Kubernetes Events** |
| `demo-tests` | K3s, деплой поды, RestAssured, 4 падающих параметризованных кейса |

## Как запускать

Сборка и тесты библиотеки **без Docker** (parser + persistent store + Event Handling harness):

```bash
mvn -DskipTests package
mvn -pl junit-pod-logger -am test
```

Приёмка store: [`PersistentLogStoreTest`](junit-pod-logger/src/test/java/com/example/podlogger/store/PersistentLogStoreTest.java) (display name **persistent log store test**).

Приёмка Events: [`OpenshiftEventHandlingTest`](junit-pod-logger/src/test/java/com/example/podlogger/OpenshiftEventHandlingTest.java) (сценарии 1–5, кластер не нужен).

Полное демо с подой (нужен Docker Desktop). Сначала пакет `demo-app` (jar для Dockerfile), затем тесты:

```bash
mvn -pl demo-app -am package -DskipTests
docker build -t demo-api:local demo-app
mvn -pl demo-tests -am test
```

`demo-tests` сам соберёт образ `demo-api:local`, если jar уже есть, поднимет K3s, импортирует образ, применит [`k8s/demo-api.yaml`](k8s/demo-api.yaml) (копия в classpath: `demo-tests/src/test/resources/k8s/demo-api.yaml`), сделает port-forward и бьёт в API с хоста.

Четыре invocation **ожидаемо красные**: после проверки HTTP 400 тест вызывает `Assertions.fail(...)`, иначе при `collectOnFailOnly=true` не будет ни Allure-аттача логов, ни строк в SQLite.

Отчёт Allure:

```bash
mvn -pl demo-tests allure:report
```

Результаты: `demo-tests/target/allure-results`. На каждый кейс (`UNKNOWN_SKU`, `OUT_OF_STOCK`, `PAYMENT_DECLINED`, `USER_BLOCKED`) — аттач `pod-logs-<code>.json` с событиями **только своего** временного окна. На fail при непустых Events — ещё `pod-events-<code>.json`.

## Аннотация

```java
@PodLogger(collectOnFailOnly = true)   // Allure + SQLite логов только при fail
@PodLogger(collectOnFailOnly = false)  // Allure + SQLite логов после каждого invocation
@PodLogger(
    namespace = "default",
    podLabelSelector = "app=demo-api",
    testRunName = "order-error-demo",
    testSuiteName = "com.example.demotest.OrderErrorIT",
    environmentType = EnvironmentType.LOCAL,  // DEV | ST | FT | LOCAL
    serviceType = "demo-api",
    publishLifecycleEvents = true,
    failFastOnStandDownEvent = true,
    healthCheckUrl = "")
```

Отдельного атрибута `persist` нет. Gate логов:

```text
shouldCollect = !collectOnFailOnly || failed
```

На failed CollectGate всегда true. Events читаются **только** на fail; пустой список Events-аттач не создаёт.

`PodLoggerService` в `afterEach`:

1. passed → `attachLogsIfNeeded` (CollectGate) без Events;
2. failed → `getEvents(window)` → Allure Events если непусто → `probePodAvailability` → логи окна: persist только если под доступна; Allure логов — если под доступна или хвост лога непустой.

Ошибки save/Allure **глотаются** (тест не краснеет из‑за store). Ошибка `startTestRun` в `beforeAll` — **fail-fast** с пошаговым SLF4J-логом.

## Kubernetes Events

Публикуем ровно два lifecycle-Event на прогон (не в stdout поды, не per-test):

| Хук | reason (код) | message |
| --- | --- | --- |
| `beforeAll` после `startTestRun` | `TestRunStarted` | имя прогона, `testRunId`, suite |
| `afterAll` | `TestRunFinished` | имя, `total`, `passed`, `failed` |

Потребление: `getEvents` только на упавшем invocation и внутри `isPodAvailable()`. Stand-down Event (`Maintenance`, `Evicted`, …) → fail-fast оставшихся тестов (`beforeEach` бросает `Stand unavailable: <code>`). Красный HTTP health **без** такого Event прогон не рвёт, но SQLite этого invocation не пишет.

`PodLogDto.relevantEvents` заполняется на fail для Allure JSON; в SQLite поле не хранится.

RBAC (для закрытого контура):

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-logger-events
rules:
  - apiGroups: [""]
    resources: ["pods", "pods/log"]
    verbs: ["get", "list"]
  - apiGroups: [""]
    resources: ["events"]
    verbs: ["get", "list", "create"]
```

Без `create`: Started/Finished не появятся, прогон идёт. Без `list`: Events пустые, fail-fast по Event не сработает; k8s Ready всё ещё работает.

## Persistent Log Store (SQLite)

Реализовано в `junit-pod-logger`. Primary store — **один файл SQLite**. Export JSON в v1 нет.

### Файл БД

По умолчанию: `{user.dir}/target/pod-logger-store.sqlite`.

Переопределение (первый найденный выигрывает):

1. system property `pod.logger.store-path`
2. env `POD_LOGGER_STORE_PATH`
3. `PodLoggerProperties.storePath`

Файлы `*.sqlite*` в [`.gitignore`](.gitignore). CI-artifact БД пока не требуется.

### Test run

`PodLoggerExtension`:

- `beforeAll` — `TestRunStore.startTestRun(...)` (`startedAt`), затем publish `TestRunStarted`, затем probe;
- `afterEach` — collect/save/Allure по gate (+ Events на fail);
- `afterAll` — `collectAndMergeLogsForTestRun` + publish `TestRunFinished` + `finishTestRun` (`finishedAt`).

Прогоны `DEV` / `ST` / `FT` / `LOCAL` живут в **одной** базе; различаются `EnvironmentType` и `testRunId` (имя прогона неуникально).

### Публичный API

- `PodStoreService` — save / get по overload-ам и `LogQuery`, `getLogsForWholeRun`, `deleteOlderThan(days)`
- `TestRunStore` — lifecycle и metadata прогона
- `PodLogDto` — поля лога из поды **плюс** контекст: `testRunId`, `runName`/`testRunName`, `testSuiteName`, `relatedTestClass`, `relatedTestMethod`, `environmentType`, `serviceType`, `fingerprint`, `relevantEvents` (runtime only)

`deleteOlderThan(days)` считает возраст по `test_run.started_at`, удаляет **закрытый** run и его `log_entry`. Открытые run (`finished_at IS NULL`) не трогает.

`GetUniqueLogs` / `GetRelevantLogs` в v1 **не** реализованы (зарезервирован analysis-слой).

## Jenkins

- [`Jenkinsfile`](Jenkinsfile) — package, `docker build`, тесты `demo-tests` (UNSTABLE из‑за ожидаемых fail), Allure.
- Библиотечные тесты store/events в этом пайплайне отдельно не вызываются; для закрытого контура добавьте `mvn -pl junit-pod-logger -am test`.
- Агент с Maven 17 + docker CLI: [`docker/jenkins/Dockerfile`](docker/jenkins/Dockerfile). Агенту нужен доступ к Docker socket (`/var/run/docker.sock`) для Testcontainers.

## Коды ошибок API

| code | message в JSON и в логе поды |
| --- | --- |
| `UNKNOWN_SKU` | Unknown SKU |
| `OUT_OF_STOCK` | Item is out of stock |
| `PAYMENT_DECLINED` | Payment was declined |
| `USER_BLOCKED` | User is blocked |
