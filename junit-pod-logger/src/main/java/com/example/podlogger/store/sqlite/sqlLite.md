# Пакет `com.example.podlogger.store.sqlite`

SQLite-реализация repository: файл, WAL, DDL, JDBC. Каноническая схема и смысл колонок — [`PersistentLogStoreStory.md`](../../../../../../../../../docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md) §5; этот файл не повторяет полный `CREATE TABLE`.

Модуль: [`junit-pod-logger.md`](../../../../../../../../junit-pod-logger.md).  
Порты: [`repository.md`](../repository/repository.md). Путь к файлу: [`store.md`](../store.md) (`StorePathResolver`).  
Команды чтения БД: [`PodLoggerJunitDemoCommands.md`](../../../../../../../../../docs/PodLoggerJunitDemoCommands.md) → Store.

## Классы

| Класс | Роль |
| --- | --- |
| `SqliteDataSourceFactory` | создать файл и каталоги, `jdbc:sqlite:`, WAL, `busy_timeout=5000` |
| `SchemaMigrator` | идемпотентный DDL (`IF NOT EXISTS`) |
| `SqliteLogStoreRepository` | `LogStoreRepository`: INSERT OR IGNORE, SELECT + JOIN |
| `SqliteTestRunRepository` | `TestRunRepository` |
| `JdbcSupport` | callback вокруг `Connection` |

Bean `DataSource` поднимает [`PodLoggerConfiguration`](../../podLogger.md) только если в контексте ещё нет другого DataSource. Драйвер: `org.xerial:sqlite-jdbc:3.47.1.0`.

## Что есть в схеме (без DDL-копии)

Две таблицы: `test_run` (PK `id` TEXT = UUID) и `log_entry` (FK `test_run_id`). Unique index дедупа: `idx_log_dedup (test_run_id, timestamp, fingerprint)`. Колонки Events **нет**. Timestamp в TEXT формата `StoreTime` (`yyyy-MM-dd'T'HH:mm:ss.SSS`) — лексикографически сравним как время.

Повторный `SchemaMigrator.migrate` на существующем файле безопасен. Смена SQLite на другую СУБД и export JSON — вне v1.

## Границы

Query-сборка `LogQuery` живёт в `SqliteLogStoreRepository`, не в `PodLoggerService`. Publish/list Kubernetes Events этот пакет не делает. Прочитанный `PodLogDto.relevantEvents` = `null`.

## Риски миграции

| Риск | Симптом | Где смотреть | Решение |
| --- | --- | --- | --- |
| Ожидание, что SQLite-слой сам адаптируется к чужим DTO | схема мигрируется успешно, но в `log_entry` попадают не те значения или `null` | `store.md`, `parser/logParser.md`, `client/openshiftClient.md` | сначала зафиксировать состав полей `PodLogDto`, потом проверять SQLite |
| Недоступен путь к файлу или права на каталог | fail-fast на создании DataSource или миграции схемы | `SqliteDataSourceFactory`, `SchemaMigrator`, `PodLoggerConfiguration` | смотреть resolved `storePath`, права на каталог, WAL |
| Новая СУБД в банковском контуре | текущий JDBC/PRAGMA слой неприменим | `repository.md` | писать новую repository-реализацию, а не перегружать этот пакет |

### Миграционный чек-лист

1. Включить `DEBUG` для `com.example.podlogger.store.sqlite` и `com.example.podlogger`.
2. Проверить логи `Creating pod logger DataSource`, `Opening SQLite DataSource`, `Starting SQLite schema migration`.
3. Если schema поднялась, но данные не те, перейти в `store.md` и `parser/logParser.md`: причина почти наверняка выше по стеку.

### Профит для агента в новом контуре

- Этот раздел фиксирует ваш ожидаемый тезис: прямых рисков у SQLite немного, потому что библиотека сама контролирует схему и читает только свои поля.
- Агент понимает, что SQLite здесь опосредована входным mapping: нужно знать, какие поля пришли и что именно сохраняется локально.

## Приёмка

`PersistentLogStoreTest` бьёт в store API и тем самым в эти реализации (временный файл SQLite, не Docker). Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../../docs/PodLoggerJunitDemoTest.md) §3.
