package com.example.podlogger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.example.podlogger.allure.AllureSink;
import com.example.podlogger.allure.DefaultAllureSink;
import com.example.podlogger.client.OpenshiftClient;
import com.example.podlogger.parser.LogParser;
import com.example.podlogger.store.StorePathResolver;
import com.example.podlogger.store.sqlite.SchemaMigrator;
import com.example.podlogger.store.sqlite.SqliteDataSourceFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.fabric8.openshift.client.OpenShiftClient;

import javax.sql.DataSource;

/**
 * Spring-конфигурация библиотеки: component-scan пакета {@code com.example.podlogger}
 * и явные bean'ы, которые нельзя (или нежелательно) поднимать через {@code @Component}.
 *
 * <p>Ожидает, что приложение-потребитель предоставит fabric8 {@link OpenShiftClient}
 * (в демо это {@code ClusterConfig}). Без него bean {@link OpenshiftClient} не создаётся.
 *
 * <p>SQLite {@link DataSource} создаётся только если в контексте ещё нет другого DataSource
 * ({@link ConditionalOnMissingBean}): путь резолвит {@link StorePathResolver}, затем
 * {@link SchemaMigrator#migrate(DataSource)}.
 */
@Configuration
@ComponentScan(
        basePackages = "com.example.podlogger",
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
public class PodLoggerConfiguration {

    /**
     * Jackson для парсера JSON-логов поды и Allure JSON-аттачей.
     * Даты пишутся ISO-строками, не epoch.
     *
     * @return ObjectMapper с {@link JavaTimeModule}
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Runtime-клиент к поде. Требует уже существующий fabric8 {@link OpenShiftClient}.
     *
     * @param fabric8    адаптер OpenShift/Kubernetes API
     * @param properties namespace, selector, health URL, stand-down коды
     * @param logParser  разбор stdout поды в {@code PodLogDto}
     * @return обёртка библиотеки над fabric8
     */
    @Bean
    @ConditionalOnBean(OpenShiftClient.class)
    @ConditionalOnMissingBean(OpenshiftClient.class)
    public OpenshiftClient openshiftClient(
            OpenShiftClient fabric8,
            PodLoggerProperties properties,
            LogParser logParser) {
        return new OpenshiftClient(fabric8, properties, logParser);
    }

    /**
     * Выход в Allure. В тестах подменяется записывающим stub'ом.
     *
     * @return делегат на {@code Allure.addAttachment}
     */
    @Bean
    @ConditionalOnMissingBean(AllureSink.class)
    public AllureSink allureSink() {
        return new DefaultAllureSink();
    }

    /**
     * Файл SQLite + schema. Не создаётся, если потребитель уже объявил {@link DataSource}.
     *
     * @param properties путь к файлу (после system property и env)
     * @return SQLite DataSource с WAL и накатанной схемой
     */
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource podLoggerDataSource(PodLoggerProperties properties) {
        DataSource dataSource = SqliteDataSourceFactory.create(StorePathResolver.resolve(properties));
        SchemaMigrator.migrate(dataSource);
        return dataSource;
    }
}
