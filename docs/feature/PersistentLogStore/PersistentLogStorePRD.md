# PRD: Persistent Log Store для `@PodLogger`

**Статус:** целевая архитектура, слой ещё не реализован в коде.  
**Модуль внедрения:** `junit-pod-logger`  
**Базовый persistent storage v1:** SQLite  
**Источник требований:** [`initPrompt.md.md`](initPrompt.md.md) (вопрос + ответ внешней модели) и фактический код demo-проекта.

Этот документ — контракт на разработку. Он переводит generic-имена из внешнего ответа (`LoggerExtension`, `LogStoreService`, `LogDto`) на существующие классы проекта и закрывает неоднозначности исходного вопроса.

---

## 1. Цель

Расширить библиотеку `junit-pod-logger` persistent-слоем, который:

1. сохраняет распарсенные pod logs текущего и прошлых локальных прогонов;
2. фиксирует контекст test run: имя прогона, suite, стенд, окно `BeforeAll → AfterAll`;
3. отдаёт historical logs по фильтрам (время, run, suite, environment, service type);
4. позволяет получить логи и за один invocation, и за весь прогон;
5. умеет **совместить** already-stored logs и свежий runtime dump поды в одном orchestration-методе;
6. не ломает текущие Allure-аттачи на `afterEach`;
7. оставляет точку расширения для будущих `GetUniqueLogs` / `GetRelevantLogs`.

Persistent store **не заменяет** runtime-сбор. Он добавляет второй выход: «что произошло исторически», рядом с Allure («что показать в отчёте этого прогона»).

---

## 2. Исходный вопрос и принятые решения

### 2.1 Что просил исходный prompt

- Реализовать **Pod Store Service** и связать его с `PodLoggerExtension`, `PodLoggerService` и соседними классами.
- Публичные действия store:
  1. сохранить список логов в БД;
  2. получить список логов по разным фильтрам (несколько overload-методов).
- Отдельные классы **тестового прогона**: в `BeforeAll` / `AfterAll` фиксировать `startedAt` / `finishedAt`, даже если в классе 1 тест или 100.
- Метод, который берёт логи **из persistent-слоя и из runtime поды** и дальше ими оперирует. Вопрос: класть его в `PodLoggerService` или в store.
- Расширить DTO полями: `testRunName`, `testSuiteName`, `serviceType` **и/или** `EnvironmentType` (`DEV` / `ST` / `FT`), потому что прогоны трёх стендов живут в одном хранилище, а ошибки на стендах различаются.

### 2.2 Что взять из ответа внешней модели

Внешняя модель права в главном:

- store — отдельный application service, не SQL внутри `PodLoggerService`;
- доступ к БД изолировать через repository;
- `LoggerService` (у нас `PodLoggerService`) остаётся runtime-оркестратором;
- test run — отдельная сущность и отдельный `TestRunStore`;
- `testRunId` обязателен, `testRunName` может повторяться;
- в БД нормализовать `test_run` 1:N `log_entry`, а в DTO денормализовать для удобства API;
- Allure — отдельный output layer;
- `GetUniqueLogs` / `GetRelevantLogs` — не store и не runtime client, а будущий analysis-слой;
- целевой query-контракт — объект `LogQuery`, overload-ы — переходный business API.

### 2.3 Что скорректировать относительно внешней модели и черновика

| Тема | Решение для этого репозитория |
| --- | --- |
| Имена | Сохраняем фактические: `PodLoggerExtension`, `PodLoggerService`, `PodLogDto`, `OpenshiftClient`. Store называется **`PodStoreService`**, не `LogStoreService`. |
| Где метод «persistent + runtime» | Только в **`PodLoggerService`**. `PodStoreService` не знает про Kubernetes, HTTP и парсинг. |
| `TestRunStore.getRunWindowLogs` | Не входит в `TestRunStore`. Чтение логов — только `PodStoreService`. `TestRunStore` отдаёт metadata и окно `[startedAt, finishedAt]`. |
| `syncRuntimeAndPersistentLogs` на store | Запрещён. Orchestration = `PodLoggerService.collectAndMergeLogsForTestRun(testRunId)`. |
| Environment vs serviceType | **Оба поля обязательны в модели.** `EnvironmentType` — стенд (`DEV`/`ST`/`FT`). `serviceType` — какой сервис/под (например `demo-api`, `payments`). Один стенд содержит много сервисов. |
| Jackson и новые поля DTO | Поля контекста **не приходят из stdout поды**. Parser заполняет только `timestamp`/`level`/`message`/`logger` (+ опционально `stackTrace`). Контекст прогона проставляет store/service после parse. |
| Дубли логов | `afterEach` сохраняет invocation-окно; `afterAll` добирает run-окно. Вставка идемпотентна по fingerprint внутри `test_run_id`. |

---

## 3. Текущее состояние кода (as-is)

Слой persistence **отсутствует**. Сейчас работает только runtime → Allure.

| Компонент | Файл | Что делает сейчас |
| --- | --- | --- |
| `@PodLogger` | `junit-pod-logger/.../PodLogger.java` | Мета-аннотация: `SpringExtension` + `PodLoggerExtension`. Атрибуты: `collectOnFailOnly`, `namespace`, `podLabelSelector`. |
| `PodLoggerExtension` | `.../PodLoggerExtension.java` | Только `BeforeEachCallback` / `AfterEachCallback`. Пишет `testStartUtc` в `ExtensionContext.Store`, вызывает `attachLogsIfNeeded`. **Нет BeforeAll/AfterAll.** |
| `PodLoggerService` | `.../PodLoggerService.java` | `openshiftClient.getLog()` → фильтр `[start-2s, end+2s]` → `Allure.addAttachment`. Ошибки глотаются. |
| `OpenshiftClient` | `.../client/OpenshiftClient.java` | Fabric8 `pods/log` + **inline** `parseLogDump`. |
| `PodLogDto` | `.../client/PodLogDto.java` | `timestamp`, `level`, `message`, `logger`. |
| `PodLoggerProperties` | `.../PodLoggerProperties.java` | Зеркало трёх атрибутов аннотации. |
| Demo IT | `demo-tests/.../OrderErrorIT.java` | `@PodLogger` на классе, 4 parametrized fail-кейса, K3s. |

Инварианты, которые **нельзя сломать**:

- `List<PodLogDto> log = openshiftClient.getLog();` остаётся публичным runtime-контрактом;
- per-invocation Allure attach с sanitised display name;
- **Один флаг управления:** только `collectOnFailOnly` на `@PodLogger`. Отдельного `persist` / `persistOnFailOnly` **нет**. Один и тот же gate решает и Allure attach, и запись в SQLite:
  - `collectOnFailOnly=true` → collect/attach/save **только если** invocation failed;
  - `collectOnFailOnly=false` → collect/attach/save после **каждого** invocation;
- ошибка save/get/Allure **не** меняет статус теста (swallow + log);
- ошибка `startTestRun` в `beforeAll` — **fail-fast** (валит class), с пошаговым SLF4J-логом.

---

## 4. Целевая архитектура

### 4.1 Принцип слоёв

```text
JUnit lifecycle                 runtime collection              persistence                 output / future
─────────────────               ──────────────────              ────────────                ──────────────
PodLoggerExtension  ──►  PodLoggerService  ──► OpenshiftClient
        │                        │                    │
        │                        ├── LogParser ◄──────┘  (вынести из client)
        │                        ├── PodStoreService ──► LogStoreRepository ──► SQLite
        │                        └── LogAllureAttachmentService ──► Allure
        │
        └── TestRunStore ──► TestRunRepository ──► SQLite

LogAnalysisService (фаза 2) ──► PodStoreService   // unique / relevant, без K8s
```

- **Extension** — когда начать/закончить run и invocation, какой test class.
- **PodLoggerService** — достать runtime, распарсить, отфильтровать окно, сохранить, приложить к Allure, **смержить persistent + runtime**.
- **PodStoreService** — DTO ↔ persistence, query API.
- **TestRunStore** — lifecycle и metadata прогона.
- **Repository** — SQL, schema, connection.
- **OpenshiftClient** — только dump из кластера, без БД.
- **Allure** — отчёт текущего прогона, не история.

### 4.2 Target package layout

Все новые типы — в модуле `junit-pod-logger`, package `com.example.podlogger.*`:

```text
com.example.podlogger
├── PodLogger
├── PodLoggerExtension
├── PodLoggerService
├── PodLoggerProperties
├── PodLoggerConfiguration
├── client
│   ├── OpenshiftClient          // fetchRawLog + getLog(); parse уходит в parser
│   └── PodLogDto
├── parser
│   └── LogParser                // String raw → List<PodLogDto>
├── allure
│   └── LogAllureAttachmentService
└── store
    ├── EnvironmentType
    ├── PodStoreService          // интерфейс публичного API
    ├── DefaultPodStoreService   // Spring @Component
    ├── TestRunStore             // интерфейс
    ├── DefaultTestRunStore
    ├── LogStoreRepository
    ├── TestRunRepository
    ├── sqlite
    │   ├── SqliteDataSourceFactory
    │   ├── SchemaMigrator
    │   ├── SqliteLogStoreRepository
    │   └── SqliteTestRunRepository
    └── dto
        ├── LogQuery
        ├── TestRunDto
        └── MergedLogResult      // результат collectAndMergeLogsForTestRun
```

Spring: новые `@Component` подхватываются существующим `@ComponentScan(basePackages = "com.example.podlogger")` в `PodLoggerConfiguration`.

### 4.3 Зависимости Maven (модуль `junit-pod-logger`)

Добавить:

| Dependency | Scope | Зачем |
| --- | --- | --- |
| `org.xerial:sqlite-jdbc` | compile | JDBC driver |
| существующий `spring-jdbc` **или** чистый JDBC | compile | v1 достаточно `DataSource` + `JdbcTemplate` **или** ручной `PreparedStatement`. Предпочтение: **чистый JDBC** + тонкий helper, чтобы не тянуть spring-jdbc, если его нет в BOM. Если в parent уже есть Spring, допустим `spring-jdbc`. |

Файл БД по умолчанию: `./target/pod-logger-store.sqlite` относительно `user.dir` тестового модуля. Override свойством.

---

## 5. Ответственность компонентов

### 5.1 `PodLoggerExtension`

Реализует дополнительно `BeforeAllCallback` и `AfterAllCallback`.

**Делает:**

- `beforeAll`: прочитать `@PodLogger` + properties, `testRunStore.startTestRun(...)`, положить `testRunId` в class-level store;
- `beforeEach` / `afterEach`: как сейчас + передать `testRunId` в service при persist;
- `afterAll`: `podLoggerService.collectAndMergeLogsForTestRun(testRunId)` затем `testRunStore.finishTestRun(testRunId)`.

**Не делает:** SQL, schema, parse raw dump, прямые вызовы repository.

**Store keys (class namespace):**

| Key | Тип | Когда |
| --- | --- | --- |
| `testStartUtc` | `LocalDateTime` | invocation, как сейчас |
| `testRunId` | `UUID` | class-level, from `beforeAll` |

Для class-level данных использовать store родительского class-context, не invocation-scoped ячейку `beforeEach`. Практический способ: `context.getRoot().getStore(STORE_NS)` **нельзя** — корень общий на весь engine и смешает параллельные классы. Нужно:

```text
context.getStore(STORE_NS) в beforeAll/afterAll  → scope = тестовый класс
context.getStore(STORE_NS) в beforeEach/afterEach → scope = invocation
```

`testRunId` читать в `afterEach` так:

```java
UUID testRunId = context.getParent()
    .orElse(context)
    .getStore(STORE_NS)
    .get(TEST_RUN_ID_KEY, UUID.class);
```

Если `startTestRun` в `beforeAll` падает — **fail-fast**: исключение пробрасывается, class не стартует. Обязателен пошаговый SLF4J:

```text
log.info("PodLogger beforeAll: resolving run metadata...");
log.info("PodLogger beforeAll: opening SQLite at {}...", storePath);
log.info("PodLogger beforeAll: startTestRun name={} suite={} env={}", ...);
log.info("PodLogger beforeAll: testRunId={} started", testRunId);
// on failure at any step:
log.error("PodLogger beforeAll FAIL-FAST at step '{}': {}", step, message, ex);
throw ex;
```

Шаги fail-fast (минимум): `resolve-metadata` → `open-datasource` → `migrate-schema` → `start-test-run` → `put-testRunId`.

### 5.2 `PodLoggerService`

**Делает:**

- применение аннотации к properties (расширить новыми атрибутами);
- runtime collect + window filter (invocation и run);
- вызов `podStoreService.saveLogs(...)`;
- Allure через `LogAllureAttachmentService`;
- **`collectAndMergeLogsForTestRun`**.

**Не делает:** SQL, построение динамического WHERE для historical query, schema migration.

Новые публичные методы сервиса — см. §7.

### 5.3 `PodStoreService`

Публичный API persistent-слоя. Только DTO ↔ БД.

**Не делает:** `getLogsFromPod()`, HTTP, Fabric8, parse, Allure, JUnit callbacks, unique/relevant analysis.

### 5.4 `TestRunStore`

Старт/финиш/чтение прогона. Не возвращает `List<PodLogDto>`.

### 5.5 `LogParser`

Вынести `parseLogDump` из `OpenshiftClient`. Контракт:

```java
public interface LogParser {
    List<PodLogDto> parse(String rawDump);
}
```

`OpenshiftClient.getLog()` = `parser.parse(fetchRawLog())`. Unit-тест `OpenshiftClientParseTest` переносится/дублируется на `LogParser`.

### 5.6 `LogAllureAttachmentService`

```java
void attachJson(String attachmentName, List<PodLogDto> logs);
```

Сериализация pretty JSON + `Allure.addAttachment` + sanitize имени. `PodLoggerService` больше не вызывает `Allure` напрямую.

---

## 6. Модель данных

### 6.1 `EnvironmentType`

```java
public enum EnvironmentType {
    DEV,
    ST,
    FT,
    LOCAL   // значение по умолчанию для demo/K3s; в закрытом контуре не использовать как прод-стенд
}
```

`LOCAL` нужен demo, чтобы не врать `DEV` на ноутбуке. Маппинг из строки — case-insensitive; неизвестное значение → fail-fast в `startTestRun` либо fallback `LOCAL` + warn. Для закрытого контура: только `DEV`/`ST`/`FT`.

### 6.2 `PodLogDto` (расширение существующего класса)

Файл остаётся `com.example.podlogger.client.PodLogDto`.

Данные из поды сами по себе — только событие лога. Чтобы ими удобно пользоваться **локально** (фильтры, сравнение стендов, unique/relevant), каждую запись нужно обогатить контекстом теста, прогона и K8s **до** `saveLogs`.

```java
public class PodLogDto {

    // ─── A. Из JSON stdout поды (LogParser) ───────────────────────────
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;
    private String level;
    private String message;
    private String logger;
    private String stackTrace;            // если encoder отдаёт; иначе null
    private String threadName;            // опционально из logback/MDC
    private String traceId;               // опционально correlation id из MDC
    private String spanId;                // опционально

    // ─── B. Контекст тестового прогона (PodLoggerService / Extension) ─
    private UUID id;                      // UUID записи; если null — random при persist
    private UUID testRunId;               // обязателен; уникальный прогон
    private String runName;               // краткое имя прогона (= testRunName / @PodLogger.testRunName)
    private String testRunName;           // alias/совместимость с §7; в v1 = runName
    private String testSuiteName;
    private String relatedTestClass;      // FQN тестового класса, напр. com.example.demotest.OrderErrorIT
    private String relatedTestMethod;     // имя метода, напр. apiErrorIsLoggedOnPod
    private String testDisplayName;       // Jupiter displayName invocation (UNKNOWN_SKU и т.п.)
    private EnvironmentType environmentType;
    private String serviceType;
    private Boolean testFailed;           // failed ли invocation, к которому привязали запись
    // collectedAt на каждую строку НЕ храним — см. §6.2.1

    // ─── C. Контекст K8s / источника (OpenshiftClient + Service) ──────
    private String podName;
    private String namespace;
    private String containerName;         // если multi-container; иначе null
    private String podLabelSelector;      // app=demo-api
    private String nodeName;              // опционально; полезно при node-local сбоях

    // ─── D. Систематизация / дедуп (Service при persist) ───────────────
    private String fingerprint;           // см. §6.6
    private String errorCategory;         // фаза 2; в v1 null
}
```

#### 6.2.1 Решение по `collectedAt`: не класть в `PodLogDto`

Два разных момента времени:

| Момент | Поле | Смысл |
| --- | --- | --- |
| Событие в поде | `timestamp` | когда приложение написало строку в stdout |
| Сбор в JVM | «collectedAt» | когда тест сходил в `pods/log` |

Для ваших сценариев (фильтр по окну теста, unique/relevant, сравнение стендов) нужен **только `timestamp`**. Окно прогона уже закрывают `TestRunDto.startedAt` / `finishedAt`. Invocation-окно — `beforeEach`/`afterEach`.

`collectedAt` на **каждой** строке лога избыточен:

- почти никогда не попадает в `LogQuery`;
- раздувает схему и Allure JSON;
- при одном dump все строки получают одно и то же `collectedAt` — это атрибут **батча сбора**, не события.

Если когда-нибудь понадобится диагностика «dump сняли в T», достаточно одного поля на уровне `test_run` (например `last_collected_at`) или лога фреймворка — не колонки в `log_entry`. **В v1: не добавляем `collectedAt` в DTO и schema.**

#### Зачем три запрошенных поля

| Поле | Назначение | Откуда |
| --- | --- | --- |
| `relatedTestClass` | «какие логи относятся к этому IT-классу» без JOIN по suite | `ExtensionContext.getRequiredTestClass().getName()` |
| `relatedTestMethod` | фильтр по методу при нескольких `@Test` / `@ParameterizedTest` в классе | `context.getRequiredTestMethod().getName()`; для run-level merge — `null` |
| `runName` | человекочитаемое имя прогона в локальных запросах и Allure | `@PodLogger.testRunName()` / properties / fallback `{simpleClass}-{yyyyMMddHHmmss}` |

`runName` и уже запланированный `testRunName` в v1 — **одно значение**. Держим оба имени в DTO только для совместимости с API §7 (`testRunName`) и с вашей терминологией (`runName`); в SQLite достаточно одной колонки `test_run_name`, в DTO оба поля заполняются одинаково.

#### Что ещё обязательно популировать для локальной работы

Минимум, без которого локальный store почти бесполезен для систематизации:

| Поле | Зачем локально |
| --- | --- |
| `testRunId` | однозначный прогон; `runName` повторяется |
| `environmentType` | DEV/ST/FT в одной БД |
| `serviceType` | какой сервис/под, не только стенд |
| `namespace` + `podName` | воспроизвести, откуда сняли dump |
| `testDisplayName` | кейс parametrized (`UNKNOWN_SKU`); method name один на 4 кейса |
| `fingerprint` | unique / дедуп / GetUniqueLogs |
| `testFailed` | потом отфильтровать «логи только падений» без Allure |

Желательно (если есть в логах / Fabric8 без лишней цены):

| Поле | Зачем |
| --- | --- |
| `stackTrace` | группировка ERROR, relevant |
| `threadName` | шум vs бизнес-поток |
| `traceId` / `spanId` | связать несколько строк одного запроса |
| `containerName` | multi-container pods |
| `podLabelSelector` | аудит «каким селектором искали» |

Не класть в каждую строку лога (жить в `test_run`): `startedAt` / `finishedAt` прогона, `status` прогона, момент сбора dump.

#### Кто что заполняет

```text
LogParser          → timestamp, level, message, logger, stackTrace, threadName, traceId, spanId
OpenshiftClient    → podName, namespace, containerName, (nodeName)
PodLoggerExtension → relatedTestClass, relatedTestMethod, testDisplayName, testFailed
PodLoggerService   → testRunId, runName, testRunName, testSuiteName, environmentType,
                     serviceType, podLabelSelector, fingerprint, id
```

Jackson: `@JsonIgnoreProperties(ignoreUnknown = true)` сохраняется. Контекстные поля B/C/D при parse из поды будут `null` — это нормально; enrichment обязателен **до** `saveLogs`.

При сериализации в Allure контекстные поля **включаются**: в отчёте видно класс, метод, run и стенд.

Обязательность при `saveLogs`:

| Поле | На persist |
| --- | --- |
| `timestamp` | обязателен; записи без timestamp не сохраняются |
| `testRunId` | обязателен |
| `runName` / `testRunName` | обязателен (хотя бы fallback) |
| `relatedTestClass` | обязателен для invocation-срезов; для run-level merge допустим FQN класса прогона |
| `relatedTestMethod` | обязателен для invocation; `null` для run-level добора |
| `message` | может быть null/empty |
| `id` | если null — `UUID.randomUUID()` |
| `fingerprint` | если null — вычислить |

Причина `testRunId` рядом с `runName`: имя `"Regression"` повторится тысячи раз; UUID однозначен.

### 6.3 `TestRunDto`

```java
public class TestRunDto {
    private UUID id;
    private String testRunName;
    private String testSuiteName;
    private EnvironmentType environmentType;
    private String serviceType;
    private String namespace;
    private String podLabelSelector;
    private LocalDateTime startedAt;    // UTC, момент beforeAll (до первого теста)
    private LocalDateTime finishedAt;   // UTC, момент afterAll после merge; null пока run открыт
    private String status;              // STARTED | FINISHED | FAILED_TO_FINISH
}
```

`finishedAt` выставляется **после** финального collect/merge, чтобы окно покрывало получение последних логов (включая sleep 500 ms).

### 6.4 `LogQuery`

Целевой расширяемый контракт. Все nullable; незаполненные поля не участвуют в WHERE. Несколько условий — AND.

```java
public class LogQuery {
    private UUID testRunId;
    private String runName;               // = testRunName
    private String testRunName;
    private String testSuiteName;
    private String relatedTestClass;
    private String relatedTestMethod;
    private EnvironmentType environmentType;
    private String serviceType;
    private LocalDateTime from;           // inclusive, по log_entry.timestamp
    private LocalDateTime to;             // inclusive
    private String level;
    private String logger;
    private String messageContains;       // SQL LIKE %value%
    private String testDisplayName;
    private Boolean testFailed;
    private String fingerprint;
    private Integer limit;                // optional cap, default 10_000
}
```

Builder/lombok. Валидация: если `from` и `to` заданы и `from.isAfter(to)` — `IllegalArgumentException`.

### 6.5 `MergedLogResult`

Результат orchestration-метода, не таблица БД:

```java
public class MergedLogResult {
    private UUID testRunId;
    private List<PodLogDto> fromPersistent;
    private List<PodLogDto> fromRuntime;
    private List<PodLogDto> merged;       // union, sorted by timestamp, deduped
    private int insertedNewCount;         // сколько runtime-записей дописали в store
}
```

### 6.6 Fingerprint

Для идемпотентности v1 и будущего `GetUniqueLogs`:

```text
fingerprint = sha256(
    coalesce(level,'') + '\n' +
    coalesce(logger,'') + '\n' +
    coalesce(message,'') + '\n' +
    coalesce(stackTrace,'')
)
```

**Не** включает timestamp: одна и та же ошибка в разное время — один fingerprint (нужно для unique).  
Идемпотентность вставки: unique key `(test_run_id, timestamp, fingerprint)`. Повторный `afterEach`/`afterAll` не плодит дубли одной и той же строки лога.

---

## 7. Публичный API

### 7.1 `PodStoreService`

```java
public interface PodStoreService {

    void saveLogs(List<PodLogDto> logs);

    void saveLogs(UUID testRunId, List<PodLogDto> logs);

    List<PodLogDto> getLogs();

    List<PodLogDto> getLogs(UUID testRunId);

    List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to);

    List<PodLogDto> getLogs(
            LocalDateTime from,
            LocalDateTime to,
            EnvironmentType environmentType);

    List<PodLogDto> getLogs(UUID testRunId, EnvironmentType environmentType);

    List<PodLogDto> getLogs(String testSuiteName, EnvironmentType environmentType);

    List<PodLogDto> getLogs(
            String testRunName,
            String testSuiteName,
            EnvironmentType environmentType);

    List<PodLogDto> getLogs(LogQuery query);

    /**
     * Все сохранённые записи прогона (эквивалент getLogs(testRunId)).
     * Окно BeforeAll–AfterAll применяется, если в test_run уже есть started_at/finished_at;
     * если finished_at ещё null — возвращается всё, что успели сохранить.
     */
    List<PodLogDto> getLogsForWholeRun(UUID testRunId);

    /**
     * Удаляет закрытые test_run с started_at старше now-days и все их log_entry.
     * Открытые run (finished_at IS NULL) не трогает.
     * Возвращает число удалённых test_run.
     */
    int deleteOlderThan(int days);
}
```

Правила:

- `saveLogs(List)` требует, чтобы у каждого элемента был `testRunId`; иначе `IllegalArgumentException`.
- `saveLogs(UUID, List)` проставляет `testRunId` (и подтягивает name/suite/env/serviceType из `test_run`) на каждую запись перед insert.
- overload-ы — тонкие делегаты в `getLogs(LogQuery)`.
- сортировка результата: `timestamp ASC`, затем `id`.
- пустой результат — пустой список, не null и не exception.
- `getLogs()` без фильтра в v1 допустим, но обязан уважать `limit` (default 10_000) и логировать warn — защита от случайного полного скана.

**Запрещённые методы на этом интерфейсе:** `getLogsFromPod`, `syncRuntimeAndPersistentLogs`, `collectAndMerge*`.

### 7.2 `TestRunStore`

```java
public interface TestRunStore {

    UUID startTestRun(
            String testRunName,
            String testSuiteName,
            EnvironmentType environmentType,
            String serviceType);

    /** Расширенный старт: namespace и selector для аудита. */
    UUID startTestRun(TestRunDto draft);

    void finishTestRun(UUID testRunId);

    Optional<TestRunDto> getTestRun(UUID testRunId);

    List<TestRunDto> getTestRuns(String testRunName);

    List<TestRunDto> getTestRuns(LocalDateTime from, LocalDateTime to);

    List<TestRunDto> getTestRuns(EnvironmentType environmentType);
}
```

`getTestRun(String testRunName)` как `Optional` **не использовать**: имя неуникально. Внешняя модель здесь ошибалась. Возвращаем `List`.

`startTestRun` всегда создаёт **новую** строку и новый UUID, даже если имя совпало.

`finishTestRun`: пишет `finished_at = now(UTC)`, `status = FINISHED`. Повторный finish идемпотентен.

### 7.3 Новые методы `PodLoggerService`

```java
public class PodLoggerService {

    // as-is, плюс persist invocation window если store enabled
    public void attachLogsIfNeeded(ExtensionContext context,
                                   LocalDateTime start,
                                   LocalDateTime end,
                                   boolean failed);

    /** Runtime dump → parse → filter by run window → save → merge with persistent. */
    public MergedLogResult collectAndMergeLogsForTestRun(UUID testRunId);

    /** Только runtime + parse + filter, без записи в БД. Для тестов и отладки. */
    public List<PodLogDto> collectRuntimeLogs(LocalDateTime from, LocalDateTime to);
}
```

#### Алгоритм `collectAndMergeLogsForTestRun`

Это ответ на вопрос prompt «где метод, который берёт persistent и runtime».

1. `TestRunDto run = testRunStore.getTestRun(id).orElseThrow(...)`.
2. `from = run.startedAt.minusSeconds(SKEW)`, `to = now().plusSeconds(SKEW)` (ещё нет finishedAt).
3. `fromPersistent = podStoreService.getLogs(testRunId)`.
4. `fromRuntime = collectRuntimeLogs(from, to)`; проставить контекст run на каждую DTO.
5. `merged = union by (timestamp, fingerprint)`; sort by timestamp.
6. `saveLogs(testRunId, runtimeEntriesMissingInPersistent)`.
7. optional Allure attach `pod-logs-run-<testRunName>` **только если** property `attachRunSummaryToAllure=true` (default false, чтобы не дублировать 4 invocation-аттача в демо).
8. вернуть `MergedLogResult`.
9. любое исключение — log error, вернуть best-effort или empty merge; тест не падает.

`PodStoreService` в этом алгоритме участвует только шагами 3 и 6.

### 7.4 Изменения `@PodLogger`

```java
public @interface PodLogger {
    /**
     * Единственный флаг gate для extension:
     * true  — Allure attach + save в SQLite только при failed invocation;
     * false — Allure attach + save после каждого invocation.
     * Отдельного атрибута persist нет.
     */
    boolean collectOnFailOnly() default true;

    String namespace() default "default";

    String podLabelSelector() default "app=demo-api";

    /** Имя прогона. Пустая строка → fallback `{simpleClassName}-{yyyyMMddHHmmss}`. */
    String testRunName() default "";

    /**
     * Имя suite. Пустая строка → FQN тестового класса.
     */
    String testSuiteName() default "";

    EnvironmentType environmentType() default EnvironmentType.LOCAL;

    /** Логический тип сервиса/поды. Пустая строка → value из podLabelSelector (часть после '='). */
    String serviceType() default "";
}
```

Priority конфигурации (высокий → низкий):

1. атрибуты аннотации (строки: пустая = «взять из properties/env»);
2. `PodLoggerProperties` / env: `POD_LOGGER_ENVIRONMENT`, `POD_LOGGER_SERVICE_TYPE`, `POD_LOGGER_TEST_RUN_NAME`, `POD_LOGGER_STORE_PATH`;
3. fallback: suite = test class FQN, run name = `{simpleClassName}-{yyyyMMddHHmmss}`, serviceType из selector, env = `LOCAL`, storePath = `{user.dir}/target/pod-logger-store.sqlite`.

Так закрытый контур может ставить `POD_LOGGER_ENVIRONMENT=ST` в Jenkins без правки каждой аннотации.

### 7.5 `PodLoggerProperties` — новые поля

| Поле | Default |
| --- | --- |
| `storePath` | `{user.dir}/target/pod-logger-store.sqlite` |
| `environmentType` | `LOCAL` |
| `testRunName` | `""` |
| `testSuiteName` | `""` |
| `serviceType` | `""` |
| `attachRunSummaryToAllure` | `false` |
| `queryLimit` | `10000` |
| `retentionDays` | `30` (для `deleteOlderThan`; 0 = не вызывать авто) |

**Нет поля `persist`.** Gate = только `collectOnFailOnly` (зеркало аннотации).

YAML (опционально, фаза 1.1):

```yaml
pod:
  logger:
    store-path: target/pod-logger-store.sqlite
    environment-type: ST
    service-type: demo-api
```

Рекомендация v1: annotation + env + properties bean. YAML — позже.

---

## 8. Lifecycle

### 8.1 Два окна времени

```text
beforeAll now = startedAt
    │
    ├── beforeEach  invocationStart
    │     test body / API → pod stdout
    ├── afterEach   invocationEnd     → window A = [start-2s, end+2s]
    │
    ├── ... N тестов / parametrized invocations ...
    │
afterAll collectAndMerge + finish     → window B = [startedAt-2s, finishedAt+2s]
```

- **Window A** — Allure per invocation **и** save в SQLite по **одному** gate `collectOnFailOnly` + `failed`.
- **Window B** — run-level merge в `afterAll` (этап 6): только если в run уже есть что merge'ить / по той же политике collect; детали на этапе 6.

```text
shouldCollect = !collectOnFailOnly || failed

if (!shouldCollect) {
  log.debug("Skip Allure+SQLite: collectOnFailOnly={} failed={}", ...);
  return;  // ни attach, ни save
}
// один pipeline:
runtime → parse → filter window → saveLogs → Allure.attach
```

Отдельного флага `persist` нет: «что ушло в Allure — то же уходит в store» (тот же window list).

### 8.2 Sequence

```text
TestClass
  → Extension.beforeAll
       → applyAnnotation
       → testRunStore.startTestRun(name, suite, env, serviceType)
       → store.put(testRunId)
  → Extension.beforeEach
       → store.put(invocationStart)
  → test method
  → Extension.afterEach
       → Service.attachLogsIfNeeded
            → runtime getLog / parse / filter window A
            → if persist: podStoreService.saveLogs(testRunId, window)
            → if Allure needed: attachmentService.attach(...)
  → Extension.afterAll
       → Service.collectAndMergeLogsForTestRun(testRunId)
       → testRunStore.finishTestRun(testRunId)
```

`startedAt` фиксируется **до** первого теста. `finishedAt` — **после** финального runtime fetch.

### 8.3 Параллельные тестовые классы

Каждый класс — свой `testRunId` и свои строки. SQLite file один: включить WAL + timeout busy_timeout=5000. Не открывать второй write-connection без нужды: один `DataSource` singleton на JVM.

Параллельные invocations **внутри** класса: текущее демо последовательное. Если JUnit parallel method — возможен гоночный `getLog()`; v1 не обещает корректный persist при `junit.jupiter.execution.parallel.enabled=true` на методах. Зафиксировать в ограничениях.

---

## 9. SQLite schema

### 9.0 Выбор хранилища: дерево папок vs H2 vs SQLite

Для этой библиотеки primary store = **один локальный файл SQLite**. Ниже — почему не дерево папок и не H2 как default.

| Критерий | Папки/JSON в `resources` или `target/pod-logs/{run}/` | H2 (file/mem) | SQLite |
| --- | --- | --- | --- |
| Запрос «ERROR на ST за неделю по fingerprint» | сканировать все файлы вручную | SQL + индексы | SQL + индексы |
| DEV/ST/FT в одном хранилище | отдельные деревья или дубли метаданных в каждом JSON | одна БД | одна БД |
| `GetUniqueLogs` / `GetRelevantLogs` | почти нереально без своей «БД поверх файлов» | удобно | удобно |
| Дедуп `(run, timestamp, fingerprint)` | сами писать merge | UNIQUE + SQL | UNIQUE + SQL |
| Просмотр глазами / артефакт CI | **отлично** | через клиент | через DB Browser / CLI |
| Зависимости | почти нет | `com.h2database:h2` | `org.xerial:sqlite-jdbc` |
| Портативность файла | много файлов | `.mv.db` / lock-файлы | один `.sqlite` |
| Несколько JVM на один файл | плохо | file-lock H2 капризный | WAL + busy_timeout ок для «редко» |
| «Как у Maven resources» | привычно, но **не для query-слоя** | — | — |

**Аргументы за дерево папок** (когда оно уместно):

- человек открыл папку прогона и сразу видит JSON/лог;
- удобно класть в `allure-results` или archive Jenkins как zip;
- нет JDBC, проще отладка «что записалось».

**Почему это плохо как primary store именно здесь:** ваши цели — фильтры по env/suite/time/service, история между прогонами, потом unique/relevant. Дерево папок превращается в самописную БД без индексов. Класть store в `src/test/resources` нельзя: resources — classpath, read-only в jar, не место для растущей истории прогонов.

**Аргументы за H2:**

- pure Java, без native bits sqlite-jdbc;
- SQL ближе к Postgres при будущем remote store;
- in-memory удобен в unit-тестах.

**Почему не default:** для embedded local cache SQLite проще (один файл, привычный tooling, WAL). H2 file mode даёт лишние lock/MV-файлы и меньше выигрыша при вашем объёме (тысячи–сотни тысяч строк логов, не миллионы TPS). Repository-интерфейс всё равно позволит подменить на H2 позже, если понадобится.

**Вердикт v1:** SQLite file `target/pod-logger-store.sqlite`.  
Опционально позже: **export** одного run в `target/pod-logs/{testRunId}/events.json` для глаз/CI — это secondary artifact, не источник истины.

Инициализация при старте бина repository: `CREATE TABLE IF NOT EXISTS` + индексы. Отдельный `schema_version` на будущее.

```sql
CREATE TABLE IF NOT EXISTS test_run (
    id                 TEXT PRIMARY KEY,
    test_run_name      TEXT NOT NULL,
    test_suite_name    TEXT,
    environment_type   TEXT NOT NULL,
    service_type       TEXT,
    namespace          TEXT,
    pod_label_selector TEXT,
    started_at         TEXT NOT NULL,
    finished_at        TEXT,
    status             TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS log_entry (
    id                 TEXT PRIMARY KEY,
    test_run_id        TEXT NOT NULL,
    timestamp          TEXT NOT NULL,
    level              TEXT,
    logger             TEXT,
    message            TEXT,
    stack_trace        TEXT,
    thread_name        TEXT,
    trace_id           TEXT,
    span_id            TEXT,
    pod_name           TEXT,
    namespace          TEXT,
    container_name     TEXT,
    service_type       TEXT,
    related_test_class TEXT,
    related_test_method TEXT,
    test_display_name  TEXT,
    test_failed        INTEGER,          -- 0/1/NULL
    fingerprint        TEXT NOT NULL,
    FOREIGN KEY (test_run_id) REFERENCES test_run(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_log_dedup
    ON log_entry(test_run_id, timestamp, fingerprint);

CREATE INDEX IF NOT EXISTS idx_log_test_run ON log_entry(test_run_id);
CREATE INDEX IF NOT EXISTS idx_log_timestamp ON log_entry(timestamp);
CREATE INDEX IF NOT EXISTS idx_log_level ON log_entry(level);
CREATE INDEX IF NOT EXISTS idx_log_fingerprint ON log_entry(fingerprint);
CREATE INDEX IF NOT EXISTS idx_log_related_class ON log_entry(related_test_class);
CREATE INDEX IF NOT EXISTS idx_log_related_method ON log_entry(related_test_method);
CREATE INDEX IF NOT EXISTS idx_run_environment ON test_run(environment_type);
CREATE INDEX IF NOT EXISTS idx_run_suite ON test_run(test_suite_name);
CREATE INDEX IF NOT EXISTS idx_run_name ON test_run(test_run_name);
```

`runName` / `testRunName` / `testSuiteName` / `environmentType` **не** дублируются обязательными колонками `log_entry` (берутся JOIN из `test_run`).  
`related_test_class` / `related_test_method` / `test_display_name` живут **в** `log_entry`: одна запись может относиться к конкретному invocation внутри большого run.

`service_type` на `log_entry` дублируется сознательно: один run theoretically мог бы писать логи нескольких сервисов (фаза 2). В v1 значение копируется из `test_run`.

Время хранить как ISO-8601 text (`yyyy-MM-dd'T'HH:mm:ss.SSS`), UTC, без timezone suffix — как текущий `@JsonFormat` и logback demo-app.

Insert: `INSERT OR IGNORE` по unique dedup index.

JOIN-пример `getLogs(suite, env)`:

```sql
SELECT e.*, r.test_run_name, r.test_suite_name, r.environment_type
FROM log_entry e
JOIN test_run r ON r.id = e.test_run_id
WHERE r.test_suite_name = ?
  AND r.environment_type = ?
ORDER BY e.timestamp ASC, e.id ASC
LIMIT ?;
```

Файл БД не коммитить в git. Добавить в `.gitignore`: `**/*.sqlite`, `**/*.sqlite-journal`, `**/*.sqlite-wal`.

---

## 10. Взаимосвязи с существующими классами

### 10.1 Wiring Spring

`PodLoggerService` получает в конструктор дополнительно:

- `PodStoreService`
- `TestRunStore`
- `LogAllureAttachmentService`
- (опционально оставить `ObjectMapper` только в attachment service)

`OpenshiftClient` получает `LogParser` вместо собственного parse, либо `getLog()` делегирует parser.

Условное отключение записи: только через gate `collectOnFailOnly` + `failed` (см. §3, §8.1). Beans store всегда присутствуют.

### 10.2 Что меняется в `attachLogsIfNeeded`

После построения `window` (если gate `shouldCollect` пройден):

1. `podStoreService.saveLogs(testRunId, window)` — тот же list, что пойдёт в Allure;
2. `attachmentService.attach(...)` — тот же list.

Оба шага в отдельных try/catch (swallow + error log). Ошибка save не блокирует Allure и наоборот.

### 10.3 Demo `OrderErrorIT` и демонстрационный store-тест

`OrderErrorIT` остаётся runtime+Allure демо (K3s). Минимальные аннотации store — по желанию после интеграции extension.

**Обязательный deliverable v1:** один тестовый класс без Docker/K3s, который **демонстрирует и принимает** store (save / query / export). Спецификация — §12–§13. Не считать store «готовым», пока этот класс зелёный.

---

## 11. Что не входит в v1

| Функция | Куда |
| --- | --- |
| `GetUniqueLogs` / `GetRelevantLogs` | `LogAnalysisService` поверх `PodStoreService` |
| Категоризация ошибок, known-error DB | analysis / отдельная таблица |
| PostgreSQL / общая CI-база на команду | другой `LogStoreRepository` |
| Multi-pod / несколько `serviceType` в одном run | schema уже позволяет, API сбора — нет |
| Parallel method execution | ограничение |
| UI над SQLite | нет |
| Шифрование файла БД | нет |
| Primary store в виде дерева папок | отклонён; export JSON — optional secondary |

Эскиз фазы 2 (не реализовывать сейчас):

```java
public interface LogAnalysisService {
    List<PodLogDto> getUniqueLogs(UUID testRunId);
    List<PodLogDto> getUniqueLogs(LocalDateTime from, LocalDateTime to);
    List<PodLogDto> getRelevantLogs(UUID testRunId);
}
```

---

## 12. Definition of Done и принцип проверки

**Исполненной считается только работа, подтверждённая зелёным автотестом.**

| Правило | Смысл |
| --- | --- |
| Нет зелёного теста → этап не закрыт | Код без теста не принимается как «готово» |
| Этап = инкремент + тест(ы) этапа | Каждый шаг §14 имеет явный Verification |
| Демо-класс store — gate релиза v1 | Без `PodStoreServiceDemoTest` store не accepted |
| Runtime Allure regression | `OpenshiftClientParseTest` / `LogParserTest` остаются зелёными; `OrderErrorIT` не в gate v1 store (нужен Docker) |

Команда проверки локально (store v1):

```bash
mvn -pl junit-pod-logger -am test
```

Ожидание: все тесты модуля `junit-pod-logger` SUCCESS, включая демо-класс store.

---

## 13. Критерии приёмки (Acceptance Criteria)

### 13.1 Продуктовые / архитектурные AC

| ID | Критерий | Как подтверждается |
| --- | --- | --- |
| AC-01 | Runtime-имена сохранены: `PodLoggerExtension`, `PodLoggerService`, `PodLogDto`, `OpenshiftClient.getLog()` | code review + compile |
| AC-02 | `PodStoreService` не содержит Fabric8 / Allure / JUnit lifecycle | code review |
| AC-03 | `TestRunStore.start/finish` фиксирует `startedAt` / `finishedAt` | тест демо-класса |
| AC-04 | `PodLogDto` содержит `testRunId`, `runName`/`testRunName`, `testSuiteName`, `relatedTestClass`, `relatedTestMethod`, `environmentType`, `serviceType` | save/get round-trip в демо-классе |
| AC-05 | В одной SQLite сосуществуют `DEV`, `ST`, `FT`, `LOCAL` без коллизии id | демо-тест фильтр по env |
| AC-06 | Есть overload-фильтры **и** `getLogs(LogQuery)` | демо-тест по матрице §13.3 |
| AC-07 | `collectAndMergeLogsForTestRun` только в `PodLoggerService` | code review + unit merge-тест (этап 6) |
| AC-08 | Повторный `saveLogs` тех же событий не плодит дубли | демо-тест dedup |
| AC-09 | save/get/Allure — swallow + log; `startTestRun` в beforeAll — fail-fast с пошаговым логом | unit + DemoTest / Extension-тест на этапе 5 |
| AC-10 | Primary store = SQLite `{user.dir}/target/pod-logger-store.sqlite` + override; не resources-tree | path + `.gitignore` |
| AC-11 | Export JSON **не** в v1 | отсутствие API export |
| AC-12 | `GetUniqueLogs` в v1 нет | отсутствие класса |
| AC-13 | README: path sqlite, один флаг `collectOnFailOnly` для Allure+store | doc review |
| AC-14 | `PodStoreServiceDemoTest` зелёный (в т.ч. pass→empty при gate fail-only) | `mvn -pl junit-pod-logger test` |
| AC-15 | `deleteOlderThan(days)` по `started_at`; удаляет закрытый run + log_entry; открытые не трогает | DemoTest |
| AC-16 | Один gate: нет атрибута `persist`; Allure и SQLite получают один и тот же window list | code review + DemoTest |

### 13.2 Требования к демо-тестовому классу

| Атрибут | Требование |
| --- | --- |
| Имя | `com.example.podlogger.store.PodStoreServiceDemoTest` |
| Модуль | `junit-pod-logger` (`src/test/java`) |
| Инфраструктура | **Без** Docker, K3s, Fabric8, сети. Temp SQLite + сервисы store |
| Назначение | Одновременно **приёмка** и **демонстрация** save / query / purge |
| Lifecycle | `@TempDir` или уникальный файл в `target/`; `@BeforeEach` чистая schema; close DataSource в teardown |
| Стиль | JUnit 5; имена `should…`; без `@Disabled` на happy-path |
| Фикстуры | ≥2 test run (`ST` и `DEV`), ≥3 log entries с разными level/class/method/time |

Обязательные сценарии (отдельный `@Test` или `@ParameterizedTest`):

| Тест | Что проверяет |
| --- | --- |
| `shouldStartAndFinishTestRun` | `startedAt` / после finish `finishedAt` + status FINISHED |
| `shouldSaveLogsAndGetByTestRunId` | save → `getLogs(testRunId)` по содержимому |
| `shouldFilterByTimeRange` | события вне `[from,to]` отсутствуют |
| `shouldFilterByEnvironment` | ST-запрос не содержит DEV |
| `shouldFilterBySuiteAndEnvironment` | overload suite+env |
| `shouldFilterByRunNameSuiteEnvironment` | overload runName+suite+env |
| `shouldFilterByRelatedTestClassAndMethod` | через `LogQuery` |
| `shouldFilterByLogQueryCombined` | несколько полей LogQuery (AND) |
| `shouldGetLogsForWholeRun` | все записи run |
| `shouldDeduplicateOnResave` | дважды save → count неизменен |
| `shouldRejectSaveWithoutTestRunId` | `IllegalArgumentException` |
| `shouldSkipPersistWhenCollectOnFailOnlyAndPassed` | симуляция gate: pass + collectOnFailOnly → в БД нет новых строк (N6) |
| `shouldDeleteOlderThanRemovesClosedRunsOnly` | старый FINISHED удалён; открытый и свежий остались |

`shouldExportTestRun` — out of scope (Q1=b).

Класс **не** поднимает `PodLoggerExtension` / Spring Boot. Ручная сборка store в `@BeforeEach` (Q4). Для N6 gate тестируется как helper/метод политики `shouldCollect(collectOnFailOnly, failed)` + отсутствие save при false (без K8s).

### 13.3 Матрица фильтров для демо-класса

| API | Минимальная проверка |
| --- | --- |
| `saveLogs(UUID, List)` | insert + enrichment run context |
| `getLogs()` | не null; уважает limit |
| `getLogs(UUID)` | только этот run |
| `getLogs(from, to)` | границы inclusive |
| `getLogs(from, to, env)` | time ∩ env |
| `getLogs(testRunId, env)` | id ∩ env |
| `getLogs(suite, env)` | JOIN |
| `getLogs(runName, suite, env)` | JOIN |
| `getLogs(LogQuery)` | relatedTestClass, relatedTestMethod, level, messageContains |
| `getLogsForWholeRun(id)` | все сохранённые по run |

---

## 14. Этапы имплементации (что / зачем / порядок / проверка)

Правило: **этап закрыт ⟺ Verification зелёный.**

### Этап 0 — Контракт моделей

| | |
| --- | --- |
| **Что** | `EnvironmentType`, расширенный `PodLogDto`, `TestRunDto`, `LogQuery`, `MergedLogResult` |
| **Зачем** | Единый контракт для store/API/тестов |
| **Проверка** | `mvn -pl junit-pod-logger -DskipTests compile`; optional unit на enum/DTO |

### Этап 1 — LogParser

| | |
| --- | --- |
| **Что** | Вынести parse из `OpenshiftClient` в `LogParser` |
| **Зачем** | Один parse для runtime и будущих сценариев |
| **Проверка** | `LogParserTest` / `OpenshiftClientParseTest` green |

### Этап 2 — SQLite schema + repositories

| | |
| --- | --- |
| **Что** | DataSource, SchemaMigrator, Sqlite repositories |
| **Зачем** | Физическое хранение, индексы, dedup |
| **Проверка** | Узкие repository-тесты на temp file |

### Этап 3 — `PodStoreService` + `TestRunStore` (**GATE**)

| | |
| --- | --- |
| **Что** | Default implementations, все overload-ы → `LogQuery` |
| **Зачем** | Публичный API persistent-слоя |
| **Проверка** | **`PodStoreServiceDemoTest`** — все сценарии §13.2 кроме export/merge. **Главный gate store.** |

### Этап 4 — Export (если Q1=да)

| | |
| --- | --- |
| **Что** | `exportTestRun(UUID, Path)` → JSON |
| **Зачем** | Человекочитаемый артефакт при primary=SQLite |
| **Проверка** | `shouldExportTestRun` в демо-классе |

### Этап 5 — Extension lifecycle + enrichment

| | |
| --- | --- |
| **Что** | BeforeAll/AfterAll, enrichment class/method/runName, persist в afterEach |
| **Зачем** | Реальные прогоны пишут в БД |
| **Проверка** | Unit/mock: start/save вызваны с ожидаемым контекстом |

### Этап 6 — Merge + Allure extract

| | |
| --- | --- |
| **Что** | `collectAndMergeLogsForTestRun`; `LogAllureAttachmentService` |
| **Зачем** | Persistent+runtime; Allure = output layer |
| **Проверка** | Unit merge (overlap → insertedNewCount); Allure не ломает persist |

### Этап 7 — Docs / DoD

| | |
| --- | --- |
| **Что** | README, `.gitignore` `*.sqlite*`, статус PRD → implemented только после §12 |
| **Зачем** | Не обещать недоказанное |
| **Проверка** | `mvn -pl junit-pod-logger test` полностью green |

### Сводка

```text
0 Models → 1 Parser → 2 SQLite repos → 3 PodStoreService + DemoTest (GATE)
                                         → 4 Export (optional)
                                         → 5 Extension → 6 Merge/Allure → 7 Docs
```

---

## 15. Риски и ограничения

| Риск | Митигация |
| --- | --- |
| Рост sqlite при частых локальных прогонах | `limit` на query + `deleteOlderThan(days)` |
| Часы JVM vs pod | skew 2s |
| Не-JSON логи | parser skip non-JSON |
| Смешение стендов в одном файле | фильтр `EnvironmentType` |
| `testRunName` как уникальный ключ | запрещён; только UUID |
| Двойная выгрузка pods/log | приемлемо для demo |
| Демо без Spring vs production wiring | демо вручную; wiring-тест на этапе 5 |

---

## 16. Краткий ответ на развилку prompt

> Метод «логи за BeforeAll–AfterAll из persistent **и** из runtime» — в `PodLoggerService`.  
> Метод «логи за этот прогон уже лежащие в БД» — `PodStoreService.getLogs(testRunId)` / `getLogsForWholeRun`.  
> `PodStoreService` не ходит в поду.
>
> В DTO: `EnvironmentType`, `serviceType`, `testRunName`/`runName`/`testSuiteName`, `relatedTestClass`/`relatedTestMethod`, обязательный `testRunId`.

---

## 17. Traceability к init prompt

| Требование prompt | Где в PRD |
| --- | --- |
| Store service + связи | §4, §5, §10 |
| save / get by filters | §7.1, §13.3 |
| test run BeforeAll–AfterAll | §7.2, §8 |
| persistent + runtime | §7.3 |
| поля DTO | §6.2 |
| SQLite / Allure | §1, §9, §5.6 |
| unique/relevant | §11 |
| DoD = зелёный тест | §12–§14 |
| Демо-класс store | §13.2 |

---

## 18. Решения пользователя (закрыто) и один остаток

### 18.1 Зафиксировано

| ID | Решение |
| --- | --- |
| Q1 | **b** — export JSON нет в v1 |
| Q2 / N1 / N7 | **Один флаг** `collectOnFailOnly`: тот же gate для Allure и SQLite; атрибута `persist` нет |
| Q3 | CI artifact SQLite пока не нужен |
| Q4 | **a** — ручная сборка DemoTest |
| Q5 | **a** — `runName` + `testRunName` в DTO, одна колонка БД |
| Q6 | **a** — первый PR = этапы 0–3 (+ `deleteOlderThan` в API store) |
| Q7 | **b** — `deleteOlderThan(days)` в v1 |
| Q8 | **a** — DemoTest только в `junit-pod-logger` |
| Q9 | **a** — UTC `LocalDateTime` text |
| Q10 / N4 | **c** — start fail-fast + пошаговый SLF4J; save/get/Allure swallow |
| N3 | **a** — открытые run при purge не удалять |
| N2 | **a** — возраст по `test_run.started_at`; удаляется весь run + `log_entry` |
| N6 | **a** — тест pass → в БД пусто |
| N8 | **a** — `{user.dir}/target/pod-logger-store.sqlite` + override |

### 18.2 Единый gate Allure + Store (нормативная логика)

```text
shouldCollect = !collectOnFailOnly || failed

if (!shouldCollect) {
    // ни Allure, ни SQLite
    return;
}
List<PodLogDto> window = collect+filter;
saveLogs(testRunId, window);   // swallow errors
attachAllure(window);          // swallow errors
```

### 18.3 Purge (нормативно)

```text
deleteOlderThan(days):
  cutoff = now(UTC) - days
  delete log_entry where test_run_id in (
    select id from test_run
    where finished_at is not null
      and started_at < cutoff
  )
  delete test_run where finished_at is not null and started_at < cutoff
  return deleted test_run count
```

Открытые run (`finished_at IS NULL`) не удаляются, даже если `started_at` старше cutoff.

### 18.4 Статус

Все open questions для v1 (этапы 0–3) **закрыты**. Можно имплементировать store + `PodStoreServiceDemoTest`.
