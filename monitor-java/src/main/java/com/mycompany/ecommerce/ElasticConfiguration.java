package com.mycompany.ecommerce;

import co.elastic.apm.opentracing.ElasticApmTracer;
import co.elastic.apm.attach.ElasticApmAttacher;
import io.opentracing.Tracer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author <a href="mailto:cyrille@cyrilleleclerc.com">Cyrille Le Clerc</a>
 */
public class ElasticConfiguration implements Closeable {

    private ElasticApmTracer tracer = new ElasticApmTracer();

    public void postConstruct() throws IOException {
        String serviceVersion = "1.0.0-SNAPSHOT";
        String serviceName = "com-shoppingcart_monitor";
        String applicationPackages = getClass().getPackage().getName();

        Map<String, String> configuration = new HashMap<>();
        // https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-service-name
        configuration.put("service_name", serviceName);
        // https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-service-version
        configuration.put("service_version", serviceVersion);
        // https://www.elastic.co/guide/en/apm/agent/java/current/config-stacktrace.html#config-application-packages
        configuration.put("application_packages", applicationPackages);
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("elasticapm.properties");
        if (in != null) {
            Properties properties = new Properties();
            properties.load(in);
            for (final String name : properties.stringPropertyNames())
                configuration.put(name, properties.getProperty(name));
        }

        configuration.put("disable_instrumentations", "experimental, urlconnection");

        // https://www.elastic.co/guide/en/apm/agent/java/current/config-logging.html#config-enable-log-correlation
        configuration.put("enable_log_correlation", "true");
        // warning may contain secret `secret_token`
        System.out.println("Load ElasticAPM with configuration " + configuration);
        ElasticApmAttacher.attach(configuration);
    }

    @Override
    public void close() throws IOException {
        tracer.close();
    }

    public Tracer getTracer() {
        return tracer;
    }

}