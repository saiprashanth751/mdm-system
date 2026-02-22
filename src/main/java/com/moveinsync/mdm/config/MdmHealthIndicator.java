package com.moveinsync.mdm.config;

import com.moveinsync.mdm.enums.DeviceStatus;
import com.moveinsync.mdm.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for the MDM system.
 * Reports on Redis buffer connectivity, Kafka broker reachability,
 * and active device count — giving operators a single-glance view
 * of system readiness at GET /actuator/health.
 */
@Component("mdmSystem")
@RequiredArgsConstructor
public class MdmHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(MdmHealthIndicator.class);

    private final StringRedisTemplate redisTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final DeviceRepository deviceRepository;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // 1. Redis connectivity check
        checkRedis(builder);

        // 2. Kafka broker reachability
        checkKafka(builder);

        // 3. Active device count (business-level health signal)
        checkDeviceCount(builder);

        return builder.build();
    }

    private void checkRedis(Health.Builder builder) {
        try {
            RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
            if (factory != null) {
                RedisConnection connection = factory.getConnection();
                try {
                    String pong = connection.ping();
                    builder.withDetail("redis", "reachable — heartbeat buffer operational (PING: " + pong + ")");
                } finally {
                    connection.close();
                }
            } else {
                builder.down().withDetail("redis", "connection factory is null");
            }
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            builder.down().withDetail("redis", "unreachable — " + e.getMessage());
        }
    }

    private void checkKafka(Health.Builder builder) {
        try {
            // KafkaAdmin.describeTopics uses AdminClient internally — safe, no resource
            // leak
            var descriptions = kafkaAdmin.describeTopics("schedule.approved");
            if (descriptions != null && !descriptions.isEmpty()) {
                var topicDesc = descriptions.get("schedule.approved");
                int partitions = topicDesc != null ? topicDesc.partitions().size() : 0;
                builder.withDetail("kafka", "reachable — topic schedule.approved (" + partitions + " partitions)");
            } else {
                builder.withDetail("kafka", "reachable — topic schedule.approved not yet created");
            }
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            builder.down().withDetail("kafka", "unreachable — " + e.getMessage());
        }
    }

    private void checkDeviceCount(Health.Builder builder) {
        try {
            long activeDevices = deviceRepository.countByStatus(DeviceStatus.ACTIVE);
            builder.withDetail("activeDevices", activeDevices);
        } catch (Exception e) {
            log.warn("Device count health check failed: {}", e.getMessage());
            builder.withDetail("activeDevices", "unavailable — " + e.getMessage());
        }
    }
}
