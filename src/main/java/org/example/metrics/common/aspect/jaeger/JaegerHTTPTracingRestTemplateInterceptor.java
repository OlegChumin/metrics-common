package org.example.metrics.common.aspect.jaeger;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * TracingRestTemplateInterceptor не предоставляет возможности для изменения имени заголовков "из коробки"
 */
@Slf4j
public class JaegerHTTPTracingRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final Tracer tracer;

    public JaegerHTTPTracingRestTemplateInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        log.info("Starting HTTP request: {} {}", request.getMethod(), request.getURI());

        Span activeSpan = tracer.activeSpan();
        if (activeSpan != null) {
            tracer.inject(activeSpan.context(), Format.Builtin.HTTP_HEADERS, new TextMap() {
                @Override
                public void put(String key, String value) {
                    request.getHeaders().add(key, value); // Просто добавляем ключ и значение без изменений
                    log.info("Injected header: {} -> {}", key, value); // Логируем инжектированные заголовки
                }
                @Override
                public Iterator<Map.Entry<String, String>> iterator() {
                    throw new UnsupportedOperationException("iterator should never be used with TextMapInjectAdapter");
                }
            });
            log.info("Active span found, injecting trace context");
        } else {
            log.warn("No active span found. Cannot inject trace context.");
        }

        // Логируем форматы uber-trace-id и jaeger-trace-id
        logTraceIdFormat("uber-trace-id", request.getHeaders().getFirst("uber-trace-id"));
        logTraceIdFormat("jaeger-trace-id", request.getHeaders().getFirst("jaeger-trace-id"));

        // Выполняем запрос
        return execution.execute(request, body);
    }

    // Универсальный метод для логирования формата trace ID
    private void logTraceIdFormat(String headerName, String traceId) {
        if (traceId != null) {
            String[] parts = traceId.split(":");
            if (parts.length == 4) {
                log.info("{} format valid: traceId={}, spanId={}, parentSpanId={}, flags={}",
                        headerName, parts[0], parts[1], parts[2], parts[3]);
            } else {
                log.warn("{} format invalid: {}", headerName, traceId);
            }
        } else {
            log.warn("{} not found in headers", headerName);
        }
    }


}