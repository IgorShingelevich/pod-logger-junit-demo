# Пакет `com.example.podlogger.allure`

Выход текущего invocation в Allure: pretty JSON. Persistence не выполняет. Историю из SQLite не читает.

Модуль: [`junit-pod-logger.md`](../../../../../../../junit-pod-logger.md).  
Когда какой аттач создавать: [`OpenShiftEventHandlingStory.md`](../../../../../../../../docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) §6.6–§6.7.  
Кто вызывает: [`podLogger.md`](../podLogger.md) (`PodLoggerService`).

## Классы

| Класс | Роль |
| --- | --- |
| `LogAllureAttachmentService` | `attachJson` / `attachEvents` + `sanitize` имени |
| `AllureSink` | тестируемая обёртка над `Allure.addAttachment` |
| `DefaultAllureSink` | прод-делегат на static Allure |

В EngineTestKit-тестах `AllureSink` подменяется записывающим listener'ом — иначе static Allure не перехватить.

Ошибки сериализации/аттача глотаются: тест из-за Allure не краснеет.

## Имена и правила

Имена собирает `PodLoggerService`, не этот пакет:

| Аттач | Имя | Пустой вход |
| --- | --- | --- |
| логи invocation | `pod-logs-` + `sanitize(displayName)` | если метод вызван — сериализуется `[]` |
| Events invocation | `pod-events-` + `sanitize(displayName)` | `null`/empty → **no-op**, аттач не создавать |
| логи прогона | `pod-logs-run-` + `sanitize(testRunName)` | только если `attachRunSummaryToAllure=true` (default `false`) |

`sanitize` заменяет символы вне `[a-zA-Z0-9._-]` на `_`.

`attachJson` может сериализовать `PodLogDto.relevantEvents` как есть (то же окно, что Events-аттач). Passed-тест Events-аттач не получает — это решает service, не Allure-пакет.

## Приёмка этого пакета

`OpenshiftEventHandlingTest`:

- nested «Fail + Events → Allure» — есть `pod-events-*` с тем же `code`;
- nested «Fail + нет Events → нет аттача» — `attachEvents` не вызывался.

Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../docs/PodLoggerJunitDemoTest.md) §2. Наблюдения конкретного прогона `OrderErrorIT` в каталоге тестов — не контракт этого пакета.
