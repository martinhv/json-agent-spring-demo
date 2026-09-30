# json-agent-spring-demo

A local Spring Boot 4 app exposing JSON endpoints to test the Checkmk
`json_api` special agent (checkmk-json-agent) against.

## Build & run

Only a JRE is installed locally, so build in a container, run with the local JRE:

    podman run --rm -v "$PWD":/app:Z -v "$HOME/.m2":/root/.m2:Z -w /app \
        docker.io/library/maven:3-eclipse-temurin-21 mvn -q -DskipTests package
    java -jar target/json-agent-spring-demo-0.0.1-SNAPSHOT.jar    # port 8090

(With a JDK installed: `./mvnw spring-boot:run`.) Change the port with
`--server.port=NNNN`.

## Endpoints (http://localhost:8090)

| Path | What it's for |
| --- | --- |
| `/actuator/health` | Real Actuator health: `status`, `components[*].status`, details (`components.database.details.latencyMs`, ...). HTTP 503 when DOWN |
| `/actuator/info` | Facts for the HW/SW inventory (`app.version`, `app.region`, `java.version`) |
| `/actuator/metrics/jvm.memory.used` | Actuator metrics, `measurements[0].value` |
| `/api/status` | Flat numbers/strings, counters (`requestsTotal`, `bytesSent` -> rate), timestamps (`lastBackup` ISO 8601, `lastSyncEpochMillis`), used/total pair (`memory.usedBytes` / `memory.totalBytes`) |
| `/api/nodes` | Array for `nodes[*].health`, filters, aggregations, piggyback hosts (`name`), host labels (`region`, `role`), nested `nodes[*].services[*].running`, classify by `services[*].name` matching `^MyApp` |
| `/api/jobs?page=N&size=M` | Paginated (23 jobs): `links.next` in the body, RFC 8288 `Link` header, `total`, 404 past the last page; `X-RateLimit-Remaining` header |
| `/api/secure` | 401 unless `Authorization: Bearer demo-token` or `X-API-Key: demo-token` |
| `POST /api/query` | Echoes the JSON body |
| `/api/slow?seconds=N` | Delayed response (timeouts) |
| `/api/down` | Always HTTP 500 |
| `/api/notjson` | HTML instead of JSON |

## Provoking alerts

    curl -X POST localhost:8090/api/chaos/database/down      # health -> DOWN, HTTP 503
    curl -X POST localhost:8090/api/chaos/cache/degraded     # custom DEGRADED status, HTTP 200
    curl -X POST localhost:8090/api/chaos/database/up
    curl localhost:8090/api/chaos                            # current states

Components: `database`, `cache`, `messageQueue`.
