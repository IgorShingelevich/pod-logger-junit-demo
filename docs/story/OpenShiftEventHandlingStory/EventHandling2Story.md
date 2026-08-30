# Event Handling 2 (target-state)

> Status: forward-looking architecture guide, not the `as-built` contract.
>
> Current source of truth:
>
> - [`docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`](OpenShiftEventHandlingStory.md)
> - [`docs/PodLoggerJunitDemoPRD.md`](../../PodLoggerJunitDemoPRD.md)
> - [`docs/PodLoggerJunitDemoTest.md`](../../PodLoggerJunitDemoTest.md)
> - [`docs/PodLoggerJunitDemoCommands.md`](../../PodLoggerJunitDemoCommands.md)
> - implementation and acceptance tests in `junit-pod-logger`
>
> This document remains useful as a target design and reference for future iterations. When it conflicts with the current code, the `as-built` documents and tests win.

## 0. How to use this document

- Use it to plan the next stage of event-management evolution, not to reinterpret the current behavior retroactively.
- Treat its broader EnvironmentGate, multi-Pod and observability sections as optional expansion paths unless they are explicitly accepted into the implemented contract.
- Validate any adopted ideas against the current feature PRD and existing acceptance tests before changing runtime behavior.

## 0.1 Main deltas versus current implementation

- Current code does not keep a dedicated `BeforeAll` baseline Event snapshot; it probes Events on demand and collects a failure-time snapshot for failed invocations.
- Current fail-fast is intentionally narrower: only explicit stand-down Events abort the remaining tests.
- Current `beforeAll` warns on health-only failures instead of blocking the suite.
- Current publication order is `startTestRun -> TestRunStarted -> probeAvailability`, not `gate -> TestRunStarted`.
- Current target model is one selected Pod, not a fully aggregated multi-Pod environment.

## 1. Цель

Определить стандарт взаимодействия слоеного test framework на JUnit 5 + REST Assured + Allure с Kubernetes/OpenShift через Fabric8/OpenShift Client для среды с одной или несколькими Pod.

Целевой workflow:

```text
BeforeAll
  -> identify target Pods
  -> Kubernetes API check
  -> Pod readiness/conditions/container states
  -> application readiness/health check
  -> Kubernetes Event LIST
  -> optional Service/EndpointSlice check
  -> Environment Gate
       ├─ BLOCK -> fail-fast / abort, tests are not started
       └─ OK -> publish TEST_RUN_STARTED -> run tests

AfterEach on failure
  -> health check
  -> current Event LIST
  -> Pod state snapshot
  -> relevant logs: invocation start..end (+ small skew)
  -> previous container logs when restarted
  -> optional metrics/resource snapshot
  -> classify PRODUCT/INFRA/UNKNOWN
  -> Allure attachments

AfterAll
  -> run counters and finish time
  -> optional final snapshot
  -> publish TEST_RUN_FINISHED
```

Framework publishes exactly two Kubernetes Events per run: `TEST_RUN_STARTED` and `TEST_RUN_FINISHED`. It does not publish Kubernetes Events for every test or every HTTP request. Realtime `WATCH` is not required for MVP.

Kubernetes Events are supplemental/best-effort diagnostic data and are not a reliable sole health oracle. Use them together with Pod conditions, readiness, health endpoint and Service/EndpointSlice state. Source: https://kubernetes.io/docs/reference/kubernetes-api/core/event-v1/

---

## 2. Текущее состояние проекта

The repository already contains the key building blocks:

- `ClusterLifecycle` starts a K3s Testcontainers cluster, creates a Fabric8 client and adapts it to `OpenShiftClient`, deploys the demo application, waits for Pod readiness and creates a port-forward.
- `OpenshiftClient` discovers a target Pod by label selector, gets Pod logs, lists Events for the Pod, performs HTTP health checking and publishes a Kubernetes Event.
- `PodLoggerExtension` is a JUnit 5 class-level extension implementing `BeforeAllCallback`, `BeforeEachCallback`, `AfterEachCallback`, `AfterAllCallback` and `TestWatcher`.
- `PodLoggerService` already orchestrates failure-time Events, health checks, bounded log collection, enrichment, persistence and Allure attachments.
- `OrderErrorIT` applies `@PodLogger(collectOnFailOnly = true)` to the test class and uses REST Assured. On failure the extension attaches the corresponding diagnostics.
- Existing `docs/PodLoggerJunitDemoPRD.md` defines persistent Pod log storage and test-run lifecycle.

Current Event retrieval is a snapshot `LIST`, not a watch stream.

---

## 3. Целевая роль Kubernetes Events

Kubernetes Events are used for three purposes only:

1. **Pre-run diagnostic gate** — detect known conditions such as maintenance, stand-down, scheduling/mount problems or unhealthy state.
2. **Failure diagnostics** — attach the current Event snapshot to the failed JUnit invocation.
3. **Run lifecycle visibility** — expose `TEST_RUN_STARTED` and `TEST_RUN_FINISHED` to cluster observers.

Events are not used as:

- a durable message bus;
- a high-frequency HTTP telemetry channel;
- the only source of truth for health;
- a replacement for OpenTelemetry or Prometheus.

---

## 4. Health Check Strategies

### 4.1 Pod status and conditions

Inspect:

- Pod `phase`;
- `Ready`, `ContainersReady`, `Initialized` and other conditions;
- container `waiting`, `running`, `terminated` states;
- `restartCount`;
- last termination reason and exit code;
- deletion timestamp.

`Running` alone is insufficient to conclude that a Pod can serve traffic. Sources: https://kubernetes.io/docs/concepts/workloads/pods/pod-condition/ and https://kubernetes.io/docs/reference/kubernetes-api/core/pod-v1/.

### 4.2 Application readiness/health endpoint

Use a small configurable `/ready`, `/health`, `/healthz` or equivalent endpoint.

For test go/no-go, readiness semantics are usually more relevant than liveness: readiness describes traffic eligibility; liveness is intended to trigger restarts. Source: https://kubernetes.io/docs/concepts/workloads/pods/probes/.

### 4.3 Kubernetes Events

Classify `type`, `reason`, `message`, event time and related resource UID. Treat configured blocking Events as a fail-fast signal, not as a universal health oracle.

### 4.4 Service and EndpointSlice

A deployment may have Ready-looking Pods while the Service has no usable endpoint. Optionally inspect Service and EndpointSlice readiness (`ready`, `serving`, `terminating`). Source: https://kubernetes.io/docs/concepts/services-networking/endpoint-slices/.

### 4.5 Composite health gate — recommended

```text
Kubernetes/OpenShift API reachable
AND required Pods discovered
AND critical Pods Ready
AND application readiness OK
AND no configured blocking Event
AND Service has usable ready endpoints
=> RUN
```

A missing Event must never be interpreted as proof that the environment is healthy.

---

## 5. Multi-Pod Environment

Target environments must be modeled as a set of Pods, not a single Pod name.

Each Pod is identified by namespace + Pod name + **Pod UID**. Names may be reused after recreation; UID gives stable identity for correlation.

### Health aggregation policies

| Policy | Meaning | Example |
|---|---|---|
| ALL | all critical Pods must be healthy | strict environment |
| ANY | at least one healthy Pod is sufficient | active/passive |
| QUORUM | N of M Pods must be healthy | large replica set |
| CRITICAL_SET | only declared critical Pods are evaluated | multi-service suite |

MVP: `ALL` and `CRITICAL_SET`.

A failure snapshot must show per-Pod state, e.g. `Pod A Ready=true`, `Pod B Ready=false restartCount=7`, rather than returning only one aggregate flag.

---

## 6. Event Consumption: LIST, Polling and WATCH

### 6.1 MVP: baseline + failure snapshot

The recommended implementation is two bounded snapshots:

```text
BeforeAll  -> LIST Events -> baseline

failure    -> LIST Events -> current failure snapshot
```

The second `LIST` is important because Events can be created after `BeforeAll`; the requested Allure evidence must reflect the state near the failure. This is not realtime monitoring and does not require `WATCH`.

### 6.2 Strict single-LIST alternative

If the product explicitly forbids a second Event read, cache only the `BeforeAll` snapshot. This has lower API traffic but cannot show Events created during the test.

### 6.3 Periodic polling

Repeated Event LIST calls during the run provide more timely state but increase control-plane traffic and are unnecessary for the stated use case.

### 6.4 WATCH

Fabric8 supports Watch APIs, but the reconnect/resourceVersion lifecycle is unnecessary in the MVP. Keep it as an optional strategy for long-running suites. Sources: https://github.com/fabric8io/kubernetes-client and https://kubernetes.io/docs/reference/using-api/api-concepts/.

**Decision: no WATCH in MVP.**

---

## 7. Fail-Fast Before Test Execution

### 7.1 Decision model

```text
BLOCKING =
    critical Pod is not Ready
 OR application readiness failed
 OR Service has zero required ready endpoints
 OR configured blocking Event exists
 OR another explicit infrastructure trigger is active
```

If `BLOCKING`, the extension must prevent actual test methods from executing according to the configured JUnit abort/skip policy.

### 7.2 Event policy

Example:

```yaml
events:
  blockingReasons:
    - Maintenance
    - StandDown
    - FailedMount
    - FailedScheduling
    - Unhealthy
    - BackOff
    - Evicted
  blockingMessagePatterns:
    - "maintenance"
    - "stand down"
```

Unknown reasons default to `DIAGNOSTIC`, not fail-fast. This prevents a new informational Event type from accidentally stopping CI.

### 7.3 JUnit semantics

The extension must distinguish:

```text
environment gate rejected run
```

from:

```text
a test assertion failed
```

Do not retroactively rewrite a failed assertion. Instead add infrastructure classification to diagnostics. A configured policy may abort **subsequent** tests after infrastructure failure.

---

## 8. Failure Handling and Allure

For a failed invocation:

1. capture original JUnit failure information;
2. perform a bounded application health check;
3. obtain current Event list for target Pods/resources;
4. obtain current Pod state;
5. obtain Pod logs for the invocation window with a small configurable skew;
6. if a restart occurred, optionally retrieve previous terminated-container logs;
7. optionally collect Service/EndpointSlice, ResourceQuota and metrics snapshots;
8. classify the failure;
9. attach structured diagnostics to the same Allure test result.

Recommended artifacts:

```text
pod-logs-{test}.json
pod-events-{test}.json
pod-state-{test}.json
health-check-{test}.json
resource-snapshot-{test}.json
failure-classification.json
```

The existing project already implements the core log/Event/Allure chain and should evolve rather than be replaced.

---

## 9. Log Collection

Fabric8 supports Pod log retrieval, per-container logs, previous terminated-container logs, `sinceTime`, `sinceSeconds`, tailing and byte limits. Source: https://github.com/fabric8io/kubernetes-client/blob/main/doc/CHEATSHEET.md.

Recommended failure flow:

```text
current logs
+ previous logs if restartCount > 0
+ invocation window
+ small skew
+ Pod UID
+ timestamp sort/dedup
```

For multiple Pods, collect by Pod UID and merge only after identity is attached.

---

## 10. Framework-Published Kubernetes Events

### 10.1 TEST_RUN_STARTED

Publish exactly once after the environment gate succeeds.

Recommended metadata:

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

The estimate is informational and must not be treated as a deadline.

### 10.2 TEST_RUN_FINISHED

Publish exactly once during normal run finalization.

Target schema:

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

Minimum implementation may expose only finish time and passed count, but the schema should be extensible.

### 10.3 No per-test/per-request Kubernetes Events

Do not publish `TEST_STARTED`, `HTTP_REQUEST_STARTED`, `HTTP_REQUEST_FINISHED` or similar high-cardinality Events into Kubernetes in standard mode.

Use an internal test event model or OpenTelemetry for high-frequency telemetry.

---

## 11. OpenShift/Fabric8 Client Improvements

The current `OpenshiftClient` should remain an infrastructure gateway and be decomposed conceptually into:

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

Business rules belong in `EnvironmentGate`, `FailureDiagnosticsService` and `PodLoggerService`, not in the low-level Fabric8 adapter.

Use a framework-neutral DTO rather than exposing Fabric8 Event classes across the application:

```java
public record ClusterEvent(
    String uid,
    String type,
    String reason,
    String action,
    String message,
    String namespace,
    String resourceKind,
    String resourceName,
    String resourceUid,
    Instant eventTime
) {}
```

Where the cluster exposes the modern Event API, prefer `events.k8s.io/v1`; retain compatibility through the adapter. Source: https://kubernetes.io/docs/reference/kubernetes-api/.

---

## 12. Correlation Model

Minimum identifiers:

```text
testRunId
invocationId
podUid
namespace
```

Advanced mode adds:

```text
requestId
```

Correlation priority:

```text
testRunId + invocationId
        -> requestId
        -> podUid + timestamp window
```

Avoid relying on Pod name alone.

---

## 13. REST Assured Integration

### 13.1 Recommended baseline — JUnit-owned correlation

The developer only adds `@PodLogger` to the test class. The extension creates `testRunId` and `invocationId`, controls diagnostics and does not require changes to individual REST calls.

```text
@PodLogger
  -> JUnit Extension
     -> testRunId / invocationId
        -> REST Assured request
           -> application
              -> Pod(s)
                 -> Kubernetes Events / logs
        -> AfterEach diagnostics
           -> Allure
```

This is the recommended MVP.

### 13.2 Advanced alternative — REST Assured Filter

REST Assured Filters can inspect/modify requests and responses and therefore can create request-level correlation without changing every test statement. Source: https://github.com/rest-assured/rest-assured/wiki/Usage.

Recommended internal event:

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

Filter responsibilities:

1. obtain `testRunId`/`invocationId` from the JUnit correlation context;
2. generate `requestId`;
3. record HTTP start metadata;
4. optionally inject `X-Test-Run-Id`, `X-Test-Invocation-Id`, `X-Request-Id`;
5. execute request;
6. capture response status/duration;
7. emit `HttpExchangeEvent` to an internal correlation store or telemetry backend.

If the application writes these identifiers to logs/traces, the framework can correlate:

```text
JUnit invocation
 -> requestId
 -> REST request
 -> application log requestId
 -> Pod UID
 -> Kubernetes Event
 -> Allure
```

Do not create Kubernetes Events for each HTTP request. Use internal correlation events, OpenTelemetry or another telemetry channel.

---

## 14. Интеграция тестового фреймворка со стандартными библиотеками для целей тестирования и получения логов и взаимодействия тестового фреймворка с кластером Kubernetes

### JUnit 5

**Роль:** lifecycle/orchestration.

Use:

- `BeforeAllCallback` — run metadata, environment gate, initial Event snapshot;
- `BeforeEachCallback` — invocation start and correlation context;
- `AfterEachCallback` — failure diagnostics and Allure;
- `AfterAllCallback` — run finalization and `TEST_RUN_FINISHED`;
- `TestWatcher` — result counters.

JUnit lifecycle callbacks and extension wrapping are designed for this infrastructure style. Source: https://docs.junit.org/5.14.4/extensions/test-lifecycle-callbacks.html.

### REST Assured

**Роль:** API testing and optional request-level correlation.

Use:

- Request/Response logging filters for baseline diagnostics;
- custom Filter for advanced `requestId` correlation;
- common RequestSpecification for standard headers/timeouts.

Source: https://github.com/rest-assured/rest-assured/wiki/Usage.

### Fabric8 Kubernetes Client / OpenShiftClient

**Роль:** primary cluster SDK.

Use for:

- Pods and Pod conditions;
- Pod logs and previous logs;
- Kubernetes Events;
- Services and EndpointSlices;
- Deployments/ReplicaSets;
- ResourceQuota/LimitRange;
- Nodes when explicitly needed;
- metrics via `client.top()` where Metrics API is installed;
- port-forward and exec;
- applying/waiting for resources.

Sources: https://github.com/fabric8io/kubernetes-client and https://github.com/fabric8io/kubernetes-client/blob/main/doc/CHEATSHEET.md.

### Official Kubernetes Java Client

Alternative SDK for organizations that standardize on upstream Kubernetes Java APIs or prefer generated API types. Do not add both SDKs without a concrete need. Source: https://github.com/kubernetes-client/java.

### Awaitility

**Роль:** asynchronous waiting without arbitrary sleeps.

Use for Pod readiness, rollout completion and asynchronous endpoint availability. Source: https://github.com/awaitility/awaitility/wiki/Getting_started.

### Testcontainers K3s

**Роль:** reproducible local/CI Kubernetes integration environment. The current project already uses `K3sContainer`. Source: https://java.testcontainers.org/modules/k3s/.

### Allure JUnit 5

**Роль:** report/diagnostics output. Attach structured logs, Events, health, resource snapshots and classification to the same failed invocation.

### Metrics API / metrics-server

**Роль:** current Pod/Node CPU and memory usage.

Kubernetes v1.37 promotes `metrics.k8s.io/v1` to stable. It is intentionally a small metrics API, not a complete monitoring solution. Source: https://kubernetes.io/blog/2026/08/27/kubernetes-v1-37-metrics-api-ga/.

Fabric8 exposes the API via `client.top()` when Metrics API is available. Source: https://github.com/fabric8io/kubernetes-client/blob/main/doc/CHEATSHEET.md.

Capture metrics at run start and failure/end when enabled; missing metrics must not itself fail tests.

### ResourceQuota / LimitRange

**Роль:** diagnose namespace resource pressure and admission failures.

Capture `hard` and `used` values for relevant CPU/memory/object/extended-resource quotas. Source: https://kubernetes.io/docs/concepts/policy/resource-quotas/.

### EndpointSlice

**Роль:** verify that the Service has usable ready/serving endpoints in a multi-Pod environment. Source: https://kubernetes.io/docs/concepts/services-networking/endpoint-slices/.

### API Priority and Fairness

**Роль:** cluster-level evidence of API-server overload/flow control.

Record API 429 responses, request latency, timeout and retry behavior. APF metrics can be used as an optional cluster diagnostic signal. Source: https://kubernetes.io/docs/concepts/cluster-administration/flow-control/.

Do not assume a universal SaaS-style `remaining-rate-limit` header exists for Kubernetes APIs.

### OpenTelemetry

**Роль:** advanced distributed correlation from test -> HTTP -> application -> downstream service -> Pod.

Use in Phase 2/3; Kubernetes Events remain infrastructure evidence.

### Prometheus / kube-state-metrics

**Роль:** historical/high-cardinality metrics and cluster-state telemetry when Kubernetes API data is insufficient.

Prefer consuming an existing monitoring stack rather than building a second metrics system inside the test framework.

### NVIDIA DCGM Exporter

**Роль:** GPU utilization/temperature/power/error telemetry in GPU test environments. DCGM Exporter exposes Prometheus metrics and Kubernetes pod mapping. Source: https://docs.nvidia.com/datacenter/dcgm/latest/reference/dcgm-exporter-metrics.html.

Kubernetes core APIs should still be used for extended-resource allocation; vendor telemetry is needed for actual accelerator utilization.

---

## 15. Resource and Environment Snapshot

### At run start

```text
Run metadata:
  testRunId, name, suite, environment, startedAt, estimate

Cluster:
  API reachability, server version where available, namespace

Pods:
  name, UID, phase, conditions, Ready, container states,
  restartCount, node, requests/limits, QoS

Service:
  Service and EndpointSlice readiness

Events:
  baseline LIST + blocking verdict

Health:
  endpoint status/latency

Optional:
  PodMetrics CPU/memory
  ResourceQuota hard/used
  node pressure
  accelerator allocation
  API client timeout/retry policy
```

### At failure

Mandatory:

```text
JUnit failure
invocation identity
Pod(s) and Pod UID
Pod readiness/conditions
container state/restartCount
health result
current Event list
invocation-window logs
previous logs when restarted
```

Recommended:

```text
EndpointSlice
ResourceQuota
PodMetrics
Node conditions / PSI
extended resources
API 429/retry/timeout evidence
```

### At run end

```text
finishedAt
totalTests
passedTests
failedTests
skippedTests
abortedTests
duration
```

---

## 16. Rate Limits, Retries and Client Resilience

Distinguish two concepts:

1. **Fabric8 client retry/backoff** — client behavior, configurable through Fabric8.
2. **Kubernetes API Priority and Fairness** — server-side flow control under overload.

The framework should have one explicit retry budget for infrastructure diagnostics. Avoid nesting generic framework retries on top of client retries because latency multiplies.

Recommended diagnostic fields:

```text
operation
attempts
totalDuration
lastStatus
http429Count
timeoutCount
```

Transient errors may be retried within a strict budget. Deterministic `401/403`, invalid configuration or missing namespace should fail fast rather than retry repeatedly.

Fabric8 retry/backoff reference: https://github.com/fabric8io/kubernetes-client/blob/main/doc/FAQ.md.

---

## 17. Failure Classification

### PRODUCT_FAILURE

```text
Pod(s) healthy
health OK
ready endpoints OK
no blocking Event
```

### INFRASTRUCTURE_FAILURE

Any of:

- critical Pod not Ready;
- application health failure;
- no required ready endpoints;
- blocking Event;
- unexpected restart/eviction;
- relevant resource pressure;
- cluster API unavailable during critical diagnostics.

### UNKNOWN

Evidence is incomplete or contradictory.

The JUnit result remains the original result; infrastructure classification is an additional diagnostic field.

---

## 18. Security / RBAC

Minimum read permissions should be namespace-scoped where possible:

```text
pods get/list
pods/log get
events get/list
services get/list
endpointslices get/list
resourcequotas get/list
pods metrics get
nodes get   # only if specifically required
```

For run-event publication, add only Event create permission in the target namespace.

No cluster-admin requirement.

Do not attach Secrets, authentication headers or sensitive response bodies to Allure without redaction/allowlist.

---

## 19. Performance Rules

No Kubernetes call for each REST request in standard mode.

Expected profile:

```text
BeforeAll:
  API check
  Pod discovery
  Event LIST
  health check
  optional EndpointSlice/metrics

AfterEach failure:
  Pod snapshot
  Event LIST
  N bounded log reads
  health check
  optional metrics

AfterAll:
  counters
  optional final snapshot
  one TEST_RUN_FINISHED publish
```

Bound:

- number of Pods;
- log bytes;
- retry attempts;
- total operation time;
- number of Event records attached.

---

## 20. Лучшая практика использования ивентов в тестовом фреймворке

1. Kubernetes Event is **evidence**, not truth.
2. Use Events for coarse-grained infrastructure signals: maintenance, stand-down, scheduling/mount problems, unhealthy/backoff/eviction.
3. Make the pre-run gate composite: Pod readiness + application health + Service/EndpointSlice + blocking Event policy.
4. Read Events with `LIST`, not `WATCH`, for the MVP use case.
5. Take an initial Event snapshot in `BeforeAll` and a failure-time snapshot in `AfterEach` when a test fails.
6. Correlate by `testRunId + invocationId + Pod UID`; add `requestId` only in advanced REST Assured mode.
7. Never create a Kubernetes Event for every JUnit test or REST request.
8. Keep the original JUnit result separate from infrastructure classification.
9. In multi-Pod environments use an explicit aggregation rule such as `ALL` or `CRITICAL_SET`.
10. Keep unknown Events diagnostic by default; only configured rules can block a run.
11. Use readiness semantics for test go/no-go; do not use liveness as the primary test gate.
12. Capture previous-container logs when a restart is detected.
13. Treat CPU/memory/GPU metrics as context unless an explicit domain threshold makes them a blocking condition.
14. Keep all cluster calls bounded by time, size and retry budget.

---

## 21. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Event absent despite real failure | Composite health gate; never rely only on Events |
| Event retention/cleanup | Failure-time snapshot + persistence |
| Pod `Running` but application broken | Readiness + application health |
| Multi-Pod mixed health | Per-Pod state + aggregation policy + Pod UID |
| Unknown Event blocks suite | Unknown -> diagnostic |
| API 429/throttling | Record 429/retry; strict retry budget |
| Nested retries cause long diagnostics | Single explicit retry budget |
| Logs flushed after assertion failure | Small configurable flush grace |
| Restart hides original error | Previous terminated-container logs + termination reason |
| Wrong resource correlation | Namespace + UID + owner reference |
| Parallel tests mix state | Per-invocation JUnit context and isolated correlation |
| Secrets in Allure | Redaction/allowlist |
| Metrics API absent | Optional capability; do not fail test solely for missing metrics |
| Metrics stale | Record collection timestamp/source; use as context |
| GPU/TPU utilization unavailable | Capture Kubernetes allocation; use vendor telemetry for usage |
| Events become message broker | Only two framework Events; internal request telemetry |
| Legacy/new Event model differences | Framework-neutral ClusterEvent adapter |
| Fail-fast hides product defects | Preserve JUnit result + explicit infra classification |
| Health endpoint itself flaky | Small bounded retry and record latency/error |
| Pod recreated during run | Correlate by UID and workload ownership |
| Event snapshot contains later unrelated Events | Filter by target resource and event time window |
| Failure-time snapshot multiplies API load | Run only on failure and keep target scope bounded |
| Cluster permissions too broad | Namespace-scoped least privilege |
| `OpenShiftClient` becomes god object | Separate Event/Health/Metrics/Resource gateway interfaces |

---

## 22. Strategy Comparison

| Strategy | Event collection | Health | API load | Complexity | Recommendation |
|---|---|---|---:|---:|---|
| Snapshot only | BeforeAll LIST | Pod + health | Low | Low | simple suites |
| Baseline + failure snapshot | BeforeAll + failure LIST | composite | Medium | Low | **MVP recommended** |
| Periodic polling | repeated LIST | composite | High | Medium | not needed for current case |
| WATCH | streaming | composite | Variable | High | future long-running suites |
| OpenTelemetry + Prometheus | Events + traces + metrics | composite | Medium/high | High | advanced observability |

---

## 23. Proposed Package Structure

```text
com.example.podlogger
├── PodLogger
├── PodLoggerExtension
├── PodLoggerService
├── PodLoggerProperties
├── health
│   ├── EnvironmentGate
│   ├── HealthCheckService
│   └── MultiPodHealthAggregator
├── event
│   ├── ClusterEvent
│   ├── ClusterEventClient
│   ├── EventClassifier
│   └── EventPolicy
├── client
│   ├── OpenshiftClient
│   ├── ClusterHealthClient
│   ├── ClusterMetricsClient
│   └── ClusterResourceClient
├── diagnostics
│   ├── FailureDiagnosticsService
│   ├── PodStateCollector
│   └── ResourceSnapshotCollector
├── correlation
│   ├── TestCorrelationContext
│   └── HttpCorrelationFilter
├── parser
│   └── LogParser
├── allure
│   └── LogAllureAttachmentService
└── store
    ├── PodStoreService
    └── TestRunStore
```

---

## 24. Acceptance Criteria

1. `BeforeAll` validates the target environment before actual tests execute.
2. A non-ready critical Pod can cause fail-fast/abort.
3. A failed application readiness check can cause fail-fast/abort.
4. A configured blocking Kubernetes Event can cause fail-fast/abort.
5. Absence of an Event alone never proves health.
6. A failed invocation gets an Allure attachment with relevant Pod logs.
7. A failed invocation gets an up-to-date Event snapshot for target Pods/resources.
8. Multi-Pod diagnostics preserve namespace + Pod UID.
9. Exactly one `TEST_RUN_STARTED` is published per run when enabled.
10. Exactly one `TEST_RUN_FINISHED` is published per run when enabled.
11. Standard execution does not create Kubernetes Events per test or HTTP request.
12. Metrics are optional and do not make a run fail when the Metrics API is unavailable.
13. 429/retry/timeout diagnostics are bounded and observable.
14. RBAC does not require cluster-admin.
15. The original JUnit result remains distinct from infrastructure classification.

---

## 25. Implementation Roadmap

Recommended adoption order:

1. `As-Is Plus Cleanup`: align canonical docs and freeze the current contract.
2. `Policy Extraction`: separate policy from transport/orchestration without changing semantics.
3. `Gateway Split` and multi-Pod redesign: only when broader cluster diagnostics become a confirmed requirement.

### Phase 1 — MVP

- `EnvironmentGate`;
- target Pod set and UID-based correlation;
- composite health check;
- initial Event LIST in `BeforeAll`;
- configurable blocking policy;
- failure-time Event LIST in `AfterEach`;
- Pod state + logs + Allure attachments;
- previous container logs;
- exactly two run lifecycle Events.

### Phase 2 — Advanced diagnostics

- EndpointSlice;
- ResourceQuota/LimitRange;
- Metrics API CPU/memory;
- API 429/retry instrumentation;
- REST Assured correlation Filter;
- stronger infrastructure-vs-product classification.

### Phase 3 — Advanced observability

- OpenTelemetry;
- Prometheus adapter;
- GPU/DCGM telemetry;
- optional WATCH for specialized long-running suites;
- cross-run `GetUniqueLogs` / `GetRelevantLogs`.

---

## 26. References

### Project

- `demo-tests/src/test/java/com/example/demotest/ClusterLifecycle.java`
- `demo-tests/src/test/java/com/example/demotest/OrderErrorIT.java`
- `junit-pod-logger/src/main/java/com/example/podlogger/PodLoggerExtension.java`
- `junit-pod-logger/src/main/java/com/example/podlogger/PodLoggerService.java`
- `junit-pod-logger/src/main/java/com/example/podlogger/client/OpenshiftClient.java`
- `docs/PodLoggerJunitDemoPRD.md`

### Kubernetes / libraries

- Events: https://kubernetes.io/docs/reference/kubernetes-api/core/event-v1/
- API concepts: https://kubernetes.io/docs/reference/using-api/api-concepts/
- Probes: https://kubernetes.io/docs/concepts/workloads/pods/probes/
- Pod conditions: https://kubernetes.io/docs/concepts/workloads/pods/pod-condition/
- Pod API: https://kubernetes.io/docs/reference/kubernetes-api/core/pod-v1/
- EndpointSlice: https://kubernetes.io/docs/concepts/services-networking/endpoint-slices/
- ResourceQuota: https://kubernetes.io/docs/concepts/policy/resource-quotas/
- Pod QoS: https://kubernetes.io/docs/concepts/workloads/pods/pod-qos/
- APF: https://kubernetes.io/docs/concepts/cluster-administration/flow-control/
- Metrics API v1.37: https://kubernetes.io/blog/2026/08/27/kubernetes-v1-37-metrics-api-ga/
- Fabric8: https://github.com/fabric8io/kubernetes-client
- Fabric8 Cheat Sheet: https://github.com/fabric8io/kubernetes-client/blob/main/doc/CHEATSHEET.md
- Fabric8 FAQ: https://github.com/fabric8io/kubernetes-client/blob/main/doc/FAQ.md
- Official Kubernetes Java Client: https://github.com/kubernetes-client/java
- JUnit lifecycle: https://docs.junit.org/5.14.4/extensions/test-lifecycle-callbacks.html
- REST Assured: https://github.com/rest-assured/rest-assured/wiki/Usage
- Awaitility: https://github.com/awaitility/awaitility/wiki/Getting_started
- Testcontainers K3s: https://java.testcontainers.org/modules/k3s/
- NVIDIA DCGM Exporter: https://docs.nvidia.com/datacenter/dcgm/latest/reference/dcgm-exporter-metrics.html
