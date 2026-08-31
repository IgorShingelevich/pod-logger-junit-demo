# Пакет `com.example.podlogger.parser`

Построчный разбор сырого dump `pods/log` в `List<PodLogDto>`. Пакет не знает ни о JUnit, ни об Allure, ни о SQLite, ни о Kubernetes Events.

Модуль: [`junit-pod-logger.md`](../../../../../../../junit-pod-logger.md).  
Кто зовёт parse: [`OpenshiftClient.getLog()`](../client/openshiftClient.md).  
Какие поля DTO отсюда не приходят: [`PersistentLogStoreStory.md`](../../../../../../../../docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md) §8.

## Классы

| Класс | Роль |
| --- | --- |
| `LogParser` | контракт: `parse(rawDump)` → список DTO; `null`/blank → пустой список |
| `JsonLogParser` | Spring `@Component`: только строки, у которых trim начинается с `{` |

Невалидный JSON логируется на debug и **пропускается**, вызов не валит. Строки kube-preamble и `not json` DTO не становятся.

## Что парсер не заполняет

Контекст прогона (`testRunId`, suite, environment, related test, fingerprint) и `relevantEvents` из stdout не приходят и здесь не ставятся. Поле `podName` в JSON `demo-app` отсутствует — в DTO остаётся `null`, если его не было в строке.

`@JsonIgnoreProperties(ignoreUnknown=true)` на `PodLogDto`: неизвестные ключи с поды не падают.

## Приёмка этого пакета

`OpenshiftClientParseTest.parsesJsonLinesAndSkipsNoise` (display name метода: `проверка парсинга JSON строк и пропуска шумов`): смешанный dump даёт ровно 2 DTO. Кластер не поднимается — тест бьёт в `JsonLogParser`.

Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../docs/PodLoggerJunitDemoTest.md) §1.
