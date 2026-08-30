# Документация проекта

Канон **контракта** живёт в `docs/`. MD в корне каждого модуля — **локальная карта этого дерева**, не второй PRD и не второй каталог тестов.

```text
docs/                              контракт и каталоги (не режем)
  README.md
  PodLoggerJunitDemoPRD.md
  PodLoggerJunitDemoTest.md
  PodLoggerJunitDemoCommands.md
  story/...
demo-app/demo-app.md               SUT: API, logback, Docker image
demo-tests/demo-test.md            K3s, потребители, зачем fail()
junit-pod-logger/junit-pod-logger.md  пакеты библиотеки, что переносить
k8s/k8s.md                        манифест, probes, RBAC
.cursor/rules/demo-commands.mdc    дописывать команды в Commands.md
```

| Документ | О чём |
| --- | --- |
| [`README.md`](../README.md) (корень репозитория) | Как запускать, модули, аннотация, путь к SQLite, Jenkins |
| [`PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md) | Общий PRD: назначение, модули, слои, общие инварианты |
| [`PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md) | Каталог **всех** тестов: приёмка, проверка, известные ошибки |
| [`PodLoggerJunitDemoCommands.md`](PodLoggerJunitDemoCommands.md) | Справочник команд по скопам |
| [`story/PersistentLogStoreStory/PersistentLogStoreStory.md`](story/PersistentLogStoreStory/PersistentLogStoreStory.md) | SQLite store |
| [`story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md) | Events, health, fail-fast (as-built) |
| [`../demo-app/demo-app.md`](../demo-app/demo-app.md) | Специфика модуля `demo-app` |
| [`../demo-tests/demo-test.md`](../demo-tests/demo-test.md) | Специфика модуля `demo-tests` |
| [`../junit-pod-logger/junit-pod-logger.md`](../junit-pod-logger/junit-pod-logger.md) | Специфика модуля `junit-pod-logger` |
| [`../k8s/k8s.md`](../k8s/k8s.md) | Манифест и RBAC |

JavaDoc: пакет `com.example.podlogger`.

`docs/propmtHistory/` — черновики. Не контракт.

Target-state (не as-built):

- [`story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`](story/OpenShiftEventHandlingStory/EventHandlingStrategies.md)
- [`story/OpenShiftEventHandlingStory/EventHandling2Story.md`](story/OpenShiftEventHandlingStory/EventHandling2Story.md)
