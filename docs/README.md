# Документация проекта

Канонические документы (единственный источник истины). Остальные файлы в `docs/` — либо указатели сюда, либо исторические черновики, либо forward-looking архитектурные guides.

| Документ | О чём |
| --- | --- |
| [`README.md`](../README.md) (корень репозитория) | Как запускать, модули, аннотация, путь к SQLite, Jenkins, коды демо-API |
| [`prd/podLoggerJunitDemoPRD.md`](prd/podLoggerJunitDemoPRD.md) | Общий PRD: назначение, модули, слои, общие инварианты |
| [`feature/PersistentLogStore/PersistentLogStorePRD.md`](feature/PersistentLogStore/PersistentLogStorePRD.md) | SQLite store: схема, API, retention, приёмка |
| [`feature/OpenShiftEventHandling/OpenShiftEventHandlingPRD.md`](feature/OpenShiftEventHandling/OpenShiftEventHandlingPRD.md) | Events, health, Allure Events, fail-fast |

JavaDoc классов и методов библиотеки: пакет `com.example.podlogger` в модуле `junit-pod-logger`.

`docs/propmtWorks/` — рабочие промпты и черновики на момент разработки. Не читать как контракт: статус там может быть «ещё не реализовано», тогда как код уже as-built.

Дополнительные материалы по развитию event management:

- [`prd/EventHandlingPRD.md`](prd/EventHandlingPRD.md) — компактный архитектурный guide и roadmap.
- [`EventHandling.PRD.md`](EventHandling.PRD.md) — расширенная версия того же guide.

Эти два документа полезны для планирования следующей итерации, но не заменяют текущий `as-built` контракт из feature-PRD и кода.
