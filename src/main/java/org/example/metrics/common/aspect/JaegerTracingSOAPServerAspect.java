package org.example.metrics.common.aspect;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.metrics.common.aspect.extractor.SoapServerTracingExtractor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;

import java.util.Map;

@Slf4j
@Aspect
@Component
public class JaegerTracingSOAPServerAspect {

    @Value("${feature-flag.ccc-jaeger.tracing.enabled}")
    private boolean tracingEnabled;

    private final Tracer tracer;
    private final SoapServerTracingExtractor extractor;

    public JaegerTracingSOAPServerAspect(Tracer tracer, @Qualifier("httpSOAPTracingExtractor") SoapServerTracingExtractor extractor) {
        this.tracer = tracer;
        this.extractor = extractor;
    }

    /**
     * Пойнткат для всех методов в классах, аннотированных как @Endpoint.
     */
    @Pointcut("@within(org.springframework.ws.server.endpoint.annotation.Endpoint)")
    private void allSoapEndpoints() {
    }

    /**
     * Перехват всех методов в SOAP Endpoint.
     *
     * @param pjp Точка выполнения.
     * @return Результат выполнения перехваченного метода.
     * @throws Throwable Исключения перехваченного метода.
     */
    @Around("allSoapEndpoints()")
    public Object traceSoapEndpoint(ProceedingJoinPoint pjp) throws Throwable {
        if (!tracingEnabled) {
            return pjp.proceed();
        }

        log.info("Tracing SOAP server-side request");

        // Извлекаем MessageContext из аргументов вызова
        MessageContext messageContext = findMessageContext(pjp.getArgs());
        if (messageContext == null) {
            log.warn("No MessageContext found in method arguments, skipping tracing");
            return pjp.proceed();
        }

        // Извлекаем SpanContext из заголовков запроса
        SpanContext parentContext = extractor.extract(messageContext);

        // Создаём новый спан
        Span span = tracer.buildSpan(pjp.getSignature().getName())
                .asChildOf(parentContext) // Продолжаем цепочку трассировки
                .start();

        try (Scope scope = tracer.scopeManager().activate(span)) {
            log.info("SOAP server method started: {}", pjp.getSignature());
            Object result = pjp.proceed();
            log.info("SOAP server method finished: {}", pjp.getSignature());
            return result;
        } catch (Throwable ex) {
            span.setTag("error", true);
            span.log(Map.of("event", "error", "error.object", ex));
            log.error("SOAP server method error: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            span.finish();
        }
    }

    /**
     * Ищет MessageContext среди аргументов вызова метода.
     *
     * @param args Аргументы вызова.
     * @return MessageContext, если найден.
     */
    private MessageContext findMessageContext(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof MessageContext) {
                return (MessageContext) arg;
            }
        }
        return null;
    }
}
