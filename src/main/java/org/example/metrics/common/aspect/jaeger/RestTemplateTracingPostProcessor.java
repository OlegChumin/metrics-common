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
        if (bean instanceof RestTemplate restTemplate) {
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());

            // Удаляем уже существующий экземпляр нашего интерцептора, если он есть
            interceptors.removeIf(interceptor -> interceptor instanceof JaegerHTTPTracingRestTemplateInterceptor);

            // Добавляем наш интерцептор в конец списка
            interceptors.add(new JaegerHTTPTracingRestTemplateInterceptor(tracer));

            // Устанавливаем обновлённый список интерцепторов
            restTemplate.setInterceptors(interceptors);

            // Логируем факт добавления интерцептора
            log.info("JaegerHTTPTracingRestTemplateInterceptor added to RestTemplate bean: {}", beanName);
        }
        return bean;
    }


}
