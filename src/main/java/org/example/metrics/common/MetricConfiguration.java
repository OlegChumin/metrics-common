package org.example.metrics.common;

import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.example.metrics.common.aspect.JaegerHTTPTracingRestTemplateInterceptor;
import org.example.metrics.common.aspect.JaegerHttpTracingExtractorNew;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.client.RestTemplate;


import javax.annotation.PostConstruct;

@Slf4j
@Configuration
@AutoConfigureAfter(io.opentracing.contrib.java.spring.jaeger.starter.JaegerAutoConfiguration.class)
@EnableAspectJAutoProxy
public class MetricConfiguration {

    static {
        log.info("MetricConfiguration has being loaded");
    }

    @PostConstruct
    public void init() {
        log.info("MetricConfiguration has been initialized and is now active");
    }

    @Bean(name = "tracingRestTemplate")
    public RestTemplate restTemplate(Tracer tracer) {
        log.info("Initializing RestTemplate bean with JaegerHTTPTracingRestTemplateInterceptor");
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new JaegerHTTPTracingRestTemplateInterceptor(tracer));
        log.info("RestTemplate bean with tracing interceptor initialized successfully");
        return restTemplate;
    }
    @Bean(name = "httpTracingExtractor")
    public JaegerHttpTracingExtractorNew httpTracingExtractorNew(Tracer tracer) {
        log.info("Initializing JaegerHttpTracingExtractorNew bean with provided Tracer");
        JaegerHttpTracingExtractorNew extractor = new JaegerHttpTracingExtractorNew(tracer);
        log.info("JaegerHttpTracingExtractorNew bean initialized successfully");
        return extractor;
    }

}