# PRD: Pod Logger JUnit Extension Demo

## 1. Введение и цели проекта

**Название продукта:** Pod Logger JUnit Extension Demo
**Версия:** 1.0.0-SNAPSHOT
**Дата:** 2026-08-30

### 1.1 Описание проблемы

При запуске интеграционных/системных тестов, обращающихся к сервисам, развернутым в Kubernetes/OpenShift кластере, возникает потребность в быстром доступе к логам подов за временной интервал выполнения конкретного теста (включая каждый кейс параметризованного теста). Ручной поиск логов по времени в кубере трудоемок и замедляет отладку падений тестов.

### 1.2 Цель продукта

Разработать **кастомное расширение для JUnit 5** (`@PodLogger`), которое:

- Ставится как аннотация над тестовым классом.
- Для каждого запуска тестового метода (включая каждый кейс `@ParameterizedTest`) фиксирует время начала (`beforeEach`) и завершения (`afterEach`).
- В случае падения теста (или по конфигурации — всегда) через Kubernetes/OpenShift API запрашивает логи пода, парсит JSON-лог, вырезает временное окно от начала до конца теста и прикрепляет его как аттач к отчету Allure.
- Предоставить полный **рабочий demo-проект** на Java 17 с локальным поднятием Kubernetes кластера (K3s через Testcontainers), демо-приложением и параметризованным тестом, триггерящим ожидаемые ошибки API.

### 1.3 Критерии успеха

- Проект компилируется и собирается через Maven (`mvn package`).
- При запуске теста локально поднимается K3s кластер, деплоится демо-приложение и выполняется параметризованный тест.
- Каждый из 4 кейсов параметризованного теста имеет в Allure отдельный аттач `pod-logs-<case>.json` с временной выборкой логов пода, содержащей ожидаемую ошибку.
- Jenkins pipeline выполняет сборку, сборку докер-образа, запуск тестов и публикацию Allure-отчета.

---

## 2. Пользователи и сценарии использования

### 2.1 Целевые пользователи

- **QA Automation Engineer** — использует библиотеку `junit-pod-logger` в своих тестовых проектах для автоматической привязки логов пода к Allure-отчету.
- **DevOps/Release Engineer** — запускает тесты в Jenkins, анализирует Allure-отчеты.

### 2.2 Основной сценарий (Happy Path)

1. Engineer размечает тестовый класс аннотацией `@PodLogger(collectOnFailOnly = true)`.
2. Запускает `mvn test`.
3. `ClusterLifecycle` поднимает K3s в Docker, деплоит демо-приложение `demo-api`.
4. Для каждого кейса `@ParameterizedTest`:
   - `beforeEach` → сохраняется `testStartUtc` (UTC `LocalDateTime`).
   - RestAssured вызывает `/api/orders/<code>` → получает 400 и ожидаемую ошибку.
   - Тест падает через `fail(...)` (для демонстрации `collectOnFailOnly`).
   - `afterEach` → `OpenShiftClient.getLog()` вытягивает логи пода, парсит JSON, фильтрует по окну `[start-2s, end+2s]` и прикрепляет как `Allure.addAttachment(...)`.
5. В Allure каждый кейс имеет свой JSON-аттач с логами.

### 2.3 Альтернативный сценарий: всегда собирать логи

1. Установить `@PodLogger(collectOnFailOnly = false)`.
2. Даже успешно прошедшие тесты получают аттач с логами.

---

## 3. Функциональные требования

### 3.1 Аннотация `@PodLogger`

| # | Требование | Статус |
|---|-----------|--------|
| FR-1 | Аннотация применима только к классу (`@Target(ElementType.TYPE)`). | ✅ |
| FR-2 | Аннотация имеет `RUNTIME` retention. | ✅ |
| FR-3 | Параметр `collectOnFailOnly` (boolean, default `true`) — управляет режимом прикрепления логов: только при падении или всегда. | ✅ |
| FR-4 | Параметр `namespace` (String, default `"default"`) — namespace кластера, где ищутся поды. | ✅ |
| FR-5 | Параметр `podLabelSelector` (String, default `"app=demo-api"`) — селектор в формате `key=value` для поиска пода. | ✅ |
| FR-6 | Аннотация автоматом подключает `SpringExtension` и `PodLoggerExtension` через `@ExtendWith`. | ✅ |

### 3.2 JUnit Extension (`PodLoggerExtension`)

| # | Требование | Статус |
|---|-----------|--------|
| FR-10 | Имплементирует `BeforeEachCallback` — сохраняет в `ExtensionContext.Store` UTC-время начала выполнения текущего вызова теста. | ✅ |
| FR-11 | Имплементирует `AfterEachCallback` — читает время начала, фиксирует время завершения, определяет факт падения (`context.getExecutionException().isPresent()`). | ✅ |
| FR-12 | Имплементирует `TestWatcher` (для интеграции с жизненным циклом JUnit). | ✅ |
| FR-13 | В `beforeEach` применяет параметры аннотации в `PodLoggerService.applyAnnotation(...)`. | ✅ |
| FR-14 | В `afterEach` делегирует `PodLoggerService.attachLogsIfNeeded(...)` передавая контекст, start/end, флаг failed. | ✅ |
| FR-15 | Для `@ParameterizedTest` каждый вызов (инвок) обрабатывается **отдельно** — у каждого кейса свой start/end и свой аттач. | ✅ (стандартное поведение JUnit + store per invocation) |
| FR-16 | Если аннотация отсутствует на классе — бросает `IllegalStateException` с понятным сообщением. | ✅ |
| FR-17 | Достает `PodLoggerService` из Spring контекста через `SpringExtension.getApplicationContext(context)`. | ✅ |

### 3.3 Сервис логики (`PodLoggerService`)

| # | Требование | Статус |
|---|-----------|--------|
| FR-20 | Spring-компонент (`@Component`). | ✅ |
| FR-21 | `applyAnnotation(PodLogger)` — применяет параметры аннотации в `PodLoggerProperties` (namespace, podLabelSelector, collectOnFailOnly). | ✅ |
| FR-22 | `attachLogsIfNeeded(context, start, end, failed)` — основная логика: | ✅ |
| FR-22.1 | Если `collectOnFailOnly=true` и тест прошел — выходим без действий. | ✅ |
| FR-22.2 | Делает задержку 500 мс, чтобы логи успели записаться и индексироваться. | ✅ |
| FR-22.3 | Вызывает `openshiftClient.getLog()` → получает `List<PodLogDto>`. | ✅ |
| FR-22.4 | Применяет временное окно: `[start - SKEW_SECONDS, end + SKEW_SECONDS]` (SKEW = 2 секунды для коррекции skew часов). | ✅ |
| FR-22.5 | Фильтрует записи, у которых `timestamp != null` и попадает в окно. | ✅ |
| FR-22.6 | Сериализует отфильтрованный список в pretty-printed JSON. | ✅ |
| FR-22.7 | Вызывает `Allure.addAttachment("pod-logs-<displayName>", "application/json", json, ".json")`. | ✅ |
| FR-22.8 | Имя аттача санитизируется: недопустимые символы заменяются `_`. | ✅ |
| FR-22.9 | Все исключения внутри логируются через `log.error` и не прокидываются (не ломают сам тест). | ✅ |

### 3.4 OpenShift/Kubernetes клиент (`OpenshiftClient`)

| # | Требование | Статус |
|---|-----------|--------|
| FR-30 | Spring-компонент (`@Component`), инжектирует `io.fabric8.openshift.client.OpenShiftClient`. | ✅ |
| FR-31 | Метод `getLog()` возвращает `List<PodLogDto>`: | ✅ |
| FR-31.1 | Вызывает `fetchRawLog()` — получает полный дамп логов как одну строку из K8s API. | ✅ |
| FR-31.2 | Вызывает `parseLogDump(raw)` — парсит JSON-строки в DTO. | ✅ |
| FR-32 | `fetchRawLog()`: | ✅ |
| FR-32.1 | Ищет поды по namespace + podLabelSelector. | ✅ |
| FR-32.2 | Если подов нет — бросает `IllegalStateException`. | ✅ |
| FR-32.3 | Предпочитает первый **Ready** pod (Running, все контейнеры ready). Если нет — берет первый. | ✅ |
| FR-32.4 | Возвращает `fabric8.pods().inNamespace(ns).withName(name).getLog()`. | ✅ |
| FR-33 | `parseLogDump(raw)`: | ✅ |
| FR-33.1 | Разбивает raw-лог по line separator (`\R`). | ✅ |
| FR-33.2 | Пропускает все строки, не начинающиеся с `{` (не JSON). | ✅ |
| FR-33.3 | Каждую JSON строку парсит в `PodLogDto` через Jackson `ObjectMapper`. | ✅ |
| FR-33.4 | Ошибки парсинга отдельных строк логируются как debug и не прерывают обработку. | ✅ |
| FR-34 | Селектор в формате `key=value` парсится через `splitSelector`. При неверном формате — `IllegalArgumentException`. | ✅ |

### 3.5 DTO (`PodLogDto`)

| # | Требование | Статус |
|---|-----------|--------|
| FR-40 | Поля: `timestamp` (LocalDateTime), `level`, `message`, `logger`. | ✅ |
| FR-41 | `timestamp` десериализуется по паттерну `yyyy-MM-dd'T'HH:mm:ss.SSS`. | ✅ |
| FR-42 | `@JsonIgnoreProperties(ignoreUnknown = true)` — нечувствительность к лишним полям в JSON логе. | ✅ |
| FR-43 | Используется Lombok: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`. | ✅ |

### 3.6 Демо-приложение (`demo-app`)

| # | Требование | Статус |
|---|-----------|--------|
| FR-50 | Spring Boot 3.x приложение на Java 17. | ✅ |
| FR-51 | Контроллер `OrderController`: | ✅ |
| FR-51.1 | `GET /health` → возвращает `{"status":"UP"}` (HTTP 200). | ✅ |
| FR-51.2 | `GET /api/orders/{code}` → возвращает HTTP 400 с JSON `{code, message}` и пишет `log.error(message)` в лог. | ✅ |
| FR-52 | 4 предопределенных кода ошибок: `UNKNOWN_SKU`, `OUT_OF_STOCK`, `PAYMENT_DECLINED`, `USER_BLOCKED`. | ✅ |
| FR-53 | Логирование настроено через logback-spring.xml в **JSON-формат** (logstash-logback-encoder) с полями timestamp/level/message/logger. | ✅ |
| FR-54 | Сборка в Docker-образ `demo-app/Dockerfile`. | ✅ |

### 3.7 Интеграционные тесты (`demo-tests`)

| # | Требование | Статус |
|---|-----------|--------|
| FR-60 | Класс `OrderErrorIT` помечен `@SpringBootTest` и `@PodLogger(collectOnFailOnly = true)`. | ✅ |
| FR-61 | `@ParameterizedTest` `apiErrorIsLoggedOnPod(code, expectedMessage)` с датапровайдером из 4 кейсов ошибок. | ✅ |
| FR-62 | В каждом кейсе: RestAssured вызывает `/api/orders/{code}`, ассертит `statusCode=400`, `code`, `message`. | ✅ |
| FR-63 | После успешного API-ответа тест **намеренно падает** через `fail(...)` — чтобы `collectOnFailOnly=true` сработал и прикрепил логи. | ✅ |
| FR-64 | Жизненный цикл кластера — `ClusterLifecycle` (static init): | ✅ |
| FR-64.1 | Поднимает K3s через `Testcontainers K3sContainer` (image `rancher/k3s:v1.31.5-k3s1`). | ✅ |
| FR-64.2 | Собирает и импортирует Docker-образ `demo-api:local` в кластер через `ctr images import`. | ✅ |
| FR-64.3 | Применяет k8s-манифест `demo-api.yaml` (Deployment + Service с selector `app=demo-api`, port 8080). | ✅ |
| FR-64.4 | Awaitility ждет Ready pod. | ✅ |
| FR-64.5 | Поднимает `LocalPortForward` из K8s Service `demo-api:8080` на случайный локальный порт. | ✅ |
| FR-64.6 | Awaitility ждет HTTP 200 от `/health`. | ✅ |
| FR-64.7 | Регистрирует shutdown hook. | ✅ |
| FR-65 | `ClusterConfig` — Spring `@Configuration`, которая экпортирует `OpenShiftClient` и `demoApiPort` как бины для инжекта в `OpenshiftClient` и тестовый класс. | ✅ |

### 3.8 Jenkins Pipeline

| # | Требование | Статус |
|---|-----------|--------|
| FR-70 | Stage `Build` — `mvn -B -DskipTests package`. | ✅ |
| FR-71 | Stage `Docker image` — `docker build -t demo-api:local demo-app`. | ✅ |
| FR-72 | Stage `Tests` — `mvn -B -pl demo-tests -am test`, оборачивается в `catchError(UNSTABLE)` так как тесты намеренно падают. | ✅ |
| FR-73 | Stage `Allure` — публикация отчета из `demo-tests/target/allure-results`. | ✅ |
| FR-74 | Инструменты: maven-3.9, jdk-17, плагин timestamps. | ✅ |

### 3.9 Докер-окружение для Jenkins

| # | Требование | Статус |
|---|-----------|--------|
| FR-80 | `docker/jenkins/Dockerfile` — кастомный образ Jenkins (Docker-in-Docker, pre-install plugins). | ✅ |

---

## 4. Нефункциональные требования

| # | Требование | Статус |
|---|-----------|--------|
| NFR-1 | **Java 17** (LTS). | ✅ |
| NFR-2 | **JUnit 5 (Jupiter)** — только Jupiter API, без поддержки JUnit 4. | ✅ |
| NFR-3 | **Spring Boot 3.3.x** — как контейнер для DI бина сервиса и клиента. | ✅ |
| NFR-4 | **Fabric8 OpenShift Client 6.13.x** — для работы с K8s/OpenShift API. | ✅ |
| NFR-5 | **Allure Framework 2.29.x** — интеграция `allure-junit5` и Maven-плагин. | ✅ |
| NFR-6 | **Lombok 1.18.x** — повсеместно для сокращения бойлерплейта. | ✅ |
| NFR-7 | **Jackson** — парсинг JSON логов и сериализация аттача. | ✅ |
| NFR-8 | **RestAssured 5.5.x** — HTTP-клиент в тестах. | ✅ |
| NFR-9 | **Testcontainers 1.20.x** — K3sContainer для локального кластера. | ✅ |
| NFR-10 | **Awaitility 4.2.x** — ожидания Ready/HTTP. | ✅ |
| NFR-11 | **Локальный запуск возможен** на Windows 11 + Docker Desktop (поднятый и готовый к работе). | ✅ |
| NFR-12 | **Отдельность временных окон**: у каждого параметризованного кейса свой start/end, их логи не смешиваются. | ✅ |
| NFR-13 | **Отказоустойчивость**: ошибка в логике сбора логов не должна ронять сам тест — только логироваться. | ✅ |
| NFR-14 | **Расширяемость**: конфигурация через аннотацию/Properties без изменения кода сервиса. | ✅ |
| NFR-15 | **Security**: `Config.setTrustCerts(true)` при работе с локальным K3s с самоподписанными сертификатами. | ✅ |

---

## 5. Архитектура решения

### 5.1 Общая схема

```
┌─────────────────────────────────────────────────────────────────────┐
│                          demo-tests (IT)                            │
│                                                                     │
│  @SpringBootTest + @PodLogger(collectOnFailOnly=true)               │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ OrderErrorIT                                                 │   │
│  │  ┌──────────────────────────────────────────────────────┐   │   │
│  │  │  @ParameterizedTest 4 cases (per invocation)         │   │   │
│  │  │   beforeEach → store startUTC                        │   │   │
│  │  │   RestAssured → GET /api/orders/<code> → 400 OK!     │   │   │
│  │  │   fail("force collectOnFailOnly demo")               │   │   │
│  │  │   afterEach → attachLogsIfNeeded(...)                │   │   │
│  │  └──────────────────────────────────────────────────────┘   │   │
│  └────────────────────────▲───────────────────▲─────────────────┘   │
│                           │ Spring bean       │ Spring bean          │
│              ┌────────────┴─────┐  ┌──────────┴──────────┐          │
│              │ PodLoggerService │  │  OpenshiftClient   │          │
│              │  (logic/window)  │  │ (fabric8 + parse)  │          │
│              └────────▲─────────┘  └──────────▲─────────┘          │
│                       │                       │                    │
│                       │ uses                  │ uses               │
│              ┌────────┴─────────┐  ┌──────────┴───────────────┐    │
│              │PodLoggerProperties │  │ OpenShiftClient fabric8│    │
│              │(collectOnFailOnly,│  │ (K3s kubeconfig)       │    │
│              │ namespace,        │  └──────────▲───────────────┘    │
│              │ podLabelSelector) │             │ LocalPortForward  │
│              └──────────────────┘             ▼                    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ K8s API (port-forward / kubeconfig)
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│              K3s Container (Testcontainers, Docker)                │
│                                                                     │
│  Namespace: default                                                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Deployment: demo-api (image demo-api:local)                │   │
│  │   labels: app=demo-api                                      │   │
│  │   ┌──────────────────────────────────────────────────────┐ │   │
│  │   │ Pod: demo-api-XXXX                                    │ │   │
│  │   │  Container port 8080 → JSON logs (logstash encoder) │ │   │
│  │   │  GET /health, GET /api/orders/{code} (→ log.error)   │ │   │
│  │   └──────────────────────────────────────────────────────┘ │   │
│  │  Service: demo-api (ClusterIP, port 8080, selector app=demo-api)│
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 Модули Maven (3 модуля, parent pom)

| Модуль | Артефакт | Назначение |
|--------|----------|-----------|
| `junit-pod-logger` | `junit-pod-logger` | Библиотека: `@PodLogger`, Extension, Service, OpenshiftClient, DTO, Properties. |
| `demo-app` | `demo-app` | Spring Boot демо-приложение с 2 ручками и JSON-логами. |
| `demo-tests` | `demo-tests` | Интеграционные тесты (IT), ClusterLifecycle, ClusterConfig, OrderErrorIT. |

---

## 6. Критерии приемки (Acceptance Criteria)

### 6.1 Непротиворечивость

- [ ] Все требования в разделе 3 (Functional) непротиворечивы и полностью покрывают исходный промпт.
- [ ] Конфигурация через аннотацию и через Properties однозначна (applyAnnotation выставляет приоритет аннотации).

### 6.2 Полнота

- [ ] **AC-1**: Проект собирается:
  ```
  mvn -B -DskipTests package
  ```
  Без ошибок компиляции.

- [ ] **AC-2**: Docker-образ собран:
  ```
  docker build -t demo-api:local demo-app
  ```

- [ ] **AC-3**: Запуск тестов:
  ```
  mvn -B -pl demo-tests -am test
  ```
  - Docker Desktop запущен.
  - Логируется старт K3s, импорт образа, деплой, port-forward, /health OK.
  - 4 параметризованных кейса выполняются.
  - Каждый кейс **падает** (так как `fail(...)`).

- [ ] **AC-4**: Allure-результаты присутствуют:
  - В `demo-tests/target/allure-results` есть attachment-файлы для каждого кейса.
  - Каждое вложение имеет имя вида `pod-logs-<CASE_NAME>.json`.
  - Каждый JSON содержит массив `PodLogDto` **своих** логов (время в окне выполнения кейса).
  - В каждом JSON присутствует **ровно одна запись с `level=ERROR` и `message`**, соответствующая коду кейса.

- [ ] **AC-5**: Jenkins pipeline проходит до конца: Build ✓, Docker image ✓, Tests (UNSTABLE) ✓, Allure ✓.

---

## 7. Риски и ограничения

| Риск | Влияние | Вероятность | Митерация |
|------|---------|-------------|-----------|
| Отсутствие Docker Desktop / привилегий на Windows | Блокировка запуска тестов | Средняя | В README указать prerequisites; pipeline использует агент с Docker. |
| Медленный первый старт K3s (образ ~300MB) | CI-таймауты | Средняя | Awaitility timeout 2 мин; в тестовом облаке прогреть кэш. |
| Рассинхрон часов (test JVM vs pod) | Не попадание логов в окно | Низкая | SKEW_SECONDS = ±2с configurable. |
| Многострочный не-JSON вывод в логах | Потеря части логов | Низкая | Демо-приложение гарантирует JSON формат; парсер пропускает не-JSON строки. |
| Несколько реплик пода | Логи только с одного пода | Средняя | Берется первый Ready pod; можно расширить до агрегации со всех. |

---

## 8. Словарь терминов

| Термин | Определение |
|--------|-------------|
| JUnit 5 Extension | Механизм расширения жизненного цикла тестов JUnit Jupiter (BeforeEach/AfterEach/TestWatcher и т.д.). |
| Invocation | Один вызов тестового метода; для `@ParameterizedTest` каждый набор аргументов — отдельный invocation. |
| K3s | Легковесный Kubernetes-дистрибутив от Rancher, идеален для CI/локальных запусков. |
| Fabric8 OpenShift Client | Клиентская Java-библиотека для взаимодействия с OpenShift/Kubernetes API. |
| Allure | Фреймворк для построения красивых отчетов о тестировании с поддержкой аттачей. |
| SKEW_SECONDS | Коррекция рассинхрона часов между тестовым JVM и подом при фильтрации логов по времени. |
