package com.mycompany.ecommerce;

import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

@Configuration
public class OpenTracingConfiguration {
    protected final Logger logger = LoggerFactory.getLogger(OpenTracingConfiguration.class);

    private Tracer tracer = OpenTracingShim.createTracerShim();

    @PreDestroy
    public void preDestroy() {
        tracer.close();
    }

    @Bean
    public Tracer getTracer() {
        return tracer;
    }
}
