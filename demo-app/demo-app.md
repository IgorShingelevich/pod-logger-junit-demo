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

## Риски миграции

| Риск | Симптом | Где смотреть | Решение |
| --- | --- | --- | --- |
| Новый сервис пишет другой JSON в stdout | `JsonLogParser` начинает пропускать строки или заполняет DTO частично `null` | `junit-pod-logger/.../parser/logParser.md` | заранее снять реальные примеры логов и адаптировать `PodLogDto`/mapping |
| Logback/layout меняет поля `timestamp`, `level`, `logger`, `message` | библиотека собирает меньше полезной информации, хотя HTTP-контракт может оставаться тем же | `src/main/resources/logback-spring.xml` | сверить ключи и формат timestamp до запуска миграции |
| Ошибка есть в API, но нет ожидаемой строки в логе | `OrderErrorIT` падает as designed, а `pod-logs-*` не содержит нужного сообщения | `OrderController`, `OrderErrorIT` | проверить, что SUT реально логирует ошибку в формате JSON-per-line |

### Миграционный чек-лист

1. Снять реальные stdout-логи целевого сервиса, а не только HTTP-ответы.
2. Сверить JSON-ключи с ожиданиями `parser/logParser.md`.
3. Если приложение логирует иначе, сначала чинить SUT layout или parser mapping, затем повторять тесты библиотеки.

### Профит для агента в новом контуре

- Этот файл быстро показывает, что главный контракт библиотеки с сервисом проходит не через DTO ответа, а через формат stdout.
- Агент может сразу проверить логовый layout и не тратить время на ложный дебаг RestAssured или SQLite.

## Образ

Без `mvn -pl demo-app -am package -DskipTests` Docker build падает: Dockerfile ждёт jar. Тег демо: `demo-api:local`. `imagePullPolicy: Never` в манифесте — образ должен оказаться в K3s (это уже `demo-tests` / [`k8s.md`](../k8s/k8s.md)).
