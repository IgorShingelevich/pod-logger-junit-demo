# Пакет `com.example.podlogger`

Корневой пакет библиотеки: публичная аннотация, JUnit lifecycle, оркестрация runtime, Spring-конфиг. SQL, parse dump, Fabric8 и формат Allure-аттачей сюда не входят.

Модуль: [`junit-pod-logger.md`](../../../../../../junit-pod-logger.md).  
Устав: [`PodLoggerJunitDemoPRD.md`](../../../../../../../docs/PodLoggerJunitDemoPRD.md) §3–§6.  
Хуки Events: [`OpenShiftEventHandlingStory.md`](../../../../../../../docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) §4.  
Store lifecycle: [`PersistentLogStoreStory.md`](../../../../../../../docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md) §9.

Соседние карты: [`client`](client/openshiftClient.md), [`parser`](parser/logParser.md), [`store`](store/store.md), [`allure`](allure/allure.md), [`event`](event/event.md).

## Классы

| Класс | Делает | Не делает |
| --- | --- | --- |
| `PodLogger` | мета-аннотация класса: Spring Test + `PodLoggerExtension` | SQL, HTTP к кластеру |
| `PodLoggerExtension` | хуки BeforeAll/Each, AfterAll/Each, TestWatcher, счётчики, `STAND_UNAVAILABLE` | parse, Fabric8, schema |
| `PodLoggerService` | окно ±2s, persist gate, Allure, Events, merge прогона | DDL, query builder |
| `CollectGate` | `shouldCollect = !collectOnFailOnly \|\| failed` | Events (они только на fail отдельно) |
| `PodLoggerProperties` | зеркало аннотации + `storePath`, `queryLimit`, `retentionDays`, `attachRunSummaryToAllure` | SQL, Allure, Kubernetes |
| `PodLoggerConfiguration` | component-scan, `ObjectMapper`, `OpenshiftClient`, `AllureSink`, SQLite `DataSource` | бизнес-логика хуков |

`GetUniqueLogs` / `GetRelevantLogs` в этом пакете **нет** (вне v1).

## Аннотация (этот пакет)

Только `@Target(TYPE)`. Полный пример атрибутов — PRD §6, не дублируется здесь.

| Атрибут | Default | Смысл в этом пакете |
| --- | --- | --- |
| `collectOnFailOnly` | `true` | один gate на Allure+SQLite логов |
| `namespace` | `default` | namespace поды |
| `podLabelSelector` | `app=demo-api` | один `key=value` |
| `testRunName` | `""` → `<SimpleClassName>-<yyyyMMddHHmmss>` | имя неуникально; уникален `testRunId` |
| `testSuiteName` | `""` → FQCN класса | |
| `environmentType` | `LOCAL` | `DEV`/`ST`/`FT`/`LOCAL`, одна SQLite |
| `serviceType` | `""` → value селектора после `=` | |
| `publishLifecycleEvents` | `true` | `TestRunStarted` / `TestRunFinished` |
| `failFastOnStandDownEvent` | `true` | abort следующих тестов класса |
| `healthCheckUrl` | `""` | пусто = только k8s Ready |
| `standDownEventCodes` | `{}` | пустой массив = дефолт библиотеки, не «матчить ничего» |
| `standDownMessagePatterns` | `{}` | то же |

Пустые stand-down массивы подменяет `PodLoggerProperties.effectiveStandDown*()` через `PodEventReasons`.

Поля, которых **нет** на аннотации (только properties): `storePath`, `queryLimit` (10_000), `retentionDays` (30), `attachRunSummaryToAllure` (`false` → run-level `pod-logs-run-*` не кладётся).

## Оркестрация (указатель)

Порядок хуков и таблица persist vs Allure — в Event story §4 и §6.7, не здесь.

Этот пакет вызывает:

1. `TestRunStore.startTestRun` → `publishTestRunStarted` → `probePodAvailability` (`beforeAll`);
2. CollectGate логов на passed; на failed — Events, probe, persist только если под доступна (`afterEach`);
3. merge + `publishTestRunFinished` + `finishTestRun` (`afterAll`).

Ошибки save/Allure/collect **не** краснят текущий тест. Ошибка `startTestRun` в `beforeAll` — fail-fast. Из `afterEach` stand-down **не** бросается (иначе затрётся исходный assertion); abort — в следующем `beforeEach`.

Matcher и коды: [`event.md`](event/event.md). Контракт fail-fast — Event story §6.3 / §6.8.

## Spring

`PodLoggerConfiguration` ждёт fabric8 `OpenShiftClient` от потребителя. SQLite `DataSource` создаётся только при `@ConditionalOnMissingBean(DataSource)`. `spring-boot-autoconfigure` в POM **optional**.

## Приёмка этого пакета

Не отдельный тест-класс: поведение хуков закрывают `PersistentLogStoreTest` и `OpenshiftEventHandlingTest` (сценарии 1–5) в [`PodLoggerJunitDemoTest.md`](../../../../../../../docs/PodLoggerJunitDemoTest.md). Кластер не нужен.
