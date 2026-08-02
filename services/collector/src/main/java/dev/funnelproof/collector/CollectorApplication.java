package dev.funnelproof.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/** Local-only collector. It binds to loopback and never logs request bodies. */
public final class CollectorApplication {
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CollectorApplication() {
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("FUNNEL_PROOF_PORT", "8080"));
        EventValidator validator = new EventValidator();
        Path dataDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_DATA_DIR", ".funnel-proof/events"));
        EventStore store = new FileEventStore(dataDirectory);
        Path eventLogDirectory = Path.of(System.getenv().getOrDefault("FUNNEL_PROOF_EVENT_LOG_DIR", ".funnel-proof/event-log"));
        EventLog eventLog = eventLogFromEnvironment(eventLogDirectory);
        registerShutdownHook(eventLog);
        EventIngestionService ingestionService = new EventIngestionService(eventLog, store);
        WorkspaceKeyResolver workspaceKeys = WorkspaceKeyResolver.localFromEnvironment();
        HttpServer server = createServer(port, validator, store, ingestionService, workspaceKeys);
        server.start();
        System.out.printf("FunnelProof collector listening on http://127.0.0.1:%d/fp/collect%n", port);
        System.out.printf("Local funnel report: http://127.0.0.1:%d/fp/insights/funnel%n", port);
    }

    private static EventLog eventLogFromEnvironment(Path localEventLogDirectory) throws IOException {
        String mode = System.getenv().getOrDefault("FUNNEL_PROOF_EVENT_LOG", "local");
        return switch (mode) {
            case "local" -> new LocalEventLog(localEventLogDirectory);
            case "kafka" -> KafkaEventLog.forBootstrapServers(
                    System.getenv().getOrDefault("FUNNEL_PROOF_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092")
            );
            default -> throw new IllegalArgumentException("FUNNEL_PROOF_EVENT_LOG must be local or kafka");
        };
    }

    private static void registerShutdownHook(EventLog eventLog) {
        if (!(eventLog instanceof AutoCloseable closeable)) return;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Shutdown must not emit payloads or mask the process exit.
            }
        }, "funnel-proof-event-log-close"));
    }

    static HttpServer createServer(
            int port,
            EventValidator validator,
            EventStore store,
            EventIngestionService ingestionService,
            WorkspaceKeyResolver workspaceKeys
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/fp/collect", exchange -> handleCollect(exchange, validator, ingestionService, workspaceKeys));
        server.createContext("/fp/insights/funnel", exchange -> handleFunnelInsights(exchange, store, workspaceKeys));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        return server;
    }

    private static void handleCollect(
            HttpExchange exchange,
            EventValidator validator,
            EventIngestionService ingestionService,
            WorkspaceKeyResolver workspaceKeys
    ) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, Map.of("accepted", false, "reason", "method_not_allowed"));
            return;
        }
        Optional<String> workspaceId = authorize(exchange, workspaceKeys);
        if (workspaceId.isEmpty()) return;

        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            respond(exchange, 413, Map.of("accepted", false, "reason", "payload_too_large"));
            return;
        }

        try {
            JsonNode candidate = MAPPER.readTree(body);
            ValidationResult result = validator.validate(candidate, Instant.now());
            if (!result.accepted()) {
                respond(exchange, 422, Map.of("accepted", false, "reason", result.reasonCode()));
                return;
            }
            EventLogAppendResult appendResult = ingestionService.ingest(workspaceId.get(), result.event());
            respond(exchange, 202, Map.of(
                    "accepted", true,
                    "event_id", result.event().path("event_id").asText(),
                    "duplicate", !appendResult.persisted()
            ));
        } catch (JsonProcessingException exception) {
            respond(exchange, 400, Map.of("accepted", false, "reason", "invalid_json"));
        } catch (IOException exception) {
            respond(exchange, 503, Map.of("accepted", false, "reason", "storage_unavailable"));
        }
    }

    private static void handleFunnelInsights(
            HttpExchange exchange,
            EventStore store,
            WorkspaceKeyResolver workspaceKeys
    ) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, Map.of("accepted", false, "reason", "method_not_allowed"));
            return;
        }
        Optional<String> workspaceId = authorize(exchange, workspaceKeys);
        if (workspaceId.isEmpty()) return;

        try {
            var report = new FunnelReportService().buildReport(store.snapshot(workspaceId.get()), Instant.now());
            respond(exchange, 200, report);
        } catch (IOException exception) {
            respond(exchange, 503, Map.of("accepted", false, "reason", "storage_unavailable"));
        }
    }

    private static Optional<String> authorize(HttpExchange exchange, WorkspaceKeyResolver workspaceKeys) throws IOException {
        String workspaceKey = exchange.getRequestHeaders().getFirst("x-funnel-proof-workspace-key");
        if (workspaceKey == null || workspaceKey.length() < 8) {
            respond(exchange, 401, Map.of("accepted", false, "reason", "invalid_workspace_key"));
            return Optional.empty();
        }
        Optional<String> workspaceId = workspaceKeys.resolve(workspaceKey);
        if (workspaceId.isEmpty()) {
            respond(exchange, 401, Map.of("accepted", false, "reason", "invalid_workspace_key"));
        }
        return workspaceId;
    }

    private static void respond(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        respond(exchange, status, (Object) body);
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] response = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("content-type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
