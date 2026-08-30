# `junit-pod-logger`

Переносимая библиотека `@PodLogger`. Docker не нужен. `demo-app` / `demo-tests` отсюда не импортировать в закрытый контур как зависимость библиотеки.

Контракт: [`docs/PodLoggerJunitDemoPRD.md`](../docs/PodLoggerJunitDemoPRD.md), store [`PersistentLogStoreStory.md`](../docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md), Events [`OpenShiftEventHandlingStory.md`](../docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md).  
Каталог тестов: [`docs/PodLoggerJunitDemoTest.md`](../docs/PodLoggerJunitDemoTest.md).  
Команды: [`docs/PodLoggerJunitDemoCommands.md`](../docs/PodLoggerJunitDemoCommands.md) → Test Commands.

## Что переносить

Модуль целиком: аннотация, extension, `PodLoggerService`, client, parser, store, allure. JavaDoc пакета `com.example.podlogger`.

Аннотация только на **класс** (`ElementType.TYPE`). Без неё `PodLoggerExtension` бросает `IllegalStateException`.

## Карта пакетов

| Пакет | Роль |
| --- | --- |
| `com.example.podlogger` | `@PodLogger`, `PodLoggerExtension`, `PodLoggerService`, `CollectGate`, properties |
| `...client` | Fabric8 `OpenShiftClient` wrapper: logs, events, health |
| `...parser` | `JsonLogParser` — только строки, начинающиеся с `{` |
| `...store` / `...store.sqlite` | SQLite, `LogQuery`, fingerprint, retention |
| `...allure` | `pod-logs-*`, `pod-events-*` |
| `...event` | `StandDownEventMatcher`, `PodEventReasons` |

Тесты модуля (без кластера): `OpenshiftClientParseTest`, `OpenshiftEventHandlingTest` + harness, `PersistentLogStoreTest` + harness.

## Зависимости, которые остаются с библиотекой

Fabric8 `openshift-client` 6.13.4, JUnit 5, Spring Test (`@ExtendWith(SpringExtension)` на аннотации), SQLite, Allure JUnit5, Jackson JavaTime.

Окно invocation: ±2s (`PodLoggerService.SKEW_SECONDS`). Events на passed не читаются. Пустой Events-аттач запрещён.
