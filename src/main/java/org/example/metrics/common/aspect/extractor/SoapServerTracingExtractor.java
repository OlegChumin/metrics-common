package org.example.metrics.common.aspect.extractor;


import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.http.HttpComponentsConnection;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
public class SoapServerTracingExtractor {

    private final Tracer tracer;

    public SoapServerTracingExtractor(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Извлекает `SpanContext` из заголовков входящего SOAP-запроса.
     *
     * @param messageContext контекст SOAP-запроса.
     * @return извлечённый `SpanContext` или null, если заголовки трассировки отсутствуют.
     */
    public SpanContext extract(MessageContext messageContext) {
        if (messageContext == null) {
            log.warn("MessageContext is null");
            return null;
        }

        // Достаём HTTP-соединение из контекста
        HttpComponentsConnection connection = (HttpComponentsConnection) messageContext.getProperty(HttpComponentsConnection.class.getName());
        if (connection == null) {
            log.warn("HttpComponentsConnection not found in MessageContext");
            return null;
        }

        Map<String, String> headers = new HashMap<>();

        try {
            // Извлекаем все заголовки HTTP-запроса
            Iterator<String> headerNames = connection.getResponseHeaderNames(); // Получаем итератор имён заголовков
            while (headerNames.hasNext()) {
                String headerName = headerNames.next();
                Iterator<String> headerValues = connection.getResponseHeaders(headerName); // Получаем значения для заголовка
                StringBuilder headerValueBuilder = new StringBuilder();

                // Соединяем все значения заголовка через запятую
                while (headerValues.hasNext()) {
                    if (headerValueBuilder.length() > 0) {
                        headerValueBuilder.append(",");
                    }
                    headerValueBuilder.append(headerValues.next());
                }

                // Добавляем заголовок в карту
                headers.put(headerName, headerValueBuilder.toString());
                log.debug("Extracted header: {} -> {}", headerName, headerValueBuilder);
            }
        } catch (Exception e) {
            log.error("Error extracting headers from SOAP request: {}", e.getMessage(), e);
            return null;
        }

        // Извлекаем SpanContext из заголовков
        SpanContext spanContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(headers));
        if (spanContext != null) {
            log.info("Successfully extracted SpanContext: traceId={}, spanId={}", spanContext.toTraceId(), spanContext.toSpanId());
        } else {
            log.warn("No SpanContext found in SOAP request headers");
        }

        return spanContext;
    }
}
