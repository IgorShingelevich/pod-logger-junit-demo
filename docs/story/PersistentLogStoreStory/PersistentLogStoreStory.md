# PRD: Persistent Log Store для `@PodLogger`

**Статус:** реализовано в `junit-pod-logger` (as-built).  
**Канонический документ фичи:** этот файл.  
**Общий PRD:** [`docs/prd/podLoggerJunitDemoPRD.md`](../../prd/podLoggerJunitDemoPRD.md).  
**Смежная фича:** [`OpenShiftEventHandlingPRD.md`](../OpenShiftEventHandling/OpenShiftEventHandlingPRD.md).  
**База v1:** один файл SQLite, не дерево папок и не H2. Export JSON в v1 нет.

---

## 1. Цель

Persistent-слой рядом с runtime-сбором (не вместо него):

1. сохранять распарсенные pod logs текущего и прошлых прогонов;
2. фиксировать контекст test run (имя, suite, стенд, окно `BeforeAll → AfterAll`);
3. искать логи по времени, run, suite, environment, service, related test;
4. отдавать логи invocation-окна и всего прогона;
5. не ломать Allure per-invocation аттачи;
6. оставить точку расширения для `GetUniqueLogs` / `GetRelevantLogs` (не входят в v1).

---

## 2. Границы

| Можно | Нельзя |
| --- | --- |
| SQL в `store.sqlite.*` | SQL в `PodLoggerService` / `PodLoggerExtension` |
| Query API на `PodStoreService` | `getLogsFromPod` / Kubernetes на store |
| Lifecycle на `TestRunStore` | Чтение логов на `TestRunStore` |
| Merge persistent+runtime | На store: только в `PodLoggerService.collectAndMergeLogsForTestRun` |
| `relevantEvents` в Allure JSON | Колонка events в SQLite |

Один gate логов: `CollectGate.shouldCollect = !collectOnFailOnly \|\| failed`.  
Events этим PRD не описываются — см. OpenShift Event Handling.

---

## 3. As-built компоненты

| Класс | Файл | Роль |
| --- | --- | --- |
| `PodStoreService` | `store/PodStoreService.java` | save / get / `getLogsForWholeRun` / `deleteOlderThan` |
| `DefaultPodStoreService` | реализация | |
| `TestRunStore` | lifecycle metadata | |
| `LogStoreRepository` / `SqliteLogStoreRepository` | `log_entry` | |
| `TestRunRepository` / `SqliteTestRunRepository` | `test_run` | |
| `StorePathResolver` | путь к файлу | |
| `SchemaMigrator` | DDL | |
| `FingerprintUtil` | SHA-256 без `relevantEvents` | |
| Приёмка | `PersistentLogStoreTest` | без Docker |

`LogParser` вынесен из `OpenshiftClient`. Allure — `LogAllureAttachmentService`.

---

## 4. Файл БД

По умолчанию: `{user.dir}/target/pod-logger-store.sqlite`.

Первый найденный выигрывает:

1. system property `pod.logger.store-path`
2. env `POD_LOGGER_STORE_PATH`
3. `PodLoggerProperties.storePath`

Файлы `*.sqlite*` в `.gitignore`. CI-artifact БД не требуется.

---

## 5. Схема

```sql
CREATE TABLE test_run (
    id TEXT PRIMARY KEY,
    test_run_name TEXT NOT NULL,
    test_suite_name TEXT,
    environment_type TEXT NOT NULL,
    service_type TEXT,
    namespace TEXT,
    pod_label_selector TEXT,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL
);

CREATE TABLE log_entry (
    id TEXT PRIMARY KEY,
    test_run_id TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    level TEXT, logger TEXT, message TEXT, stack_trace TEXT,
    thread_name TEXT, trace_id TEXT, span_id TEXT,
    pod_name TEXT, namespace TEXT, container_name TEXT,
    service_type TEXT,
    related_test_class TEXT, related_test_method TEXT, test_display_name TEXT,
    test_failed INTEGER,
    fingerprint TEXT NOT NULL,
    FOREIGN KEY (test_run_id) REFERENCES test_run(id)
);

CREATE UNIQUE INDEX idx_log_dedup ON log_entry(test_run_id, timestamp, fingerprint);
```

Timestamp в TEXT: `yyyy-MM-dd'T'HH:mm:ss.SSS` (`StoreTime`).  
`relevantEvents` в таблицу не входит.

---

## 6. Публичный API `PodStoreService`

Overload-ы — переходный business API. Целевой контракт — `getLogs(LogQuery)`.

```java
void saveLogs(List<PodLogDto> logs);                    // testRunId уже на каждой записи
void saveLogs(UUID testRunId, List<PodLogDto> logs);    // проставляет контекст run
List<PodLogDto> getLogs();
List<PodLogDto> getLogs(UUID testRunId);
List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to);
List<PodLogDto> getLogs(LocalDateTime from, LocalDateTime to, EnvironmentType env);
List<PodLogDto> getLogs(UUID testRunId, EnvironmentType env);
List<PodLogDto> getLogs(String testSuiteName, EnvironmentType env);
List<PodLogDto> getLogs(String testRunName, String testSuiteName, EnvironmentType env);
List<PodLogDto> getLogs(LogQuery query);
List<PodLogDto> getLogsForWholeRun(UUID testRunId);     // в v1 = getLogs(testRunId)
int deleteOlderThan(int days);                          // только закрытые run
```

Запрещено: `syncRuntimeAndPersistentLogs`, `getLogsFromPod`.

`deleteOlderThan(days)`: возраст по `test_run.started_at`, удаляет закрытый run и его `log_entry`. Открытые (`finished_at IS NULL`) не трогает. `days >= 1`.

---

## 7. Публичный API `TestRunStore`

```java
UUID startTestRun(String name, String suite, EnvironmentType env, String serviceType);
UUID startTestRun(TestRunDto draft);
void finishTestRun(UUID testRunId);
Optional<TestRunDto> getTestRun(UUID testRunId);
List<TestRunDto> getTestRuns(String testRunName);       // имя неуникально
List<TestRunDto> getTestRuns(LocalDateTime from, LocalDateTime to);
List<TestRunDto> getTestRuns(EnvironmentType environmentType);
```

Чтения логов на этом интерфейсе нет (`getRunWindowLogs` не входит).

---

## 8. DTO

`PodLogDto` — поля лога из поды плюс контекст: `testRunId`, `runName`/`testRunName`, `testSuiteName`, `relatedTestClass`/`relatedTestMethod`, `environmentType`, `serviceType`, `fingerprint`, `relevantEvents` (только runtime).

`EnvironmentType`: `DEV`, `ST`, `FT`, `LOCAL`.

`LogQuery`: все фильтры опциональны; `runName` и `testRunName` — синонимы (`effectiveRunName()`).

Parser заполняет только JSON stdout. Контекст прогона ставит service/store после parse.

---

## 9. Lifecycle store

- `beforeAll` — `startTestRun` (`startedAt`, статус `STARTED`); ошибка — fail-fast.
- `afterEach` — при открытом CollectGate и (для fail) доступной поде: `saveLogs`.
- `afterAll` — `collectAndMergeLogsForTestRun` (дедуп timestamp+fingerprint, дозапись новых) → `finishTestRun`.

Окно invocation: `[start-2s, end+2s]`. Окно run: `[startedAt-2s, now+2s]`.

---

## 10. Приёмка

Класс `PersistentLogStoreTest` (display name **persistent log store test**):

- start/finish run;
- save/get по id, времени, environment, suite, runName, related test, комбинированный `LogQuery`;
- `getLogsForWholeRun`;
- дедуп повторного save;
- reject save без `testRunId`;
- `deleteOlderThan` только закрытые старые;
- зелёный класс + `collectOnFailOnly` не пишет `log_entry`;
- упавший `@PodLogger` класс пишет строки, читаемые из SQLite.

Кластер не нужен: stub `OpenshiftClient` в `PersistentLogStoreHarness`.

---

## 11. Вне v1

`LogAnalysisService.getUniqueLogs` / `getRelevantLogs`, смена SQLite на другую СУБД, export JSON, CI-артефакт файла БД.
