package org.example.metrics.common.aspect.jaeger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class TraceIdUtils {

    /**
     * Проверяет формат trace ID и логирует его содержимое.
     *
     * <p>Trace ID должен состоять из четырёх частей, разделённых двоеточием:
     * <ul>
     *     <li>{@code traceId} - уникальный идентификатор трассировки</li>
     *     <li>{@code spanId} - идентификатор текущего спана</li>
     *     <li>{@code parentSpanId} - идентификатор родительского спана</li>
     *     <li>{@code flags} - флаги, указывающие состояние трассировки</li>
     * </ul>
     * Если формат trace ID неверный или заголовок отсутствует, это логируется как предупреждение.
     *
     * @param headerName имя заголовка (например, {@code uber-trace-id}).
     * @param traceId значение trace ID из заголовка.
     */
    static void logTraceIdFormat(String headerName, String traceId) {
        if (traceId != null) {
            String[] parts = traceId.split(":");
            if (parts.length == 4) {
                log.info("{} format valid: traceId={}, spanId={}, parentSpanId={}, flags={}", headerName, parts[0], parts[1], parts[2], parts[3]);
            } else {
                log.warn("{} format invalid: {}", headerName, traceId);
            }
        } else {
            log.warn("{} not found in headers", headerName);
        }
    }
}