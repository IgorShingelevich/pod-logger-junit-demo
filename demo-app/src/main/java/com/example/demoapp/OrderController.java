package com.example.demoapp;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    static final Map<String, String> ERRORS = Map.of(
            "UNKNOWN_SKU", "Unknown SKU",
            "OUT_OF_STOCK", "Item is out of stock",
            "PAYMENT_DECLINED", "Payment was declined",
            "USER_BLOCKED", "User is blocked");

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/api/orders/{code}")
    public ResponseEntity<Map<String, String>> order(@PathVariable("code") String code) {
        String message = ERRORS.getOrDefault(code, "Unknown error code: " + code);
        log.error("{}", message);
        return ResponseEntity.badRequest().body(Map.of("code", code, "message", message));
    }
}
