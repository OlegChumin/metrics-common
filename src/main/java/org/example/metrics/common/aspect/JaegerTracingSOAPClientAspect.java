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
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Aspect
@Component
public class JaegerTracingSOAPClientAspect {

    @Value("${feature-flag.ccc-jaeger.tracing.enabled}")
    private boolean tracingEnabled;

    private final Tracer tracer;

    public JaegerTracingSOAPClientAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @Pointcut("(@within(org.springframework.stereotype.Service) && (within(ru.gpb.ccl..soap..*) || within(ru.gpb.ccl..soapclient..*)))")
    private void allSoapServiceAnnotatedMethodsInDynamicPackages() {
    }

    @Pointcut("execution(* org.springframework.ws.client.core.WebServiceTemplate.*(..))")
    private void allSoapClientOperations() {
    }

    @Around("allSoapClientOperations()")
    public Object traceSoapService(ProceedingJoinPoint pjp) throws Throwable {
        if (!tracingEnabled) {
            return pjp.proceed();
        }

        String methodName = pjp.getSignature().getName();

        // Извлекаем текущий активный SpanContext, если он существует
        SpanContext activeSpanContext = tracer.activeSpan() != null ? tracer.activeSpan().context() : null;

        // Создаём новый спан, который будет дочерним
        Span span = tracer.buildSpan(methodName)
                .asChildOf(activeSpanContext) // Создаём дочерний спан, если есть родитель
                .start();

        MDC.put("traceId", span.context().toTraceId()); // Добавляем Trace ID в MDC для логов

        try (Scope scope = tracer.scopeManager().activate(span)) {
            log.info("SOAP method started: {}", pjp.getSignature());
            Object result = pjp.proceed();
            log.info("SOAP method finished: {}", pjp.getSignature());
            return result;
        } catch (Throwable ex) {
            // Логируем ошибки в спан
            span.setTag("error", true);
            span.log(Map.of(
                    "event", "error",
                    "error.object", ex
            ));
            log.error("SOAP method error: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            // Завершаем спан и очищаем MDC
            span.finish();
            MDC.clear();
        }
    }
}
