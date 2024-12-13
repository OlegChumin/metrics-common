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

/**
 * {@code RestTemplateTracingPostProcessor} - это реализация {@link BeanPostProcessor},
 * которая автоматически добавляет интерцептор {@link JaegerHTTPTracingRestTemplateInterceptor}
 * для всех бинов {@link RestTemplate} в приложении.
 *
 * <p>Цель класса - интеграция с Jaeger Tracing для обеспечения распределенного трассирования
 * HTTP-запросов, выполняемых с помощью {@link RestTemplate}.
 *
 * <p><strong>Особенности:</strong>
 * <ul>
 *     <li>Удаляет дублирующиеся экземпляры интерцептора, если они уже присутствуют.</li>
 *     <li>Добавляет интерцептор в конец списка существующих.</li>
 *     <li>Логирует факт добавления интерцептора для каждого обработанного {@link RestTemplate}.</li>
 * </ul>
 *
 * <p>Этот класс аннотирован как {@link Component}, чтобы автоматически обнаруживаться
 * и регистрироваться в контексте Spring.
 *
 * @author [Олег Чумин]
 * @version 1.0
 * @since [Текущая Дата или Версия Приложения]
 */

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
