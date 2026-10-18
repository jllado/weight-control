package com.jllado.weightcontrol.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Emits privacy-safe timings for synchronous Coach Action work. */
public final class CoachActionTiming {

    private static final Logger LOG = LoggerFactory.getLogger(CoachActionTiming.class);
    private static final String REQUEST_ID_KEY = "coachActionRequestId";

    private CoachActionTiming() {
    }

    public static <T> T time(String action, String stage, Supplier<T> operation) {
        boolean ownsRequestId = MDC.get(REQUEST_ID_KEY) == null;
        if (ownsRequestId) {
            MDC.put(REQUEST_ID_KEY, UUID.randomUUID().toString());
        }
        long started = System.nanoTime();
        String outcome = "success";
        try {
            return operation.get();
        } catch (RuntimeException | Error exception) {
            outcome = "error";
            throw exception;
        } finally {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            LOG.info("coach_action_timing action={} stage={} request_id={} elapsed_ms={} outcome={}",
                action, stage, MDC.get(REQUEST_ID_KEY), elapsedMillis, outcome);
            if (ownsRequestId) {
                MDC.remove(REQUEST_ID_KEY);
            }
        }
    }
}
