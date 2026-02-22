package com.moveinsync.mdm.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Redis write buffer for device heartbeats.
 *
 * Why Redis buffer instead of direct PostgreSQL writes:
 * - At 100K devices heartbeating every 5 minutes = 333 writes/sec to
 * PostgreSQL.
 * - At 1M devices = 3,333 writes/sec — PostgreSQL connection pool (20
 * connections)
 * becomes the bottleneck.
 *
 * Solution: Buffer heartbeats in Redis (sub-millisecond writes, no connection
 * pool
 * contention), then flush to PostgreSQL in batches every 30 seconds.
 *
 * Why NOT Kafka for heartbeats:
 * - Heartbeats are idempotent — losing a few in a crash is harmless (device
 * sends
 * another heartbeat in 5 minutes).
 * - Kafka adds latency and complexity for no business-critical benefit here.
 * - This architectural split (Kafka for critical events, Redis for
 * high-frequency
 * idempotent writes) demonstrates tool-appropriate engineering decisions.
 *
 * Redis key pattern: heartbeat:{imei}
 * Redis value: Hash with fields {lastHeartbeat, region, appVersion}
 * TTL: 15 minutes (2x heartbeat interval) — auto-cleanup for stale entries.
 */
@Service
@RequiredArgsConstructor
public class HeartbeatBufferService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatBufferService.class);
    private static final String KEY_PREFIX = "heartbeat:";
    private static final Duration HEARTBEAT_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    /**
     * Buffer a heartbeat in Redis instead of directly writing to PostgreSQL.
     * This is an O(1) operation with sub-millisecond latency.
     */
    public void bufferHeartbeat(String imei, LocalDateTime timestamp, String region, String appVersion) {
        String key = KEY_PREFIX + imei;

        Map<String, String> fields = Map.of(
                "lastHeartbeat", timestamp.toString(),
                "region", region != null ? region : "",
                "appVersion", appVersion != null ? appVersion : "");

        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, HEARTBEAT_TTL);

        log.debug("Heartbeat buffered in Redis: imei={}, timestamp={}", imei, timestamp);
    }

    /**
     * Get the Redis key prefix for scanning buffered heartbeats.
     */
    public String getKeyPrefix() {
        return KEY_PREFIX;
    }
}
