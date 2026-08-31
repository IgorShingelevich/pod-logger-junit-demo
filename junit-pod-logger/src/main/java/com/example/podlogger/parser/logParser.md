# Пакет `com.example.podlogger.parser`

Построчный разбор сырого dump `pods/log` в `List<PodLogDto>`. Пакет не знает ни о JUnit, ни об Allure, ни о SQLite, ни о Kubernetes Events.

Модуль: [`junit-pod-logger.md`](../../../../../../../junit-pod-logger.md).  
Кто зовёт parse: [`OpenshiftClient.getLog()`](../client/openshiftClient.md).  
Какие поля DTO отсюда не приходят: [`PersistentLogStoreStory.md`](../../../../../../../../docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md) §8.

`pods/log` возвращает **сырой текст контейнера**, а не нормализованный Kubernetes DTO. Поэтому `PodLogDto` в этом пакете отражает не кластерный стандарт, а тот JSON, который пишет приложение.

## Классы

| Класс | Роль |
| --- | --- |
| `LogParser` | контракт best-effort: `parse(rawDump)` → список DTO; `null`/blank → пустой список |
| `JsonLogParser` | Spring `@Component`: JSON-кандидаты по строкам, continuation в `stackTrace`, debug-диагностика |

Невалидный JSON логируется на debug вместе с throwable и **пропускается**, вызов не валит. Строки kube-preamble до первого JSON в DTO не попадают.

## Участники пайплайна

| Класс | Метод | Шаг |
| --- | --- | --- |
| `OpenshiftClient` | `fetchRawLog()` | читает текст `pods/log` выбранной поды |
| `OpenshiftClient` | `getLog()` | вызывает `fetchRawLog()` и делегирует parse |
| `OpenshiftClient` | `parseLogDump(String)` | тот же parse без Fabric8, удобно для миграции по сохранённому dump |
| `LogParser` | `parse(String)` | контракт parse-слоя |
| `JsonLogParser` | `parse(String)` | идёт по dump построчно, классифицирует строки и строит `PodLogDto` |
| `PodLogDto` | `stackTrace` | принимает continuation-строки после успешно распознанного JSON |

Пайплайн:

1. `OpenshiftClient.fetchRawLog()` получает один большой текстовый dump.
2. `OpenshiftClient.getLog()` передаёт его в `LogParser`.
3. `JsonLogParser.parse()` читает dump построчно без `split("\\R")`.
4. Если `trim()` строки начинается с `{`, строка считается новым JSON-кандидатом и уходит в `ObjectMapper.readValue(..., PodLogDto.class)`.
5. Если JSON распознан, этот DTO становится «текущим».
6. Следующие non-JSON строки приклеиваются к `current.stackTrace`, пока не встретится новый JSON-кандидат.
7. Если JSON битый по типам или формату, строка логируется на debug и пропускается.

## Что парсер не заполняет

Контекст прогона (`testRunId`, suite, environment, related test, fingerprint) и `relevantEvents` из stdout не приходят и здесь не ставятся. Поле `podName` в JSON `demo-app` отсутствует — в DTO остаётся `null`, если его не было в строке.

`@JsonIgnoreProperties(ignoreUnknown=true)` на `PodLogDto`: неизвестные ключи с поды не падают.

## Что можно переносить без правки

Если целевой сервис пишет строки с ключами `timestamp`, `level`, `logger`, `message` и ISO timestamp формата `yyyy-MM-dd'T'HH:mm:ss.SSS`, `PodLogDto` и текущий parse можно оставить без изменений.

Если на новом проекте меняются **имена** ключей, **типы** (`epoch millis` вместо ISO) или структура JSON, придётся адаптировать либо сам DTO (`@JsonAlias`, `@JsonProperty`), либо mapping в parse-слое. Kubernetes здесь ничего не гарантирует.

## Возможные риски парсинга

| Риск | Что произойдёт сейчас | Что учитывать при переносе |
| --- | --- | --- |
| Другие имена полей (`@timestamp`, `severity`, `msg`) | строка распарсится, но поля DTO останутся `null` | добавить alias или mapping под формат сервиса |
| Другой тип `timestamp` | Jackson бросит исключение, строка уйдёт в skip debug | менять формат даты или custom deserializer |
| Лишние JSON-ключи | безопасно игнорируются | DTO расширять не обязательно |
| Меньше полей, чем в демо | объект создастся, missing-поля будут `null` | downstream должен быть готов к `null` |
| Многострочный stack trace после JSON | continuation приклеится в `PodLogDto.stackTrace` | проверить, как целевой сервис печатает `Caused by`, `Suppressed`, пустые строки |
| Pretty-printed JSON в несколько строк | текущий алгоритм не собирает один объект из нескольких строк | нужен другой parser-state machine, если сервис логирует не JSON-per-line |
| Нешумовая non-JSON строка после валидного JSON | она будет приклеена к `stackTrace` текущего DTO | это осознанный эвристический компромисс v1 |
| Очень большой dump | исходный `String` уже в памяти; parser больше не делает `split("\\R")`, но всё ещё работает с полным dump | следующий шаг для сверхбольших логов — stream from source |
| Debug-логирование dump | полный dump в debug не пишется | оставлять только длины, индексы строк и сокращённые snippet'ы |
| Fingerprint после склейки stack trace | `FingerprintUtil` включает `stackTrace`, fingerprint изменится | это корректно: dedup должен учитывать реальный стек |

`demo-app` сейчас пишет JSON только с `timestamp`, `level`, `logger`, `message`; JSON-поля `stackTrace` там нет. Поэтому проверка continuation особенно важна именно для переноса на другой сервис.

## Миграционный чек-лист

1. Снять реальный сырой dump `pods/log` целевой поды до любых правок библиотеки.
2. Проверить, действительно ли лог идёт как JSON-per-line, а не pretty-printed JSON и не plain text.
3. Сверить имена ключей с `PodLogDto`: `timestamp`, `level`, `logger`, `message`, `stackTrace`, `threadName`, `traceId`, `spanId`.
4. Отдельно проверить тип `timestamp`: ISO-строка, epoch millis, timezone offset, другое.
5. Найти в dump хотя бы один пример exception и убедиться, как печатаются continuation-строки: `at ...`, `Caused by`, `Suppressed`, пустые строки.
6. Запустить parse локально через `OpenshiftClient.parseLogDump(String)` или `JsonLogParser.parse(String)` на сохранённом dump.
7. Включить `DEBUG` для `com.example.podlogger.parser` и `com.example.podlogger.client`, чтобы увидеть line-by-line классификацию и Jackson-fail.
8. Если лишние ключи просто игнорируются, DTO не расширять. Если missing-критичны или имена другие, добавить alias/mapper.
9. Если сервис пишет pretty JSON или нестандартный multiline-формат, не лечить это только DTO: нужен другой state-machine в parse.
10. После адаптации проверить downstream: `fingerprint`, Allure JSON и SQLite не ломаются на `null` и на новом `stackTrace`.

## Приёмка этого пакета

`OpenshiftClientParseTest.parsesJsonLinesAndSkipsNoise` (display name метода: `проверка парсинга JSON строк и пропуска шумов`): смешанный dump даёт ровно 2 DTO. Кластер не поднимается — тест бьёт в `JsonLogParser`.

Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../docs/PodLoggerJunitDemoTest.md) §1.
