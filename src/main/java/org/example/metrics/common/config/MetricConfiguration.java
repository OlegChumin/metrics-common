package org.example.metrics.common.config;

import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.example.metrics.common.aspect.extractor.JaegerHttpTracingExtractorNew;
import org.example.metrics.common.aspect.extractor.SoapServerTracingExtractor;
import org.example.metrics.common.aspect.interceptor.SoapClientTracingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.annotation.PostConstruct;

import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;


@Slf4j
@Configuration
@ComponentScan(basePackages = "org.example.metrics.common")
@AutoConfigureAfter(io.opentracing.contrib.java.spring.jaeger.starter.JaegerAutoConfiguration.class)
@EnableAspectJAutoProxy
public class MetricConfiguration {

    static {
        log.info("JaegerConfiguration has been loaded");
    }

    @PostConstruct
    public void init() {
        log.info("JaegerConfiguration has been initialized and is now active");
    }

    // Бин для Jaeger Http Tracing Extractor
    @Bean(name = "httpTracingExtractor")
    public JaegerHttpTracingExtractorNew httpTracingExtractorNew(Tracer tracer) {
        log.info("Initializing JaegerHttpTracingExtractorNew bean with provided Tracer");
        return new JaegerHttpTracingExtractorNew(tracer);
    }

    @Bean(name = "httpSOAPTracingExtractor")
    public SoapServerTracingExtractor soapServerTracingExtractor(Tracer tracer) {
        return new SoapServerTracingExtractor(tracer);
    }

    // Бин для SoapClientTracingInterceptor
    @Bean
    public SoapClientTracingInterceptor soapClientTracingInterceptor(Tracer tracer) {
        log.info("Initializing SoapClientTracingInterceptor bean with provided Tracer");
        return new SoapClientTracingInterceptor(tracer);
    }

    // Бин для HTTP Message Sender
    @Bean
    public HttpComponentsMessageSender httpComponentsMessageSender() {
        log.info("Initializing HttpComponentsMessageSender");
        return new HttpComponentsMessageSender();
    }

    // Бин для WebServiceTemplate
    @Bean
    public WebServiceTemplate webServiceTemplate(SoapClientTracingInterceptor tracingInterceptor) {
        log.info("Initializing WebServiceTemplate with tracing interceptor");
        WebServiceTemplate template = new WebServiceTemplate();

        // Указываем HTTP-сообщения для клиента
        template.setMessageSender(httpComponentsMessageSender());

        // Добавляем интерцептор для трассировки
        template.setInterceptors(new ClientInterceptor[]{tracingInterceptor});

        return template;
    }
}
