/**
 * Библиотека {@code junit-pod-logger}: JUnit 5 extension {@link com.example.podlogger.PodLogger},
 * который на каждый test invocation снимает логи целевой Kubernetes/OpenShift-поды,
 * прикладывает срез в Allure и сохраняет тот же срез в локальный SQLite.
 *
 * <p>Слои:
 * <ul>
 *   <li>{@link com.example.podlogger.PodLoggerExtension} — lifecycle JUnit (BeforeAll/Each, AfterAll/Each, TestWatcher);</li>
 *   <li>{@link com.example.podlogger.PodLoggerService} — оркестрация runtime-сбора, persist, Allure и Events;</li>
 *   <li>{@link com.example.podlogger.client.OpenshiftClient} — gateway к API поды (logs, events, health);</li>
 *   <li>{@link com.example.podlogger.store.PodStoreService} / {@link com.example.podlogger.store.TestRunStore} — persistent store;</li>
 *   <li>{@link com.example.podlogger.allure.LogAllureAttachmentService} — выход в Allure.</li>
 * </ul>
 *
 * <p>Контракт фич: {@code docs/PodLoggerJunitDemoPRD.md},
 * {@code docs/story/PersistentLogStoreStory/PersistentLogStoreStory.md},
 * {@code docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md}.
 * Каталог тестов: {@code docs/PodLoggerJunitDemoTest.md}.
 */
package com.example.podlogger;
