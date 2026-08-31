# Пакет `com.example.podlogger.client`

Gateway к Kubernetes/OpenShift API. Классы пакета **не** зависят от SQLite, JUnit и Allure.

Модуль: [`junit-pod-logger.md`](../../../../../../../junit-pod-logger.md).  
Контракт API и probe: [`OpenShiftEventHandlingStory.md`](../../../../../../../../docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) §6.1–§6.3.  
Оркестрация вызовов: [`podLogger.md`](../podLogger.md). Парсер dump: [`logParser.md`](../parser/logParser.md).

## Классы

| Класс | Роль |
| --- | --- |
| `OpenshiftClient` | конкретный класс (не Java-interface): resolve, logs, events, publish, probe |
| `PodLogDto` | JSON-строка лога + контекст прогона + runtime `relevantEvents` |
| `PodEventDto` | Kubernetes Event; `code` = `reason` |
| `PodAvailability` | итог probe: `available`, `standDownEventPresent`, `healthPassed`, `code` |
| `PodEventMapper` | fabric8 Event → `PodEventDto` (timestamp: lastTimestamp / eventTime / creationTimestamp) |

Тесты наследуют stub `OpenshiftClient`; живой кластер этому пакету не нужен.

## Публичные методы `OpenshiftClient`

```text
Pod resolveTargetPod()
List<PodLogDto> getLog()                          // сигнатуру не менять
List<PodEventDto> getEvents()
List<PodEventDto> getEvents(from, to)             // фильтр на клиенте
PodEventDto publishPodEvent(type, reason, message)
boolean isPodAvailable()                          // probePodAvailability().available()
PodAvailability probePodAvailability()
```

Алиас `listPodEvents` в публичном API библиотеки не оставляем — имя метода `getEvents`.

Fabric8 **6.13.4**: только `client.v1().events()`, не `client.events()` (`events.k8s.io`). `involvedObject.kind=Pod`. Имена Event — `generateName("pod-logger-")`. Publish — best-effort: ошибка create не бросается в extension. List при ошибке = «ивентов нет», не лже-stand-down.

`getLog()` зовёт `resolveTargetPod()` и [`LogParser`](../parser/logParser.md); сырой dump в Allure/SQLite этот пакет не кладёт.

## DTO (границы полей)

- Parser заполняет из stdout в первую очередь `timestamp`, `level`, `message`, `logger`. Поля прогона и `fingerprint` ставит service/store **после** parse.
- `podName` парсер из JSON приложения не ставит, если поля не было в строке.
- `relevantEvents` заполняет только `PodLoggerService` на **failed** invocation. В SQLite колонки нет. `FingerprintUtil` поле игнорирует.
- `PodEventDto.code` и `reason` в v1 равны.

## Probe (этот пакет считает, оркестратор решает)

Порядок short-circuit — Event story §6.3: stand-down Events → k8s Ready → HTTP `healthCheckUrl` (если непустой).  
`available == true` только если нет stand-down **и** health зелёный. Fail-fast триггерит только `standDownEventPresent`, не красный health. Ошибка probe в `PodLoggerService` глотается в `PodAvailability.up()` (не лже-stand-down).

HTTP health — JDK `HttpClient` внутри client. Matcher stand-down — [`event.md`](../event/event.md), не этот пакет.

## Приёмка этого пакета

| Тест | Что закрывает |
| --- | --- |
| `OpenshiftClientParseTest` | parse JSON dump (через parser, не живой `getLog`) |
| `OpenshiftEventHandlingTest` nested «Publish/get с кодами» | `publishPodEvent` → `getEvents`, поле `code` |

Карточки: [`PodLoggerJunitDemoTest.md`](../../../../../../../../docs/PodLoggerJunitDemoTest.md) §1–§2. RBAC — [`k8s.md`](../../../../../../../../k8s/k8s.md), не этот файл.
