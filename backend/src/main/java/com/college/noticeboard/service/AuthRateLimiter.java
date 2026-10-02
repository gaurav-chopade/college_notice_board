package com.college.noticeboard.service;

import com.college.noticeboard.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limits for sign-in and registration.
 * <ul>
 *     <li>Failed sign-ins per email + IP: stops password guessing on one account without letting
 *         a stranger lock the real owner out from their own network.</li>
 *     <li>Failed sign-ins per IP: stops one machine spraying many accounts.</li>
 *     <li>Registrations per IP: stops scripted sign-up floods into approvers' queues.</li>
 * </ul>
 * Single-instance only. Behind a load balancer, move the counters to a shared store (e.g. Redis)
 * and read the client IP from a trusted proxy header.
 */
@Component
public class AuthRateLimiter {

    private final int maxFailuresPerAccount;
    private final int maxFailuresPerIp;
    private final int maxRegistrationsPerIp;
    private final Duration loginWindow;
    private final Duration registrationWindow = Duration.ofHours(1);
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public AuthRateLimiter(@Value("${app.auth.max-login-failures:5}") int maxFailuresPerAccount,
                           @Value("${app.auth.max-login-failures-per-ip:20}") int maxFailuresPerIp,
                           @Value("${app.auth.login-window-minutes:15}") int windowMinutes,
                           @Value("${app.auth.max-registrations-per-hour:10}") int maxRegistrationsPerIp) {
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.maxRegistrationsPerIp = maxRegistrationsPerIp;
        this.loginWindow = Duration.ofMinutes(windowMinutes);
    }

    public void checkLogin(String email, String ip) {
        Instant now = Instant.now();
        long wait = Math.max(waitSeconds("fail:" + email + "|" + ip, maxFailuresPerAccount, loginWindow, now),
                waitSeconds("failip:" + ip, maxFailuresPerIp, loginWindow, now));
        if (wait > 0) {
            throw new TooManyRequestsException("Too many failed sign-in attempts. Try again in " + minutes(wait) + ".");
        }
    }

    public void recordLoginFailure(String email, String ip) {
        Instant now = Instant.now();
        record("fail:" + email + "|" + ip, now);
        record("failip:" + ip, now);
    }

    public void recordLoginSuccess(String email, String ip) {
        events.remove("fail:" + email + "|" + ip);
    }

    public void checkAndRecordRegistration(String ip) {
        Instant now = Instant.now();
        long wait = waitSeconds("reg:" + ip, maxRegistrationsPerIp, registrationWindow, now);
        if (wait > 0) {
            throw new TooManyRequestsException("Too many registrations from this network. Try again in " + minutes(wait) + ".");
        }
        record("reg:" + ip, now);
    }

    /** Seconds until the oldest event in the window expires, or 0 if under the limit. */
    private long waitSeconds(String key, int max, Duration window, Instant now) {
        Deque<Instant> q = events.get(key);
        if (q == null) {
            return 0;
        }
        synchronized (q) {
            prune(q, window, now);
            if (q.size() < max) {
                return 0;
            }
            return Math.max(1, Duration.between(now, q.peekFirst().plus(window)).toSeconds());
        }
    }

    private void record(String key, Instant now) {
        if (events.size() > 50_000) {
            cleanUp(now);
        }
        Deque<Instant> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(now);
            while (q.size() > 100) {
                q.pollFirst();
            }
        }
    }

    private void cleanUp(Instant now) {
        Duration longest = loginWindow.compareTo(registrationWindow) > 0 ? loginWindow : registrationWindow;
        events.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), longest, now);
                return e.getValue().isEmpty();
            }
        });
    }

    private static void prune(Deque<Instant> q, Duration window, Instant now) {
        Instant cutoff = now.minus(window);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }

    private static String minutes(long seconds) {
        long m = Math.max(1, (seconds + 59) / 60);
        return m == 1 ? "1 minute" : m + " minutes";
    }
}
