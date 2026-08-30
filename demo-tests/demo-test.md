# `demo-tests`

Потребитель библиотеки: поднимает K3s, бьёт в demo-api, включает `@PodLogger` на `OrderErrorIT`.

Полный каталог тестов (карточки, приёмка, ошибки): [`docs/PodLoggerJunitDemoTest.md`](../docs/PodLoggerJunitDemoTest.md).  
Команды: [`docs/PodLoggerJunitDemoCommands.md`](../docs/PodLoggerJunitDemoCommands.md) → Test Commands.  
SUT: [`../demo-app/demo-app.md`](../demo-app/demo-app.md). Кластер YAML: [`../k8s/k8s.md`](../k8s/k8s.md).

## Классы

| Класс | `@PodLogger` | Docker | Зачем в этом модуле |
| --- | --- | --- | --- |
| `InfrastructureLoggingTest` | нет | да | шаг 1–3: Docker → image → health UP |
| `OrderErrorIT` | `collectOnFailOnly = true` | да | 4 parameterized 400 + обязательный `fail()` |
| `ClusterLifecycle` | — | да | singleton: K3s, import образа, manifest, port-forward |
| `ClusterConfig` | — | да | Spring beans: `OpenShiftClient`, `demoApiPort` |
| `DemoTestApplication` | — | нет | `@SpringBootApplication` для `@SpringBootTest` |

## Как устроен стенд (только этот модуль)

1. `ClusterLifecycle.start()` (static в `OrderErrorIT` и шаг 3 infra).
2. Образ K3s: `rancher/k3s:v1.31.5-k3s1`.
3. `docker build -t demo-api:local` из `demo-app`, затем `ctr images import` внутрь K3s.
4. Манифест из classpath: `src/test/resources/k8s/demo-api.yaml` (копия [`k8s/demo-api.yaml`](../k8s/demo-api.yaml)).
5. Port-forward на случайный локальный порт; RestAssured бьёт в `127.0.0.1:{port}`.

Docker Desktop named pipe на Windows достаточен. `kubectl`/`oc` из тестов не вызываются.

## Почему `OrderErrorIT` красный

`collectOnFailOnly = true` → без `Assertions.fail(...)` после HTTP 400 не будет Allure/SQLite. Maven `BUILD FAILURE` — ожидаемый исход модуля, не дефект.

Артефакты этого модуля: `target/allure-results`, `target/pod-logger-store.sqlite`, `target/site/allure-maven-plugin`.
