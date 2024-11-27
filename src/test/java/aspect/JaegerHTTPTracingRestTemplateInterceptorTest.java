package aspect;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.example.metrics.common.aspect.jaeger.JaegerHTTPTracingRestTemplateInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JaegerHTTPTracingRestTemplateInterceptorTest {

    private MockTracer tracer;
    private JaegerHTTPTracingRestTemplateInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tracer = new MockTracer();
        interceptor = new JaegerHTTPTracingRestTemplateInterceptor(tracer);
    }

    @Test
    void testInjectTraceHeaders() throws IOException {
        // Создаем Mock Span
        MockSpan span = tracer.buildSpan("test-span").start();
        try (Scope scope = tracer.scopeManager().activate(span)) { // Управляем контекстом через Scope
            // Мокаем HttpRequest и ClientHttpRequestExecution
            HttpRequest request = mock(HttpRequest.class);
            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
            ClientHttpResponse response = mock(ClientHttpResponse.class);

            // Устанавливаем поведение для запроса
            HttpHeaders headers = new HttpHeaders();
            when(request.getHeaders()).thenReturn(headers);
            when(request.getMethod()).thenReturn(HttpMethod.GET);
            when(request.getURI()).thenReturn(URI.create("http://localhost/test"));
            when(execution.execute(request, new byte[0])).thenReturn(response);

            // Запускаем интерцептор
            interceptor.intercept(request, new byte[0], execution);

            // Проверяем, что заголовки добавлены
            assertTrue(headers.containsKey("jaeger-trace-id"), "Header 'jaeger-trace-id' should be present");
            String jaegerTraceId = headers.getFirst("jaeger-trace-id");
            assertNotNull(jaegerTraceId, "Header 'jaeger-trace-id' should not be null");

            // Проверяем, что формат заголовка правильный
            String[] parts = jaegerTraceId.split(":");
            assertEquals(4, parts.length, "jaeger-trace-id should have 4 parts");

            // Проверяем, что запрос был выполнен
            verify(execution).execute(request, new byte[0]);
        } finally {
            span.finish(); // Убедитесь, что спан завершён
        }
    }


    @Test
    void testNoActiveSpan() throws IOException {
        // Мокаем HttpRequest и ClientHttpRequestExecution
        HttpRequest request = mock(HttpRequest.class);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);

        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("http://localhost/test"));
        when(execution.execute(request, new byte[0])).thenReturn(response);

        // Запускаем интерцептор без активного спана
        interceptor.intercept(request, new byte[0], execution);

        // Проверяем, что заголовки не добавлены
        assertFalse(headers.containsKey("jaeger-trace-id"), "Header 'jaeger-trace-id' should not be present without an active span");

        // Проверяем, что запрос был выполнен
        verify(execution).execute(request, new byte[0]);
    }

    @Test
    void testInvalidJaegerTraceId() throws IOException {
        // Создаем Mock Span с кастомным traceId
        MockSpan span = tracer.buildSpan("test-span").start();
        span.setBaggageItem("uber-trace-id", "invalid-trace-id");
        tracer.activateSpan(span);

        // Мокаем HttpRequest и ClientHttpRequestExecution
        HttpRequest request = mock(HttpRequest.class);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);

        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("http://localhost/test"));
        when(execution.execute(request, new byte[0])).thenReturn(response);

        // Запускаем интерцептор
        interceptor.intercept(request, new byte[0], execution);

        // Проверяем, что заголовок добавлен, но содержит некорректные данные
        assertTrue(headers.containsKey("jaeger-trace-id"), "Header 'jaeger-trace-id' should be present");
        String jaegerTraceId = headers.getFirst("jaeger-trace-id");
        assertEquals("invalid-trace-id", jaegerTraceId, "The 'jaeger-trace-id' should match the invalid trace id");

        // Проверяем, что запрос был выполнен
        verify(execution).execute(request, new byte[0]);
    }
}
