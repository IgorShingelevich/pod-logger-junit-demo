# Пакет `com.example.podlogger.event`

Чистые функции над уже полученными `PodEventDto`: какие Events — stand-down, какие коды библиотека публикует сама. Пакет **не** ходит в Kubernetes, не пишет в Allure и не трогает SQLite.

Модуль: [`junit-pod-logger.md`](../../../../../../../junit-pod-logger.md).  
Семантика fail-fast и probe: [`OpenShiftEventHandlingStory.md`](../../../../../../../../docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) §6.3, §6.8.  
Кто достаёт Events из API: [`openshiftClient.md`](../client/openshiftClient.md). Кто оркестрирует хуки: [`podLogger.md`](../podLogger.md).

## Классы

| Класс | Роль |
| --- | --- |
| `PodEventReasons` | константы lifecycle/health и дефолтные allowlist кодов и message-паттернов |
| `StandDownEventMatcher` | `match(events[, codes, patterns])` и `matchesText` (ещё для тела HTTP health) |

Экземпляры не создаются. Пустой список кодов/паттернов на входе matcher **заменяется дефолтом библиотеки**, не трактуется как «матчить ничего». То же правило на аннотации — [`podLogger.md`](../podLogger.md).

## Константы (этот пакет)

Норматив «когда abort прогона» — Event story. Здесь — поверхность кода.

| Константа | Значение | Matcher |
| --- | --- | --- |
| `TEST_RUN_STARTED` / `TEST_RUN_FINISHED` | lifecycle, которые публикуем мы | всегда **исключаются** (`LIFECYCLE_CODES`) |
| `POD_NOT_READY` / `HEALTH_CHECK_FAILED` | коды health в `PodAvailability`, не k8s Event.reason | не allowlist stand-down |
| `DEFAULT_STAND_DOWN_CODES` | `StandUnavailable`, `Maintenance`, `Evicted`, `Killing`, `FailedScheduling`, `FailedMount`, `NetworkNotReady`, `Unhealthy`, `NodeNotReady`, `TaintManagerEviction`, `DisruptionTarget` | match по `code`/`reason`, case-insensitive |
| `DEFAULT_MESSAGE_PATTERNS` | `maintenance`, `unavailable`, `shutting down`, `drain`, `preempt`, `eviction` | contains в reason/code **или** message |

Kube-шум `Pulled` / `Created` / `Started` / `Scheduled` в allowlist **не** входит. Красный health без matching Event этот пакет fail-fast не объявляет — только `standDownEventPresent` после match.

`matchesText` — case-insensitive contains; пустой/blank текст → `false`. Client зовёт его и для тела HTTP health.

## Границы

Publish/list Events, имена аттачей, persist gate — не этот пакет. `code` = `reason` в v1 — контракт DTO в client, не дублируется здесь.

## Приёмка этого пакета

`OpenshiftEventHandlingTest` nested `StandDownEventMatcher`:

- G4 — `Maintenance` / `StandUnavailable` / `Evicted` матчятся;
- G3 — `TestRunStarted` / `TestRunFinished` / `Pulled` не stand-down;
- pattern `maintenance` в message при чужом коде → match.

Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../docs/PodLoggerJunitDemoTest.md) §2.6. Сценарии fail-fast 4–5 проверяют оркестрацию, не этот пакет в изоляции.
