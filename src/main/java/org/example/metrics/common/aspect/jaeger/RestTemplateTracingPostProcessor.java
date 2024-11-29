package org.example.metrics.common.aspect.jaeger;

import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

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
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
            // Проверяем, есть ли уже трейсинг-интерсептор
            if (interceptors.stream()
                    .noneMatch(interceptor -> interceptor instanceof JaegerHTTPTracingRestTemplateInterceptor)) {
                interceptors.add(new JaegerHTTPTracingRestTemplateInterceptor(tracer));
                restTemplate.setInterceptors(interceptors); // Устанавливаем обновленный список
                log.info("JaegerHTTPTracingRestTemplateInterceptor added to RestTemplate bean: {}", beanName);
            }
        }
        return bean;
    }

}
