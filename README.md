# JUnit `@PodLogger` — логи поды K3s в Allure

Демонстрация кастомного JUnit 5 extension: на тестовый класс ставится `@PodLogger(collectOnFailOnly = true)`, каждый invocation (в том числе каждый кейс `@ParameterizedTest`) фиксирует UTC-время начала/конца, ходит в Kubernetes API через Fabric8 **OpenShiftClient**, парсит JSON-строки лога поды в `PodLogDto` и прикладывает выборку в Allure.

Это **не** полноценный OpenShift (CRC/OKD). Локальный кластер — **K3s в Docker (Testcontainers)**. API `pods/log` тот же.

## Требования

- JDK 17
- Maven 3.9+
- Docker Desktop (для `OrderErrorIT`)

## Модули

| Модуль | Назначение |
| --- | --- |
| `demo-app` | Spring Boot API: `GET /health`, `GET /api/orders/{code}` — 400 + ERROR JSON в stdout |
| `junit-pod-logger` | `@PodLogger`, `PodLoggerExtension`, `PodLoggerService`, `OpenshiftClient` |
| `demo-tests` | K3s, деплой поды, RestAssured, 4 падающих параметризованных кейса |

## Как запускать

Сборка без Docker:

```bash
mvn -DskipTests package
mvn -pl junit-pod-logger test
```

Полное демо (нужен Docker Desktop). Сначала пакет `demo-app` (jar для Dockerfile), затем тесты:

```bash
mvn -pl demo-app -am package -DskipTests
docker build -t demo-api:local demo-app
mvn -pl demo-tests -am test
```

`demo-tests` сам соберёт образ `demo-api:local`, если jar уже есть, поднимет K3s, импортирует образ, применит [`k8s/demo-api.yaml`](k8s/demo-api.yaml), сделает port-forward и бьёт в API с хоста.

Четыре invocation **ожидаемо красные**: после проверки HTTP 400 тест вызывает `Assertions.fail(...)`, иначе при `collectOnFailOnly=true` Allure-аттача не будет.

Отчёт Allure:

```bash
mvn -pl demo-tests allure:report
```

Результаты: `demo-tests/target/allure-results`. На каждый кейс (`UNKNOWN_SKU`, `OUT_OF_STOCK`, `PAYMENT_DECLINED`, `USER_BLOCKED`) — аттач `pod-logs-<code>.json` с событиями **только своего** временного окна.

## Аннотация

```java
@PodLogger(collectOnFailOnly = true)          // только упавшие invocation
@PodLogger(collectOnFailOnly = false)         // логи после каждого invocation
@PodLogger(namespace = "default", podLabelSelector = "app=demo-api")
```

`PodLoggerService` в `afterEach` вызывает `List<PodLogDto> log = openshiftClient.getLog()`, фильтрует по `LocalDateTime` начала/конца кейса (±2 с) и делает `Allure.addAttachment`.

## Jenkins

- [`Jenkinsfile`](Jenkinsfile) — package, `docker build`, тесты (UNSTABLE из‑за ожидаемых fail), Allure.
- Агент с Maven 17 + docker CLI: [`docker/jenkins/Dockerfile`](docker/jenkins/Dockerfile). Агенту нужен доступ к Docker socket (`/var/run/docker.sock`) для Testcontainers.

## Planned Persistent Store

Текущий код проекта уже умеет собирать runtime pod logs и прикладывать их к Allure на уровне каждого invocation через `@PodLogger`.

Следующий архитектурный этап описан в [`docs/prd/podLoggerJunitDemoPRD.md`](docs/prd/podLoggerJunitDemoPRD.md):

- отдельный `PodStoreService` для persistent pod logs;
- `TestRunStore` для lifecycle `BeforeAll -> AfterAll`;
- `SQLite` как локальное хранилище между прогонами;
- historical queries по `testRunId`, времени, `testSuiteName`, `EnvironmentType` и `serviceType`;
- разделение между per-test Allure attachments и persistent log retrieval.

Пока этот persistent слой описан как целевая архитектура в PRD и не должен восприниматься как уже реализованная runtime-функция демо.

## Коды ошибок API

| code | message в JSON и в логе поды |
| --- | --- |
| `UNKNOWN_SKU` | Unknown SKU |
| `OUT_OF_STOCK` | Item is out of stock |
| `PAYMENT_DECLINED` | Payment was declined |
| `USER_BLOCKED` | User is blocked |
