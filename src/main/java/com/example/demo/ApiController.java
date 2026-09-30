package com.example.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

	/** Accepted as {@code Authorization: Bearer <token>} or {@code X-API-Key: <token>} (dev only!). */
	static final String API_TOKEN = "demo-token";

	private static final int JOB_COUNT = 23;

	private final Instant startedAt = Instant.now();
	private final AtomicLong requestsTotal = new AtomicLong(100_000);
	private final AtomicLong bytesSent = new AtomicLong(50_000_000);
	private final ChaosState chaos;

	ApiController(ChaosState chaos) {
		this.chaos = chaos;
	}

	/** Flat status document: strings, numbers, a growing counter and timestamps. */
	@GetMapping("/status")
	Map<String, Object> status() {
		var rnd = ThreadLocalRandom.current();
		var body = new LinkedHashMap<String, Object>();
		body.put("status", chaos.all().containsValue(ChaosState.State.DOWN) ? "error" : "ok");
		body.put("version", "1.4.2");
		body.put("uptimeSeconds", Duration.between(startedAt, Instant.now()).toSeconds());
		body.put("requestsTotal", requestsTotal.addAndGet(rnd.nextInt(50, 500)));    // counter -> rate
		body.put("bytesSent", bytesSent.addAndGet(rnd.nextInt(10_000, 900_000)));   // counter -> bytes/s
		body.put("errorRate", Math.round(rnd.nextDouble(0, 0.08) * 1000) / 1000.0);
		body.put("temperatureCelsius", Math.round(rnd.nextDouble(35, 75) * 10) / 10.0);
		body.put("memory", Map.of("usedBytes", 1_200_000_000L + rnd.nextLong(0, 600_000_000L),
				"totalBytes", 2_147_483_648L));                                          // used/total -> %
		body.put("lastBackup", Instant.now().minusSeconds(rnd.nextLong(600, 7200)).toString()); // ISO 8601
		body.put("lastSyncEpochMillis", Instant.now().minusSeconds(30).toEpochMilli());          // epoch ms
		body.put("message", body.get("status").equals("ok") ? "all systems nominal" : "a dependency is down");
		return body;
	}

	/** An array for [*] wildcards, filters, aggregations and piggyback hosts. */
	@GetMapping("/nodes")
	Map<String, Object> nodes() {
		var rnd = ThreadLocalRandom.current();
		List<Map<String, Object>> nodes = List.of(
				node("node-1", "eu-central", "primary", "ok", rnd.nextDouble(0.1, 0.6)),
				node("node-2", "eu-central", "replica", "ok", rnd.nextDouble(0.1, 0.6)),
				node("node-3", "us-east", "replica", "degraded", rnd.nextDouble(0.7, 0.95)),
				node("node-4", "us-east", "replica", "critical", rnd.nextDouble(0.9, 1.0)));
		return Map.of("cluster", "demo-cluster", "nodes", nodes);
	}

	private static Map<String, Object> node(String name, String region, String role, String health, double load) {
		var rnd = ThreadLocalRandom.current();
		return Map.of("name", name, "region", region, "role", role, "health", health,
				"load", Math.round(load * 100) / 100.0,
				"services", List.of(
						Map.of("name", "MyApp-api", "running", true),
						Map.of("name", "MyApp-worker", "running", !health.equals("critical")),
						Map.of("name", "node-exporter", "running", true)),
				"diskFreePercent", rnd.nextInt(5, 90));
	}

	/**
	 * A paginated collection: {@code ?page=N&size=M}. Each page carries {@code items},
	 * {@code total}, {@code links.next} (absent on the last page) and an RFC 8288 Link header.
	 */
	@GetMapping("/jobs")
	ResponseEntity<Map<String, Object>> jobs(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "5") int size) {
		if (page < 1 || size < 1) {
			return ResponseEntity.badRequest().body(Map.of("error", "page and size must be >= 1"));
		}
		int from = (page - 1) * size;
		if (from >= JOB_COUNT && page > 1) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "page out of range"));
		}
		String[] states = {"queued", "running", "done", "failed"};
		var items = IntStream.range(from, Math.min(from + size, JOB_COUNT))
				.mapToObj(i -> Map.<String, Object>of("id", i + 1, "state", states[i % states.length],
						"durationSeconds", (i * 37) % 600))
				.toList();
		var body = new LinkedHashMap<String, Object>();
		body.put("page", page);
		body.put("size", size);
		body.put("total", JOB_COUNT);
		body.put("items", items);
		var response = ResponseEntity.ok();
		if (from + size < JOB_COUNT) {
			String next = "/api/jobs?page=" + (page + 1) + "&size=" + size;
			body.put("links", Map.of("next", next));
			response.header("Link", "<" + next + ">; rel=\"next\"");
		}
		return response.header("X-RateLimit-Remaining", String.valueOf(ThreadLocalRandom.current().nextInt(0, 1000)))
				.body(body);
	}

	/** Requires {@code Authorization: Bearer demo-token} or {@code X-API-Key: demo-token}. */
	@GetMapping("/secure")
	ResponseEntity<Map<String, Object>> secure(@RequestHeader(value = "Authorization", required = false) String auth,
			@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
		boolean ok = ("Bearer " + API_TOKEN).equals(auth) || API_TOKEN.equals(apiKey);
		if (!ok) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header("WWW-Authenticate", "Bearer")
					.body(Map.of("error", "unauthorized", "hint", "send 'Authorization: Bearer demo-token'"));
		}
		return ResponseEntity.ok(Map.of("status", "ok", "authorized", true, "secretCount", 42));
	}

	/** POST with a JSON body: echoes it back, for testing POST requests with a body. */
	@PostMapping("/query")
	Map<String, Object> query(@RequestBody(required = false) Map<String, Object> request) {
		return Map.of("status", "ok", "echo", request == null ? Map.of() : request, "resultCount", 3);
	}

	/** Responds after {@code ?seconds=N} (default 5) - for per-endpoint timeouts. */
	@GetMapping("/slow")
	Map<String, Object> slow(@RequestParam(defaultValue = "5") int seconds) throws InterruptedException {
		Thread.sleep(Duration.ofSeconds(Math.min(seconds, 60)));
		return Map.of("status", "ok", "delayedSeconds", seconds);
	}

	/** Always HTTP 500. */
	@GetMapping("/down")
	ResponseEntity<Map<String, Object>> down() {
		return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "simulated failure"));
	}

	/** Returns text, not JSON. */
	@GetMapping(value = "/notjson", produces = "text/html")
	String notJson() {
		return "<html><body>This is not JSON</body></html>";
	}

	/** Current simulated dependency states. */
	@GetMapping("/chaos")
	Map<String, ChaosState.State> chaosState() {
		return chaos.all();
	}

	/** Flip a dependency: {@code POST /api/chaos/database/down} (states: up, degraded, down). */
	@PostMapping("/chaos/{component}/{state}")
	ResponseEntity<Map<String, ChaosState.State>> setChaos(@PathVariable String component, @PathVariable String state) {
		ChaosState.State parsed;
		try {
			parsed = ChaosState.State.valueOf(state.toUpperCase());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}
		return chaos.set(component, parsed) ? ResponseEntity.ok(chaos.all()) : ResponseEntity.notFound().build();
	}
}
