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
import org.example.metrics.common.aspect.extractor.JaegerHttpTracingExtractorNew;
import org.example.metrics.common.utils.TraceIdUtils;
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

/**
 * Аспект для интеграции трассировки Jaeger в методы сервисов и REST-контроллеров.
 *
 * <p>Этот аспект автоматически извлекает контекст трассировки из входящих HTTP-запросов, создаёт и управляет спанами,
 * а также логирует trace ID для дальнейшей корреляции и отладки. Поддерживает включение и отключение через feature flag
 * и интегрируется с бинами, управляемыми Spring.
 *
 * <h2>Ключевые особенности:</h2>
 * <ul>
 *     <li>Извлечение контекста родительского спана из заголовков HTTP-запроса.</li>
 *     <li>Создание новых спанов или дочерних спанов для аннотированных методов сервисов и контроллеров.</li>
 *     <li>Логирование информации трассировки, включая trace ID и ошибки, в MDC для последующего логирования.</li>
 *     <li>Может быть включен или отключен с помощью свойства {@code feature-flag.ccc-jaeger.tracing.enabled}.</li>
 * </ul>
 *
 * <h2>Использование:</h2>
 * <ul>
 *     <li>Аннотируйте свои классы с помощью {@code @Service} или {@code @RestController}, чтобы включить трассировку.</li>
 *     <li>Убедитесь, что аспект правильно настроен с {@link Tracer} и экстрактором контекста HTTP.</li>
 * </ul>
 *
 * <p>Этот аспект инициализируется как компонент Spring и использует AspectJ для перехвата методов.
 */
@Slf4j
@Aspect
@Component("customTracingAspect")
public class JaegerTracingAspect {

    /**
     * Инициализирует аспект и логирует информацию об успешной инициализации.
     *
     * <p>Этот метод вызывается автоматически после создания бина благодаря аннотации {@link PostConstruct}.
     */
    @PostConstruct
    public void init() {
        log.info("JaegerTracingAspect has been initialized");
    }

    /**
     * Флаг включения или отключения аспекта Jaeger.
     * <p>Определяется через свойство {@code feature-flag.ccc-jaeger.tracing.enabled}.
     */
    @Value("${feature-flag.ccc-jaeger.tracing.enabled}")
    private boolean tracingEnabled;

    /**
     * Экземпляр {@link Tracer} для управления созданием и завершением спанов.
     */
    private final Tracer tracer;

    /**
     * Экстрактор для извлечения контекста трассировки из HTTP-запросов.
     */
    private final JaegerHttpTracingExtractorNew httpTracingExtractor;

    /**
     * Конструктор для инициализации аспекта трассировки Jaeger.
     *
     * @param tracer                  экземпляр {@link Tracer} для управления трассировкой.
     * @param httpTracingExtractorNew экстрактор {@link JaegerHttpTracingExtractorNew}
     *                                для извлечения контекста из HTTP-запросов.
     */
    public JaegerTracingAspect(Tracer tracer, @Qualifier("httpTracingExtractor") JaegerHttpTracingExtractorNew httpTracingExtractorNew) {
        this.tracer = tracer;
        this.httpTracingExtractor = httpTracingExtractorNew;
    }

    /**
     * Поинткат для методов внутри бинов, аннотированных как @Service.
     */
    @Pointcut("@within(org.springframework.stereotype.Service)")
    private void allServiceMethods() {
    }

    /**
     * Поинткат для методов внутри бинов, аннотированных как @RestController.
     */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    private void allRestControllers() {
    }

    /**
     * Основной метод трассировки, который оборачивает вызовы сервисных и контроллерных методов.
     *
     * <p>Создаёт новый спан или дочерний спан в зависимости от наличия контекста трассировки в запросе.
     * Активирует спан, логирует начало и завершение метода, а также обрабатывает ошибки.
     *
     * @param pjp объект {@link ProceedingJoinPoint}, предоставляющий доступ к оборачиваемому методу.
     * @return результат выполнения оборачиваемого метода.
     * @throws Throwable если оборачиваемый метод выбрасывает исключение.
     */
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
        /*
          Переменная scope необходима для активации и деактивации Span, несмотря на то, что она не используется
          явно в коде. Она управляет временем жизни Span и автоматически закрывает его по завершению метода.
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

    /**
     * Логирует информацию о заголовке запроса {@code uber-trace-id}.
     *
     * <p>Если заголовок {@code uber-trace-id} присутствует, также проверяет и логирует его формат.
     * Этот метод предназначен для диагностики заголовков трассировки, которые передаются в HTTP-запросах.
     *
     * @param request объект {@link HttpServletRequest}, содержащий заголовки запроса.
     */
    private void logRelevantRequestHeaders(HttpServletRequest request) {
        // Логируем uber-trace-id
        String uberTraceId = request.getHeader("uber-trace-id");
        log.info("Header: uber-trace-id = {}", uberTraceId != null ? uberTraceId : "uber-trace-id not found");
        if (uberTraceId != null) {
            TraceIdUtils.logTraceIdFormat("uber-trace-id", uberTraceId);
        }

//        // Логируем jaeger-trace-id
//        String jaegerTraceId = request.getHeader("jaeger-trace-id");
//        log.info("Header: jaeger-trace-id = {}", jaegerTraceId != null ? jaegerTraceId : "jaeger-trace-id not found");
//        if (jaegerTraceId != null) {
//            logTraceIdFormat("jaeger-trace-id", jaegerTraceId);
//        }
    }

}