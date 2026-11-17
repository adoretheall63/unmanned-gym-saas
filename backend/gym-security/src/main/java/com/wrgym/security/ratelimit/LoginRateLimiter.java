package com.wrgym.security.ratelimit;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流（docs/接口文档.md §1.1、§2.2）：同一账号 10 分钟内最多 5 次**失败**尝试。
 *
 * <p>只对失败计入次数，成功登录清零：避免正常用户/自动化联调被自己之前的成功登录拖累；
 * Redis 不可用时降级为进程内计数并告警，不阻断登录（§1.1）。
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final Map<String, Deque<Long>> fallback = new ConcurrentHashMap<>();

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 尝试登录前检查：失败次数已达上限则 429。 */
    public void check(String type, String account) {
        long failures = currentFailures(type, account);
        if (failures >= LIMIT) {
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
    }

    /** 登录失败后计数。 */
    public void recordFailure(String type, String account) {
        String key = key(type, account);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
        } catch (RuntimeException ex) {
            log.warn("Redis 限流不可用，降级为进程内计数: {}", ex.getMessage());
            recordFailureInMemory(type, account);
        }
    }

    /** 登录成功后清零。 */
    public void reset(String type, String account) {
        fallback.remove(key(type, account));
        try {
            redis.delete(key(type, account));
        } catch (RuntimeException ex) {
            log.debug("Redis 限流清零失败: {}", ex.getMessage());
        }
    }

    private long currentFailures(String type, String account) {
        try {
            String value = redis.opsForValue().get(key(type, account));
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException ex) {
            log.warn("Redis 限流不可用，降级为进程内计数: {}", ex.getMessage());
            return currentFailuresInMemory(type, account);
        }
    }

    private void recordFailureInMemory(String type, String account) {
        long now = System.currentTimeMillis();
        Deque<Long> attempts = fallback.computeIfAbsent(key(type, account), k -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() > WINDOW.toMillis()) {
                attempts.pollFirst();
            }
            attempts.addLast(now);
        }
    }

    private long currentFailuresInMemory(String type, String account) {
        long now = System.currentTimeMillis();
        Deque<Long> attempts = fallback.get(key(type, account));
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() > WINDOW.toMillis()) {
                attempts.pollFirst();
            }
            return attempts.size();
        }
    }

    private String key(String type, String account) {
        return "gym:auth:limit:" + type + ":" + account;
    }
}
