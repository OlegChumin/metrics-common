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
import org.example.metrics.common.aspect.jaeger.JaegerHttpTracingExtractorNew;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Aspect
@Component("customTracingAspect")
public class JaegerTracingAspect {

    @PostConstruct
    public void init() {
        log.info("JaegerTracingAspect has been initialized");
    }

    // ff флаг работы аспекта jaeger
    @Value("${feature-flag.ccc-jaeger.tracing.enabled}")
    private boolean tracingEnabled;
    private final Tracer tracer;
    private final JaegerHttpTracingExtractorNew httpTracingExtractor;

    public JaegerTracingAspect(Tracer tracer, @Qualifier("httpTracingExtractor") JaegerHttpTracingExtractorNew httpTracingExtractorNew) {
        this.tracer = tracer;
        this.httpTracingExtractor = httpTracingExtractorNew;
    }

    @Pointcut("@within(org.springframework.stereotype.Service)")
    private void allServiceMethods() {
    }

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    private void allRestControllers() {
    }

    @Around("allServiceMethods() || allRestControllers()")
    public Object traceMethod(ProceedingJoinPoint pjp) throws Throwable {
        log.debug("TracingEnabled {}", tracingEnabled);

        if (!tracingEnabled) {
            return pjp.proceed();
        }

        String methodName = pjp.getSignature().getName();
        log.debug("methodName: {}", methodName);

        // Извлечение HttpServletRequest
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = null;
        if (requestAttributes instanceof ServletRequestAttributes) {
            request = ((ServletRequestAttributes) requestAttributes).getRequest();
        }

        // Инициализация parentContext и логирование заголовков
        SpanContext parentContext = null;

        if (request != null) {
            logRelevantRequestHeaders(request);
            parentContext = httpTracingExtractor.extract(request);
            if (parentContext != null) {
                log.info("Extracted parentContext with Trace ID: {}, Span ID: {}", parentContext.toTraceId(), parentContext.toSpanId());
            } else {
                log.warn("No parentContext found in headers.");
            }
        } else {
            log.warn("HttpServletRequest not available for method: {}", methodName);
        }

        // Если контекст был извлечен, создаем дочерний спан или новый спан
        Span span;
        if (parentContext != null) {
            span = tracer.buildSpan(methodName).asChildOf(parentContext).start();
            log.info("Started child span for method: {} with Trace ID: {}, Span ID: {}", methodName, span.context().toTraceId(), span.context().toSpanId());
        } else {
            span = tracer.buildSpan(methodName).start();
            log.info("Started new span for method: {} with Trace ID: {}, Span ID: {}", methodName, span.context().toTraceId(), span.context().toSpanId());
        }

        // Установка trace-id в MDC
        //MDC.put("jaeger-trace-id", span.context().toTraceId());
        MDC.put("uber-trace-id", span.context().toTraceId());

        // Логируем начало выполнения в спан -> все последующие действия, происходящие в этом потоке
        // (например, вызовы к другим сервисам, внутренние методы и т.д.), будут ассоциированы с этим активным Span.
        span.log("Starting method execution");

        // Активируем спан с помощью Scope
        /**
         * Переменная scope необходима для активации и деактивации Span, несмотря на то, что она не используется
         * явно в коде. Она управляет временем жизни Span и автоматически закрывает его по завершению метода.
         */
        try (Scope scope = tracer.scopeManager().activate(span)) {
            return pjp.proceed();
        } // scope.close() вызывается автоматически, когда выполнение блока try завершено
        catch (Throwable throwable) {
            span.setTag("error", true);
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("event", "error");
            logMap.put("error.object", throwable);
            span.log(logMap);
            throw throwable;
        } finally {
            span.log("Method execution finished");
            span.finish(); // Завершаем спан
            log.info("Finished span for method: {} with Trace ID: {}, Span ID: {}", methodName, span.context().toTraceId(), span.context().toSpanId());
        }
    }

    // Метод для логирования заголовков запроса
    private void logRelevantRequestHeaders(HttpServletRequest request) {
        // Логируем uber-trace-id
        String uberTraceId = request.getHeader("uber-trace-id");
        log.info("Header: uber-trace-id = {}", uberTraceId != null ? uberTraceId : "uber-trace-id not found");
        if (uberTraceId != null) {
            logTraceIdFormat("uber-trace-id", uberTraceId);
        }

//        // Логируем jaeger-trace-id
//        String jaegerTraceId = request.getHeader("jaeger-trace-id");
//        log.info("Header: jaeger-trace-id = {}", jaegerTraceId != null ? jaegerTraceId : "jaeger-trace-id not found");
//        if (jaegerTraceId != null) {
//            logTraceIdFormat("jaeger-trace-id", jaegerTraceId);
//        }
    }

    // Универсальный метод для проверки формата trace ID
    private void logTraceIdFormat(String headerName, String traceId) {
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