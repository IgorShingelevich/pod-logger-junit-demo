# Документация проекта

Канонические документы (единственный источник истины). Остальные файлы в `docs/` — либо указатели сюда, либо исторические черновики, либо forward-looking архитектурные guides.

Текущая структура (после реорганизации: папок `docs/prd/` и `docs/feature/` нет):

```text
docs/
  README.md                          ← этот файл
  PodLoggerJunitDemoPRD.md         ← общий as-built PRD
  PodLoggerJunitDemoTest.md         ← каталог всех тестов
  story/
    PersistentLogStoreStory/PersistentLogStoreStory.md
    OpenShiftEventHandlingStory/
      OpenShiftEventHandlingStory.md   as-built Events
      EventHandlingStrategies.md      compact target-state
      EventHandling2Story.md           expanded target-state
```

| Документ | О чём |
| --- | --- |
| [`README.md`](../README.md) (корень репозитория) | Как запускать, модули, аннотация, путь к SQLite, Jenkins, коды демо-API |
| [`PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md) | Общий PRD: назначение, модули, слои, общие инварианты |
| [`PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md) | Каталог тестов: приёмка, проверка, команды, известные ошибки |
| [`story/PersistentLogStoreStory/PersistentLogStoreStory.md`](story/PersistentLogStoreStory/PersistentLogStoreStory.md) | SQLite store: схема, API, retention, приёмка |
| [`story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) | Events, health, Allure Events, fail-fast (as-built) |

JavaDoc классов и методов библиотеки: пакет `com.example.podlogger` в модуле `junit-pod-logger`.

`docs/propmtHistory/` — рабочие промпты и черновики на момент разработки. Не читать как контракт.

Дополнительные материалы по развитию event management (не as-built):

- [`story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`](story/OpenShiftEventHandlingStory/EventHandlingStrategies.md) — компактный архитектурный guide и roadmap.
- [`story/OpenShiftEventHandlingStory/EventHandling2Story.md`](story/OpenShiftEventHandlingStory/EventHandling2Story.md) — расширенная версия того же guide.

Эти два документа полезны для планирования следующей итерации, но не заменяют текущий as-built контракт из story-PRD и кода.
