package com.example.podlogger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

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

@Configuration
@ComponentScan(basePackages = "com.example.podlogger")
public class PodLoggerConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    @ConditionalOnBean(OpenShiftClient.class)
    @ConditionalOnMissingBean(OpenshiftClient.class)
    public OpenshiftClient openshiftClient(
            OpenShiftClient fabric8,
            PodLoggerProperties properties,
            LogParser logParser) {
        return new OpenshiftClient(fabric8, properties, logParser);
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource podLoggerDataSource(PodLoggerProperties properties) {
        DataSource dataSource = SqliteDataSourceFactory.create(StorePathResolver.resolve(properties));
        SchemaMigrator.migrate(dataSource);
        return dataSource;
    }
}
