package com.mycompany.ecommerce;

import co.elastic.apm.attach.ElasticApmAttacher;
import co.elastic.apm.opentracing.ElasticApmTracer;
import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
@Configuration
public class ElasticConfiguration {

    protected final Logger logger = LoggerFactory.getLogger(ElasticConfiguration.class);

    private ElasticApmTracer tracer = new ElasticApmTracer();

    @PreDestroy
    public void preDestroy() {
        tracer.close();
    }

    @Bean
    public Tracer getTracer() {
        return tracer;
    }

}