# PRD: OpenShift Event Handling для `@PodLogger`

**Статус:** реализовано в `junit-pod-logger` (as-built).  
**Канонический документ фичи:** этот файл (`docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`).  
**Общий PRD проекта:** [`docs/PodLoggerJunitDemoPRD.md`](../../PodLoggerJunitDemoPRD.md).  
**Каталог тестов:** [`docs/PodLoggerJunitDemoTest.md`](../../PodLoggerJunitDemoTest.md).  
**Смежная фича:** [`PersistentLogStoreStory.md`](../PersistentLogStoreStory/PersistentLogStoreStory.md).  
**Модуль:** `junit-pod-logger`  
**Клиент:** fabric8 `openshift-client` **6.13.4** (`io.fabric8.openshift.client.OpenShiftClient`)  

Контракт ниже совпадает с кодом. Исторические черновики в `docs/propmtHistory` источником истины не являются.

---

## 0. Инварианты (читать до кода)

1. В stdout поды (`oc logs`) из фреймворка **не пишем**. Маркеры прогона — только Kubernetes Events на целевой поде.
2. Публикуем **ровно два** lifecycle-Event на прогон: старт и финиш. Per-test `TestFailed` Event **не публикуем** (отменено).
3. Потребляем Events **только при упавшем invocation** и внутри `isPodAvailable()`.
4. Allure: Events прилагаем **отдельным аттачем** и дублируем в `PodLogDto.relevantEvents`. Пустой список **не** аттачим.
5. SQLite: Events **не** отдельная таблица. `relevantEvents` в БД **не** пишем. Persist логов — только если под доступна (`isPodAvailable == true`).
6. Event с кодом «стенд не работает» → **fail-fast всего прогона** (оставшиеся тесты не выполняются). Красный health **без** такого Event прогон **не** рвёт.
7. Ошибка publish/list/health HTTP **не** является stand-down Event. Publish — best-effort. List при ошибке = «ивентов нет».
8. `getLog()` не ломаем. Core Events только через `client.v1().events()`, не `client.events()`.

---

## 1. Цель

1. Публиковать на целевой поде Kubernetes Event в **начале** тестового прогона и **второй** Event в **конце** прогона (имя прогона + total/passed/failed).
2. Потреблять Events с поды, парсить в DTO с **кодом** (`reason`/`code`).
3. При красном тесте сначала понять, жива ли пода: `isPodAvailable()` = нет stand-down Event **и** health зелёный.
4. Неожиданное падение может быть следствием неработоспособности стенда. Тогда:
   - в Allure кладём Events (логи сервера могут отсутствовать);
   - если логи окна теста всё же снялись — кладём и их, и Events;
   - в SQLite логи **не** пишем, пока под недоступна;
   - stand-down Event → fail-fast оставшегося прогона.
5. Расширить `PodLogDto` полем `relevantEvents`. Протянуть `getEvents` через `PodLoggerService` и `PodLoggerExtension`.

---

## 2. Что изменилось относительно предыдущей редакции

| Тема | Было (черновик) | Стало (эта редакция) |
| --- | --- | --- |
| Маркер упавшего теста в stdout | вопрос, затем «нельзя» | подтверждено: **нельзя**, тема закрыта |
| Сколько Event публикуем | start + finish + per-test `TestFailed` | **только start и finish прогона** |
| Finish message | `testRunId` | имя прогона + `total` / `passed` / `failed` |
| Infra + CollectGate | skip **и** SQLite, **и** Allure | SQLite skip если под недоступна; Allure Events **всегда** при непустом списке на fail |
| Abort прогона | запрещён | **обязан** при stand-down Event |
| Health | не было | обязательный вход `isPodAvailable()` |
| `PodLogDto` | не трогаем | поле `relevantEvents` |
| Allure Events | «не в v1» | **в v1**, отдельный аттач + поле DTO |

Старые формулировки «skip весь forensic-выход» и «тест не abort» **недействительны**.

---

## 3. Два канала (не смешивать)

| Канал | API | Запись из `junit-pod-logger` | Чтение | Куда в отчёт |
| --- | --- | --- | --- | --- |
| Pod logs | `GET .../pods/{name}/log` | нет | `OpenshiftClient.getLog()` | Allure `pod-logs-*`, SQLite `log_entry` |
| Kubernetes Event | `POST/GET .../api/v1/namespaces/{ns}/events` | да, `publishPodEvent` | `getEvents` / `listPodEvents` | Allure `pod-events-*`, поле `relevantEvents` |

Fabric8 6.13:

```java
fabric8.v1().events().inNamespace(ns).resource(event).create();
fabric8.v1().events().inNamespace(ns).withInvolvedObject(podRef).list();
```

`client.events()` — группа `events.k8s.io`, **не** используем. `involvedObject.kind=Pod`, `name`+`uid` целевой поды. Имена Event — `generateName("pod-logger-")`.

---

## 4. Когда какой метод вызывается

Это обязательный контракт для extension. Реализатор не имеет права вызывать publish/get в других хуках без смены PRD.

```text
beforeAll
  1. applyAnnotation, startTestRun, положить testRunId в Store     // как сейчас, fail-fast при ошибке store
  2. PUBLISH  publishPodEvent(Normal, TestRunStarted, ...)         // best-effort
  3. GET+HEALTH  isPodAvailable()                                  // list events + health
     если stand-down Event → FAIL-FAST прогона (тесты класса не стартуют)
     если только health красный → лог warn, тесты идут (persist потом отсечётся)

beforeEach
  4. если Store.STAND_UNAVAILABLE → бросить IllegalStateException  // оставшиеся тесты не бегут
  5. записать testStartUtc                                         // как сейчас
  6. publish / getEvents — НЕТ

afterEach
  7. failed = executionException.isPresent()
     если !failed: attachLogsIfNeeded как сейчас (CollectGate). getEvents / isPodAvailable / Events-аттач — НЕТ. конец хука
  8. GET  только если failed: events = getEvents(start-SKEW, end+SKEW)
  9. GET+HEALTH  только если failed: availability = isPodAvailable()
     (внутри снова list без узкого окна — «текущее» состояние поды, см. §6.3)
 10. если failed и events не пустой:
        Allure attach pod-events-<displayName>.json
        каждому PodLogDto окна выставить relevantEvents = events
 11. если failed и events пустой: Events-аттач НЕ создавать
 12. failed → CollectGate для логов всегда true; collectRuntimeLogs(окно):
        если availability.available → SQLite saveLogs + Allure pod-logs-*
        если !availability.available:
           SQLite НЕ писать
           если логи окна непустые → Allure pod-logs-* всё равно
           (сервер мог отдать хвост лога до смерти)
 13. если failed и availability.standDownEventPresent:
        Store.STAND_UNAVAILABLE = true (+ code/reason)
        текущий тест остаётся failed, из afterEach НЕ бросаем
        (иначе затрём исходный assertion)
 14. publish в afterEach — НЕТ

TestWatcher (тот же класс extension)
  15. testSuccessful / testFailed / testAborted / testDisabled → счётчики
      beforeEach-fail из п.4 учитывается как failed

afterAll  (выполняется даже после fail-fast)
 16. collectAndMergeLogsForTestRun     // как сейчас; merge не обязан заново ходить в Events
 17. PUBLISH  publishPodEvent(Normal, TestRunFinished, name + counts)
 18. finishTestRun
```

Сводка:

| Метод | Класс | Кто вызывает | Хук |
| --- | --- | --- | --- |
| `publishPodEvent` | `OpenshiftClient` | `PodLoggerExtension` → `PodLoggerService.publishTestRunStarted/Finished` | **только** `beforeAll` (после startTestRun) и `afterAll` (перед/сразу после finish, но **после** подсчёта TestWatcher) |
| `getEvents` / `listPodEvents` | `OpenshiftClient` | `PodLoggerService` | **только** `afterEach` при `failed` **и** внутри `isPodAvailable()` (beforeAll + afterEach failed) |
| `isPodAvailable` | `OpenshiftClient` (или тонкий фасад в service) | `PodLoggerService` | `beforeAll` шаг 3; `afterEach` шаг 9 |

Passed-тест: ни getEvents, ни Events-аттач, ни `relevantEvents`.

---

## 5. As-built (код сейчас)

Реализовано в `junit-pod-logger`:

- `OpenshiftClient` — `getLog()`, `getEvents` / `getEvents(from,to)`, `publishPodEvent`, `probePodAvailability` / `isPodAvailable`, `resolveTargetPod`.
- `PodLoggerExtension` — хуки §4, счётчики TestWatcher, `STAND_UNAVAILABLE`, fail-fast в `beforeEach`.
- `PodLoggerService.handleAfterEach` / `handleFailedInvocation` — Events только на fail; persist только если `available`; Allure Events если список непустой.
- `CollectGate` — один флаг на Allure+SQLite логов. На failed CollectGate всегда true.
- `PodLogDto.relevantEvents` — runtime/Allure; в SQLite колонки нет.
- `LogAllureAttachmentService.attachEvents` — пустой список не аттачит.
- `AllureSink` — обёртка для тестов.
- Приёмка: `OpenshiftEventHandlingTest` (сценарии 1–5, кластер не нужен).

Нельзя ломать: `getLog()`, per-invocation Allure logs, единый `collectOnFailOnly` для логов, fail-fast `startTestRun` в `beforeAll`, ошибки collect не меняют статус **текущего** теста (кроме stand-down abort **следующих**).

---

## 6. Контракт API

### 6.1 `OpenshiftClient`

```java
public Pod resolveTargetPod();

public List<PodLogDto> getLog();                         // без изменения сигнатуры

public List<PodEventDto> getEvents();                    // все Events involvedObject = target pod
public List<PodEventDto> getEvents(LocalDateTime from, LocalDateTime to);

public PodEventDto publishPodEvent(String type, String reason, String message);

public PodAvailability isPodAvailable();                 // boolean + детали; см. ниже
```

Алиас `listPodEvents` = `getEvents`. В публичном API библиотеки оставляем **`getEvents`**.

`publishPodEvent`: `type` = `Normal`|`Warning`; `reason` = PascalCase = **код**; `message` без секретов; `involvedObject` = `resolveTargetPod()`; create best-effort (лог error, вернуть empty/null, **не** бросать в extension).

Окно `getEvents(from, to)` фильтруем на клиенте: `lastTimestamp` иначе `eventTime` иначе `metadata.creationTimestamp`.

### 6.2 `PodEventDto`

| Поле | Источник | Назначение |
| --- | --- | --- |
| `code` | `Event.reason` | машиночитаемый код (`Evicted`, `Maintenance`, `StandUnavailable`, …) |
| `reason` | `Event.reason` | то же значение, совместимость с k8s-именем |
| `type` | `Normal`/`Warning` | |
| `message` | `Event.message` | |
| `timestamp` | lastTimestamp / creationTimestamp | |
| `count` | `Event.count` | |
| `podName`, `namespace` | | |
| `uid` | metadata Event | |

`code` и `reason` в v1 **равны**. Отдельного поля в k8s Event нет: код = `reason`. Тесты и Allure проверяют `code`.

### 6.3 `PodAvailability` и `isPodAvailable()`

Возвращаем не голый `boolean`, чтобы fail-fast и логи видели код. Для удобства:

```java
public boolean isPodAvailable() {
    return probePodAvailability().available();
}

public PodAvailability probePodAvailability() { ... }

// PodAvailability:
//   boolean available
//   boolean standDownEventPresent
//   boolean healthPassed
//   String code            // код первого stand-down Event или PodNotReady / HealthCheckFailed
//   String message
//   List<PodEventDto> standDownEvents
```

Под капотом, **строго в этом порядке**, short-circuit:

```
1. events = getEvents()                    // без окна: актуальные Events поды
   standDown = StandDownEventMatcher.match(events)
   если standDown не пуст:
        available=false, standDownEventPresent=true, healthPassed=<не считаем обязательным>
        code=standDown[0].code
        return

2. pod = resolveTargetPod()
   k8sReady = все контейнеры Ready и phase=Running   // текущая OpenshiftClient.isReady
   если !k8sReady:
        available=false, standDownEventPresent=false, healthPassed=false
        code=PodNotReady
        return

3. если healthCheckUrl в properties непустой:
        HTTP GET, timeout короткий (например 2s)
        2xx и тело не матчит maintenance-pattern → healthPassed=true
        иначе available=false, healthPassed=false, code=HealthCheckFailed
        return

4. available=true, standDownEventPresent=false, healthPassed=true
```

**Health в этом PRD** — два независимых источника, оба входят в метод:

- Event на поде с кодом/текстом «стенд/под не работает» (в том числе probe `Unhealthy`, если он в allowlist);
- API: Kubernetes Ready **всегда**; HTTP `/health` — если задан URL.

Нет URL → шаг 3 пропускаем, достаточно k8s Ready.

`isPodAvailable()==true` только если нет stand-down Event **и** health (k8s Ready [+ HTTP]) зелёный.

#### Что такое stand-down Event

Конфиг, дефолт **кодов** (`reason`/`code`):

```
StandUnavailable, Maintenance, Evicted, Killing, FailedScheduling,
FailedMount, NetworkNotReady, Unhealthy, NodeNotReady,
TaintManagerEviction, DisruptionTarget
```

Плюс contains (case-insensitive) в `reason` или `message`:

```
maintenance, unavailable, shutting down, drain, preempt, eviction
```

**Исключить** наши маркеры: `TestRunStarted`, `TestRunFinished`. Иначе финиш/старт сами себя не триггерят. Kube-шум `Pulled`/`Created`/`Started`/`Scheduled` — не stand-down.

Разработчик стенда публикует Event на поде с кодом из списка (рекомендуемые: `Maintenance`, `StandUnavailable`) — это явный сигнал «падения тестов связаны с работами на поде».

### 6.4 Lifecycle Events, которые публикуем мы

| Хук | type | reason **(код)** | message |
| --- | --- | --- | --- |
| `beforeAll` после `startTestRun` | Normal | `TestRunStarted` | `testRunName=<name> testRunId=<uuid> suite=<class>` |
| `afterAll` | Normal | `TestRunFinished` | `testRunName=<name> total=<n> passed=<p> failed=<f>` |

`failed` включает тесты, упавшие из-за `STAND_UNAVAILABLE` в `beforeEach`. `total = passed + failed + disabled` (disabled отдельно не обязателен в message; если легко — добавить `skipped=`).

### 6.5 `PodLogDto.relevantEvents`

```java
private List<PodEventDto> relevantEvents;
```

- Заполняется **только** в `PodLoggerService` при обработке **упавшего** invocation, списком из `getEvents(window)`.
- Один и тот же список копируется на **каждую** запись окна перед Allure JSON (денормализация для отчёта).
- `FingerprintUtil` **игнорирует** поле (иначе сломается дедуп).
- `SqliteLogStoreRepository` **не** добавляет колонку; INSERT как сейчас. Прочитанные из БД DTO имеют `relevantEvents=null`.
- Parser JSON логов поле не заполняет (`@JsonIgnoreProperties(ignoreUnknown=true)` на входе с поды уже есть).

Jackson Allure-аттача логов сериализует `relevantEvents` как есть.

### 6.6 Allure

`LogAllureAttachmentService`:

- `attachJson(name, List<PodLogDto>)` — как сейчас, в JSON уже будут `relevantEvents`.
- `attachEvents(name, List<PodEventDto>)` — новый метод. Вызывать **только** если `events != null && !events.isEmpty()`.
- Имя Events-аттача: `pod-events-` + sanitize(displayName).

Два аттача на один fail — норма: логи (если есть что класть по §4 шаг 12) и Events (если список не пуст).

### 6.7 Persist vs Allure (без противоречий)

`CollectGate` по-прежнему решает, **заходим ли** в collect логов. Сверху — доступность поды.

`CollectGate.shouldCollect` = `!collectOnFailOnly || failed`. Для **failed** invocation CollectGate **всегда true** — строки «fail + CollectGate false» не существует. Passed + `collectOnFailOnly=true` → логов нет и Events нет. Passed + `collectOnFailOnly=false` → логи по старому правилу, Events **нет**.

| failed | `isPodAvailable` | stand-down Event | SQLite logs | Allure logs | Allure Events |
| --- | --- | --- | --- | --- | --- |
| нет | в afterEach не считаем | — | как CollectGate сейчас | как CollectGate сейчас | нет |
| да | true | нет | да | да | да, если `events≠∅` |
| да | false (только health) | нет | **нет** | да, если логи окна снялись | да, если `events≠∅` |
| да | false | **да** | **нет** | да, если логи окна снялись | **да** (список непустой) + fail-fast |

Events-аттач на **любом** fail не зависит от persist: под может быть мертва, логи сервера пусты, коды Event в отчёте всё равно нужны.

### 6.8 Fail-fast прогона

Триггер **только** `standDownEventPresent == true`.

Не триггер: красный HTTP health без stand-down Event; ошибка list/publish; `PodNotReady` без matching Event (это health, persist skip, прогон живёт). Если kubelet уже повесил `Unhealthy` — это Event в allowlist → stand-down → fail-fast. Это согласовано.

Механика JUnit 5:

1. В class `ExtensionContext.Store`: `STAND_UNAVAILABLE=true`, `STAND_UNAVAILABLE_CODE=code`.
2. Из `afterEach` **не** throw.
3. Следующие `beforeEach` бросают `IllegalStateException("Stand unavailable: " + code)`.
4. Если stand-down пойман в `beforeAll` — throw сразу, тесты класса не стартуют; `afterAll` всё равно должен опубликовать `TestRunFinished` (в `try/finally` вокруг тела `beforeAll` флаг `started`; если startTestRun прошёл — afterAll живой). Если `beforeAll` бросил до `put(testRunId)`, afterAll no-op как сейчас — тогда TestRunFinished не будет. Правило: **сначала** startTestRun+publish Started, **потом** isPodAvailable; если stand-down — publish всё равно Started, кладём testRunId, бросаем. afterAll опубликует Finished с total≈0.

### 6.9 Аннотация / properties

```java
boolean publishLifecycleEvents() default true;     // false = не публиковать start/finish
boolean failFastOnStandDownEvent() default true;
String healthCheckUrl() default "";               // пусто = только k8s Ready
String[] standDownEventCodes() default {};        // пусто = дефолт библиотеки
String[] standDownMessagePatterns() default {};
```

Зеркало в `PodLoggerProperties`. Пустой массив = дефолт, не «матчить ничего».

---

## 7. Раскладка пакетов

```text
com.example.podlogger.client
├── OpenshiftClient          // resolve, getLog, getEvents, publishPodEvent, probePodAvailability
├── PodLogDto                // + relevantEvents
├── PodEventDto
└── PodAvailability

com.example.podlogger.event
├── StandDownEventMatcher    // чистая функция по code/message
└── PodEventReasons          // TestRunStarted, TestRunFinished + дефолтный stand-down набор

com.example.podlogger.allure
└── LogAllureAttachmentService  // + attachEvents
```

`OpenshiftClient` не знает Allure/SQLite/JUnit. `PodLoggerService` оркестрирует getEvents → matcher → enrich relevantEvents → persist gate → attach. `PodLoggerExtension` только хуки и счётчики и флаг fail-fast.

HTTP health: маленький helper в client (JDK `HttpClient`), URL из properties. Если URL пустой — не вызывать.

---

## 8. RBAC

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-logger-events
rules:
  - apiGroups: [""]
    resources: ["pods", "pods/log"]
    verbs: ["get", "list"]
  - apiGroups: [""]
    resources: ["events"]
    verbs: ["get", "list", "create"]
```

Без `create`: Started/Finished не появятся, прогон идёт. Без `list`: `getEvents` → [], `standDownEventPresent=false`, fail-fast по Event не сработает; health k8s Ready всё ещё работает. Это явно слабее, но не лже-stand-down.

---

## 9. Последовательность имплементации

Делать **по порядку**. Каждый шаг должен компилиться и не ломать существующие тесты PersistentLogStore / parse.

1. **DTO:** `PodEventDto`, `PodAvailability`; в `PodLogDto` поле `List<PodEventDto> relevantEvents`. Юнит на сериализацию Jackson (поле есть в JSON, unknown с поды не падает).
2. **`StandDownEventMatcher` + `PodEventReasons`:** юнит-тесты кодов (match `Maintenance`/`Evicted`, ignore `TestRunStarted`/`Pulled`, pattern `maintenance` в message).
3. **`OpenshiftClient.resolveTargetPod()`:** вынести из `fetchRawLog`; `getLog()` зовёт его. Регрессия parse-теста не затрагивается.
4. **`getEvents` / `getEvents(from,to)`:** map fabric8 Event → DTO (`code=reason`). Юнит на маппинг timestamp/code (без кластера: package-visible mapper).
5. **`publishPodEvent`:** EventBuilder + `v1().events().create`. Юнит с mock `OpenShiftClient` (если тяжело — mapper+builder тест + один интеграционный в шаге 14).
6. **`probePodAvailability` / `isPodAvailable`:** шаги §6.3. Юнит: stand-down → false+flag; Ready false без Event → false без fail-fast flag; HTTP 503 → HealthCheckFailed; всё зелёное → true.
7. **Properties + `@PodLogger`:** новые атрибуты, `applyAnnotation` прокидывает.
8. **`LogAllureAttachmentService.attachEvents`:** пустой список не вызывает `Allure.addAttachment`. Юнит с подменой/capturing listener либо spy; если Allure static мешает — тонкая обёртка `AllureSink` с дефолтом на `Allure.addAttachment` (минимально, только если иначе не протестировать).
9. **`PodLoggerService`:**
   - `publishTestRunStarted(testRunId, name, suite)`
   - `publishTestRunFinished(name, total, passed, failed)`
   - `handleFailedInvocation(...)`: getEvents(window) → relevantEvents → attachEvents → health/availability → persist/logs attach по таблице §6.7 → вернуть `PodAvailability` в extension
   - `attachLogsIfNeeded` не вызывать вслепую из extension; extension зовёт новый метод, который внутри для passed оставляет старый CollectGate-путь без Events.
10. **`PodLoggerExtension`:** хуки строго по §4; счётчики TestWatcher; `STAND_UNAVAILABLE` в Store; `beforeEach` abort; `beforeAll` порядок start→publish Started→isPodAvailable.
11. **Fingerprint / SQLite:** убедиться, что `relevantEvents` не в fingerprint и не в INSERT. Существующие `PersistentLogStoreTest` зелёные.
12. **Приёмочные тесты §10** — обязательны до merge фичи.
13. **Demo (по желанию, не блокер PRD):** в `OrderErrorIT` ничего не ломать. Отдельный IT на k3s: publish Started, `getEvents` видит код `TestRunStarted`; руками создать Event `Maintenance` → fail-тест аттачит код, следующий тест не стартует.
14. **README:** два хука publish, get только на fail, RBAC, что `oc logs` не используется.

---

## 10. Критерии приёмки

Критерии проверяют **однозначность, непротиворечивость и полноту**. Фича закрыта, только если все пункты зелёные.

### 10.1 Публикация

| ID | Критерий |
| --- | --- |
| P1 | После `beforeAll` на поде есть Event с `code=TestRunStarted` и именем прогона в message |
| P2 | После `afterAll` на поде есть Event с `code=TestRunFinished` и message, содержащим `testRunName`, `total`, `passed`, `failed` |
| P3 | Между тестами класса **нет** третьего обязательного publish (нет per-test Event) |
| P4 | Ошибка create Event не валит suite и не помечает стенд недоступным |

### 10.2 Потребление и коды

| ID | Критерий |
| --- | --- |
| G1 | `getEvents` возвращает DTO, у которых `code` равен k8s `reason` |
| G2 | Окно `from..to` не включает Events вне интервала |
| G3 | Наши `TestRunStarted`/`TestRunFinished` не считаются stand-down |
| G4 | Код `Maintenance` / `StandUnavailable` / `Evicted` → `standDownEventPresent=true` |

### 10.3 Allure

| ID | Критерий |
| --- | --- |
| A1 | Упавший тест **и** непустой `getEvents(window)` → аттач `pod-events-*` содержит те же `code` |
| A2 | Упавший тест **и** пустой список Events → **нет** Events-аттача |
| A3 | `pod-logs-*` при непустых логах содержит `relevantEvents` с теми же кодами, что аттач Events |
| A4 | Passed-тест не получает Events-аттач |
| A5 | Если логи окна снялись **и** есть stand-down Events — в отчёте **оба** аттача |

### 10.4 Persist и health

| ID | Критерий |
| --- | --- |
| H1 | `isPodAvailable()` false, если есть stand-down Event, **даже если** HTTP health 200 |
| H2 | `isPodAvailable()` false, если нет stand-down Event, но k8s Ready=false или HTTP health не 2xx (при заданном URL) |
| H3 | `isPodAvailable()` true только при отсутствии stand-down **и** зелёном health |
| H4 | При `available=false` SQLite **не** получает `saveLogs` этого invocation |
| H5 | При `available=true` и CollectGate поведение persist как сегодня |

### 10.5 Fail-fast

| ID | Критерий |
| --- | --- |
| F1 | Stand-down Event на упавшем тесте → последующие тесты класса **не выполняются** (`beforeEach` exception) |
| F2 | Текущий упавший тест остаётся failed с **исходной** причиной, не подменённой stand-down |
| F3 | Красный health **без** stand-down Event **не** abort'ит прогон |
| F4 | `afterAll` всё равно публикует `TestRunFinished` со счётчиками |

### 10.6 Обязательный тест (полнота фичи)

Класс в `junit-pod-logger/src/test/...` (имя ориентир: `OpenshiftEventHandlingTest`). Кластер не обязателен: mock `OpenshiftClient` + `EngineTestKit` / Spring test + capturing Allure sink.

Сценарии в **одном** тестовом классе (можно `@Nested`):

1. **Publish/get с кодами.** Вызвать `publishPodEvent("Warning", "Maintenance", "stand down")`, затем `getEvents()` — в списке есть элемент `code=Maintenance` (на моке: publish кладёт в in-memory list, get читает его).
2. **Fail + Events → Allure.** Упавший JUnit-метод, mock `getEvents(window)` возвращает Event с `code=Maintenance`. Assert: Allure sink получил аттач, JSON содержит `"code":"Maintenance"`. `PodLogDto.relevantEvents` (если логи тоже мокнуты непустыми) содержит тот же код.
3. **Fail + нет Events → нет аттача.** Mock `getEvents` = `List.of()`. Assert: `attachEvents` не вызывался / sink без `pod-events-`.
4. **Stand-down → fail-fast.** Класс с двумя `@Test`: первый падает, mock availability `standDownEventPresent=true`. Второй метод **не** должен дойти до тела (EngineTestKit: 1 failed от ассерта, 1 failed/aborted от `Stand unavailable`, 0 successful во втором).
5. **Health-only red → нет fail-fast.** Первый тест падает, `available=false`, `standDownEventPresent=false`. Второй тест **выполняется**. Persist `saveLogs` для первого не вызван.

Без сценариев 1–5 фича **не принимается**.

---

## 11. Проверка документа на противоречия и дыры

| Вопрос | Решение в этом PRD |
| --- | --- |
| Пишем ли в `oc logs`? | Нет. §0.1 |
| Events на fail в Allure vs «не аттачить при infra» | Аттачим Events всегда если непусты. Старое skip Allure отменено. §2, §6.7 |
| Skip persist vs аттач логов если они есть | Persist только при available. Allure логов — если collect снял непустой хвост. §6.7 |
| Fail-fast vs «не менять статус теста» | Текущий тест не переписываем. Abort только **следующих**. §6.8 F2 |
| Fail-fast vs красный health | Abort **только** stand-down Event. Health режет persist. §6.8 F3, H4 |
| Когда publish | Только beforeAll и afterAll. §4 |
| Когда get | afterEach failed + внутри isPodAvailable. §4 |
| `collectOnFailOnly` vs Events-аттач | На failed CollectGate всегда true. Events — на любом fail, если список непустой. На passed Events нет, логи — только если `collectOnFailOnly=false`. §6.7 |
| `relevantEvents` в SQLite | Нет колонки. §6.5 |
| `code` vs `reason` | Одно значение. §6.2 |
| HTTP health обязателен? | Нет, если URL пустой. §6.3 |
| beforeAll stand-down до testRunId | Сначала persist run + Started, потом probe, потом throw. §6.8 |
| Пустой Events-аттач | Запрещён. A2 |
| Watch / events.k8s.io / Node events / таблица events | Вне v1 |

Оставшийся осознанный gap (не блокер): HTTP 503 в **теле ответа теста** (RestAssured) клиент не видит. Сигнал стенда в v1 = Event на поде и/или `/health` и/или k8s Ready.

---

## 12. Вне v1

Watch, `events.k8s.io/v1`, Events Node, exec в контейнер, marker-endpoint в demo-app, отдельная таблица events, per-test publish `TestFailed`.

---

## 13. As-built заметки (соответствие коду)

| Тема | Как в коде |
| --- | --- |
| `OpenshiftClient` | Конкретный класс, не Java-interface. Тесты наследуют stub. |
| `AllureSink` | Вынесен, чтобы EngineTestKit перехватывал аттачи. |
| Ошибка probe в `PodLoggerService.probeAvailability` | Swallow → `PodAvailability.up()` (не лже-stand-down). |
| Приёмка | `OpenshiftEventHandlingTest` + `OpenshiftEventHandlingHarness`, без Docker. |
| README | Операционное описание тех же хуков и RBAC. |
