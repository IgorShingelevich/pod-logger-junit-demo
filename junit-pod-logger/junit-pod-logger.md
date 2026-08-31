# `junit-pod-logger`

Переносимая библиотека `@PodLogger`. Docker не нужен. `demo-app` / `demo-tests` отсюда не импортировать в закрытый контур как зависимость библиотеки.

Это **карта модуля**, не второй PRD и не каталог тестов. Норматив поведения остаётся в уставах:

- проект: [`docs/PodLoggerJunitDemoPRD.md`](../docs/PodLoggerJunitDemoPRD.md)
- store: [`PersistentLogStoreStory.md`](../docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md)
- Events / health / fail-fast: [`OpenShiftEventHandlingStory.md`](../docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md)

Каталог тестов: [`docs/PodLoggerJunitDemoTest.md`](../docs/PodLoggerJunitDemoTest.md).  
Команды: [`docs/PodLoggerJunitDemoCommands.md`](../docs/PodLoggerJunitDemoCommands.md) → Test Commands.

Частные MD лежат рядом с пакетами и описывают **дерево этого модуля**. Они не копируют SQL-схему, полный контракт хуков и критерии приёмки.

## Частные карты пакетов

| MD | Пакет | Скоп этого файла |
| --- | --- | --- |
| [`podLogger.md`](src/main/java/com/example/podlogger/podLogger.md) | `com.example.podlogger` | аннотация, extension, runtime, CollectGate, properties, Spring config |
| [`openshiftClient.md`](src/main/java/com/example/podlogger/client/openshiftClient.md) | `...client` | Fabric8 wrapper, DTO логов/Events, health probe |
| [`logParser.md`](src/main/java/com/example/podlogger/parser/logParser.md) | `...parser` | разбор stdout `pods/log` в `PodLogDto` |
| [`store.md`](src/main/java/com/example/podlogger/store/store.md) | `...store` (+ `...store.dto`) | application API persist, путь к файлу, fingerprint, DTO query |
| [`repository.md`](src/main/java/com/example/podlogger/store/repository/repository.md) | `...store.repository` | интерфейсы `log_entry` / `test_run`, без SQL наружу |
| [`sqlLite.md`](src/main/java/com/example/podlogger/store/sqlite/sqlLite.md) | `...store.sqlite` | DataSource, WAL, DDL, JDBC-реализации |
| [`allure.md`](src/main/java/com/example/podlogger/allure/allure.md) | `...allure` | JSON-аттачи `pod-logs-*` / `pod-events-*` |
| [`event.md`](src/main/java/com/example/podlogger/event/event.md) | `...event` | `StandDownEventMatcher`, `PodEventReasons` — чистые функции над DTO |

## Что переносить

Модуль целиком: аннотация, extension, service, client, parser, store (включая `store.dto` и `store.sqlite`), allure, event. JavaDoc пакета `com.example.podlogger`. Spring-конфиг [`PodLoggerConfiguration`](src/main/java/com/example/podlogger/podLogger.md).

Аннотация только на **класс** (`ElementType.TYPE`). Без неё `PodLoggerExtension` бросает `IllegalStateException`.

Потребитель обязан дать fabric8 `OpenShiftClient` в Spring-контексте. Без него bean обёртки `OpenshiftClient` не создаётся (`@ConditionalOnBean`).

## Карта пакетов

| Пакет | Роль | Карта |
| --- | --- | --- |
| `com.example.podlogger` | `@PodLogger`, `PodLoggerExtension`, `PodLoggerService`, `CollectGate`, `PodLoggerProperties`, `PodLoggerConfiguration` | [`podLogger.md`](src/main/java/com/example/podlogger/podLogger.md) |
| `...client` | Fabric8 `OpenShiftClient` wrapper: logs, events, health | [`openshiftClient.md`](src/main/java/com/example/podlogger/client/openshiftClient.md) |
| `...parser` | `JsonLogParser` — только строки, начинающиеся с `{` | [`logParser.md`](src/main/java/com/example/podlogger/parser/logParser.md) |
| `...store` / `...store.dto` | query API, fingerprint, путь к файлу, `LogQuery` | [`store.md`](src/main/java/com/example/podlogger/store/store.md) |
| `...store.repository` | порты persist, без SQL | [`repository.md`](src/main/java/com/example/podlogger/store/repository/repository.md) |
| `...store.sqlite` | SQLite schema, JDBC | [`sqlLite.md`](src/main/java/com/example/podlogger/store/sqlite/sqlLite.md) |
| `...allure` | `pod-logs-*`, `pod-events-*` | [`allure.md`](src/main/java/com/example/podlogger/allure/allure.md) |
| `...event` | `StandDownEventMatcher`, `PodEventReasons` | [`event.md`](src/main/java/com/example/podlogger/event/event.md) |

Тесты модуля (без кластера): `OpenshiftClientParseTest`, `OpenshiftEventHandlingTest` + harness, `PersistentLogStoreTest` + harness. Карточки — в каталоге тестов, не здесь.

## Зависимости, которые остаются с библиотекой

| Артефакт | Зачем |
| --- | --- |
| Fabric8 `openshift-client` **6.13.4** | API поды и core/v1 Events |
| JUnit 5 (`junit-jupiter-api`) | extension |
| Spring `spring-context` + `spring-test` | beans; `@ExtendWith(SpringExtension)` на аннотации |
| `spring-boot-autoconfigure` (**optional**) | `@ConditionalOnBean` / `@ConditionalOnMissingBean` в `PodLoggerConfiguration` |
| `sqlite-jdbc` **3.47.1.0** | файл store |
| Allure JUnit5 | аттачи |
| Jackson databind + `jackson-datatype-jsr310` | parse JSON-логов и Allure JSON |

Lombok и SLF4J — compile/runtime helpers, не часть публичного контракта.

Окно invocation: ±2s (`PodLoggerService.SKEW_SECONDS`). Events на passed не читаются. Пустой Events-аттач запрещён. Один `collectOnFailOnly` на оба выхода логов.
