# Implementation Manual: Pod Logger JUnit Demo

Документ описывает **каждый класс, каждый метод, каждый конфигурационный файл** проекта. Цель — полное восстановление логики при миграции в закрытый контур.

Версия продукта: `1.0.0-SNAPSHOT` от 2026-08-30

---

## 0. Общая архитектура проекта

Проект — **Maven multi-module (3 модуля, packaging pom)**.

```
pod-logger-junit-demo (parent, GAV: com.example.podlogger:pod-logger-junit-demo:1.0.0-SNAPSHOT)
├── junit-pod-logger     ← БИБЛИОТЕКА: @PodLogger, Extension, Service, OpenshiftClient, DTO
├── demo-app             ← ДЕМО-СЕРВИС: Spring Boot 2 ручки, JSON-логи stdout
└── demo-tests           ← ИНТЕГРАЦИОННЫЕ ТЕСТЫ: K3s(Testcontainers) + @ParameterizedTest + Allure
```

Принцип потока:
1. Тестовый класс `OrderErrorIT` помечен `@PodLogger(collectOnFailOnly = true)` над классом.
2. При запуске static-блок поднимает K3s в Docker, деплоит `demo-app` как K8s Deployment+Service, делает port-forward на локальный порт.
3. Для **каждого invocation** `@ParameterizedTest`:
   - `beforeEach` → кладёт в ExtensionContext.Store UTC-время начала кейса.
   - RestAssured вызывает `GET /api/orders/<code>` на `127.0.0.1:<port>`.
   - Демо-приложение в пода возвращает 400 + пишет `log.error(message)` в stdout.
   - Тест вызывает `fail()` гарантированно → `failed=true`.
   - `afterEach` → берёт start/end окна времени → `OpenshiftClient.getLog()` через K8s pods/log API достаёт полный дамп логов одной строкой → парсит по строкам Jackson в `PodLogDto` → фильтрует список по окну `[start-2s, end+2s]` → вызов `Allure.addAttachment("pod-logs-<displayName>", "application/json", json, ".json")`.
4. На каждый из 4 кейсов — **свой отдельный Allure attachment** с его временным окном и его же ERROR-записью в логах поды.

---

# Раздел 1. Модуль `junit-pod-logger` (библиотека расширения)

Package: `com.example.podlogger.*` и `com.example.podlogger.client.*`

---

## 1.1 Класс `PodLogger` — аннотация над тестовым классом

- **FQN**: `com.example.podlogger.PodLogger`
- **Файл**: [junit-pod-logger/src/main/java/com/example/podlogger/PodLogger.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/junit-pod-logger/src/main/java/com/example/podlogger/PodLogger.java#L1-L29)
- **Назначение**: Маркерная аннотация на **уровне класса**. При наличии над тестовым классом активирует `PodLoggerExtension` и `SpringExtension`.

### Метаданные аннотации

| Элемент | Значение | Для чего |
|---------|----------|----------|
| `@Target(ElementType.TYPE)` | Только класс/интерфейс/enum | Запрещает ставить на метод/поле. Гарантирует «1 аннотация = 1 тестовый класс = все его методы под наблюдением». |
| `@Retention(RetentionPolicy.RUNTIME)` | Хранится в байт-коде, доступна рефлексией в рантайме | Нужно, чтобы `PodLoggerExtension.annotation(context).getAnnotation(PodLogger.class)` нашла её при выполнении тестов. |
| `@ExtendWith({SpringExtension.class, PodLoggerExtension.class})` | **Мета-аннотация** — автоматом подключает 2 JUnit 5 Extension | Пользователю НЕ нужно отдельно писать `@ExtendWith(...)` над классом — достаточно `@PodLogger`. |

### Параметры (атрибуты) аннотации

| Атрибут | Тип | Значение по умолчанию | Детальное назначение |
|---------|-----|----------------------|---------------------|
| `collectOnFailOnly()` | `boolean` | `true` | Режим прикрепления Allure-аттача. `true` = аттач создаётся **только** если invocation упал (execution exception present). `false` = аттач после **каждого** invocation. ВАЖНО: в демо-тесте `OrderErrorIT` для демонстрации работы `true` все кейсы намеренно падают через `Assertions.fail(...)`. |
| `namespace()` | `String` | `"default"` | Имя K8s/OpenShift namespace, в котором библиотека будет искать поды по лейблу (через `fabric8.pods().inNamespace(ns)`). **При миграции в закрытый контур**: заменить на реальный namespace, где живёт тестируемый сервис. |
| `podLabelSelector()` | `String` | `"app=demo-api"` | Селектор поиска нужного пода, формат строго `key=value`. Парсится внутри `OpenshiftClient.splitSelector`. **При миграции в закрытый контур**: заменить на реальный labelSelector для подов вашего сервиса. |

### Примеры использования
```java
@PodLogger(collectOnFailOnly = true)                                    // только падения, default namespace и селектор
@PodLogger(collectOnFailOnly = false)                                   // всегда логи, даже зелёные кейсы
@PodLogger(namespace = "payments-dev", podLabelSelector = "app=payments-svc")
```

---

## 1.2 Класс `PodLoggerExtension` — JUnit 5 Extension (жизненный цикл BeforeEach/AfterEach)

- **FQN**: `com.example.podlogger.PodLoggerExtension`
- **Файл**: [PodLoggerExtension.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/junit-pod-logger/src/main/java/com/example/podlogger/PodLoggerExtension.java#L1-L43)
- **Назначение**: Glue-код между JUnit 5 Jupiter lifecycle и Spring-бина `PodLoggerService`. Отвечает за:
  - Запись времени начала кейса;
  - Чтение аннотации `@PodLogger` с тестового класса;
  - Делегирование всей бизнес-логики в `PodLoggerService`.
- **Реализуемые интерфейсы JUnit 5**:
  - `BeforeEachCallback` → метод `beforeEach`
  - `AfterEachCallback` → метод `afterEach`
  - `TestWatcher` → marker (интеграция для Allure/других наблюдателей; методы default, не переопределены)

### Статические константы

| Константа | Тип | Значение | Назначение |
|-----------|-----|----------|-----------|
| `STORE_NS` | `ExtensionContext.Namespace` | `Namespace.create(PodLoggerExtension.class)` | Пространство имён для `ExtensionContext.Store`. Изолирует ключи расширения от других Extension/JUnit. |
| `START_KEY` | `String` | `"testStartUtc"` | Ключ, под которым в Store хранится UTC-время начала текущего invocation. |

### Методы класса

#### 1.2.1 `public void beforeEach(ExtensionContext context)` — вызов per-invocation

- **Параметры**: `ExtensionContext context` — стандартный контекст Jupiter (информация о тесте, store, requiredTestClass и т.д.)
- **Шаги метода**:
  1. `context.getStore(STORE_NS).put(START_KEY, LocalDateTime.now(ZoneOffset.UTC))` — **сохраняет текущее UTC-время как момент старта кейса**.
     - ВАЖНО per-invocation: JUnit 5 Store в пространстве `Namespace.create(Extension.class)` имеет **Invocation scope** для `BeforeEach/AfterEach`. То есть для 4 параметризованных кейсов — это 4 независимых ячейки в Store, у каждой свой start.
  2. `service(context).applyAnnotation(annotation(context))` — передаёт Spring-бину `PodLoggerService` все 3 параметра аннотации с класса (namespace, podLabelSelector, collectOnFailOnly).
- **Возврат**: `void`.
- **Побочные эффекты**: Обновляет `PodLoggerProperties` в Spring-контексте значениями из аннотации.

#### 1.2.2 `public void afterEach(ExtensionContext context)` — вызов per-invocation

- **Параметры**: тот же `ExtensionContext context`.
- **Шаги метода**:
  1. Читает `start` из Store по ключу `START_KEY` → `LocalDateTime`.
  2. Фиксирует `end = LocalDateTime.now(ZoneOffset.UTC)` — время завершения кейса.
  3. Вычисляет флаг `failed = context.getExecutionException().isPresent()` — упал ли текущий invocation.
  4. Делегирует Spring-бину: `service(context).attachLogsIfNeeded(context, start, end, failed)`.
- **Возврат**: `void`.
- **Побочные эффекты**: Может создать Allure attachment (через `Allure.addAttachment` внутри сервиса).
- **Отказоустойчивость**: Ошибки внутри `attachLogsIfNeeded` **не прокидываются** из сервиса — ловятся там же и логируются через SLF4J. Поэтому ошибка в сборе логов никогда не сломает результат самого теста.

#### 1.2.3 `private static PodLogger annotation(ExtensionContext context)` — читает аннотацию с класса

- **Параметры**: контекст Jupiter.
- **Шаги**:
  1. `context.getRequiredTestClass().getAnnotation(PodLogger.class)` — рефлексия ищет `@PodLogger` **строго на уровне класса** теста.
  2. Если `annotation == null` — **бросает `IllegalStateException("@PodLogger must be present on the test class")`**. Это fail-fast защита: если пользователь случайно поставил `@ExtendWith(PodLoggerExtension.class)` напрямую, забыв саму аннотацию — расширение упадёт с понятным сообщением, а не продолжит работу с дефолтами.
- **Возврат**: `@PodLogger` аннотация instance.

#### 1.2.4 `private static PodLoggerService service(ExtensionContext context)` — получает Spring бин

- **Параметры**: контекст Jupiter.
- **Шаги**: `SpringExtension.getApplicationContext(context).getBean(PodLoggerService.class)`.
- **Почему так**: Extension создаётся JUnit-engine, а не Spring. Поэтому инжект `@Autowired` в Extension не работает. Доступ к ApplicationContext — через публичный утилитный метод `SpringExtension.getApplicationContext`, который достаёт контекст из Store Jupiter.
- **Возврат**: Spring-бин `PodLoggerService` (Singleton scope).
- **Требование**: Чтобы это работало, тестовый класс **обязательно** должен запускаться под Spring (`@SpringBootTest` / `@ExtendWith(SpringExtension.class)`). Сама аннотация `@PodLogger` включает `SpringExtension` через мета-аннотацию — поэтому пользователю достаточно `@SpringBootTest`.

---

## 1.3 Класс `PodLoggerService` — Spring-компонент, бизнес-логика

- **FQN**: `com.example.podlogger.PodLoggerService`
- **Файл**: [PodLoggerService.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/junit-pod-logger/src/main/java/com/example/podlogger/PodLoggerService.java#L1-L74)
- **Стереотип Spring**: `@Component` (автоскан через `PodLoggerConfiguration.@ComponentScan`)
- **Назначение**: **Вся чистая бизнес-логика библиотеки** (JUnit-независимая, легко тестируется unit-тестами).
- **Конструктор**: Lombok `@RequiredArgsConstructor` — генерирует конструктор со всеми `final` полями. Spring 4.3+ автоматически инжектит через constructor injection (без `@Autowired` над конструктором).

### Зависимости (injected final fields)

| Поле | Тип | Назначение |
|------|-----|-----------|
| `openshiftClient` | `com.example.podlogger.client.OpenshiftClient` | Декоратор над fabric8-клиентом K8s/OS с методом `getLog() → List<PodLogDto>`. |
| `properties` | `PodLoggerProperties` | Хранит 3 текущих параметра (collectOnFailOnly, namespace, podLabelSelector). Обновляются из аннотации per-class. |
| `objectMapper` | `com.fasterxml.jackson.databind.ObjectMapper` | Jackson: сериализация итогового JSON аттача в pretty-printed. Конфигурируется в `PodLoggerConfiguration.objectMapper()` (JavaTimeModule + отключение WRITE_DATES_AS_TIMESTAMPS). |

### Статические константы сервиса

| Константа | Тип / Значение | Назначение |
|-----------|---------------|-----------|
| `log` | SLF4J `Logger` | Логирование событий сбора. |
| `SKEW_SECONDS` | `int = 2` | Коррекция рассинхрона часов между тестовой JVM и контейнером поды при фильтрации по временному окну. Фактическое окно = `[start - 2с, end + 2с]`. Если в закрытом контуре наблюдается большой drift NTP — увеличить до 5–10. |

### Методы класса

#### 1.3.1 `public void applyAnnotation(PodLogger annotation)` — применить параметры аннотации к Properties

- **Вход**: instance аннотации `@PodLogger` (из `PodLoggerExtension.annotation(context)`).
- **Шаги**: сеттит 3 поля в `PodLoggerProperties`:
  1. `properties.setNamespace(annotation.namespace())`
  2. `properties.setPodLabelSelector(annotation.podLabelSelector())`
  3. `properties.setCollectOnFailOnly(annotation.collectOnFailOnly())`
- **Возврат**: `void`.
- **Когда вызывается**: Из `PodLoggerExtension.beforeEach` — то есть **на каждый invocation**. Хотя аннотация стоит над классом (одна на все кейсы), вызов на каждый invocation безвреден — идемпотентные сеттеры.

#### 1.3.2 `public void attachLogsIfNeeded(ExtensionContext context, LocalDateTime start, LocalDateTime end, boolean failed)` — основная точка входа

- **Входные параметры**:

| Параметр | Тип | Откуда берётся | Назначение |
|----------|-----|---------------|-----------|
| `context` | `ExtensionContext` | Jupiter | Используем `getDisplayName()` для имени аттача; `getDisplayName()` у параметризованного с `name = "{0}"` = код ошибки (UNKNOWN_SKU и т.д.). |
| `start` | `LocalDateTime` (UTC) | Store `START_KEY` | Левая граница окна времени, до вычитания SKEW. |
| `end` | `LocalDateTime` (UTC) | `LocalDateTime.now()` в afterEach | Правая граница, после прибавления SKEW. |
| `failed` | `boolean` | `context.getExecutionException().isPresent()` | Флаг, включающий прикрепление при режиме `collectOnFailOnly=true`. |

- **Шаги метода**:

1. **Early-exit check 1 (режим collectOnFailOnly)**:
   ```java
   if (properties.isCollectOnFailOnly() && !failed) {
     log.debug("Skipping pod logs..."); return;
   }
   ```
   Если аннотация говорит «только падения» и кейс зелёный — сразу выходим, K8s API не трогаем. Экономия трафика/времени.

2. **Early-exit check 2 (нет start timestamp)**:
   ```java
   if (start == null) { log.warn("No start timestamp..."); return; }
   ```
   Защита от редких гонок, если Store по какой-то причине пуст.

3. **Задержка flush-логов**:
   ```java
   Thread.sleep(500);
   ```
   500 мс сна, чтобы записи в stdout поды успели «дойти» до `pods/log` API kubectl (асинхронный stdout flush в containerd иногда задерживается). При миграции — если наблюдаете пропуски последних записей — увеличить до 1000–1500 мс.

4. **Получение сырых логов и парсинг**:
   ```java
   List<PodLogDto> podLogs = openshiftClient.getLog();
   ```
   Делегируем полностью — client достаёт одну большую строку из K8s API, парсит по JSON-строкам.

5. **Формирование временного окна с учётом skew**:
   ```java
   LocalDateTime from = start.minusSeconds(SKEW_SECONDS);
   LocalDateTime to = end.plusSeconds(SKEW_SECONDS);
   ```

6. **Stream-фильтрация записей по окну**:
   ```java
   List<PodLogDto> window = podLogs.stream()
       .filter(entry -> entry.getTimestamp() != null)
       .filter(entry -> !entry.getTimestamp().isBefore(from) && !entry.getTimestamp().isAfter(to))
       .collect(Collectors.toList());
   ```
   - Записи, у которых null timestamp (например, строка JSON без этого поля или она не спарсилась) — пропускаются.
   - Логика включения границ: `>= from` И `<= to` (через отрицание isBefore/isAfter).

7. **Сериализация + Allure attachment**:
   ```java
   String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(window);
   String name = "pod-logs-" + sanitize(context.getDisplayName());
   Allure.addAttachment(name, "application/json", json, ".json");
   log.info("Attached {} pod log events for {} (window {} .. {})", window.size(), name, from, to);
   ```
   - `writerWithDefaultPrettyPrinter()` — человекочитаемый многострочный JSON в аттаче Allure.
   - `sanitize(...)` — удаляет небезопасные символы из имени файла (пробелы, `/`, кириллица → заменяются `_`).

8. **Try/catch оборачивает пункты 4-7**:
   ```java
   } catch (Exception e) { log.error("Failed to attach pod logs to Allure", e); }
   ```
   Любая ошибка (недоступность K8s API, null в Jackson, ошибка записи attachment в Allure) — **только логируется**, не кидается дальше. Тестовый кейс не ломается из-за проблем со сбором логов.

#### 1.3.3 `private static String sanitize(String displayName)` — очистка имени аттача

- **Вход**: произвольная строка displayName (например, `UNKNOWN_SKU` или `Case with spaces/slashes`)
- **Шаги**: `displayName.replaceAll("[^a-zA-Z0-9._-]", "_")`
- **Возврат**: безопасная для файловой системы строка (все символы, кроме латинских букв/цифр и `._-` → подчёркивание).
- **Зачем**: Имя аттача используется как часть имени файла в allure-results. Пробелы/слеши/кириллица могут сломать генератор отчёта Allure на некоторых OS/Jenkins.

---

## 1.4 Класс `PodLoggerProperties` — хранилище конфигурации runtime

- **FQN**: `com.example.podlogger.PodLoggerProperties`
- **Файл**: [PodLoggerProperties.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/junit-pod-logger/src/main/java/com/example/podlogger/PodLoggerProperties.java#L1-L16)
- **Стереотип Spring**: `@Component`
- **Назначение**: 3 mutable-поля с актуальными значениями параметров (обновляются `PodLoggerService.applyAnnotation(...)` перед каждым invocation).
- **Lombok**: `@Getter` + `@Setter` — генерирует все геттеры/сеттеры.

### Поля с default-значениями

| Поле | Тип | Default | Для чего |
|------|-----|---------|---------|
| `collectOnFailOnly` | `boolean` | `true` | Зеркало атрибута аннотации. |
| `namespace` | `String` | `"default"` | Зеркало атрибута аннотации. |
| `podLabelSelector` | `String` | `"app=demo-api"` | Зеркало атрибута аннотации. |

**Почему отдельный класс, а не три поля прямо в Service?** — Разделение ответственности: Properties — изменяемое состояние, Service — stateless логика. Легко потом заменить на `@ConfigurationProperties(prefix = "pod-logger")` и читать из `application.yml`, если в закрытом контуре захотят переопределять дефолты пропертями, а не аннотацией.

---

## 1.5 Класс `PodLoggerConfiguration` — Spring auto-конфигурация + ObjectMapper

- **FQN**: `com.example.podlogger.PodLoggerConfiguration`
- **Файл**: [PodLoggerConfiguration.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/junit-pod-logger/src/main/java/com/example/podlogger/PodLoggerConfiguration.java#L1-L24)
- **Стереотип Spring**: `@Configuration` + `@ComponentScan(basePackages = "com.example.podlogger")`
- **Назначение**:
  1. Сканирует package `com.example.podlogger` и регистрирует бины: `PodLoggerService`, `PodLoggerProperties`, `OpenshiftClient`. Без этого компоненты не попадали бы в ApplicationContext.
  2. Предоставляет кастомный бин `ObjectMapper` с настроенной поддержкой `LocalDateTime` (используется для сериализации/десериализации аттача и парсинга логов).

### Методы конфигурации

#### 1.5.1 `public ObjectMapper objectMapper()` — кастомный Jackson бин

- **Аннотации Spring**:
  - `@Bean` — регистрирует в контексте как синглтон.
  - `@ConditionalOnMissingBean(ObjectMapper.class)` — **Spring Boot auto-config friendly**: если в приложении уже есть свой `ObjectMapper` (например, стандартный Spring Boot Jackson auto-config с кастомными модулями), то библиотека **не перезапишет** его, а использует существующий. Это важно при интеграции в реальный проект.
- **Шаги конфигурации**:
  1. `new ObjectMapper()`
  2. `registerModule(new JavaTimeModule())` — ОБЯЗАТЕЛЬНО: включает десериализацию/сериализацию `java.time.LocalDateTime` у Jackson. Без этого `PodLogDto.timestamp` типа `LocalDateTime` сломается при парсинге (Jackson core не знает про JSR-310 типы из коробки).
  3. `disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` — чтобы при сериализации `LocalDateTime` в JSON аттача получалась строка ISO `yyyy-MM-ddTHH:mm:ss.SSS`, а не массив `[year, month, day, ...]` как таймстемп.
- **Возврат**: настроенный `ObjectMapper`.

---

## 1.6 Класс `com.example.podlogger.client.OpenshiftClient` — клиент сбора логов K8s/OS

- **FQN**: `com.example.podlogger.client.OpenshiftClient`
- **Файл**: [junit-pod-logger/src/main/java/com/example/podlogger/client/OpenshiftClient.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/junit-pod-logger/src/main/java/com/example/podlogger/client/OpenshiftClient.java#L1-L90)
- **Стереотип Spring**: `@Component`
- **Назначение**: Декоратор Fabric8-клиента. Реализует **ровно тот метод**, который просили в промпте: `List<PodLogDto> log = openshiftClient.getLog();`. Два шага:
  1. Получить полный дамп логов через Fabric8 `pods().getLog()` — **одна большая String** (как требует промпт: «получаю большой лог файл в одной Стринге»).
  2. Парсить построчно Jackson на `PodLogDto`.
- **Конструктор**: Lombok `@RequiredArgsConstructor`.

### Зависимости (final fields)

| Поле | Тип | Назначение |
|------|-----|-----------|
| `fabric8` | `io.fabric8.openshift.client.OpenShiftClient` | Реальный клиент Fabric8 (уже адаптированный к OpenShift). **Инжектируется не в этой библиотеке, а в consuming-проекте**: в демо-модуле `demo-tests` это `ClusterConfig.fabric8OpenShiftClient()`, который создаёт клиент из kubeconfig K3s. При миграции — ваш тестовый проект обязан поставить бин `OpenShiftClient` (с подключением к реальному кластеру закрытого контура). |
| `properties` | `PodLoggerProperties` | Берём отсюда текущие namespace и podLabelSelector (уже обновлённые аннотацией). |
| `objectMapper` | `ObjectMapper` | Используется для парсинга отдельных JSON-строк лога в `PodLogDto`. |

### Методы класса

#### 1.6.1 `public List<PodLogDto> getLog()` — публичный контрактный метод (точно как промпт)

- **Шаги**:
  1. `String raw = fetchRawLog()` — получаем одну большую String-дампу из K8s.
  2. `return parseLogDump(raw)` — парсим её в список DTO.
- **Возврат**: `List<PodLogDto>`, возможно пустой (если нет логов или все строки не JSON).
- **Важно**: именно такой сигнатуры и именования придерживались строго по тексту промпта: «методом `List<PodLogDto> log = openshiftClient.getLog();`».

#### 1.6.2 `String fetchRawLog()` — достаёт дамп из Fabric8 (package-private для теста)

- **Модификатор доступа**: `String` (package-level, не public) — чтобы unit-тест из того же пакета `OpenshiftClientParseTest` мог вызывать `parseLogDump` напрямую без моков Fabric8, а сам `fetchRawLog` не светился публично.
- **Шаги**:
  1. `String namespace = properties.getNamespace()`
  2. `String[] selector = splitSelector(properties.getPodLabelSelector())` — разбивает `"app=demo-api"` на `["app","demo-api"]`
  3. Fabric8 запрос:
     ```java
     List<Pod> pods = fabric8.pods()
         .inNamespace(namespace)
         .withLabel(selector[0], selector[1])
         .list()
         .getItems();
     ```
  4. Если `pods.isEmpty()` → бросает `IllegalStateException("No pods found in namespace " + ns + " with selector " + sel)` — fail-fast, когда подов нет.
  5. Выбор подходящего пода из списка:
     ```java
     Pod ready = pods.stream()
         .filter(OpenshiftClient::isReady)
         .findFirst()
         .orElse(pods.get(0));
     ```
     Приоритет: первый Running pod у которого все контейнеры `Ready=true`. Если таких нет — fallback на первый попавшийся (лучше частичные логи, чем никаких).
  6. Дамп логов:
     ```java
     return fabric8.pods().inNamespace(namespace).withName(ready.getMetadata().getName()).getLog();
     ```
     Возвращает **всю доступную историю stdout контейнера как одну строку** с разделителями строк `\n` (или `\r\n`).

#### 1.6.3 `List<PodLogDto> parseLogDump(String raw)` — парсер одной строки в список DTO (package-private)

- **Вход**: `String raw` — всё, что вернул Fabric8 (одна большая String с line separators).
- **Шаги**:
  1. Создаём пустой `ArrayList<PodLogDto> events`.
  2. Guard: `if raw == null || blank → return empty`.
  3. Цикл по `raw.split("\\R")` — Java regex `\R` универсально режет по `\n`, `\r\n`, `\r`. Для каждой `line`:
     - `trim()`
     - Если не начинается с символа `{` → **пропускаем**. Это фильтр от:
       - Строк K8s-preamble (иногда API добавляет свои заголовочные строки не JSON)
       - Стандартного Spring banner при старте (не JSON)
       - Строки WARN/INFO сторонних библиотек в текстовом формате
     - Пытаемся парсить: `objectMapper.readValue(trimmed, PodLogDto.class)`
     - Успех → добавляем в `events`
     - Exception → `log.debug("Skipping non-DTO log line: {}", trimmed)`. Отдельная строка не ломает весь парсинг.
- **Возврат**: `List<PodLogDto>`.

#### 1.6.4 `private static boolean isReady(Pod pod)` — критерий готовности пода

- **Логика (все условия должны быть true)**:
  1. `pod.getStatus() != null`
  2. `"Running".equals(pod.getStatus().getPhase())` (не Pending/СrashLoopBackOff)
  3. `pod.getStatus().getContainerStatuses() != null`
  4. `containerStatuses.stream().allMatch(cs → Boolean.TRUE.equals(cs.getReady()))` — **ВСЕ** контейнеры в поде имеют `Ready = true` (инициализирующие контейнеры не считаются, их нет в containerStatuses).
- **Возврат**: `true` / `false`.
- **Примечание**: используем `Boolean.TRUE.equals(...)` вместо `cs.getReady()` — потому что Fabric8 DTO возвращает `Boolean` (nullable), а не `boolean`. Прямое сравнение с unboxing выбросит NPE, если контейнер ещё не пометился ready.

#### 1.6.5 `static String[] splitSelector(String selector)` — парсер селектора `key=value`

- **Модификатор**: `static`, package-level — тестируемо без инстанса.
- **Вход**: строка формата `key=value`, например `"app=demo-api"`.
- **Шаги**:
  1. `int eq = selector.indexOf('=')`
  2. Если `eq <= 0` (нет знака или он первый символ) → `IllegalArgumentException("podLabelSelector must be key=value, got: " + selector)`. Недопустимые форматы: `"=value"`, `"app"` (без знака), `"app=="` (первый знак 0 позиции).
  3. Возврат 2-элементный массив: `[0]=key substring до =`, `[1]=value substring после =`.
- **Ограничение текущей реализации**: Не поддерживает множественные селекторы через запятую (`app=x,tier=y`) и нестандартные форматы (`!=`, `in/not in`). Только **строго одно равенство**. Если в закрытом контуре нужно несколько лейблов — расширить метод.

---

## 1.7 Класс `com.example.podlogger.client.PodLogDto` — Data Transfer Object события лога пода

- **FQN**: `com.example.podlogger.client.PodLogDto`
- **Файл**: [PodLogDto.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/junit-pod-logger/src/main/java/com/example/podlogger/client/PodLogDto.java#L1-L26)
- **Назначение**: POJO для десериализации **одной JSON-строки** stdout пода и сериализации итогового массива в Allure attachment.
- **Lombok аннотации**:

| Аннотация | Что генерирует |
|-----------|---------------|
| `@Data` | Getters, Setters, `toString()`, `equals()`, `hashCode()` |
| `@Builder` | Builder pattern: `PodLogDto.builder().message("...").build()` — удобно в тестах |
| `@NoArgsConstructor` | Безаргументный конструктор — требуется Jackson для десериализации |
| `@AllArgsConstructor` | Конструктор со всеми полями — нужен для `@Builder` |

- **Jackson-аннотации**:

| Аннотация | Над чем | Назначение |
|-----------|---------|-----------|
| `@JsonIgnoreProperties(ignoreUnknown = true)` | Класс | **НЕ падаем** если в JSON-строке лога есть лишние поля, которых нет в DTO (например, `@version`, `thread_name`, `stack_trace` у логстэша). |
| `@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")` | Поле `timestamp` | Жёстко фиксированный формат timestamp. Полностью совпадает с `<pattern>...</pattern>` в `logback-spring.xml` демо-приложения. |

### Поля DTO

| Поле | Тип | Для чего | Источник в JSON-логе |
|------|-----|---------|---------------------|
| `timestamp` | `java.time.LocalDateTime` | Время события лога. Используется для фильтрации по временному окну. | JSON поле `timestamp` формата `2026-08-30T02:55:12.456` (UTC, как настроено в logback). |
| `level` | `String` | Уровень логирования: `"ERROR"`, `"INFO"`, `"WARN"`, `"DEBUG"`. В демо-кейсе интересует ровно одна ERROR-запись на кейс. | JSON поле `level`. |
| `message` | `String` | Текст сообщения: `"Unknown SKU"` и т.д. Основное полезное содержимое для расследования. | JSON поле `message`. |
| `logger` | `String` | Имя logger-а (класса), который написал строку: `"com.example.demoapp.OrderController"`. Полезно фильтровать вручную в аттаче. | JSON поле `logger`. |

---

# Раздел 2. Модуль `demo-app` — Spring Boot демо-сервис

Package: `com.example.demoapp.*`

Назначение модуля: **простейшее web-приложение с 2 ручками**, которое пишет ERROR в stdout в **JSON формате** (логстэш), чтобы OpenshiftClient мог его парсить обратно. Полностью воспроизводимо — не требует БД, очередей, внешних зависимостей.

---

## 2.1 Класс `DemoAppApplication` — точка входа Spring Boot

- **FQN**: `com.example.demoapp.DemoAppApplication`
- **Файл**: [demo-app/src/main/java/com/example/demoapp/DemoAppApplication.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-app/src/main/java/com/example/demoapp/DemoAppApplication.java#L1-L12)
- **Аннотация**: `@SpringBootApplication` (составная из `@Configuration`, `@EnableAutoConfiguration`, `@ComponentScan("com.example.demoapp")`)
- **Метод** `public static void main(String[] args)`: `SpringApplication.run(DemoAppApplication.class, args)` — стандартная точка входа, запускает embedded Tomcat на порту из `application.properties` (8080).

---

## 2.2 Класс `OrderController` — REST-контроллер (2 HTTP ручки)

- **FQN**: `com.example.demoapp.OrderController`
- **Файл**: [OrderController.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-app/src/main/java/com/example/demoapp/OrderController.java#L1-L34)
- **Аннотация Spring Web**: `@RestController` → `@Controller` + `@ResponseBody` на все методы.
- **Logger**: SLF4J `private static final Logger log = LoggerFactory.getLogger(OrderController.class)`
- **Статическая карта кодов ошибок**:

```java
static final Map<String, String> ERRORS = Map.of(
    "UNKNOWN_SKU",      "Unknown SKU",
    "OUT_OF_STOCK",     "Item is out of stock",
    "PAYMENT_DECLINED", "Payment was declined",
    "USER_BLOCKED",     "User is blocked");
```

Используем `Map.of` (immutable map Java 9+). Строки-значения — ровно то, что `OrderErrorIT` проверяет в `.body("message", equalTo(expectedMessage))` ассертах.

### Методы HTTP

#### 2.2.1 `@GetMapping("/health") health()` — readiness/liveness probe

- **HTTP**: `GET http://host:8080/health`
- **Возврат**: `Map<String, String> = Map.of("status", "UP")` → JSON `{"status":"UP"}`
- **Статус HTTP**: 200 OK (по умолчанию у `@RestController`)
- **Использование**: K8s `readinessProbe` + `livenessProbe` в манифесте (`httpGet /health 8080`). Без этой ручки K3s считал бы pod не Ready и Testcontainers Awaitility завис бы на 2 минуты до таймаута.

#### 2.2.2 `@GetMapping("/api/orders/{code}") order(@PathVariable String code)` — основная «ошибочная» ручка

- **HTTP**: `GET http://host:8080/api/orders/<code>` где `<code>` — один из ключей `ERRORS`
- **Вход**: `@PathVariable String code` — сегмент URL
- **Шаги**:
  1. `String message = ERRORS.getOrDefault(code, "Unknown error code: " + code)` — достаём текст сообщения по коду. Если код не из списка → custom fallback.
  2. **⭐ Ключевая сторока для всего демо**:
     ```java
     log.error("{}", message);
     ```
     Пишет ровно ОДНУ ERROR-запись в stdout. Logback-encoder преобразует её в JSON строку:
     ```json
     {"timestamp":"2026-08-30T03:00:05.123","level":"ERROR","logger":"com.example.demoapp.OrderController","message":"Unknown SKU"}
     ```
     Именно эта строка потом:
     - попадает в `pods/log` K8s API → `fetchRawLog`
     - парсится `parseLogDump` → `PodLogDto(timestamp=..., level=ERROR, message=Unknown SKU, logger=...)`
     - попадает в окно времени кейса → сериализуется в Allure attachment pod-logs-UNKNOWN_SKU.json
     - человек в отчёте видит: «тест упал, в логах поды есть именно ожидаемая ошибка».
  3. Возврат HTTP 400 Bad Request с JSON телом:
     ```java
     ResponseEntity.badRequest().body(Map.of("code", code, "message", message));
     ```
     RestAssured в тесте ассертит это `.statusCode(400)` + body `code` и `message`.

---

## 2.3 Конфигурационные файлы demo-app

### 2.3.1 `application.properties`

Файл: [demo-app/src/main/resources/application.properties](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-app/src/main/resources/application.properties#L1-L2)

| Свойство | Значение | Для чего |
|----------|----------|---------|
| `server.port` | `8080` | Совпадает с containerPort Deployment и port Service в K8s манифесте. |
| `spring.application.name` | `demo-api` | Имя приложения (отображается в логах / метриках). |

### 2.3.2 `logback-spring.xml` — JSON stdout логирование

Файл: [demo-app/src/main/resources/logback-spring.xml](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-app/src/main/resources/logback-spring.xml#L1-L24)

**Критический компонент** — форматирует каждую запись лога как **однострочный JSON**, идентичный структуре `PodLogDto`. Без этого OpenshiftClient не смог бы парсить логи.

Структура:
- `Appender name="JSON"` типа `ch.qos.logback.core.ConsoleAppender` (stdout — ровно то, что K8s перехватывает как container log).
- `Encoder` = `LoggingEventCompositeJsonEncoder` (logstash-logback-encoder). Составные провайдеры полей:

| Provider-провайдер | Поле в JSON | Формат | Совпадает с DTO полем |
|-------------------|-------------|--------|----------------------|
| `<timestamp>` | `timestamp` | `yyyy-MM-dd'T'HH:mm:ss.SSS`, **timeZone UTC** | `PodLogDto.timestamp` @JsonFormat |
| `<logLevel>` | `level` | стандарт | `PodLogDto.level` |
| `<loggerName>` | `logger` | полное FQN класса | `PodLogDto.logger` |
| `<message/>` | `message` | текст | `PodLogDto.message` |

- **`<timeZone>UTC</timeZone>`** — КРИТИЧЕСКИ важная синхронизация: Extension пишет start/end как `LocalDateTime.now(ZoneOffset.UTC)`. Если логгер будет писать в локальной таймзоне хоста — фильтр по окну времени в `PodLoggerService` ничего не найдёт (сдвиг на несколько часов).
- Root level = `INFO` с `<appender-ref ref="JSON"/>`.

### 2.3.3 `Dockerfile` демо-приложения

Файл: [demo-app/Dockerfile](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-app/Dockerfile#L1-L5)

| Строка | Назначение |
|--------|-----------|
| `FROM eclipse-temurin:17-jre-alpine` | Базовый образ: JRE 17 HotSpot на Alpine (компактный ~50MB). |
| `WORKDIR /app` | Рабочая директория контейнера. |
| `COPY target/demo-app.jar app.jar` | Копирует Maven-produced fat-jar в образ как `/app/app.jar`. Требует предварительно `mvn package` — jar должен лежать в `target/`. |
| `EXPOSE 8080` | Документирует, что контейнер слушает на 8080. |
| `ENTRYPOINT ["java", "-jar", "/app/app.jar"]` | Команда запуска (exec form, чтобы сигналы об остановке доходили до JVM). |

---

# Раздел 3. Модуль `demo-tests` — интеграционные тесты с поднятием K3s

Package: `com.example.demotest.*`
Scope: test (всё в src/test/java, src/test/resources)

---

## 3.1 Класс `DemoTestApplication` — Spring Boot приложение-тест-хост

- **FQN**: `com.example.demotest.DemoTestApplication`
- **Файл**: [demo-tests/src/test/java/com/example/demotest/DemoTestApplication.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-tests/src/test/java/com/example/demotest/DemoTestApplication.java#L1-L9)
- **Аннотации Spring**:
  - `@SpringBootApplication(scanBasePackages = {"com.example.demotest", "com.example.podlogger"})` — запускает component scan для двух пакетов:
    - `com.example.demotest` — тестовые бины `ClusterConfig`, сам класс-тест.
    - `com.example.podlogger` — бины библиотеки (`PodLoggerService`, `PodLoggerProperties`, `OpenshiftClient`). `PodLoggerConfiguration` там же лежит — её `@ComponentScan` дублируется, но это безвредно.
  - `@Import(ClusterConfig.class)` — явно импортирует конфигурационный класс с бинами `OpenShiftClient` и `demoApiPort`.

**Тело класса пустое**: нет полей, нет методов — всего лишь класс-маяк для Spring Boot Test, чтобы `@SpringBootTest(classes = DemoTestApplication.class)` знал, откуда поднимать контекст.

---

## 3.2 Класс `ClusterConfig` — Spring @Configuration с бинами Fabric8 и порта

- **FQN**: `com.example.demotest.ClusterConfig`
- **Файл**: [ClusterConfig.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-tests/src/test/java/com/example/demotest/ClusterConfig.java#L1-L22)
- **Аннотация Spring**: `@Configuration`
- **Назначение**: мост между `ClusterLifecycle` (static утилитный класс, не Spring бин) и Spring DI контекстом — выставляет бины, которые требует библиотека `junit-pod-logger`.

### Методы-бины

#### 3.2.1 `@Bean(destroyMethod = "") fabric8OpenShiftClient()` — бин OpenShiftClient

- **Spring-аннотации**: `@Bean(destroyMethod = "")`
  - `destroyMethod = ""` — отключаем Spring-автоматический вызов `.close()` у бина при shutdown контекста. Потому что закрытие клиента регистрируется отдельно в **JVM shutdown hook** внутри `ClusterLifecycle.stop()` (через `Runtime.getRuntime().addShutdownHook`). Если Spring тоже попытается закрыть — double-close race condition.
- **Тело метода**:
  1. `ClusterLifecycle.start()` — Idempotent. Если кластер ещё не подняли — поднимает. Если уже поднят — возвращает контроль сразу (synchronized-check `if(k3s != null) return`).
  2. `return ClusterLifecycle.client()` — берёт уже готовый Fabric8 OpenShiftClient instance.
- **Возврат**: `io.fabric8.openshift.client.OpenShiftClient` (Singleton Spring Bean).
- **Инжектится библиотекой** в поле `OpenshiftClient.fabric8`.

#### 3.2.2 `@Bean Integer demoApiPort()` — бин локального порта port-forward

- **Тело метода**:
  1. `ClusterLifecycle.start()` — снова idempotent гарантия.
  2. `return ClusterLifecycle.localPort()` — возвращает случайный свободный порт localhost, который Testcontainers K3s пробросил на `Service demo-api:8080` через `LocalPortForward`.
- **Возврат**: `Integer` (авто-unboxed Spring, когда нужно в int)
- **Инжектится** полем в `OrderErrorIT.demoApiPort` и передаётся RestAssured: `.port(demoApiPort)`.

---

## 3.3 Класс `ClusterLifecycle` — утилитный lifecycle K3s кластера (Testcontainers)

- **FQN**: `com.example.demotest.ClusterLifecycle`
- **Файл**: [ClusterLifecycle.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-tests/src/test/java/com/example/demotest/ClusterLifecycle.java#L1-L208)
- **Модификатор класса**: `public final class`, приватный конструктор `private ClusterLifecycle() {}` — **utility static-класс**.
- **Назначение**: Весь «грязный» код инфраструктуры: поднять K3s, собрать и импортировать образ demo-app, применить Deployment+Service, дождаться Ready pod, сделать port-forward, зарегистрировать cleanup.

### Статические константы

| Константа | Значение | Назначение |
|-----------|----------|---------|
| `log` | SLF4J Logger | Логгирование событий поднятия кластера. |
| `IMAGE` | `"demo-api:local"` | Тег Docker-образа демо-приложения. Тот же самый, что используется в Docker build и K8s Deployment manifest (imagePullPolicy Never). |
| `K3S_IMAGE` | `"rancher/k3s:v1.31.5-k3s1"` | Образ K3s для Testcontainers K3sContainer. Фиксированная версия — воспроизводимость. |

### Статические поля (lifecycle state)

| Поле | Тип | Назначение |
|------|-----|-----------|
| `private static K3sContainer k3s` | K3sContainer | Singleton контейнера K3s (null пока не стартовали). |
| `private static OpenShiftClient client` | Fabric8 | Fabric8-клиент, собранный из kubeconfig контейнера. |
| `private static LocalPortForward forward` | Fabric8 | Активное проброс соединения localhost → K8s Service demo-api:8080. |
| `private static int localPort` | int | Локальный порт на хосте (возвращается `ClusterLifecycle.localPort()`). |

### Публичные static-методы API

#### 3.3.1 `public static synchronized void start()` — точка входа поднятия всей инфраструктуры

- **Ключевое слово `synchronized`**: гарантия, что при конкурентных вызовах из нескольких тестов/потоков поднимется ровно 1 кластер.
- **Early-exit**: `if (k3s != null) { return; }` — idempotency.

**Порядок шагов:**

1. `buildImage()` — Docker build `demo-api:local`.
2. `k3s = new K3sContainer(DockerImageName.parse(K3S_IMAGE))`
   ```java
   .withCommand("server", "--disable=traefik", "--tls-san=127.0.0.1")
   .withLogConsumer(new Slf4jLogConsumer(...))
   k3s.start();
   ```
   - `--disable=traefik` — отключаем стандартный Ingress K3s Traefik (не нужен, экономим RAM/время старта).
   - `--tls-san=127.0.0.1` — добавляем IP в SAN TLS-сертификата API-server K3s, чтобы Fabric8 с `trustCerts=true` мог подключаться без ошибок hostname verification.
   - LogConsumer → логи K3s сервера (kubelet, apiserver) выводятся через SLF4J под logger `k3s` — полезно при диагностике проблем старта кластера.

3. Fabric8 client из kubeconfig K3s Container:
   ```java
   Config config = Config.fromKubeconfig(k3s.getKubeConfigYaml());
   config.setTrustCerts(true);     // самоподписанные сертификаты K3s
   client = new KubernetesClientBuilder().withConfig(config).build().adapt(OpenShiftClient.class);
   ```
   `.adapt(OpenShiftClient.class)` — преобразует Kubernetes client в OpenShift-совместимый, чтобы работал импорт `io.fabric8.openshift.client.OpenShiftClient`.

4. `importImage()` — `docker save` → tar → import в containerd K3s через `ctr`.
5. `applyManifest()` — Deployment + Service из `/k8s/demo-api.yaml` classpath resource.
6. `waitForPod()` — Awaitility до Ready pod с лейблом `app=demo-api`.
7. `LocalPortForward forward = client.services().inNamespace("default").withName("demo-api").portForward(8080)` — пробрасываем Service порт.
   `localPort = forward.getLocalPort()` — запоминаем случайный локальный порт.
8. `waitForHttp()` — Awaitility GET `/health` на `127.0.0.1:localPort` до HTTP 200 `"UP"`.
9. **Shutdown hook**: `Runtime.getRuntime().addShutdownHook(new Thread(ClusterLifecycle::stop))` — при завершении JVM гарантированно закроем port-forward, клиент, остановим K3s контейнер.
10. Финальный info-лог: `demo-api is reachable at http://127.0.0.1:{localPort}`.

#### 3.3.2 `public static OpenShiftClient client()` — getter для Spring-бина

- Возврат: готовый `client`.

#### 3.3.3 `public static int localPort()` — getter порта для RestAssured

- Возврат: `localPort`.

#### 3.3.4 `static void stop()` — cleanup shutdown hook

**Шаги (каждый try/catch индивидуально — если одна часть уже не работает, остальное чистим)**:
1. Закрываем `forward.close()` — LocalPortForward.
2. Закрываем `client.close()` — Fabric8 client, освобождаем HTTP соединения.
3. `k3s.stop()` — удаляет K3s Docker-контейнер (Testcontainers by default с удалением container на stop).

### Private static вспомогательные методы класса

#### 3.3.5 `private static void buildImage()` — docker build демо-образа

1. `Path demoApp = findDemoApp()` — ищет папку demo-app (с Dockerfile).
2. `Path jar = demoApp.resolve("target").resolve("demo-app.jar")`.
3. Guard: если jar не найден → `IllegalStateException` с сообщением: `"Run: mvn -pl demo-app -am package -DskipTests"`. Пользователь часто забывает собрать demo-app перед тестами.
4. `run(demoApp, "docker", "build", "-t", IMAGE, ".")` — запуск docker build.

#### 3.3.6 `private static void importImage()` — импортируем образ из хостового Docker в K3s containerd

K3s запущен в отдельном Docker контейнере, его containerd runtime **не видит образы хостового Docker Engine**. Поэтому нужен полный цикл export-import:

1. `Path tar = Files.createTempFile("demo-api", ".tar")` — временный tarball.
2. `run(Path.of("."), "docker", "save", "-o", tar.toAbsolutePath().toString(), IMAGE)` — хостовый Docker сохраняет образ как tar.
3. `k3s.copyFileToContainer(MountableFile.forHostPath(tar), "/tmp/demo-api.tar")` — копируем tar внутрь K3s контейнера по файловой системе.
4. `exec("ctr", "-n", "k8s.io", "images", "import", "/tmp/demo-api.tar")` — `ctr` (containerd CLI) импортирует tar в containerd namespace `k8s.io` (тот, в котором Kubelet ищет образы).
5. Опционально `exec("ctr ... images tag docker.io/library/demo-api:local demo-api:local")` — пытаемся доп-tag, чтобы Deployment с `image: demo-api:local` точно нашёл образ. Если образ уже так тегнут — `already exists` exception, обрабатываем: если stderr содержит «already exists», возвращаемся успешно.
6. `Files.deleteIfExists(tar)` — чистим временный файл.
7. Любая ошибка оборачивается в `IllegalStateException("Failed to import demo-api image into k3s", e)`.

#### 3.3.7 `private static void applyManifest()` — применяем K8s манифест

1. `try (InputStream in = ClusterLifecycle.class.getResourceAsStream("/k8s/demo-api.yaml"))` — читаем из classpath src/test/resources/k8s/demo-api.yaml.
2. Guard: если in null → `IllegalStateException("Missing classpath resource /k8s/demo-api.yaml")`.
3. `client.load(in).inNamespace("default").createOrReplace()` — Fabric8 DSL: загружает Deployment + Service (два объекта в одном YAML, разделённых `---`), создаёт или обновляет в namespace `default`. Idempotent.

#### 3.3.8 `private static void waitForPod()` — Awaitility до Ready pod

```java
Awaitility.await()
    .atMost(2, TimeUnit.MINUTES)
    .pollInterval(2, TimeUnit.SECONDS)
    .until(() -> {
       List<Pod> pods = client.pods().inNamespace("default").withLabel("app", "demo-api").list().getItems();
       return pods.stream().anyMatch(ClusterLifecycle::isReady);
    });
```

- Лейбл `app=demo-api` — совпадает с podSelector Deployment.
- Максимум 2 минуты, проверка каждые 2 секунды.
- Если за 2 минуты ни один pod не Ready — Awaitility ConditionTimeoutException.

#### 3.3.9 `private static boolean isReady(Pod pod)` — копия логики из OpenshiftClient (дублирование)

То же условие Running+ReadyAllContainers, что и в [ClusterLifecycle.java#L145-L150](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-tests/src/test/java/com/example/demotest/ClusterLifecycle.java#L145-L150). Небольшое дублирование, чтобы demo-tests не зависел от package-private метода OpenshiftClient. (При миграции можно вынести в общий utility или использовать OpenshiftClient).

#### 3.3.10 `private static void waitForHttp()` — Awaitility до HTTP 200 /health

1. `HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()` — стандартный Java 11 `java.net.http.HttpClient` (без внешних зависимостей).
2. Awaitility: atMost 1 мин, poll каждую 1 сек.
3. Poll lambda: GET `http://127.0.0.1:<localPort>/health`, ожидаем `statusCode 200 && body contains "UP"`. Любые ConnectException/SocketTimeoutException пока поднимается port-forward → возвращаем `false` и пробуем снова.

#### 3.3.11 `private static void exec(String... cmd)` — exec-команда внутри K3s container (ctr import/tag)

1. `ExecResult result = k3s.execInContainer(cmd)` — Testcontainers exec.
2. Если `exitCode != 0`:
   - Особый случай: команда содержит `"images tag"` И `stderr` содержит `"already exists"` → return (нормально, образ уже с нужным тегом).
   - Иначе → `IllegalStateException("k3s exec failed (" + joined + "): " + stderr)`.

#### 3.3.12 `private static void run(Path workDir, String... command)` — запуск внешней команды на хосте (docker build/save)

1. `ProcessBuilder pb = new ProcessBuilder(command).directory(workDir).redirectErrorStream(true)` — stdout + stderr слиты.
2. `Process process = pb.start()`
3. Читаем **всё** output процесса: `String output = new String(process.getInputStream().readAllBytes())`
4. `int code = process.waitFor()`
5. Если `code != 0` → `IllegalStateException(Arrays.toString(command) + " failed:\n" + output)`.
6. Если ок → info-лог `command joined -> output` (пользователь видит вывод docker build).

#### 3.3.13 `private static Path findDemoApp()` — разрешение пути к директории demo-app

Тест может запускаться из разных рабочих директорий: корень проекта, из модуля demo-tests, из IDEA с нестандартной cwd. Поэтому 3 кандидата:

1. `userDir.resolve("demo-app")` — cwd = корень проекта (обычный случай)
2. `userDir.resolve("..").resolve("demo-app")` — cwd = `demo-tests/`
3. `userDir.getParent().resolve("demo-app")` — fallback

Выбираем первый кандидата, у которого: `Files.isDirectory` и `Files.exists(resolve("Dockerfile"))`.

---

## 3.4 Класс `OrderErrorIT` — Integration Test, демонстрация @PodLogger

- **FQN**: `com.example.demotest.OrderErrorIT`
- **Файл**: [OrderErrorIT.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-tests/src/test/java/com/example/demotest/OrderErrorIT.java#L1-L65)
- Соглашение об именах: `*IT.java` — Integration Test (подхватывается maven-surefire-plugin в demo-tests/pom: `<include>**/*IT.java</include>`).
- **Аннотации над классом**:

```java
@SpringBootTest(classes = DemoTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@PodLogger(collectOnFailOnly = true)
```

- `@SpringBootTest(webEnvironment = NONE)` — Spring Test не запускает embedded Tomcat (он не нужен, мы дергаем внешний pod на K3s через RestAssured). Экономим время старта контекста.
- `@PodLogger(collectOnFailOnly = true)` — **ключевая аннотация над тестовым классом**, как и требовалось в промпте.

### Static-блок инициализации кластера

```java
static {
    ClusterLifecycle.start();
}
```

- Выполняется **один раз при загрузке класса OrderErrorIT в JVM** (перед всеми методами/beforeAll).
- Стратегия выбрана, чтобы кластер был готов ещё до того, как Spring начнёт создавать бины (которые вызывают ClusterLifecycle.start() повторно). Безопасно, idempotent.

### @BeforeAll статический метод — REST-assured глобальные фильтры логирования

```java
@BeforeAll
static void configureRestAssuredLogging() {
    RestAssured.replaceFiltersWith(
            new RequestLoggingFilter(LogDetail.ALL, true),
            new ResponseLoggingFilter(LogDetail.ALL, true));
}
```

Стандартные фильтры из пакета `io.restassured.filter.log`. Работают для всех 4 invocation кейса, в консоль выводят:
- Request: URI + method + headers + path params + body + cookies
- Response: status line + headers + pretty-printed JSON body

### Авто-инжектируемое поле

```java
@Autowired
private Integer demoApiPort;
```

Значение из бина `ClusterConfig.demoApiPort()` — локальный случайный порт проброшенный в K3s Service.

### Data provider (MethodSource) для параметризованного теста

```java
static Stream<Arguments> errorCases() {
    return Stream.of(
        Arguments.of("UNKNOWN_SKU",      "Unknown SKU"),
        Arguments.of("OUT_OF_STOCK",     "Item is out of stock"),
        Arguments.of("PAYMENT_DECLINED", "Payment was declined"),
        Arguments.of("USER_BLOCKED",     "User is blocked"));
}
```

Ровно 4 кейса (нижняя граница 3–4 из промпта). Пары code + expectedMessage полностью совпадают с `Map.of` в `OrderController.ERRORS`. Порядок не важен — JUnit запускает их Stream-порядок.

### Метод теста — параметризованный

```java
@ParameterizedTest(name = "{0}")
@MethodSource("errorCases")
void apiErrorIsLoggedOnPod(String code, String expectedMessage)
```

- `name = "{0}"` — displayName **для каждого invocation** будет равно первому аргументу `code` → `"UNKNOWN_SKU"`, `"OUT_OF_STOCK"` и т.д. Это displayName потом используется в `PodLoggerService.sanitize(context.getDisplayName())` для имени аттача: `"pod-logs-UNKNOWN_SKU"`. Видно в Allure Suite дереве.
- Параметры метода: `code` (арг №0) + `expectedMessage` (арг №1).

#### Тело метода (RestAssured DSL + fail)

```java
given()
    .baseUri("http://127.0.0.1")   // localhost, где висит LocalPortForward
    .port(demoApiPort)              // случайный порт из бина
.when()
    .get("/api/orders/{code}", code)   // GET /api/orders/<code>
.then()
    .statusCode(400)                  // ожидаем HTTP 400 (для всех кодов)
    .body("code", equalTo(code))      // JSON-поле code === параметр
    .body("message", equalTo(expectedMessage));  // JSON-поле message === ожидаемый текст

fail("collectOnFailOnly demo: force failure after expected API error " + code
     + " so @PodLogger attaches this invocation's pod logs to Allure");
```

**Почему вызов fail() на конце?** — демонстрация режима `collectOnFailOnly=true`. Без `fail()` кейс прошёл бы успешно (ассерты на HTTP 400 сработали), и аттача Allure не было бы. Чтобы пользователь мог увидеть в отчёте 4 аттача — намеренно роняем кейс.

**Порядок жизненного цикла invocation (1 кейс):**
1. `PodLoggerExtension.beforeEach` → Store:start, applyAnnotation
2. `configureRestAssuredLogging` фильтры применяются (но @BeforeAll — 1 раз на класс, не per invocation)
3. `apiErrorIsLoggedOnPod(code, message)` выполняется
4. `fail()` кидает AssertionFailedError
5. JUnit помечает invocation как FAILED
6. `PodLoggerExtension.afterEach` → start/end читаются, failed=true → attachLogsIfNeeded
7. OpenshiftClient.getLog() достаёт логи, парсит → окно по времени → Allure attachment `"pod-logs-" + code + ".json"`

---

# Раздел 4. Unit-тест библиотеки

## 4.1 Класс `com.example.podlogger.client.OpenshiftClientParseTest` — unit-тест парсера логов

- **FQN**: `com.example.podlogger.client.OpenshiftClientParseTest`
- **Файл**: [junit-pod-logger/src/test/java/com/example/podlogger/client/OpenshiftClientParseTest.java](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/junit-pod-logger/src/test/java/com/example/podlogger/client/OpenshiftClientParseTest.java#L1-L34)
- **Модуль**: `junit-pod-logger` (src/test/java)
- **Запуск не требует Docker/K3s** — чисто unit-тест пакетно-видимого метода `parseLogDump`.

### Метод `@Test void parsesJsonLinesAndSkipsNoise()`

**Тестовый дамп**:
```
some kube preamble
{"timestamp":"2026-08-29T17:01:02.123","level":"ERROR","message":"Unknown SKU","logger":"c.e.d.OrderController"}
not json
{"timestamp":"2026-08-29T17:01:03.000","level":"INFO","message":"started","logger":"demo"}
```

Содержит 3 типа строк: 2 JSON (1 ERROR, 1 INFO), 1 kube-preamble, 1 не-JSON строка. Парсер должен пропустить kube-preamble и not json → распарсить ровно 2 записи.

**Arrange**: ObjectMapper с `JavaTimeModule` (как делает PodLoggerConfiguration), новый OpenshiftClient с null fabric8 (не используется) и стандартными PodLoggerProperties.

**Act**: `List<PodLogDto> logs = client.parseLogDump(dump)`

**Asserts**:
```java
assertEquals(2, logs.size());                                                             // 2 записи
assertEquals("ERROR", logs.get(0).getLevel());                                            // 1 = ERROR
assertEquals("Unknown SKU", logs.get(0).getMessage());                                    // message
assertEquals(LocalDateTime.of(2026,8,29,17,1,2, 123_000_000), logs.get(0).getTimestamp()); // наносекунды точно
assertTrue(logs.get(1).getMessage().contains("started"));                                 // 2 = INFO started
```

**Статус при сборке**: ✅ `Tests run: 1, Failures: 0, Errors: 0` BUILD SUCCESS.

---

# Раздел 5. Поддержка CI/CD: Jenkins, Dockerfile для агента, Kubernetes манифесты, POM-файлы

## 5.1 Jenkinsfile — declarative pipeline

Файл: [Jenkinsfile](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/Jenkinsfile#L1-L34)

| Структура | Назначение |
|-----------|-----------|
| `agent any` | Любой Jenkins агент с Docker socket + Maven 3.9 + JDK 17. |
| tools `maven 'maven-3.9'` / `jdk 'jdk-17'` | Требуемая toolchain Jenkins Global Tool Configuration. |
| options `timestamps()` | Аннотирует вывод консоли временными метками. |
| Stage Build `mvn -B -DskipTests package` | Сборка всех 3 модулей, jar артефакты. |
| Stage Docker image `docker build -t demo-api:local demo-app` | Образ local-tag, чтобы K3s manifest нашёл его с `imagePullPolicy: Never`. |
| Stage Tests `catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') { mvn -pl demo-tests -am test }` | Оборачиваем в catchError, потому что **все 4 кейса намеренно падают** fail() → иначе Pipeline бы окрасился в FAILURE и не дошёл до Allure. UNSTABLE — жёлтый цвет в Jenkins, Allure плагин работает. |
| Stage Allure `allure results: [[path: 'demo-tests/target/allure-results']]` | Jenkinss Allure Plugin генерирует отчёт и публикует в сборке. |

## 5.2 Dockerfile Jenkins агента

Файл: [docker/jenkins/Dockerfile](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/docker/jenkins/Dockerfile#L1-L15)

Базовый образ `maven:3.9.9-eclipse-temurin-17` (уже есть JDK17 + Maven), сверху доустанавливаем **docker-ce-cli** официальным путём (GPG key + apt repo). WORKDIR `/workspace`.

Требование к использованию: агент должен иметь `mount /var/run/docker.sock` от хоста — чтобы Testcontainers K3s мог запускать вложенные контейнеры (DinD-подход через socket-share).

## 5.3 Kubernetes манифест demo-api.yaml (Deployment + Service)

Файл: [k8s/demo-api.yaml](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/k8s/demo-api.yaml#L1-L48) и идентичная копия в demo-tests classpath [demo-tests/src/test/resources/k8s/demo-api.yaml](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-tests/src/test/resources/k8s/demo-api.yaml#L1-L47)

**Deployment `demo-api`**:
- Replicas: 1
- Label selector: `app=demo-api` (совпадает с default `@PodLogger.podLabelSelector`)
- Container: image `demo-api:local`, `imagePullPolicy: Never` (не пушить в registry, образ уже импортирован локально)
- containerPort 8080
- readinessProbe httpGet /health:8080 initialDelay=3s period=2s
- livenessProbe httpGet /health:8080 initialDelay=10s period=10s

**Service `demo-api` ClusterIP**:
- Selector: `app=demo-api`
- port 8080 → targetPort 8080

## 5.4 allure.properties — путь результатов

Файл: [demo-tests/src/test/resources/allure.properties](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-tests/src/test/resources/allure.properties#L1-L1)
```
allure.results.directory=target/allure-results
```
Генерация результатов (JSON результатов + attachment файлы) в `demo-tests/target/allure-results`. Эта же папка в Jenkins stage.

## 5.5 Три POM-файла модулей + parent

### 5.5.1 Parent pom.xml — dependencyManagement версии

Файл: [pom.xml](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/pom.xml#L1-L135)

Modules list: `demo-app`, `junit-pod-logger`, `demo-tests`

Версии (properties):
- Java 17, Spring Boot 3.3.5, Fabric8 6.13.4, Allure 2.29.1, Testcontainers 1.20.4, Lombok 1.18.34, RestAssured 5.5.0, Awaitility 4.2.2, BouncyCastle 1.78.1, Logstash-logback 8.0

Plugins:
- Maven-compiler release 17 + annotation processor path Lombok
- Spring Boot maven plugin version
- Allure maven plugin 2.15.0

### 5.5.2 demo-app/pom.xml

Зависимости: spring-boot-starter-web, logstash-logback-encoder, lombok (optional).
Build: `<finalName>demo-app</finalName>` — чтобы jar был ровно `demo-app.jar` (как ожидает Dockerfile COPY). Плагин `spring-boot-maven-plugin` с goal `repackage` → fat-jar executable.

### 5.5.3 junit-pod-logger/pom.xml

Зависимости compile: junit-jupiter-api, spring-context, spring-test, jackson-databind, jackson-jsr310, fabric8 openshift-client, allure-junit5, slf4j-api, lombok (optional). Test: junit-jupiter (запускает unit-тест OpenshiftClientParseTest). `spring-boot-autoconfigure` = optional → для auto-configuration @ConditionalOnMissingBean.

### 5.5.4 demo-tests/pom.xml

Зависимости compile: самописная библиотека junit-pod-logger, spring-boot-starter, rest-assured, testcontainers junit-jupiter + k3s, awaitility, bcpkix-jdk18on, allure-junit5. Test: spring-boot-starter-test, junit-jupiter-params. Lombok optional.

Build plugins:
- `maven-surefire-plugin` 3.5.2 includes `**/*Test.java` + `**/*IT.java` (запускает и unit и IT).
- `allure-maven` — команда `allure:report` для локального отчёта.

---

# Раздел 6. Чек-лист миграции в закрытый контур

Для переноса библиотеки и логики в реальный проект внутри закрытого контура — последовательно выполнить:

1. ✅ Скопировать **артефакт `junit-pod-logger`** (код) как отдельный модуль или jar-зависимость в ваш проект тестов.
2. ✅ В тестовом проекте объявить бин **`io.fabric8.openshift.client.OpenShiftClient`** с реальным подключением к K8s/OS закрытого контура (kubeconfig, токен service account, etc.). Это единственная реальная замена демо-классов ClusterLifecycle/ClusterConfig.
3. ✅ В тестовом классе поставить **`@PodLogger(namespace="…", podLabelSelector="…", collectOnFailOnly=…)` над классом** как показано в [OrderErrorIT.java#L20-L25](file:///d:/repos/llmWorks/trae/pod-logger-junit-demo/demo-tests/src/test/java/com/example/demotest/OrderErrorIT.java#L20-L25). Тестовый класс обязан запускаться под Spring (`@SpringBootTest`).
4. ✅ В тестовом приложении гарантировать, что **stdout логи пишутся как JSON строки** с 4 полями timestamp (UTC, pattern yyyy-MM-ddTHH:mm:ss.SSS), level, message, logger. Аналог logback-spring.xml из demo-app.
5. ✅ При запуске тестов: библиотека автоматически в before/after фиксирует окно UTC, запрашивает `pods/log` через Fabric8, парсит Jackson, фильтрует, прикладывает Allure attachment.
6. ✅ Проверка: запустить тест с намеренным падением → открыть Allure отчёт → во вложениях кейса должен появиться `pod-logs-<имя кейса>.json` с записями только его временного окна.

---

КОНЕЦ ДОКУМЕНТА
