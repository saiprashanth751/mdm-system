package com.moveinsync.mdm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Context load test — verifies that all Spring beans wire properly.
 *
 * Uses @MockitoBean for KafkaTemplate, KafkaAdmin, and StringRedisTemplate
 * because:
 * - Kafka auto-config is disabled in tests (no broker available)
 * - Redis is not available in CI environments
 * - KafkaAdmin is required by MdmHealthIndicator for broker reachability checks
 *
 * This test validates Spring wiring, NOT Kafka/Redis functionality
 * (those are tested via Mockito in the service unit tests).
 */
@SpringBootTest
class MdmSystemApplicationTests {

	@MockitoBean
	private KafkaTemplate<?, ?> kafkaTemplate;

	@MockitoBean
	private KafkaAdmin kafkaAdmin;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@Test
	void contextLoads() {
	}

}
