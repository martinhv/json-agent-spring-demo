package com.example.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Mutable state of the simulated dependencies, flipped at runtime via
 * {@code POST /api/chaos/{component}/{state}} so alerts can be provoked on demand.
 */
@Component
public class ChaosState {

	public enum State { UP, DEGRADED, DOWN }

	private final Map<String, State> components = new ConcurrentHashMap<>(Map.of(
			"database", State.UP,
			"cache", State.UP,
			"messageQueue", State.UP));

	public State get(String component) {
		return components.getOrDefault(component, State.UP);
	}

	public boolean set(String component, State state) {
		return components.replace(component, state) != null;
	}

	public Map<String, State> all() {
		return Map.copyOf(components);
	}
}
