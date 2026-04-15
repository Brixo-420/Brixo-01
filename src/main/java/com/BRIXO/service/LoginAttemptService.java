package com.BRIXO.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final ConcurrentHashMap<String, int[]> attemptsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lockCache = new ConcurrentHashMap<>();

    public void loginFailed(String email) {
        if (email == null || email.isBlank()) return;
        String key = email.toLowerCase().trim();

        attemptsCache.compute(key, (k, v) -> {
            int count = (v == null) ? 1 : v[0] + 1;
            return new int[]{count};
        });

        if (attemptsCache.getOrDefault(key, new int[]{0})[0] >= MAX_ATTEMPTS) {
            lockCache.put(key, LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        }
    }

    public void loginSucceeded(String email) {
        if (email == null || email.isBlank()) return;
        String key = email.toLowerCase().trim();
        attemptsCache.remove(key);
        lockCache.remove(key);
    }

    public boolean isBlocked(String email) {
        if (email == null || email.isBlank()) return false;
        String key = email.toLowerCase().trim();
        LocalDateTime lockUntil = lockCache.get(key);
        if (lockUntil == null) return false;
        if (LocalDateTime.now().isAfter(lockUntil)) {
            lockCache.remove(key);
            attemptsCache.remove(key);
            return false;
        }
        return true;
    }

    public int getRemainingAttempts(String email) {
        if (email == null || email.isBlank()) return MAX_ATTEMPTS;
        String key = email.toLowerCase().trim();
        int[] attempts = attemptsCache.get(key);
        if (attempts == null) return MAX_ATTEMPTS;
        return Math.max(0, MAX_ATTEMPTS - attempts[0]);
    }

    public int getLockMinutesRemaining(String email) {
        if (email == null || email.isBlank()) return 0;
        String key = email.toLowerCase().trim();
        LocalDateTime lockUntil = lockCache.get(key);
        if (lockUntil == null) return 0;
        long mins = java.time.Duration.between(LocalDateTime.now(), lockUntil).toMinutes();
        return (int) Math.max(0, mins + 1);
    }
}
