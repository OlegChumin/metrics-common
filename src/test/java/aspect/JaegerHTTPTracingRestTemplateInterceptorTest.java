package aspect;

import io.opentracing.Scope;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.example.metrics.common.aspect.jaeger.JaegerHTTPTracingRestTemplateInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


class JaegerHTTPTracingRestTemplateInterceptorTest {

    private static final Logger log = LoggerFactory.getLogger(JaegerHTTPTracingRestTemplateInterceptorTest.class);
    private MockTracer tracer;
    private JaegerHTTPTracingRestTemplateInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tracer = new MockTracer();
        interceptor = new JaegerHTTPTracingRestTemplateInterceptor(tracer);
    }

    /**
     * Тест проверяет добавление заголовка "uber-trace-id" в HTTP-запрос.
     *
     * <p>Тестируемый сценарий:</p>
     * 1. Создается фиктивный (mock) спан с использованием `MockTracer` для имитации активной трассировки.
     * 2. С помощью мока эмулируется HTTP-запрос (`HttpRequest`), выполнение запроса (`ClientHttpRequestExecution`) и HTTP-ответ (`ClientHttpResponse`).
     * 3. Генерируется ожидаемый заголовок "uber-trace-id" на основе информации из спана.
     * 4. Заголовок добавляется в запрос, и вызывается метод `intercept` тестируемого класса `JaegerHTTPTracingRestTemplateInterceptor`.
     * 5. Выполняются проверки:
     *    - Заголовок "uber-trace-id" присутствует в HTTP-запросе.
     *    - Значение заголовка соответствует ожидаемому формату.
     *    - Выполнение запроса через `ClientHttpRequestExecution` действительно происходит.
     *
     * <p>Цель:</p>
     * Убедиться, что метод `intercept` корректно добавляет заголовок "uber-trace-id"
     * на основе текущего активного спана в контексте трассировки.
     *
     * @throws IOException если происходит ошибка во время выполнения HTTP-запроса
     */
    @Test
    void testInjectTraceHeadersUberTraceId() throws IOException {
        // Создаем MockSpan (фиктивный спан) для имитации реальной трассировки
        MockSpan span = tracer.buildSpan("test-span").start();

        // Активируем созданный спан, чтобы он стал текущим в контексте
        try (Scope scope = tracer.scopeManager().activate(span)) {
            // Создаем моки для HttpRequest, ClientHttpRequestExecution и ClientHttpResponse
            HttpRequest request = mock(HttpRequest.class); // Имитация HTTP-запроса
            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class); // Имитация выполнения HTTP-запроса
            ClientHttpResponse response = mock(ClientHttpResponse.class); // Имитация HTTP-ответа

            // Создаем экземпляр HttpHeaders, который будет содержать заголовки запроса
            HttpHeaders headers = new HttpHeaders();

            // Настраиваем поведение мока: возвращаем созданные заголовки, метод и URI
            when(request.getHeaders()).thenReturn(headers); // Возвращаем заголовки для мока запроса
            when(request.getMethod()).thenReturn(HttpMethod.GET); // Устанавливаем HTTP-метод (GET)
            when(request.getURI()).thenReturn(URI.create("http://localhost/test")); // Устанавливаем URI
            when(execution.execute(request, new byte[0])).thenReturn(response); // Указываем, что выполнение запроса возвращает фиктивный ответ

            // Генерируем ожидаемый заголовок uber-trace-id вручную
            String uberTraceId = String.format("%s:%s:%s:%s",
                    span.context().toTraceId(), // Идентификатор трассы
                    span.context().toSpanId(), // Идентификатор текущего спана
                    "0", // Идентификатор родительского спана (отсутствует)
                    "1"  // Флаг (например, "1" для активной трассировки)
            );

            // Добавляем ожидаемый заголовок в HttpHeaders
            headers.add("uber-trace-id", uberTraceId);

            // Запускаем метод intercept (основной метод для тестирования)
            interceptor.intercept(request, new byte[0], execution);

            // Проверяем, что заголовок 'uber-trace-id' добавлен в HttpHeaders
            assertTrue(headers.containsKey("uber-trace-id"), "Header 'uber-trace-id' should be present");

            // Извлекаем значение заголовка 'uber-trace-id' из HttpHeaders
            String actualUberTraceId = headers.getFirst("uber-trace-id");

            // Проверяем, что значение заголовка не равно null
            assertNotNull(actualUberTraceId, "Header 'uber-trace-id' should not be null");

            // Проверяем, что значение заголовка совпадает с ожидаемым
            assertEquals(uberTraceId, actualUberTraceId, "The 'uber-trace-id' should match the manually added value");

            // Убеждаемся, что выполнение запроса через execution произошло
            verify(execution).execute(request, new byte[0]);
        } finally {
            // Завершаем спан (закрываем трассировку)
            span.finish();
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

    @Disabled
    @Test
    void testInvalidJaegerJaegerTraceId() throws IOException {
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

    @Disabled
    @Test
    void testInvalidJaegerUberTraceId() throws IOException {
        MockSpan span = tracer.buildSpan("test-span").start();
        try (Scope scope = tracer.scopeManager().activate(span)) {
            span.setBaggageItem("uber-trace-id", "invalid-trace-id");

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
            assertTrue(headers.containsKey("uber-trace-id"), "Header 'uber-trace-id' should be present");
            String uberTraceId = headers.getFirst("uber-trace-id");
            assertEquals("invalid-trace-id", uberTraceId, "The 'uber-trace-id' should match the invalid trace id");

            // Проверяем, что запрос был выполнен
            verify(execution).execute(request, new byte[0]);
        } finally {
            span.finish();
        }
    }

}
