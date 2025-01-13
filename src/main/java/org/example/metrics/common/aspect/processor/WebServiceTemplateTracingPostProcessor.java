package org.example.metrics.common.aspect.processor;

import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.example.metrics.common.aspect.interceptor.SoapClientTracingInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;


import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class WebServiceTemplateTracingPostProcessor implements BeanPostProcessor {
private final Tracer tracer;

    public WebServiceTemplateTracingPostProcessor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof WebServiceTemplate webServiceTemplate) {
            // Получаем текущие интерцепторы
            List<ClientInterceptor> interceptors = new ArrayList<>(List.of(webServiceTemplate.getInterceptors()));

            // Удаляем уже существующий экземпляр SoapClientTracingInterceptor, если он есть
            interceptors.removeIf(interceptor -> interceptor instanceof SoapClientTracingInterceptor);

            // Добавляем новый SoapClientTracingInterceptor
            interceptors.add(new SoapClientTracingInterceptor(tracer));
            // Устанавливаем обновлённый список интерцепторов
            webServiceTemplate.setInterceptors(interceptors.toArray(new ClientInterceptor[0]));

            // Логируем факт добавления интерцептора
            log.info("SoapClientTracingInterceptor added to WebServiceTemplate bean: {}", beanName);
        }
        return bean;
    }
}