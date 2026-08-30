# EventHandling.PRD

> Статус: архитектурный guide и roadmap, а не `as-built` контракт.
>
> Для текущего поведения источником истины являются:
>
> - [`docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](OpenShiftEventHandlingStory.md)
> - [`docs/PodLoggerJunitDemoPRD.md`](../../PodLoggerJunitDemoPRD.md)
> - [`docs/PodLoggerJunitDemoTest.md`](../../PodLoggerJunitDemoTest.md)
> - код в `junit-pod-logger`
>
> Этот документ описывает целевую эволюцию event management. Если он расходится с текущим кодом, приоритет у `as-built` документов и тестов.

## 0. Как использовать этот документ

- Использовать как набор вариантов и направлений развития, а не как обязательство немедленно переписать текущую реализацию.
- Не трактовать различия с кодом как defect по умолчанию: часть различий отражает осознанно более широкий target-state.
- Перед любыми изменениями сверять решения с текущими acceptance tests и feature-PRD.

## 0.1 Ключевые отличия от текущего `as-built`

- В текущем коде нет отдельного baseline snapshot в `BeforeAll` с последующим сравнением; есть `getEvents()` внутри availability probe и windowed snapshot при failed invocation.
- Текущий `beforeAll` не abort'ит suite по любому `health red`: если нет stand-down event, тесты продолжаются.
- Текущий `TestRunStarted` публикуется до availability probe, а не после успешного composite gate.
- Текущая реализация ориентирована на single target pod через selector; multi-pod aggregation пока не является действующим контрактом.
- Текущий fail-fast ограничен explicit stand-down policy, а не любым infrastructure deviation.

## 1. Цель и решения

Определить стандарт взаимодействия тестового фреймворка JUnit 5 + REST Assured + Allure с Kubernetes/OpenShift через Fabric8/OpenShift Client для environment с одной или несколькими Pod.

Целевые сценарии:

- `BeforeAll`: environment gate перед запуском test methods.
- При failed test: `AfterEach` выполняет health check, получает актуальные Events, собирает релевантные Pod logs и добавляет attachments в Allure.
- `AfterAll`: закрывает test run и публикует итог.
- Из framework публикуются ровно два Kubernetes Events: `TEST_RUN_STARTED` и `TEST_RUN_FINISHED`.
- Kubernetes Events не используются как message broker и не публикуются на каждый test/request.
- Realtime `WATCH` в MVP не нужен.

Kubernetes определяет Events как informative/best-effort/supplemental data с ограниченным retention; Event не должен быть единственным source of truth.

## 2. Текущее состояние проекта

В `ClusterLifecycle` создаётся K3s Testcontainers, из kubeconfig строится Fabric8 client и выполняется `adapt(OpenShiftClient.class)`. Затем framework deploys manifest, ждёт Ready Pod и устанавливает port-forward.

`OpenshiftClient` уже умеет:

- discovery target Pod по label selector;
- `getLog()`;
- `getEvents()` через Event `LIST` и `involvedObject`;
- HTTP health check;
- базовую Pod readiness;
- publish Kubernetes Event.

`PodLoggerExtension` уже реализует `BeforeAllCallback`, `BeforeEachCallback`, `AfterEachCallback`, `AfterAllCallback`, `TestWatcher`. `PodLoggerService` уже связывает failure → Events → health → logs → Allure/persistence.

## 3. Целевая архитектура

```text
JUnit test class
      |
 @PodLogger
      |
      v
PodLoggerExtension
      |
      +--> BeforeAll EnvironmentGate
      |       +--> Kubernetes API
      |       +--> target Pod(s)
      |       +--> readiness/conditions
      |       +--> app health
      |       +--> Event LIST
      |       +--> EndpointSlice (optional)
      |       |
      |       +--> BLOCK -> ABORT/FAIL FAST
      |       +--> OK -> TEST_RUN_STARTED
      |
      +--> Test execution / REST Assured
      |
      +--> AfterEach on failure
      |       +--> health check
      |       +--> current Event LIST
      |       +--> Pod state
      |       +--> logs: invocationStart..invocationEnd
      |       +--> previous logs if restarted
      |       +--> optional metrics
      |       +--> Allure
      |
      +--> AfterAll
              +--> final counters
              +--> optional final snapshot
              +--> TEST_RUN_FINISHED
```

## 4. BeforeAll: fail-fast environment gate

### 4.1 Checks

Минимум:

1. Kubernetes/OpenShift API reachable.
2. Все required Pod найдены.
3. Critical Pod не находится в недопустимом state.
4. `Ready` / `ContainersReady` удовлетворяют policy.
5. Нет критических container waiting/terminated state.
6. Нет явно blocking Events.
7. Application readiness endpoint отвечает.
8. Для Service-based workloads опционально есть ready endpoints.

`Running` не равен «готов принимать traffic». Для go/no-go предпочтительнее Pod readiness и application readiness. См. Kubernetes Probes и Pod Conditions.

### 4.2 Health strategies

| Стратегия | Плюсы | Минусы | Роль |
|---|---|---|---|
| Pod status | Быстро, без HTTP contract | Не доказывает app health | обязательный базовый signal |
| Readiness/health endpoint | Проверяет реальную готовность сервиса | Зависит от endpoint | обязательный рекомендуемый signal |
| Kubernetes Events | Видны maintenance/failures | Best-effort, limited retention | diagnostic/blocking signal |
| Service/EndpointSlice | Видно, есть ли ready endpoints | Ещё один API call | recommended для multi-Pod |
| Composite | Наиболее надёжно | Больше calls | **MVP target** |

## 5. Event consumption без WATCH

### 5.1 Рекомендуемый MVP

```text
BeforeAll
  -> LIST Events -> baseline

Test fails
  -> AfterEach
  -> LIST Events -> current failure snapshot
```

Первый snapshot отвечает на вопрос: «можно ли начинать run?». Второй — «какие Events уже присутствовали к моменту failure?». Это два bounded LIST, а не realtime WATCH.

### 5.2 Strict single-LIST alternative

Можно сохранить только baseline и повторно attach-ить его в `AfterEach`. Недостаток: Events, появившиеся во время invocation, будут потеряны.

**Рекомендация:** baseline + failure snapshot.

## 6. Multi-Pod handling

Target environment должен быть набором Pod, а не одним именем:

```text
namespace
selector/workload
criticalPods
service
health endpoints
```

Каждый record должен иметь:

```text
namespace
podName
podUid
ownerReference (если доступен)
nodeName
```

Health aggregation:

- `ALL` — все critical Pods здоровы;
- `ANY` — хотя бы один здоров;
- `QUORUM` — N из M;
- `CRITICAL_SET` — проверять только явно критические Pods.

MVP: `ALL` и `CRITICAL_SET`.

## 7. Failure diagnostics

При падении теста JUnit extension запускает:

```text
failure
 -> health check
 -> current Events LIST
 -> Pod snapshot
 -> logs for invocation window
 -> previous container logs if restart
 -> optional metrics
 -> correlation
 -> Allure attachments
```

Attachments:

```text
pod-logs-{test}.json
pod-events-{test}.json
pod-state-{test}.json
health-check-{test}.json
resource-snapshot-{test}.json   # optional
```

Важно: `AfterEach` не должен «переписывать» уже полученный JUnit failure. Он должен добавить отдельный verdict:

```text
JUnit result: FAILED
Infrastructure verdict: HEALTHY | UNHEALTHY | UNKNOWN
Failure classification: PRODUCT_FAILURE | INFRASTRUCTURE_FAILURE | UNKNOWN
```

Если infrastructure failure обнаружен, policy может остановить последующие tests.

## 8. Kubernetes Event policy

### 8.1 Blocking examples

```text
Maintenance
StandDown
FailedMount
FailedScheduling
Unhealthy
BackOff
CrashLoopBackOff
Evicted
```

Неизвестные reasons по умолчанию — diagnostic only.

### 8.2 Application-defined maintenance

Приложение может публиковать:

```text
type: Warning
reason: Maintenance
message: deployment is under maintenance
```

Framework policy может преобразовать это в:

```text
Maintenance -> BLOCK_RUN
StandDown   -> BLOCK_RUN
```

### 8.3 Publication from framework

`TEST_RUN_STARTED`:

```text
testRunId
testRunName
testSuiteName
startedAt
estimatedDuration
estimatedEndAt
environment
service
namespace
frameworkVersion
```

`TEST_RUN_FINISHED`:

```text
testRunId
testRunName
finishedAt
totalTests
passedTests
failedTests
skippedTests
abortedTests
```

Минимальная реализация может ограничить finish event `finishedAt + passedTests`, но целевой контракт должен быть расширяемым.

## 9. OpenShift/Fabric8 enrichment

`OpenshiftClient` следует постепенно разделить на gateway-интерфейсы:

```java
interface ClusterEventClient {
    List<ClusterEvent> list(EventQuery query);
    ClusterEvent publish(ClusterEvent event);
}

interface ClusterHealthClient {
    PodHealthSnapshot podHealth(TargetPod pod);
    ApplicationHealthSnapshot applicationHealth(TargetService service);
}

interface ClusterMetricsClient {
    Optional<PodMetricsSnapshot> podMetrics(String namespace, String podName);
}
```

Business policy остаётся в `PodLoggerService`/`EnvironmentGate`; Fabric8 objects не должны протекать в JUnit/Allure layers.

## 10. REST Assured integration

### 10.1 Recommended baseline

`@PodLogger` ставится над test class. `PodLoggerExtension` создаёт `testRunId` и `invocationId` в `ExtensionContext.Store`.

```text
JUnit invocation
   |
   +-- testRunId
   +-- invocationId
   |
 REST Assured
   |
 application
   |
 Pod(s)
   |
 Kubernetes Events / logs
   |
 Allure
```

Individual test code не обязан менять REST calls.

### 10.2 Advanced correlation Filter

REST Assured поддерживает custom `Filter`, который видит request до отправки и response после получения.

```java
public record HttpExchangeEvent(
    UUID testRunId,
    UUID invocationId,
    UUID requestId,
    Instant startedAt,
    Instant finishedAt,
    String method,
    String uri,
    int statusCode,
    long durationMs
) {}
```

Filter может:

1. получить `testRunId`/`invocationId`;
2. создать `requestId`;
3. записать method/URI/start;
4. выполнить request;
5. записать status/duration;
6. optionally добавить headers:

```text
X-Test-Run-Id
X-Test-Invocation-Id
X-Request-Id
```

Если application пишет этот requestId в log/trace, получается:

```text
JUnit
 -> requestId
 -> REST request
 -> application log requestId
 -> Pod UID
 -> Kubernetes Event
 -> Allure
```

Request events должны быть internal telemetry/correlation objects, а не Kubernetes Events на каждый HTTP request.

## 11. Стандартные библиотеки для тестирования и cluster interaction

| Решение | Для чего в framework | Роль |
|---|---|---|
| JUnit 5 | lifecycle, extension, result callbacks | core orchestration |
| REST Assured | HTTP/API testing, filters, request/response logging | API layer |
| Fabric8 Kubernetes Client | Pods, logs, Events, Service, EndpointSlice, metrics, port-forward, exec | **primary cluster SDK** |
| OpenShiftClient | OpenShift-specific APIs поверх Fabric8 | OpenShift adapter |
| Kubernetes official Java client | upstream generated Kubernetes API alternative | alternative SDK |
| Awaitility | wait/readiness/rollout without raw sleeps | async test utility |
| Testcontainers K3s | real local Kubernetes API in integration tests | test environment |
| Allure JUnit 5 | attachments/reporting | diagnostics output |
| metrics.k8s.io / metrics-server | current Pod/Node CPU+memory | resource snapshot |
| ResourceQuota / LimitRange APIs | quota and admission context | environment diagnostics |
| EndpointSlice API | ready/serving/terminating endpoints | service health |
| Prometheus / kube-state-metrics | historical/high-cardinality metrics | advanced observability |
| OpenTelemetry | test→HTTP→service distributed correlation | advanced tracing |
| NVIDIA DCGM Exporter | GPU telemetry when GPU workloads are tested | accelerator diagnostics |

Fabric8 provides direct support for Pod logs, waiting/readiness and `client.top()` metrics. Its client also has configurable request retry/backoff, which must not be doubled by a second uncontrolled framework retry loop.

## 12. Что собирать в начале run

```text
Run:
  testRunId, name, suite, environment, startedAt, estimate

Cluster:
  API reachable, version, namespace

Pods:
  name, UID, phase, conditions, Ready, restartCount,
  container states, node, requests/limits, QoS

Service:
  endpoints / EndpointSlice readiness

Events:
  baseline snapshot + blocking verdict

Health:
  endpoint status + latency

Optional:
  PodMetrics CPU/memory
  ResourceQuota hard/used
  node pressure
  accelerator allocation
  API client timeout/retry configuration
```

## 13. Что собирать при failure

```text
JUnit failure
invocation identity

Pod(s): state + conditions + restart count
Events: current snapshot
Health: current result
Logs: invocation start -> invocation end
Previous logs: if container restarted

Optional:
EndpointSlice
PodMetrics
ResourceQuota
Node conditions / PSI
accelerator telemetry
API 429/retry/timeout evidence
```

## 14. Rate limits, retries и resource diagnostics

Не смешивать:

- client retry/backoff;
- Kubernetes API-server flow control.

Framework должен собирать `429`, request latency, timeout и retry count (если доступно). Kubernetes API Priority and Fairness даёт cluster-level observability для очередей/перегрузки.

Resource Metrics API предоставляет CPU/memory для Pod/Node. Kubernetes v1.37 сделал `metrics.k8s.io/v1` stable.

ResourceQuota позволяет видеть aggregate usage и extended resources.

GPU actual utilization обычно требует vendor telemetry, например NVIDIA DCGM Exporter, а не только Kubernetes core API.

## 15. Security / RBAC

Нужны namespace-scoped permissions, например:

```text
pods get/list
pods/log get
events get/list
services get/list
endpointslices get/list
resourcequotas get/list
pods metrics get
nodes get   # only when required
```

Для run Events нужен только create Event в нужном namespace.

Cluster-admin не нужен. Secrets не должны попадать в Allure/log attachments без явного allowlist/redaction.

## 16. Стратегии и выбор

| Стратегия | Нагрузка | Сложность | Fit |
|---|---:|---:|---|
| BeforeAll snapshot only | Low | Low | минимальный вариант |
| BeforeAll + failure snapshot | Medium | Low | **рекомендуемый MVP** |
| Periodic polling | High | Medium | не нужен для текущего сценария |
| WATCH | Variable | High | future для long-running suites |
| OpenTelemetry + Prometheus | Medium/High | High | advanced observability |

## 17. Лучшая практика использования ивентов в тестовом фреймворке

1. Event — signal/evidence, не source of truth.
2. Fail-fast делается в `BeforeAll` по composite health gate.
3. При failure в `AfterEach` делается failure-time Event LIST.
4. Logs + Events + health state прикладываются к одному JUnit invocation в Allure.
5. Использовать `testRunId + invocationId + Pod UID`; `requestId` — только в advanced REST correlation.
6. Не создавать Kubernetes Event на каждый test или HTTP request.
7. Unknown Event -> diagnostic; только явная policy делает Event blocking.
8. Для multi-Pod всегда использовать явную aggregation policy.
9. JUnit result и infrastructure verdict хранить отдельно.
10. Все cluster calls ограничивать timeout/response-size/retry budget.
11. При restart собирать previous container logs.
12. CPU/memory/GPU metrics — context for diagnosis, а не автоматический failure без явного threshold policy.

## 18. Risks & Mitigation

| Риск | Митигирование |
|---|---|
| Event отсутствует | Composite health gate |
| Event retention ограничен | Failure-time snapshot + persistence |
| Pod Running, app broken | Readiness + app health |
| Несколько Pod, смешанное состояние | Multi-Pod aggregation + UID |
| Новый Event случайно ломает CI | Unknown -> diagnostic |
| 429/API throttling | Bounded retry + telemetry |
| Double retry | Single retry budget |
| Container restarted | Previous logs + termination reason |
| Health endpoint flaky | Small bounded retry + timestamped snapshot |
| Wrong Pod selected | UID + selector + owner reference |
| Parallel JUnit contexts mixed | ExtensionContext.Store isolation |
| Secrets leak | Redaction/allowlist |
| Metrics unavailable | Optional feature; do not fail by itself |
| Events used as message bus | Two lifecycle Events only |
| API model incompatibility | ClusterEvent DTO/adapter |
| Fail-fast hides product bug | Explicit infrastructure classification |

## 19. Acceptance Criteria

- `BeforeAll` checks target environment before tests run.
- Non-ready critical Pod can block the run.
- Failed readiness/health endpoint can block the run.
- Configured blocking Event can block the run.
- No Event alone is considered proof of health.
- Failed invocation receives relevant logs and current Events in Allure.
- Diagnostics work for multiple Pods and record Pod UID.
- Exactly one `TEST_RUN_STARTED` and one `TEST_RUN_FINISHED` are published per run when enabled.
- No per-test/per-request Kubernetes Events in standard mode.
- API 429/retry/timeout information is observable.
- Optional metrics do not turn missing Metrics API into a test failure.
- RBAC follows least privilege.

## 20. Roadmap

Рекомендуемая стратегия внедрения:

1. `As-Is Plus Cleanup` — сначала выровнять канонические документы и границы текущего контракта.
2. `Policy Extraction` — затем при необходимости вынести stand-down / availability policy из низкоуровневого client-слоя.
3. `Gateway Split` и multi-pod model — только если реально требуется следующая итерация observability и cluster diagnostics.

**Phase 1:** EnvironmentGate, multi-Pod discovery, Event LIST, health/readiness composite gate, failure snapshot, Allure state/events/logs, two lifecycle Events.

**Phase 2:** EndpointSlice, ResourceQuota, Metrics API, previous logs, API throttling instrumentation, infrastructure classification, REST Assured Filter.

**Phase 3:** OpenTelemetry, Prometheus adapter, accelerator telemetry, optional WATCH for specialized long-running suites, cross-run log relevance analysis.

## 21. References

Project:
- `demo-tests/src/test/java/com/example/demotest/ClusterLifecycle.java`
- `demo-tests/src/test/java/com/example/demotest/OrderErrorIT.java`
- `junit-pod-logger/src/main/java/com/example/podlogger/PodLoggerExtension.java`
- `junit-pod-logger/src/main/java/com/example/podlogger/PodLoggerService.java`
- `junit-pod-logger/src/main/java/com/example/podlogger/client/OpenshiftClient.java`
- `docs/podLoggerJunitDemoPRD.md`

Official/current technical sources:
- https://kubernetes.io/docs/reference/kubernetes-api/core/event-v1/
- https://kubernetes.io/docs/concepts/workloads/pods/probes/
- https://kubernetes.io/docs/concepts/workloads/pods/pod-condition/
- https://kubernetes.io/docs/reference/kubernetes-api/core/pod-v1/
- https://kubernetes.io/docs/concepts/services-networking/endpoint-slices/
- https://kubernetes.io/docs/concepts/policy/resource-quotas/
- https://kubernetes.io/docs/concepts/workloads/pods/pod-qos/
- https://kubernetes.io/docs/concepts/cluster-administration/flow-control/
- https://kubernetes.io/blog/2026/08/27/kubernetes-v1-37-metrics-api-ga/
- https://github.com/fabric8io/kubernetes-client
- https://github.com/kubernetes-client/java
- https://docs.junit.org/5.14.4/extensions/test-lifecycle-callbacks.html
- https://github.com/rest-assured/rest-assured/wiki/Usage
- https://github.com/awaitility/awaitility/wiki/Getting_started
- https://java.testcontainers.org/modules/k3s/
- https://docs.nvidia.com/datacenter/dcgm/latest/reference/dcgm-exporter-metrics.html
