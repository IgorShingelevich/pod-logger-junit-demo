вопрос 
Соответственно фактической структуре реализуй под store service и установи взаимосвязи с под логер extension, под логер сервис и под логер классами.

Для чего под лог store service должен выполнять следующие публичные действия: 

1. Сохранение списка логов в базу данных.
2. Получение списка логов из этой базы данных на основе разных фильтров, то есть нескольких перегруженных методов, которые будут искать по разным фильтрам в базе данных блогов.

 Этот сервис также должен иметь классы работы с тестовым прогоном, чтобы у меня в начале и в конце тестового прогона, состоящего из одного теста или из 100 тестов, бралось время Before All и время After All. Таким образом я мог фиксировать один большой длинный промежуток времени от начала прогона до конца прогона.

Чтобы мне получить логи за этот промежуток времени от начала до конца прогона, это лучше сделать в отдельном методе внутри подлогер сервиса, так как он уже реализует методы выборки логов. Либо же этот метод должен лежать в новом под Store Service. Вот такой метод должен быть, который получит логи из Persistent слоя, получит логи из runtime поды и после этого будет ими дальше уже оперировать.

Напиши состав PRD с публичными методами, которые будут использоваться в под Store Service. Для этого в DTO, которое у нас под логер, нужно добавить поля. Добавь, пожалуйста, для каждого код log.dto следующие поля: 

- test-run name
- test-suit name
- serviceType Или еще поле Environment Type, которое будет определять, на каком стенде был прогон, потому что есть стенд DEV, ST и FT. Прогоны могут идти на трех стендах и ошибки могут быть разные на трех стендах, но лежать в одном хранилище. 
ответ модели 
Да. С учётом уточнений я бы немного скорректировал предыдущую архитектуру: **`LogStoreService` становится отдельным application-level сервисом над persistent storage**, но сам доступ к БД лучше дополнительно изолировать через `LogStoreRepository`. Тогда `LoggerService` не превращается в слой работы с БД.

Ниже PRD, который можно практически использовать как основу для PR/задачи в проекте.

---

# PRD: Persistent Log Store для Logger Extension

## 1. Цель

Добавить в существующую систему логирования persistent-слой, который:

1. сохраняет распарсенные логи каждого тестового прогона;
2. сохраняет контекст тестового прогона;
3. позволяет получать логи из локального persistent storage по различным фильтрам;
4. позволяет получать все логи конкретного test run;
5. сохраняет историю логов между локальными прогонами;
6. позволяет в дальнейшем реализовать `GetUniqueLogs` и `GetRelevantLogs`;
7. продолжает прикладывать runtime-логи текущего теста/прогона к Allure;
8. хранит логи разных environments в одном локальном хранилище.

Основной persistent storage для локального запуска — **SQLite**.

---

# 2. Общая архитектура

Предлагаемая структура:

```text
logger/
│
├── LoggerExtension
│
├── LoggerService
│
├── LogParser
│
├── dto/
│   └── LogDto
│
├── store/
│   ├── LogStoreService
│   ├── LogStoreRepository
│   ├── SqliteLogStoreRepository
│   │
│   ├── TestRunStore
│   ├── SqliteTestRunStore
│   │
│   └── dto/
│       └── TestRunDto
│
└── allure/
    └── LogAllureAttachmentService
```

Логические зависимости:

```text
                 LoggerExtension
                       │
                       ▼
                 LoggerService
                  │          │
                  │          └──────────► Allure
                  │
                  ▼
               LogParser
                  │
                  ▼
                LogDto
                  │
                  ▼
             LogStoreService
                  │
             ┌────┴─────┐
             ▼          ▼
        LogRepository  TestRunStore
             │          │
             └────┬─────┘
                  ▼
               SQLite
```

---

# 3. Ответственность компонентов

## `LoggerExtension`

Отвечает за интеграцию с lifecycle тестового framework.

Он должен знать:

* когда начинается test run;
* когда заканчивается test run;
* когда начинается тест;
* когда заканчивается тест;
* когда нужно вызвать `LoggerService`;
* когда нужно сохранить полученные логи.

Он **не должен содержать SQL или persistence logic**.

---

# 4. `LoggerService`

`LoggerService` остаётся ответственным за получение runtime-логов.

Например:

```java
public interface LoggerService {

    String getLogs();

    List<LogDto> getParsedLogs();

    void collectAndStoreLogs();
}
```

Но здесь важный момент.

### `LoggerService` не должен реализовывать SQL.

Он делает:

```text
runtime pod
     ↓
getLogs()
     ↓
raw String
     ↓
LogParser
     ↓
List<LogDto>
     ↓
LogStoreService
```

---

# 5. `LogStoreService`

Это основной публичный application service для persistent-слоя.

Именно его я бы сделал основным API для работы с историческими логами.

```java
public interface LogStoreService {

    void saveLogs(List<LogDto> logs);

    List<LogDto> getLogs();

    List<LogDto> getLogs(UUID testRunId);

    List<LogDto> getLogs(LocalDateTime from, LocalDateTime to);

    List<LogDto> getLogs(
        LocalDateTime from,
        LocalDateTime to,
        EnvironmentType environment
    );

    List<LogDto> getLogs(
        UUID testRunId,
        EnvironmentType environment
    );

    List<LogDto> getLogs(
        String testSuiteName,
        EnvironmentType environment
    );

    List<LogDto> getLogs(
        String testRunName,
        String testSuiteName,
        EnvironmentType environment
    );
}
```

Однако я бы **не делал десятки overload'ов**, если фильтров станет много.

На первом этапе можно поддержать overload'ы, которые ты описал, но архитектурно лучше сразу предусмотреть:

```java
LogQuery
```

Например:

```java
public class LogQuery {

    private UUID testRunId;
    private String testRunName;
    private String testSuiteName;
    private EnvironmentType environmentType;

    private LocalDateTime from;
    private LocalDateTime to;

    private String level;
    private String logger;
}
```

Тогда:

```java
List<LogDto> getLogs(LogQuery query);
```

Это гораздо легче расширять.

---

# 6. Test Run

Это важнейшая часть новой архитектуры.

Нужно иметь отдельную сущность:

```java
public class TestRunDto {

    private UUID id;

    private String testRunName;

    private String testSuiteName;

    private EnvironmentType environmentType;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
```

### `EnvironmentType`

Например:

```java
public enum EnvironmentType {

    DEV,
    ST,
    FT
}
```

Таким образом, в одной SQLite базе совершенно спокойно могут находиться:

```text
run-001 / DEV
run-002 / ST
run-003 / FT
run-004 / DEV
run-005 / ST
```

И они не конфликтуют.

---

# 7. Lifecycle Test Run

`LoggerExtension` должен создавать test run в `BeforeAll`.

Например:

```text
BeforeAll
    │
    ▼
createTestRun()
    │
    ├── UUID
    ├── testRunName
    ├── testSuiteName
    ├── environmentType
    └── startedAt = now()
```

После завершения всех тестов:

```text
AfterAll
    │
    ▼
finishTestRun()
    │
    └── finishedAt = now()
```

Получается:

```text
┌─────────────────────────────────────────────┐
│ Test Run                                    │
│                                             │
│ startedAt                                   │
│      │                                      │
│      ├── Test 1                             │
│      ├── Test 2                             │
│      ├── Test 3                             │
│      ├── ...                                │
│      └── Test 100                           │
│                                      │      │
│                              finishedAt      │
└─────────────────────────────────────────────┘
```

---

# 8. TestRunStore

Я бы вынес работу непосредственно с lifecycle run в отдельный компонент.

```java
public interface TestRunStore {

    UUID startTestRun(
        String testRunName,
        String testSuiteName,
        EnvironmentType environmentType
    );

    void finishTestRun(UUID testRunId);

    Optional<TestRunDto> getTestRun(UUID testRunId);

    Optional<TestRunDto> getTestRun(String testRunName);

    List<TestRunDto> getTestRuns(
        LocalDateTime from,
        LocalDateTime to
    );
}
```

Реализация:

```java
public class SqliteTestRunStore implements TestRunStore {
}
```

---

# 9. Взаимодействие Extension → TestRunStore

В `LoggerExtension`:

```java
@BeforeAll
void beforeAll() {

    testRunId = testRunStore.startTestRun(
        testRunName,
        testSuiteName,
        environmentType
    );
}
```

И:

```java
@AfterAll
void afterAll() {

    loggerService.collectAndStoreLogs(testRunId);

    testRunStore.finishTestRun(testRunId);
}
```

Но здесь есть **важный нюанс**.

Если логи нужны за весь интервал:

```text
BeforeAll ───────────────────────────── AfterAll
             весь runtime
```

то правильнее:

```text
BeforeAll
   │
   ├── start TestRun
   │
   ├── tests...
   │
   └── AfterAll
          │
          ├── getLogs()
          ├── parse
          ├── save
          └── finish TestRun
```

То есть `startedAt` фиксируется **до выполнения первого теста**, а `finishedAt` — **после получения последних логов**.

---

# 10. Как получить логи за TestRun

Ты задаёшь правильный вопрос: где должен находиться такой метод?

Я бы разделил два понятия.

### Persistent retrieval

```java
logStoreService.getLogs(testRunId);
```

Он только читает уже сохранённые данные.

### Runtime collection

```java
loggerService.collectLogsForTestRun(testRunId);
```

Он:

1. получает runtime logs;
2. парсит их;
3. сохраняет их в persistent store;
4. при необходимости прикладывает raw logs к Allure.

Поэтому **получение логов из runtime pod должно оставаться в `LoggerService`**, а не в `LogStoreService`.

`LogStoreService` не должен вообще знать, существует ли Kubernetes pod, файл, HTTP endpoint или другой источник.

---

# 11. Рекомендуемый flow

Для полного test run:

```text
                    BeforeAll
                       │
                       ▼
              TestRunStore.start()
                       │
                       ▼
                 testRunId
                       │
             ┌─────────┴─────────┐
             │                   │
             ▼                   ▼
          Test 1              Test 100
             │                   │
             └─────────┬─────────┘
                       │
                    AfterAll
                       │
                       ▼
              LoggerService
                       │
                  getLogs()
                       │
                       ▼
                   LogParser
                       │
                       ▼
                  List<LogDto>
                       │
                       ▼
               LogStoreService
                       │
                       ▼
                    SQLite
                       │
                       ▼
              TestRunStore.finish()
```

---

# 12. `LogDto`

В существующий `LogDto` добавить обязательный контекст:

```java
public class LogDto {

    private UUID id;

    private UUID testRunId;

    private String testRunName;

    private String testSuiteName;

    private EnvironmentType environmentType;

    private LocalDateTime timestamp;

    private String level;

    private String logger;

    private String message;

    private String stackTrace;
}
```

Из перечисленных тобой полей я бы **обязательно оставил `testRunId`**, даже если есть `testRunName`.

Причина:

```text
testRunName = "Regression"
```

может встретиться тысячу раз.

А:

```text
testRunId = 7b5...
```

однозначно идентифицирует конкретный прогон.

---

# 13. Нужно ли хранить `testRunName` и `testSuiteName` в каждом LogDto?

На уровне DTO — **да, это удобно**.

Но на уровне БД я бы не обязательно дублировал их в `log_entry`.

Лучше нормализованная схема:

```text
test_run
──────────────
id
name
suite_name
environment_type
started_at
finished_at

       │
       │ 1:N
       ▼

log_entry
──────────────
id
test_run_id
timestamp
level
logger
message
stack_trace
fingerprint
```

`testRunName`, `testSuiteName`, `environmentType` получаются через `test_run_id`.

При формировании `LogDto` repository делает mapping обратно.

---

# 14. SQLite schema

Минимальный первый вариант:

```sql
CREATE TABLE test_run (
    id TEXT PRIMARY KEY,
    test_run_name TEXT NOT NULL,
    test_suite_name TEXT,
    environment_type TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT
);
```

```sql
CREATE TABLE log_entry (
    id TEXT PRIMARY KEY,
    test_run_id TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    level TEXT,
    logger TEXT,
    message TEXT,
    stack_trace TEXT,
    fingerprint TEXT,

    FOREIGN KEY (test_run_id)
        REFERENCES test_run(id)
);
```

И индексы:

```sql
CREATE INDEX idx_log_test_run
    ON log_entry(test_run_id);

CREATE INDEX idx_log_timestamp
    ON log_entry(timestamp);

CREATE INDEX idx_log_environment
    ON test_run(environment_type);

CREATE INDEX idx_log_fingerprint
    ON log_entry(fingerprint);
```

Последний индекс пригодится для `GetUniqueLogs`.

---

# 15. `GetUniqueLogs`

Я бы **не помещал реализацию этой логики в `LoggerService`**.

`LoggerService` отвечает за получение runtime logs.

`LogStoreService` — за получение сохранённых logs.

А анализ лучше вынести в:

```java
LogAnalysisService
```

Например:

```java
public interface LogAnalysisService {

    List<LogDto> getUniqueLogs(
        LocalDateTime from,
        LocalDateTime to
    );

    List<LogDto> getUniqueLogs(
        UUID testRunId
    );

    List<LogDto> getRelevantLogs(
        UUID testRunId
    );
}
```

И тогда:

```text
LoggerService
     │
     ▼
LogStoreService
     │
     ▼
LogAnalysisService
```

Это позволит потом без боли добавить:

```text
fingerprint
category
known error
frequency
severity
environment correlation
```

---

# 16. `GetRelevantLogs`

Здесь я бы особенно не смешивал runtime и persistence.

`GetRelevantLogs` должен работать с уже накопленной историей:

```text
SQLite
   │
   ▼
LogStoreService
   │
   ▼
LogAnalysisService
   │
   ├── current run
   ├── previous runs
   ├── environment
   ├── fingerprint
   └── category
```

Например:

```java
List<LogDto> relevant =
    logAnalysisService.getRelevantLogs(testRunId);
```

После чего:

```java
allureAttachmentService.attach(relevant);
```

---

# 17. Allure

Allure остаётся полностью отдельным output layer.

```text
                 LogDto[]
                    │
          ┌─────────┴──────────┐
          ▼                    ▼
     LogStoreService         Allure
          │
          ▼
       SQLite
```

Таким образом:

**SQLite:**

> Что произошло исторически?

**Allure:**

> Что показать пользователю в отчёте этого прогона?

Это две разные задачи.

---

# 18. Что должно быть публичным API

### `TestRunStore`

```java
UUID startTestRun(
    String testRunName,
    String testSuiteName,
    EnvironmentType environmentType
);

void finishTestRun(UUID testRunId);

Optional<TestRunDto> getTestRun(UUID testRunId);

List<TestRunDto> getTestRuns(
    LocalDateTime from,
    LocalDateTime to
);
```

### `LogStoreService`

```java
void saveLogs(List<LogDto> logs);

List<LogDto> getLogs();

List<LogDto> getLogs(UUID testRunId);

List<LogDto> getLogs(
    LocalDateTime from,
    LocalDateTime to
);

List<LogDto> getLogs(
    LocalDateTime from,
    LocalDateTime to,
    EnvironmentType environmentType
);

List<LogDto> getLogs(
    UUID testRunId,
    EnvironmentType environmentType
);

List<LogDto> getLogs(
    String testSuiteName,
    EnvironmentType environmentType
);
```

И в перспективе:

```java
List<LogDto> getLogs(LogQuery query);
```

Я бы именно **`LogQuery` оставил целевой архитектурой**, даже если на первом этапе нужны overload'ы.

---

# 19. Что НЕ должно находиться в `LogStoreService`

Не помещать туда:

```text
❌ getLogsFromPod()
❌ HTTP calls
❌ Kubernetes logic
❌ parsing raw String
❌ Allure attachments
❌ test lifecycle
❌ log normalization
❌ error categorization
```

Его задача:

```text
DTO → persistence
persistence → DTO
```

---

# 20. Финальная ответственность классов

| Компонент                 | Ответственность                    |
| ------------------------- | ---------------------------------- |
| `LoggerExtension`         | lifecycle тестового прогона        |
| `LoggerService`           | получение runtime logs             |
| `LogParser`               | raw logs → DTO                     |
| `LogStoreService`         | публичная работа с persistent logs |
| `LogStoreRepository`      | SQL/persistence                    |
| `TestRunStore`            | lifecycle и хранение TestRun       |
| `Sqlite...`               | конкретная реализация SQLite       |
| `LogAnalysisService`      | unique/relevant/categorization     |
| `AllureAttachmentService` | attachments в Allure               |

И итоговая зависимость должна выглядеть так:

```text
┌───────────────────────┐
│   LoggerExtension     │
│                       │
│ BeforeAll / AfterAll  │
└───────────┬───────────┘
            │
            ▼
┌───────────────────────┐
│    LoggerService      │
│                       │
│ getLogs()             │
│ collectLogs()         │
└───────┬─────────┬─────┘
        │         │
        ▼         ▼
┌────────────┐  ┌──────────────────┐
│ LogParser  │  │ AllureAttachment │
└─────┬──────┘  └──────────────────┘
      │
      ▼
┌───────────────────────┐
│   LogStoreService     │
└───────┬───────────────┘
        │
   ┌────┴─────────────┐
   ▼                  ▼
┌──────────────┐ ┌──────────────┐
│ LogRepository│ │ TestRunStore │
└──────┬───────┘ └──────┬───────┘
       │                │
       └───────┬────────┘
               ▼
          ┌──────────┐
          │  SQLite  │
          └──────────┘

               ▲
               │
     ┌─────────┴──────────┐
     │ LogAnalysisService │
     │                    │
     │ GetUniqueLogs      │
     │ GetRelevantLogs    │
     └────────────────────┘
```

### Главное решение

**Я бы реализовал `LogStoreService` как отдельный сервис и `LogStoreRepository` как ещё более низкий слой.**

Не стоит превращать существующий `LoggerService` в persistence-service.

При этом `LoggerService` **может вызывать `LogStoreService` после парсинга**, то есть связь между ними будет dependency composition, а не наследование:

```java
LoggerService
    → LogParser
    → LogStoreService
```

А `LoggerExtension` управляет `TestRunStore` на `BeforeAll/AfterAll`.

Так архитектура одновременно решает текущую задачу — **сохранить все логи локального прогона и прикрепить их к Allure** — и оставляет правильную точку расширения для будущих `GetUniqueLogs`, `GetRelevantLogs` и категоризации ошибок.
