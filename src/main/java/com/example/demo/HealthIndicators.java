package com.example.demo;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simulated dependencies that show up under {@code /actuator/health} as
 * {@code components.database}, {@code components.cache} and {@code components.messageQueue}.
 */
@Configuration
public class HealthIndicators {

	@Bean
	HealthIndicator database(ChaosState chaos) {
		return () -> build(chaos.get("database"))
				.withDetail("database", "PostgreSQL")
				.withDetail("latencyMs", latency(chaos.get("database"), 5, 40))
				.withDetail("activeConnections", ThreadLocalRandom.current().nextInt(3, 20))
				.withDetail("maxConnections", 50)
				.build();
	}

	@Bean
	HealthIndicator cache(ChaosState chaos) {
		return () -> build(chaos.get("cache"))
				.withDetail("hitRatio", Math.round(ThreadLocalRandom.current().nextDouble(0.80, 0.99) * 1000) / 1000.0)
				.withDetail("latencyMs", latency(chaos.get("cache"), 1, 5))
				.build();
	}

	@Bean
	HealthIndicator messageQueue(ChaosState chaos) {
		return () -> build(chaos.get("messageQueue"))
				.withDetail("pendingMessages", ThreadLocalRandom.current().nextInt(0, 250))
				.withDetail("consumers", 4)
				.build();
	}

	private static Health.Builder build(ChaosState.State state) {
		return switch (state) {
			case UP -> Health.up();
			// A custom status, as real services use for "working but impaired"
			case DEGRADED -> Health.status(new Status("DEGRADED", "responding slowly"));
			case DOWN -> Health.down().withDetail("error", "connection refused");
		};
	}

	private static double latency(ChaosState.State state, int min, int max) {
		double base = ThreadLocalRandom.current().nextDouble(min, max);
		return Math.round((state == ChaosState.State.DEGRADED ? base * 20 : base) * 10) / 10.0;
	}
}
