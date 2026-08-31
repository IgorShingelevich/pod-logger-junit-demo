# Пакет `com.example.podlogger.store`

Application-слой persist: сохранить и найти логи, вести lifecycle прогона. Не ходит в Kubernetes и не пишет в Allure. SQL наружу не отдаёт — его прячут [`repository`](repository/repository.md) и [`sqlite`](sqlite/sqlLite.md).

Модуль: [`junit-pod-logger.md`](../../../../../../../junit-pod-logger.md).  
Норматив API, схемы и retention: [`PersistentLogStoreStory.md`](../../../../../../../../docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md).  
Кто вызывает save/merge: [`podLogger.md`](../podLogger.md).

`GetUniqueLogs` / `GetRelevantLogs` / `getLogsFromPod` / `syncRuntimeAndPersistentLogs` на этих интерфейсах **запрещены**. Merge runtime+persistent делает `PodLoggerService.collectAndMergeLogsForTestRun`, не store.

## Классы

| Класс | Роль |
| --- | --- |
| `PodStoreService` / `DefaultPodStoreService` | save/get логов, `getLogsForWholeRun`, `deleteOlderThan` |
| `TestRunStore` / `DefaultTestRunStore` | start/finish/get прогона; чтения логов нет |
| `StorePathResolver` | путь к файлу SQLite |
| `FingerprintUtil` | SHA-256 по `level`+`logger`+`message`+`stackTrace` |
| `StoreTime` | TEXT `yyyy-MM-dd'T'HH:mm:ss.SSS` |
| `EnvironmentType` | `DEV` / `ST` / `FT` / `LOCAL` — одна база |

## `store.dto` (отдельного MD нет)

| DTO | Роль |
| --- | --- |
| `LogQuery` | целевой query-контракт; все фильтры опциональны; `runName` и `testRunName` — синонимы (`effectiveRunName()` предпочитает `runName`) |
| `TestRunDto` | metadata строки `test_run` |
| `MergedLogResult` | результат merge в service (не repository) |

Overload-ы `getLogs(...)` — переходный business API. Расширяемый контракт — `getLogs(LogQuery)`. Полный список сигнатур — story §6–§7, не копируется здесь.

`saveLogs` без `testRunId` — `IllegalArgumentException`. `deleteOlderThan(days)`: `days >= 1`; возраст по `test_run.started_at`; открытые run (`finished_at IS NULL`) не трогает.

## Путь к файлу

Первый найденный выигрывает:

1. system property `pod.logger.store-path`
2. env `POD_LOGGER_STORE_PATH`
3. `PodLoggerProperties.storePath`
4. дефолт `{user.dir}/target/pod-logger-store.sqlite`

`*.sqlite*` в `.gitignore`. CI-artifact файла БД в v1 не требуется.

## Fingerprint и Events

`relevantEvents` в payload fingerprint **не** входят — иначе повторный save того же stdout сломает дедуп. Колонки Events в `log_entry` нет; прочитанные из БД DTO имеют `relevantEvents=null`.

## Риски миграции

| Риск | Симптом | Где смотреть | Решение |
| --- | --- | --- | --- |
| Входные поля логов изменились, а store остался прежним | в SQLite появляются строки с `null` в бизнес-полях или меняется fingerprint/dedup | `parser/logParser.md`, `DefaultPodStoreService`, `FingerprintUtil` | сначала зафиксировать mapping входных полей, потом проверять persist |
| `saveLogs` вызывается без `testRunId` | fail-fast `IllegalArgumentException` в store path | `DefaultPodStoreService`, `DefaultTestRunStore` | чинить lifecycle `beforeAll` / `afterEach`, а не SQLite schema |
| Run metadata не находится по `testRunId` | enrich/save падает на unknown run | `TestRunStore`, `PodLoggerExtension`, `PodLoggerService` | смотреть создание run в `beforeAll` и finish path |
| Ожидание, что SQLite сама решит схему чужих DTO | миграция проходит, но данные сохраняются не в том виде, как ожидается | `sqlLite.md` | помнить, что SQLite хранит только то, что library уже смэпила в `PodLogDto` |

### Миграционный чек-лист

1. Сначала проверить, какие поля реально приходят из parser/client в `PodLogDto`.
2. Включить `DEBUG` для `com.example.podlogger.store` и `com.example.podlogger`.
3. Проверить логи `saveLogs(...)`, `startTestRun`, `finishTestRun`, `persistLogs`.
4. После адаптации parser убедиться, что fingerprint, query и дедуп работают на новых данных.

### Профит для агента в новом контуре

- SQLite здесь описана как зависимая часть: прямой риск ниже, чем у parser/client, но именно здесь проявятся косвенные эффекты неправильного mapping.
- Агент сразу видит, что проблема обычно не в DDL, а в том, какие поля он пытается сохранить.

## Приёмка этого пакета

`PersistentLogStoreTest` (display name класса **persistent log store test**): start/finish, save/get, `LogQuery`, дедуп, reject без `testRunId`, `deleteOlderThan`, зелёный класс с `collectOnFailOnly` не пишет `log_entry`. Stub `OpenshiftClient` в `PersistentLogStoreHarness`. Docker не нужен.

Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../docs/PodLoggerJunitDemoTest.md) §3.
