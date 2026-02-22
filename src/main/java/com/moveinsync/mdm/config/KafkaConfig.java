package com.moveinsync.mdm.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration.
 * Topics are auto-created on startup if they don't exist.
 *
 * Production note: In a multi-instance deployment, partition count
 * should match the number of consumer instances for parallel processing.
 */
@Configuration
public class KafkaConfig {

    public static final String SCHEDULE_APPROVED_TOPIC = "schedule.approved";

    @Bean
    public NewTopic scheduleApprovedTopic() {
        return TopicBuilder.name(SCHEDULE_APPROVED_TOPIC)
                .partitions(3) // 3 partitions for parallel consumer processing
                .replicas(1) // Single broker in dev; increase in production
                .build();
    }
}
