package org.example.metrics.common.aspect.jaeger;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

import io.opentracing.propagation.TextMapAdapter;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
public class JaegerHttpTracingExtractorNew {

    private final Tracer tracer;

    public JaegerHttpTracingExtractorNew(Tracer tracer) {
        this.tracer = tracer;
    }

    public SpanContext extract(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();

        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, request.getHeader(headerName));

            // Изменяем имя заголовка на кастомный, если это uber-trace-id
//            if ("uber-trace-id".equals(headerName)) {
//                headers.put("jaeger_trace-id", headerValue);
//                log.info("Renamed header uber-trace-id -> jaeger_trace-id");
//            } else {
//                headers.put(headerName, headerValue);
//            }
            log.info("Received header: {} -> {}", headerName, request.getHeader(headerName)); // Логируем все заголовки
        }
        SpanContext spanContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(headers) {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                return headers.entrySet().iterator();
            }

            @Override
            public void put(String key, String value) {
                throw new UnsupportedOperationException("TextMapExtractAdapter is only for reading");
            }
        });

        if (spanContext != null) {
            log.info("Extracted SpanContext with Trace ID: {}, Span ID: {}", spanContext.toTraceId(), spanContext.toSpanId());
        } else {
            log.warn("No SpanContext extracted from headers.");
        }

        return spanContext;
    }
}