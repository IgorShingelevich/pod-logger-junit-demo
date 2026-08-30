package com.example.podlogger.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.podlogger.PodLoggerProperties;
import com.example.podlogger.event.PodEventReasons;
import com.example.podlogger.event.StandDownEventMatcher;
import com.example.podlogger.parser.LogParser;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.client.OpenShiftClient;
import lombok.RequiredArgsConstructor;

/**
 * Runtime-gateway к Kubernetes/OpenShift API через fabric8 {@link OpenShiftClient}.
 *
 * <p>Умеет: найти целевую под по namespace+label, снять {@code pods/log},
 * list/create core/v1 Events ({@code client.v1().events()}, не {@code events.k8s.io}),
 * probe доступности (stand-down Event → Ready → опциональный HTTP health).
 *
 * <p>Не знает про SQLite, Allure и JUnit. Ошибки list/publish глотаются:
 * list → пустой список, publish → {@code null}. Это не stand-down.
 *
 * <p>Парсинг stdout делегирован {@link LogParser}; dump kube-preamble не JSON — пропускается.
 */
@RequiredArgsConstructor
public class OpenshiftClient {

    private static final Logger log = LoggerFactory.getLogger(OpenshiftClient.class);
    /** Таймаут connect и request HTTP health (шаг 3 probe). */
    private static final Duration HTTP_HEALTH_TIMEOUT = Duration.ofSeconds(2);

    /** fabric8-адаптер; в unit-тестах может быть {@code null}. */
    private final OpenShiftClient fabric8;
    /** Namespace, selector, health URL, stand-down. */
    private final PodLoggerProperties properties;
    /** Парсер stdout поды. */
    private final LogParser logParser;
    /** JDK-клиент для опционального HTTP health. */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_HEALTH_TIMEOUT)
            .build();

    /**
     * Снимает полный dump {@code GET .../pods/{name}/log} и парсит JSON-строки в DTO.
     * Сигнатура стабильна: потребители и тесты опираются на {@code List<PodLogDto> getLog()}.
     *
     * @return распарсенные события; шум и не-JSON строки отброшены
     * @throws IllegalStateException если fabric8 не сконфигурирован или под не найдена
     */
    public List<PodLogDto> getLog() {
        String raw = fetchRawLog();
        return logParser.parse(raw);
    }

    /**
     * Ищет поды по {@code namespace} + {@code podLabelSelector} ({@code key=value}).
     * Предпочитает Ready+Running; если таких нет — берёт первую из списка.
     *
     * @return fabric8 Pod
     * @throws IllegalStateException нет клиента или нет под с селектором
     */
    public Pod resolveTargetPod() {
        if (fabric8 == null) {
            throw new IllegalStateException("OpenShift client is not configured");
        }
        String namespace = properties.getNamespace();
        String[] selector = splitSelector(properties.getPodLabelSelector());
        List<Pod> pods = fabric8.pods()
                .inNamespace(namespace)
                .withLabel(selector[0], selector[1])
                .list()
                .getItems();
        if (pods.isEmpty()) {
            throw new IllegalStateException(
                    "No pods found in namespace " + namespace + " with selector " + properties.getPodLabelSelector());
        }
        return pods.stream()
                .filter(OpenshiftClient::isReady)
                .findFirst()
                .orElse(pods.get(0));
    }

    /**
     * Все core/v1 Events с {@code involvedObject} = целевая под.
     * Ошибка list → пустой список (не throw, не stand-down).
     *
     * @return DTO; {@code code} равен k8s {@code reason}
     */
    public List<PodEventDto> getEvents() {
        try {
            Pod pod = resolveTargetPod();
            return listEventsForPod(pod);
        } catch (Exception e) {
            log.error("Failed to list pod events", e);
            return List.of();
        }
    }

    /**
     * {@link #getEvents()} с фильтром по timestamp на клиенте
     * ({@code lastTimestamp} иначе {@code eventTime} иначе {@code creationTimestamp}).
     *
     * @param fromInclusive нижняя граница UTC включительно
     * @param toInclusive   верхняя граница UTC включительно
     * @return события внутри окна
     */
    public List<PodEventDto> getEvents(LocalDateTime fromInclusive, LocalDateTime toInclusive) {
        return getEvents().stream()
                .filter(event -> PodEventMapper.inWindow(event, fromInclusive, toInclusive))
                .collect(Collectors.toList());
    }

    /**
     * Best-effort create Event на целевой поде: {@code generateName=pod-logger-},
     * {@code involvedObject.kind=Pod}. Ошибка create логируется, возвращается {@code null},
     * suite не валится.
     *
     * @param type    {@code Normal} или {@code Warning}
     * @param reason  PascalCase-код ({@code TestRunStarted}, {@code Maintenance}, …)
     * @param message без секретов
     * @return созданный DTO или {@code null}
     */
    public PodEventDto publishPodEvent(String type, String reason, String message) {
        try {
            if (fabric8 == null) {
                log.warn("Skip publishPodEvent: OpenShift client is not configured");
                return null;
            }
            Pod pod = resolveTargetPod();
            String namespace = properties.getNamespace();
            ObjectReference involved = new ObjectReferenceBuilder()
                    .withKind("Pod")
                    .withApiVersion("v1")
                    .withNamespace(namespace)
                    .withName(pod.getMetadata().getName())
                    .withUid(pod.getMetadata().getUid())
                    .build();
            String now = java.time.Instant.now().toString();
            Event event = new EventBuilder()
                    .withNewMetadata()
                        .withGenerateName("pod-logger-")
                        .withNamespace(namespace)
                    .endMetadata()
                    .withInvolvedObject(involved)
                    .withType(type)
                    .withReason(reason)
                    .withMessage(message)
                    .withLastTimestamp(now)
                    .withFirstTimestamp(now)
                    .withCount(1)
                    .withNewSource()
                        .withComponent("junit-pod-logger")
                    .endSource()
                    .build();
            Event created = fabric8.v1().events().inNamespace(namespace).resource(event).create();
            return PodEventMapper.toDto(created, pod.getMetadata().getName(), namespace);
        } catch (Exception e) {
            log.error("Failed to publish pod event reason={}", reason, e);
            return null;
        }
    }

    /**
     * Короткий boolean поверх {@link #probePodAvailability()}.
     *
     * @return {@code true} только если нет stand-down Event и health зелёный
     */
    public boolean isPodAvailable() {
        return probePodAvailability().isAvailable();
    }

    /**
     * Short-circuit probe: (1) stand-down Events по всем текущим Events поды —
     * health HTTP не обязателен; (2) Kubernetes Ready; (3) HTTP GET {@code healthCheckUrl},
     * если URL непустой. Тело 2xx, матчащее stand-down pattern, считается красным health.
     *
     * @return детальный статус; {@code available=true} только на шаге 4
     */
    public PodAvailability probePodAvailability() {
        List<PodEventDto> events;
        try {
            events = getEvents();
        } catch (Exception e) {
            log.error("Failed to list events during availability probe", e);
            events = List.of();
        }
        List<PodEventDto> standDown = StandDownEventMatcher.match(
                events,
                properties.effectiveStandDownEventCodes(),
                properties.effectiveStandDownMessagePatterns());
        if (!standDown.isEmpty()) {
            PodEventDto first = standDown.get(0);
            String code = first.getCode() != null ? first.getCode() : first.getReason();
            return PodAvailability.standDown(code, first.getMessage(), standDown);
        }

        try {
            Pod pod = resolveTargetPod();
            if (!isReady(pod)) {
                return PodAvailability.healthFailed(PodEventReasons.POD_NOT_READY, "Pod is not Ready");
            }
        } catch (Exception e) {
            log.warn("Pod resolve failed during availability probe: {}", e.getMessage());
            return PodAvailability.healthFailed(PodEventReasons.POD_NOT_READY, e.getMessage());
        }

        String healthUrl = properties.getHealthCheckUrl();
        if (healthUrl != null && !healthUrl.isBlank()) {
            if (!checkHttpHealth(healthUrl)) {
                return PodAvailability.healthFailed(
                        PodEventReasons.HEALTH_CHECK_FAILED,
                        "HTTP health check failed for " + healthUrl);
            }
        }
        return PodAvailability.up();
    }

    /**
     * Сырой dump логов выбранной поды. Для тестов парсера можно обойти через {@link #parseLogDump}.
     *
     * @return текст {@code pods/log}
     */
    String fetchRawLog() {
        log.debug("Fetching logs from namespace {} with selector {}", properties.getNamespace(), properties.getPodLabelSelector());
        Pod ready = resolveTargetPod();
        String namespace = properties.getNamespace();
        String name = ready.getMetadata().getName();
        log.debug("Reading logs from pod {}/{}", namespace, name);
        return fabric8.pods().inNamespace(namespace).withName(name).getLog();
    }

    /**
     * Делегат {@link LogParser#parse(String)} — оставлен для тестов без кластера.
     *
     * @param raw dump stdout
     * @return DTO-строки
     */
    List<PodLogDto> parseLogDump(String raw) {
        return logParser.parse(raw);
    }

    /**
     * List Events {@code involvedObject} = переданная под, map в DTO.
     *
     * @param pod целевая под
     * @return список; пустой если клиент/под/items отсутствуют
     */
    List<PodEventDto> listEventsForPod(Pod pod) {
        if (fabric8 == null || pod == null || pod.getMetadata() == null) {
            return List.of();
        }
        String namespace = properties.getNamespace();
        String podName = pod.getMetadata().getName();
        ObjectReference ref = new ObjectReferenceBuilder()
                .withKind("Pod")
                .withApiVersion("v1")
                .withName(podName)
                .withNamespace(namespace)
                .withUid(pod.getMetadata().getUid())
                .build();
        List<Event> items = fabric8.v1().events()
                .inNamespace(namespace)
                .withInvolvedObject(ref)
                .list()
                .getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<PodEventDto> result = new ArrayList<>(items.size());
        for (Event item : items) {
            PodEventDto dto = PodEventMapper.toDto(item, podName, namespace);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    /**
     * HTTP GET с таймаутом 2 с. Не-2xx или тело с stand-down pattern → {@code false}.
     * Interrupt и сетевые ошибки → {@code false} (красное health, не stand-down Event).
     *
     * @param url абсолютный URL
     * @return {@code true} если 2xx и тело не матчит pattern
     */
    boolean checkHttpHealth(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HTTP_HEALTH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                return false;
            }
            String body = response.body() == null ? "" : response.body();
            return !StandDownEventMatcher.matchesText(body, properties.effectiveStandDownMessagePatterns());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("HTTP health check interrupted for {}", url);
            return false;
        } catch (Exception e) {
            log.warn("HTTP health check failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * Kubernetes Ready: phase {@code Running} и все контейнеры {@code ready=true}.
     *
     * @param pod объект API
     * @return {@code true} если под готова принимать трафик
     */
    static boolean isReady(Pod pod) {
        return pod.getStatus() != null
                && "Running".equals(pod.getStatus().getPhase())
                && pod.getStatus().getContainerStatuses() != null
                && pod.getStatus().getContainerStatuses().stream().allMatch(cs -> Boolean.TRUE.equals(cs.getReady()));
    }

    /**
     * Режет {@code key=value} на два токена.
     *
     * @param selector строка аннотации
     * @return {@code [key, value]}
     * @throws IllegalArgumentException если нет {@code =} или ключ пустой
     */
    static String[] splitSelector(String selector) {
        int eq = selector.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException("podLabelSelector must be key=value, got: " + selector);
        }
        return new String[] {selector.substring(0, eq), selector.substring(eq + 1)};
    }
}
