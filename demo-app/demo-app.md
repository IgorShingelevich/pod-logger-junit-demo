# `demo-app`

SUT демо: Spring Boot приложение, которое пишет ERROR JSON в stdout. Это **не** библиотека `@PodLogger`.

Канон поведения проекта: [`docs/PodLoggerJunitDemoPRD.md`](../docs/PodLoggerJunitDemoPRD.md).  
Приёмка jar: [`docs/PodLoggerJunitDemoTest.md`](../docs/PodLoggerJunitDemoTest.md) §6.  
Команды сборки: [`docs/PodLoggerJunitDemoCommands.md`](../docs/PodLoggerJunitDemoCommands.md) → Build.

## Что здесь лежит

| Файл | Зачем |
| --- | --- |
| `src/main/java/.../OrderController.java` | `GET /health` → `{"status":"UP"}`; `GET /api/orders/{code}` → HTTP 400 + JSON `{code, message}` и `log.error` той же message |
| `src/main/resources/logback-spring.xml` | stdout = JSON: `timestamp` (UTC millis), `level`, `logger`, `message` |
| `Dockerfile` | `eclipse-temurin:17-jre-alpine`, копирует `target/demo-app.jar` |
| `pom.xml` | Spring Boot fat jar имя `demo-app.jar` |

Unit-тестов в модуле нет.

## Контракт API (этот модуль)

| `{code}` | HTTP | `message` в JSON и в строке лога |
| --- | --- | --- |
| `UNKNOWN_SKU` | 400 | Unknown SKU |
| `OUT_OF_STOCK` | 400 | Item is out of stock |
| `PAYMENT_DECLINED` | 400 | Payment was declined |
| `USER_BLOCKED` | 400 | User is blocked |
| любой другой | 400 | `Unknown error code: {code}` |

Парсер библиотеки берёт только строки, начинающиеся с `{`. Поле `podName` в JSON приложения **нет**.

## Образ

Без `mvn -pl demo-app -am package -DskipTests` Docker build падает: Dockerfile ждёт jar. Тег демо: `demo-api:local`. `imagePullPolicy: Never` в манифесте — образ должен оказаться в K3s (это уже `demo-tests` / [`k8s.md`](../k8s/k8s.md)).
