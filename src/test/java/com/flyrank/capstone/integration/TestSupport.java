package com.flyrank.capstone.integration;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class TestSupport {

    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

    private TestSupport() {
    }

    static String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    static String uniqueIp() {
        return "10.0.0." + IP_COUNTER.getAndIncrement();
    }
}