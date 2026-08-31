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

## Риски миграции

| Риск | Симптом | Где смотреть | Решение |
| --- | --- | --- | --- |
| DTO после миграции содержат новые или неожиданные типы | attach падает на сериализации, но тест остаётся в прежнем статусе | `LogAllureAttachmentService`, `parser/logParser.md` | смотреть debug/error around `attachJson` / `attachEvents`, затем исправлять DTO mapping |
| Пустой список Events трактуется как «что-то сломалось» | `pod-events-*` нет, хотя `pod-logs-*` есть | `PodLoggerService`, `event.md` | помнить, что empty Events = no-op по контракту |
| Ожидание, что Allure покажет первичную причину падения | аттача нет или он пустой, а исходная причина выше по стеку | `podLogger.md`, `store.md` | разбирать orchestration logs до анализа вложений |

### Миграционный чек-лист

1. Включить `DEBUG` для `com.example.podlogger.allure` и `com.example.podlogger`.
2. Проверить логи `Attaching pod logs to Allure`, `Attaching pod events to Allure`, `Skip Allure events attachment`.
3. Если логов в Allure нет, сначала проверить parser/client/store, а не только этот пакет.

### Профит для агента в новом контуре

- Этот раздел объясняет, почему отсутствие аттача не всегда означает отсутствие логов на pod.
- Агент быстрее отделяет проблему сериализации/attach от проблемы сбора данных.

## Приёмка этого пакета

`OpenshiftEventHandlingTest`:

- nested «Fail + Events → Allure» — есть `pod-events-*` с тем же `code`;
- nested «Fail + нет Events → нет аттача» — `attachEvents` не вызывался.

Карточка: [`PodLoggerJunitDemoTest.md`](../../../../../../../../docs/PodLoggerJunitDemoTest.md) §2. Наблюдения конкретного прогона `OrderErrorIT` в каталоге тестов — не контракт этого пакета.
