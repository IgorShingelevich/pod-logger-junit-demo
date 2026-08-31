# Пакет `com.example.podlogger.store.repository`

Порты persist: INSERT/SELECT без SQL в сигнатурах. Реализации — [`sqlLite.md`](../sqlite/sqlLite.md). Вызывающий слой — [`store.md`](../store.md).

Модуль: [`junit-pod-logger.md`](../../../../../../../../junit-pod-logger.md).  
Какие таблицы стоят за портами: [`PersistentLogStoreStory.md`](../../../../../../../../../docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md) §5.

Отдельного package-info в этом пакете нет: два интерфейса — вся поверхность.

## Интерфейсы

| Интерфейс | Таблица | Операции |
| --- | --- | --- |
| `LogStoreRepository` | `log_entry` | `saveAll`, `find(LogQuery)` |
| `TestRunRepository` | `test_run` | `insert`, `finish`, `findById` / `findByName` / `findByStartedBetween` / `findByEnvironment`, `deleteClosedOlderThan` |

`saveAll`: дубли по unique index молча игнорируются (`INSERT OR IGNORE`). `find` делает JOIN на `test_run`, чтобы вернуть имя/suite/environment в `PodLogDto`. Порядок — по возрастанию timestamp.

`finish`: `COALESCE(finished_at, ?)` — повторный finish время не сдвигает, статус `FINISHED`.

`deleteClosedOlderThan(cutoff)`: сначала `log_entry` закрытых старых run, затем сами `test_run`. Открытые run не удаляет. Возвращает число удалённых run.

Имя прогона неуникально: `findByName` возвращает список. UUID прогона уникален.

## Границы

SQL, PRAGMA, путь к файлу и DDL в этот пакет не входят. Kubernetes, Allure, parse — тоже. `relevantEvents` repository не читает и не пишет.

## Приёмка

Отдельного теста на интерфейсы нет: их закрывает `PersistentLogStoreTest` через `DefaultPodStoreService` / `DefaultTestRunStore`. Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../../docs/PodLoggerJunitDemoTest.md) §3.
