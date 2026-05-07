package com.teacheragent.service.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {

    private boolean success;
    private String message;
    private Long latencyMs;

    public static TestResult ok(String msg, long latency) {
        return new TestResult(true, msg, latency);
    }

    public static TestResult fail(String msg) {
        return new TestResult(false, msg, 0L);
    }
}
