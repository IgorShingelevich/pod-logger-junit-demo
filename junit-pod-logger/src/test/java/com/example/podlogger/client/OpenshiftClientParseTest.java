package com.example.podlogger.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.podlogger.PodLoggerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class OpenshiftClientParseTest {

    @Test
    void parsesJsonLinesAndSkipsNoise() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OpenshiftClient client = new OpenshiftClient(null, new PodLoggerProperties(), mapper);
        String dump = """
                some kube preamble
                {"timestamp":"2026-08-29T17:01:02.123","level":"ERROR","message":"Unknown SKU","logger":"c.e.d.OrderController"}
                not json
                {"timestamp":"2026-08-29T17:01:03.000","level":"INFO","message":"started","logger":"demo"}
                """;
        List<PodLogDto> logs = client.parseLogDump(dump);
        assertEquals(2, logs.size());
        assertEquals("ERROR", logs.get(0).getLevel());
        assertEquals("Unknown SKU", logs.get(0).getMessage());
        assertEquals(LocalDateTime.of(2026, 8, 29, 17, 1, 2, 123_000_000), logs.get(0).getTimestamp());
        assertTrue(logs.get(1).getMessage().contains("started"));
    }
}
