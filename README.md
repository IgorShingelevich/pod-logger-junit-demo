# JUnit `@PodLogger` — логи поды в Allure и SQLite

Демонстрация кастомного JUnit 5 extension: на тестовый класс ставится `@PodLogger`. Каждый invocation (в том числе каждый кейс `@ParameterizedTest`) фиксирует UTC-окно, ходит в Kubernetes API через Fabric8 **OpenShiftClient**, парсит JSON-строки лога поды в `PodLogDto` и:

- прикладывает срез в **Allure**;
- сохраняет тот же срез в локальный **SQLite** (`PodStoreService`).

**Один флаг** `collectOnFailOnly` управляет обоими выходами: что ушло в Allure — то же пишется в store.

Это **не** полноценный OpenShift (CRC/OKD). Локальный кластер демо — **K3s в Docker (Testcontainers)**. API `pods/log` тот же.

Спецификация store: [`docs/feature/PersistentLogStore/PersistentLogStorePRD.md`](docs/feature/PersistentLogStore/PersistentLogStorePRD.md).

## Требования

- JDK 17
- Maven 3.9+
- Docker Desktop (только для `OrderErrorIT` в `demo-tests`)

## Модули

| Модуль | Назначение |
| --- | --- |
| `demo-app` | Spring Boot API: `GET /health`, `GET /api/orders/{code}` — 400 + ERROR JSON в stdout |
| `junit-pod-logger` | `@PodLogger`, extension, runtime-сбор, Allure, **SQLite store** (`PodStoreService`, `TestRunStore`) |
| `demo-tests` | K3s, деплой поды, RestAssured, 4 падающих параметризованных кейса |

## Как запускать

Сборка и тесты библиотеки **без Docker** (parser + persistent store + extension harness):

```bash
mvn -DskipTests package
mvn -pl junit-pod-logger -am test
```

Приёмка store: класс [`PersistentLogStoreTest`](junit-pod-logger/src/test/java/com/example/podlogger/store/PersistentLogStoreTest.java) (display name **persistent log store test**) — запись, чтение с фильтрами, логи с упавшего класса через `@PodLogger`.

Полное демо с подой (нужен Docker Desktop). Сначала пакет `demo-app` (jar для Dockerfile), затем тесты:

```bash
mvn -pl demo-app -am package -DskipTests
docker build -t demo-api:local demo-app
mvn -pl demo-tests -am test
```

`demo-tests` сам соберёт образ `demo-api:local`, если jar уже есть, поднимет K3s, импортирует образ, применит [`k8s/demo-api.yaml`](k8s/demo-api.yaml), сделает port-forward и бьёт в API с хоста.

Четыре invocation **ожидаемо красные**: после проверки HTTP 400 тест вызывает `Assertions.fail(...)`, иначе при `collectOnFailOnly=true` не будет ни Allure-аттача, ни строк в SQLite.

Отчёт Allure:

```bash
mvn -pl demo-tests allure:report
```

Результаты: `demo-tests/target/allure-results`. На каждый кейс (`UNKNOWN_SKU`, `OUT_OF_STOCK`, `PAYMENT_DECLINED`, `USER_BLOCKED`) — аттач `pod-logs-<code>.json` с событиями **только своего** временного окна.

## Аннотация

```java
@PodLogger(collectOnFailOnly = true)   // Allure + SQLite только при fail
@PodLogger(collectOnFailOnly = false)  // Allure + SQLite после каждого invocation
@PodLogger(
    namespace = "default",
    podLabelSelector = "app=demo-api",
    testRunName = "order-error-demo",
    testSuiteName = "com.example.demotest.OrderErrorIT",
    environmentType = EnvironmentType.LOCAL,  // DEV | ST | FT | LOCAL
    serviceType = "demo-api")
```

Отдельного атрибута `persist` нет. Gate:

```text
shouldCollect = !collectOnFailOnly || failed
```

`PodLoggerService` в `afterEach` вызывает `List<PodLogDto> log = openshiftClient.getLog()`, фильтрует по окну кейса (±2 с), затем:

1. `podStoreService.saveLogs(testRunId, window)` — если `shouldCollect`;
2. Allure attachment с тем же `window`.

Ошибки save/Allure **глотаются** (тест не краснеет из‑за store). Ошибка `startTestRun` в `beforeAll` — **fail-fast** с пошаговым SLF4J-логом.

## Persistent Log Store (SQLite)

Реализовано в `junit-pod-logger`. Primary store — **один файл SQLite**, не дерево папок и не H2. Export JSON в v1 нет.

### Файл БД

По умолчанию: `{user.dir}/target/pod-logger-store.sqlite`.

Переопределение (первый найденный выигрывает):

1. system property `pod.logger.store-path`
2. env `POD_LOGGER_STORE_PATH`
3. `PodLoggerProperties.storePath`

Файлы `*.sqlite*` в [`.gitignore`](.gitignore). CI-artifact БД пока не требуется.

### Test run

`PodLoggerExtension`:

- `beforeAll` — `TestRunStore.startTestRun(...)` (`startedAt`);
- `afterEach` — collect/save/Allure по gate;
- `afterAll` — `collectAndMergeLogsForTestRun` + `finishTestRun` (`finishedAt`).

Прогоны `DEV` / `ST` / `FT` / `LOCAL` живут в **одной** базе; различаются `EnvironmentType` и `testRunId` (имя прогона неуникально).

### Публичный API

- `PodStoreService` — save / get по overload-ам и `LogQuery`, `getLogsForWholeRun`, `deleteOlderThan(days)`
- `TestRunStore` — lifecycle и metadata прогона
- `PodLogDto` — поля лога из поды **плюс** контекст: `testRunId`, `runName`/`testRunName`, `testSuiteName`, `relatedTestClass`, `relatedTestMethod`, `environmentType`, `serviceType`, `fingerprint`, …

`deleteOlderThan(days)` считает возраст по `test_run.started_at`, удаляет **закрытый** run и его `log_entry`. Открытые run (`finished_at IS NULL`) не трогает.

`GetUniqueLogs` / `GetRelevantLogs` в v1 **не** реализованы (зарезервирован analysis-слой).

## Jenkins

- [`Jenkinsfile`](Jenkinsfile) — package, `docker build`, тесты (UNSTABLE из‑за ожидаемых fail), Allure.
- Агент с Maven 17 + docker CLI: [`docker/jenkins/Dockerfile`](docker/jenkins/Dockerfile). Агенту нужен доступ к Docker socket (`/var/run/docker.sock`) для Testcontainers.

## Коды ошибок API

| code | message в JSON и в логе поды |
| --- | --- |
| `UNKNOWN_SKU` | Unknown SKU |
| `OUT_OF_STOCK` | Item is out of stock |
| `PAYMENT_DECLINED` | Payment was declined |
| `USER_BLOCKED` | User is blocked |
