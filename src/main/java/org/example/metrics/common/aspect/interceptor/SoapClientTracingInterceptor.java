package org.example.metrics.common.aspect.interceptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.http.HttpComponentsConnection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SoapClientTracingInterceptor implements ClientInterceptor {

    private final Tracer tracer;

    public SoapClientTracingInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean handleRequest(MessageContext messageContext) {
        Span activeSpan = tracer.activeSpan();
        if (activeSpan != null) {
            log.info("Injecting trace context into SOAP request headers");

            HttpComponentsConnection connection = (HttpComponentsConnection) messageContext.getProperty(HttpComponentsConnection.class.getName());
            if (connection != null) {
                Map<String, String> headers = new HashMap<>();

                // Инъектируем контекст текущего Span в заголовки
                tracer.inject(activeSpan.context(), Format.Builtin.HTTP_HEADERS, new TextMapAdapter(headers));

                // Добавляем заголовки в HTTP-запрос
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    try {
                        connection.addRequestHeader(key, value);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                log.warn("HttpComponentsConnection not found in MessageContext");
            }
        } else {
            log.warn("No active span found for SOAP request");
        }

        return true; // Продолжаем выполнение запроса
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) {
        // Завершаем активный span при успешном ответе
        Span activeSpan = tracer.activeSpan();
        if (activeSpan != null) {
            log.info("SOAP response received successfully");
            activeSpan.log("SOAP response received successfully");
        }
        return true; // Продолжаем выполнение
    }

    @Override
    public boolean handleFault(MessageContext messageContext) {
        // Логируем ошибку в span, если произошёл SOAP Fault
        Span activeSpan = tracer.activeSpan();
        if (activeSpan != null) {
            log.error("SOAP Fault received");
            activeSpan.setTag("error", true);
            activeSpan.log("SOAP Fault received");
        }
        return true; // Продолжаем выполнение
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Exception ex) {
        // Завершаем активный span после обработки запроса
        Span activeSpan = tracer.activeSpan();
        if (activeSpan != null) {
            if (ex != null) {
                activeSpan.setTag("error", true);
                activeSpan.log(Map.of(
                    "event", "error",
                    "error.object", ex
                ));
                log.error("SOAP request failed: {}", ex.getMessage());
            }
            activeSpan.finish();
        }
    }
}
