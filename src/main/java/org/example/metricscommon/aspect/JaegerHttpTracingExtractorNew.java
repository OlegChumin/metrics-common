package org.example.metricscommon.aspect;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

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
            headers.put(headerName, request.getHeader(headerName));
            log.info("Received header: {} -> {}", headerName, request.getHeader(headerName)); // Логируем все заголовки
        }
        SpanContext spanContext = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMap() {
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