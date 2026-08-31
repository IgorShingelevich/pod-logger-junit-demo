package com.example.demotest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.podlogger.PodLogger;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;

@SpringBootTest(classes = DemoTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@PodLogger(collectOnFailOnly = true)
class OrderErrorIT {

    private static final Logger log = LoggerFactory.getLogger(OrderErrorIT.class);

    static {
        ClusterLifecycle.start();
    }

    @BeforeAll
    static void configureRestAssuredLogging() {
        log.debug("Configuring RestAssured request/response logging through SLF4J");
        RestAssured.replaceFiltersWith(
                new RequestLoggingFilter(LogDetail.ALL, new Slf4jPrintStream("rest-assured-request")),
                new ResponseLoggingFilter(LogDetail.ALL, new Slf4jPrintStream("rest-assured-response")));
    }

    @Autowired
    private Integer demoApiPort;

    static Stream<Arguments> errorCases() {
        return Stream.of(
                Arguments.of("UNKNOWN_SKU", "Unknown SKU"),
                Arguments.of("OUT_OF_STOCK", "Item is out of stock"),
                Arguments.of("PAYMENT_DECLINED", "Payment was declined"),
                Arguments.of("USER_BLOCKED", "User is blocked"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorCases")
    void apiErrorIsLoggedOnPod(String code, String expectedMessage) {
        log.debug("Executing apiErrorIsLoggedOnPod: code={} expectedMessage={} port={}",
                code, expectedMessage, demoApiPort);
        given()
                .baseUri("http://127.0.0.1")
                .port(demoApiPort)
                .when()
                .get("/api/orders/{code}", code)
                .then()
                .statusCode(400)
                .body("code", equalTo(code))
                .body("message", equalTo(expectedMessage));

        fail("collectOnFailOnly demo: force failure after expected API error " + code
                + " so @PodLogger attaches this invocation's pod logs to Allure");
    }

    private static final class Slf4jPrintStream extends PrintStream {

        private final String channel;

        private Slf4jPrintStream(String channel) {
            super(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
            this.channel = channel;
        }

        @Override
        public void println(String x) {
            if (x != null && !x.isBlank()) {
                log.debug("{} {}", channel, x);
            }
        }

        @Override
        public void print(String s) {
            if (s != null && !s.isBlank()) {
                log.debug("{} {}", channel, s);
            }
        }
    }
}
