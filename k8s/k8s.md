# `k8s`

Артефакты кластера демо, не код библиотеки. CTL локально — Docker + Testcontainers K3s; `kubectl`/`oc` в прогоне не используются.

Команды: [`docs/PodLoggerJunitDemoCommands.md`](../docs/PodLoggerJunitDemoCommands.md) (Docker / CTL, Cluster).  
Как тесты применяют манифест: [`demo-tests/demo-test.md`](../demo-tests/demo-test.md).

## Файлы

| Путь | Роль |
| --- | --- |
| `k8s/demo-api.yaml` | канон Deployment + Service |
| `demo-tests/src/test/resources/k8s/demo-api.yaml` | копия в classpath для `ClusterLifecycle` |

Держать копии одинаковыми. Селектор `app=demo-api` совпадает с дефолтом `@PodLogger(podLabelSelector)`.

## Манифест (этот каталог)

- Deployment `demo-api`, 1 replica, контейнер `demo-api:local`.
- `imagePullPolicy: Never` — образ заранее в K3s (`ctr images import` из `ClusterLifecycle`).
- Service port 8080.
- readiness/liveness: HTTP `GET /health` на 8080.

## RBAC для закрытого контура

На K3s в Testcontainers отдельный Role не навешивается (тот же API server). Для чужого кластера:

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

Без `create` lifecycle Events не появятся, прогон идёт. Без `list` Events пустые, fail-fast по Event не сработает.
