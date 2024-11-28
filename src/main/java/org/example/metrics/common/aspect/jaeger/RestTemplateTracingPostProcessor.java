package org.example.metrics.common.aspect.jaeger;

import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class RestTemplateTracingPostProcessor implements BeanPostProcessor {

    private final Tracer tracer;

    public RestTemplateTracingPostProcessor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

        // Проверка наличия JaegerHTTPTracingRestTemplateInterceptor
        if (bean instanceof RestTemplate restTemplate) {
            // Проверяем, есть ли уже трейсинг-интерсептор
            if (restTemplate.getInterceptors().stream()
                .noneMatch(interceptor -> interceptor instanceof JaegerHTTPTracingRestTemplateInterceptor)) {
                restTemplate.getInterceptors().add(new JaegerHTTPTracingRestTemplateInterceptor(tracer));
                log.info("JaegerHTTPTracingRestTemplateInterceptor added to RestTemplate bean: {}", beanName);
            }
        }
        return bean;
    }
}
