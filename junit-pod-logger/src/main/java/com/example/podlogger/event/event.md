# Пакет `com.example.podlogger.event`

Чистые функции над уже полученными `PodEventDto`: какие Events — stand-down, какие коды библиотека публикует сама. Пакет **не** ходит в Kubernetes, не пишет в Allure и не трогает SQLite.

Модуль: [`junit-pod-logger.md`](../../../../../../../junit-pod-logger.md).  
Семантика fail-fast и probe: [`OpenShiftEventHandlingStory.md`](../../../../../../../../docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) §6.3, §6.8.  
Кто достаёт Events из API: [`openshiftClient.md`](../client/openshiftClient.md). Кто оркестрирует хуки: [`podLogger.md`](../podLogger.md).

Формат Event здесь **стабильнее**, чем stdout логов, потому что библиотека опирается на fabric8 core/v1 `Event`. Но различия между кластерами и версиями API всё равно возможны: какие timestamp-поля заполнены, какие `reason` публикует платформа, есть ли `eventTime`, насколько информативен `message`.

## Классы

| Класс | Роль |
| --- | --- |
| `PodEventReasons` | константы lifecycle/health и дефолтные allowlist кодов и message-паттернов |
| `StandDownEventMatcher` | `match(events[, codes, patterns])` и `matchesText` (ещё для тела HTTP health) |

Экземпляры не создаются. Пустой список кодов/паттернов на входе matcher **заменяется дефолтом библиотеки**, не трактуется как «матчить ничего». То же правило на аннотации — [`podLogger.md`](../podLogger.md).

## Участники event-пайплайна

| Класс | Метод | Шаг |
| --- | --- | --- |
| `OpenshiftClient` | `publishPodEvent(...)` | собирает и отправляет core/v1 Event на целевую поду |
| `OpenshiftClient` | `getEvents()` / `getEvents(from,to)` | получает Events и, при необходимости, режет их по окну |
| `OpenshiftClient` | `listEventsForPod(Pod)` | читает raw fabric8 Events по `involvedObject=Pod` |
| `PodEventMapper` | `toDto(Event, ...)` | проецирует raw Event в `PodEventDto` |
| `PodEventMapper` | `timestampOf` / `parseTime` | выбирает и парсит timestamp из разных полей Event |
| `StandDownEventMatcher` | `match(...)` | классифицирует DTO как stand-down или не stand-down |

Пайплайн:

1. `publishPodEvent(...)` формирует `EventBuilder`, ставит `reason`, `message`, `type`, `generateName`, `involvedObject`.
2. `listEventsForPod(...)` читает raw Events через `client.v1().events().withInvolvedObject(...)`.
3. `PodEventMapper.toDto(...)` забирает библиотечный минимум: `code`, `reason`, `type`, `message`, `timestamp`, `count`, `podName`, `namespace`, `uid`.
4. `PodEventMapper.timestampOf(...)` идёт по fallback-цепочке: `lastTimestamp` -> `eventTime` -> `creationTimestamp`.
5. `StandDownEventMatcher.match(...)` сравнивает `code`/`reason` и `message` с allowlist/паттернами.
6. `OpenshiftClient.probePodAvailability()` уже решает, влияет ли найденный Event на fail-fast и health.

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

## Что можно переносить без правки

Если целевой кластер даёт обычные core/v1 Events с понятным `reason`, `message` и хотя бы одним парсибельным timestamp-полем, текущий `PodEventDto`, `PodEventMapper` и matcher можно оставить без изменений.

Если у вас меняется источник (`events.k8s.io`, кастомный адаптер, иная семантика `reason`, пустой `lastTimestamp`, другой формат времени), придётся адаптировать mapping и, возможно, rules matcher. Сам DTO менять нужно только если реально не хватает библиотечного минимума.

## Риски миграции

| Риск | Что произойдёт сейчас | Что учитывать при переносе |
| --- | --- | --- |
| Заполнен не `lastTimestamp`, а только `eventTime` или `creationTimestamp` | mapper попробует fallback по очереди | это уже покрыто, но проверить реальный кластер обязательно |
| Timestamp в неожиданном формате | `parseTime` вернёт `null`, debug покажет raw value | нужен custom parse или другой источник времени |
| Новый platform `reason`, не входящий в allowlist | Event попадёт в DTO, но не станет stand-down | расширить `standDownEventCodes` или default allowlist |
| Полезный сигнал только в `message`, а не в `reason` | matcher сможет поймать только по contains-pattern | проверить реальные maintenance-фразы в вашем кластере |
| Шумовые platform Events приходят рядом с lifecycle Events библиотеки | DTO создадутся, matcher отфильтрует только часть | сверить allowlist и не переусердствовать с broad patterns |
| Кластер публикует `events.k8s.io` semantics, а не core/v1 | текущий код использует `client.v1().events()` | нужен отдельный adapter, не только правка MD |
| Отсутствует permission `list` или `create` | библиотека логирует error/warn и работает best-effort | заранее проверить RBAC и degraded behavior |
| Message слишком длинный или содержит много line breaks | debug пишет только сокращённый snippet | full raw payload смотреть в cluster tooling, не в app log |

### Миграционный чек-лист

1. Проверить, какие именно Events даёт ваш кластер: core/v1, `events.k8s.io`, adapter поверх них или смесь.
2. Снять реальные примеры platform Events для вашей поды: maintenance, restart, eviction, readiness, scheduling.
3. Сверить, какие timestamp-поля реально заполнены: `lastTimestamp`, `eventTime`, `creationTimestamp`.
4. Посмотреть, что находится в `reason`, а что только в `message`.
5. Проверить, не конфликтуют ли platform reasons с lifecycle-кодами библиотеки `TestRunStarted` / `TestRunFinished`.
6. Включить `DEBUG` для `com.example.podlogger.client` и `com.example.podlogger.client.PodEventMapper`, чтобы увидеть publish/list/map pipeline.
7. Проверить RBAC отдельно: `get/list` для `events`, `create` для lifecycle publish.
8. Если ваш кластер использует другие `reason` для stand-down, сначала пробуйте конфигурацию `standDownEventCodes` / `standDownMessagePatterns`, а не переписывание matcher.
9. Если timestamp не парсится или API не core/v1, адаптировать `PodEventMapper`/client, а не `event.md`.
10. После адаптации проверить два сценария: publish lifecycle Event и fail-path с list/filter/match.

### Профит для агента в новом контуре

- Этот раздел отделяет миграционные риски платформенных Events от рисков stdout-парсинга: если DTO логов уже исправлены, fail-fast всё равно может не сработать из-за других `reason`, RBAC или API surface.
- `DEBUG` на `com.example.podlogger.client` и `com.example.podlogger.client.PodEventMapper` покажет шаги `Listing pod events`, `Mapped Event -> DTO`, `Availability probe received ... event DTO(s)`, поэтому агент быстро увидит, проблема в list/publish, mapping timestamp или allowlist stand-down.
- Для банковского контура это экономит время: сначала можно адаптировать `standDownEventCodes` / `standDownMessagePatterns`, и только потом решать, нужен ли новый adapter поверх `events.k8s.io`.

## Границы

Publish/list Events, имена аттачей, persist gate и JUnit-хуки — не этот пакет. `code` = `reason` в v1 — контракт DTO в client; здесь только константы и matcher.

## Приёмка этого пакета

`OpenshiftEventHandlingTest` nested `StandDownEventMatcher`:

- G4 — `Maintenance` / `StandUnavailable` / `Evicted` матчятся;
- G3 — `TestRunStarted` / `TestRunFinished` / `Pulled` не stand-down;
- pattern `maintenance` в message при чужом коде → match.

Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../docs/PodLoggerJunitDemoTest.md) §2.6. Сценарии fail-fast 4–5 проверяют оркестрацию, не этот пакет в изоляции.
