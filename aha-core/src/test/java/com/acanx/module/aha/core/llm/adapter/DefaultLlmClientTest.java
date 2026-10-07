package com.acanx.module.aha.core.llm.adapter;

import com.acanx.module.aha.common.exception.LlmException;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.RateLimitConfig;
import com.acanx.module.aha.core.llm.openai.OpenAiAdapter;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import com.acanx.module.aha.core.llm.registry.LlmAdapterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DefaultLlmClient} 集成测试（基于自包含迷你 HTTP 服务器）。
 *
 * <p>不使用 {@code com.sun.net.httpserver}，避免测试编译期引入
 * {@code jdk.httpserver} 模块读取。</p>
 *
 * @since 0.1.0
 */
class DefaultLlmClientTest {

    private static final String CHAT_RESPONSE = """
            {"id":"chatcmpl-1","object":"chat.completion","model":"gpt-4o","choices":[\
            {"index":0,"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}],\
            "usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}""";

    private static final String STREAM_BODY = """
            data: {"id":"1","model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"He"}}]}

            data: {"id":"1","model":"gpt-4o","choices":[{"index":0,"delta":{"content":"llo"}}]}

            data: {"id":"1","model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """;

    /**
     * 请求响应器。
     */
    @FunctionalInterface
    private interface Responder {
        void respond(OutputStream out, int call) throws IOException;
    }

    private MiniHttpServer server;
    private DefaultLlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MiniHttpServer();
        String url = "http://127.0.0.1:" + server.port() + "/v1/chat/completions";
        LlmAdapterRegistry registry = new LlmAdapterRegistry();
        registry.register(new OpenAiAdapter());
        ProviderConfig provider = new ProviderConfig(OpenAiAdapter.PROVIDER_ID, url, "test-key",
                "gpt-4o", 10, 1, new RateLimitConfig(0, 0), Map.of());
        client = new DefaultLlmClient(registry,
                new LlmConfig("OpenAI", Map.of("OpenAI", provider), null, null));
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    @Test
    void chatSendsRequestAndParsesResponse() {
        ChatResponse response = client.chat(request()).join();

        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().get(0).message().content()).isEqualTo("Hello!");
        assertThat(response.usage().totalTokens()).isEqualTo(5);
        assertThat(server.bodies()).hasSize(1);
        assertThat(server.bodies().get(0)).contains("\"model\":\"gpt-4o\"").contains("\"role\":\"user\"");
    }

    @Test
    void chatRetriesOnServerError() {
        server.responder((out, call) -> {
            if (call == 1) {
                MiniHttpServer.respond(out, 500, "application/json", "{\"error\":\"boom\"}");
            } else {
                MiniHttpServer.respond(out, 200, "application/json", CHAT_RESPONSE);
            }
        });

        ChatResponse response = client.chat(request()).join();

        assertThat(server.callCount()).isEqualTo(2);
        assertThat(response.choices().get(0).message().content()).isEqualTo("Hello!");
    }

    @Test
    void chatFailsOnClientError() {
        server.responder((out, call) ->
                MiniHttpServer.respond(out, 400, "application/json", "{\"error\":\"bad\"}"));

        assertThatThrownBy(() -> client.chat(request()).join())
                .hasRootCauseInstanceOf(LlmException.class);
        assertThat(server.callCount()).isEqualTo(1);
    }

    @Test
    void streamChatParsesSseEvents() {
        server.responder((out, call) ->
                MiniHttpServer.respond(out, 200, "text/event-stream", STREAM_BODY));
        List<StreamEvent> events = new ArrayList<>();

        client.streamChat(request(), events::add, new CancellationToken());

        assertThat(events).isNotEmpty();
        assertThat(events.stream()
                .filter(e -> e.type() == StreamEventType.CONTENT_DELTA)
                .map(StreamEvent::contentDelta)
                .toList()).containsExactly("He", "llo");
        assertThat(events.stream().anyMatch(e -> e.type() == StreamEventType.DONE
                && "stop".equals(e.finishReason()))).isTrue();
    }

    @Test
    void streamChatStopsWhenCancelled() {
        server.responder((out, call) ->
                MiniHttpServer.respond(out, 200, "text/event-stream", STREAM_BODY));
        CancellationToken token = new CancellationToken();
        token.cancel();
        List<StreamEvent> events = new ArrayList<>();

        client.streamChat(request(), events::add, token);

        assertThat(events).isEmpty();
    }

    @Test
    void resolvesProviderFromRequestExtension() {
        ChatResponse response = client.chat(new ChatRequest("m",
                List.of(ChatMessage.text(Role.USER, "hi")), List.of(), 0.0, 0, false,
                Map.of("Provider", "OpenAI"))).join();

        assertThat(response.choices()).hasSize(1);
    }

    @Test
    void reportsMissingProvider() {
        ProviderConfig provider = new ProviderConfig(OpenAiAdapter.PROVIDER_ID,
                "http://127.0.0.1:1/v1", "k", "m", 1, 0, new RateLimitConfig(0, 0), Map.of());
        DefaultLlmClient other = new DefaultLlmClient(new LlmAdapterRegistry(),
                new LlmConfig("Unknown", Map.of("Other", provider), null, null));
        try {
            assertThatThrownBy(() -> other.chat(request()).join())
                    .hasRootCauseInstanceOf(LlmException.class);
        } finally {
            other.close();
        }
    }

    private static ChatRequest request() {
        return new ChatRequest("gpt-4o",
                List.of(ChatMessage.text(Role.USER, "hi")),
                List.of(), 0.0, 0, false, Map.of());
    }

    /**
     * 极简 HTTP/1.1 测试服务器（每次连接处理一个请求后关闭）。
     *
     * @since 0.1.0
     */
    private static final class MiniHttpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final Thread thread;
        private final List<String> bodies = new CopyOnWriteArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile Responder responder;

        MiniHttpServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            this.thread = Thread.ofVirtual().name("mini-http").start(this::serve);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        List<String> bodies() {
            return bodies;
        }

        int callCount() {
            return calls.get();
        }

        void responder(Responder responder) {
            this.responder = responder;
        }

        @Override
        public void close() {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // 关闭失败可忽略
            }
            thread.interrupt();
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            String requestLine = readLine(in);
            if (requestLine == null) {
                return;
            }
            int contentLength = 0;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "content-length:", 0, 15)) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            byte[] body = in.readNBytes(contentLength);
            bodies.add(new String(body, StandardCharsets.UTF_8));
            int call = calls.incrementAndGet();

            Responder current = responder;
            if (current == null) {
                respond(out, 200, "application/json", CHAT_RESPONSE);
            } else {
                current.respond(out, call);
            }
            out.flush();
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int c;
            boolean any = false;
            while ((c = in.read()) != -1) {
                any = true;
                if (c == '\n') {
                    break;
                }
                if (c != '\r') {
                    buffer.write(c);
                }
            }
            if (!any && buffer.size() == 0) {
                return null;
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        static void respond(OutputStream out, int status, String contentType, String body)
                throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.flush();
        }

        private static String reason(int status) {
            return switch (status) {
                case 200 -> "OK";
                case 400 -> "Bad Request";
                case 500 -> "Internal Server Error";
                default -> "Status";
            };
        }
    }
}
