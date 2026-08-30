# -*- coding: utf-8 -*-
from pathlib import Path

path = Path(__file__).with_name("PersistentLogStorePRD.md")
text = path.read_text(encoding="utf-8")
marker = "### 10.3 Demo `OrderErrorIT`"
idx = text.find(marker)
if idx < 0:
    raise SystemExit("marker not found")

tail = r'''### 10.3 Demo `OrderErrorIT` и демонстрационный store-тест

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
| AC-09 | Ошибка store не меняет статус теста (swallow + log) | unit на service с broken DataSource (этап 5–6) |
| AC-10 | Primary store = SQLite file; не resources-tree | path `target/*.sqlite` + `.gitignore` |
| AC-11 | Optional export run → JSON (если Q1=да) | `shouldExportTestRun` |
| AC-12 | `GetUniqueLogs` в v1 нет как реализации | отсутствие класса |
| AC-13 | README: path sqlite, persist-флаг, Allure vs store | doc review |
| AC-14 | Демо-класс §13.2 зелёный | `mvn -pl junit-pod-logger test` |

### 13.2 Требования к демо-тестовому классу

| Атрибут | Требование |
| --- | --- |
| Имя | `com.example.podlogger.store.PodStoreServiceDemoTest` |
| Модуль | `junit-pod-logger` (`src/test/java`) |
| Инфраструктура | **Без** Docker, K3s, Fabric8, сети. Temp SQLite + сервисы store |
| Назначение | Одновременно **приёмка** и **демонстрация** save / query / (export) |
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
| `shouldExportTestRun` | только если AC-11 in scope: JSON файл + parse |

Класс **не** обязан поднимать `PodLoggerExtension` / Spring Boot. Ручная сборка store в `@BeforeEach` предпочтительна (Q4).

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
| Рост sqlite при частых локальных прогонах | `limit` на query; purge вне v1 |
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

## 18. Open questions — блокируют полноту и непротиворечивость

Ответы нужно зафиксировать до закрытия соответствующих этапов. Пока нет ответа — действует **Proposed default**.

| ID | Вопрос | Зачем для AC | Proposed default |
| --- | --- | --- | --- |
| Q1 | Export JSON одного run в v1 — **да / нет**? | AC-11, этап 4 | **Да**, `target/pod-logs-export/{testRunId}.json` |
| Q2 | Persist на зелёных тестах при `collectOnFailOnly=true`: всегда / только fail / отдельный `persistOnFailOnly`? | Allure vs store | **Всегда**, если `persist=true` |
| Q3 | Файл БД в CI: только workspace / ещё и Jenkins artifact? | README, retention | workspace `target/`, artifact optional |
| Q4 | `PodStoreServiceDemoTest`: Spring или ручная сборка? | §13.2 | **Ручная сборка** |
| Q5 | В DTO оба `runName` и `testRunName`, или одно поле? | AC-04 | оба в DTO, одна колонка БД |
| Q6 | Scope первого PR: только GATE этапа 3 или сразу 0–6? | планирование | **сначала этап 3 + DemoTest**, затем 5–6 |
| Q7 | Purge/TTL API в v1? | рост файла | **нет** |
| Q8 | Демо только в `junit-pod-logger` или ещё показ в `demo-tests`? | AC-14 | **обязательно** `junit-pod-logger` |
| Q9 | Время: UTC `LocalDateTime` text или `Instant`? | schema | **UTC LocalDateTime text** |
| Q10 | Ошибка `startTestRun` в beforeAll: падать классу или warn + без persist? | AC-09 | **warn + disable persist** |
'''

path.write_text(text[:idx] + tail, encoding="utf-8")
print("patched", path)
