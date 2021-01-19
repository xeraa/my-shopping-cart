package com.mycompany.ecommerce;

import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class FrontendMonitor {

    final static Random RANDOM = new Random();

    final static int SLEEP_MAX_DURATION_MILLIS = 250;

    Tracer tracer;

    List<Product> products = Arrays.asList(
            new Product(1L, "TV Set", 300.00),
            new Product(2L, "Game Console", 200.00),
            new Product(3L, "Sofa", 100.00),
            new Product(4L, "Icecream", 5.00),
            new Product(5L, "Beer", 3.00),
            new Product(6L, "Phone", 500.00),
            new Product(7L, "Watch", 30.00),
            new Product(8L, "USB Cable", 4.00)
    );

    public FrontendMonitor(Tracer tracer) {
        this.tracer = tracer;
    }

    public void post(String url) throws InterruptedException {
        for (int i = 0; i < 100_000 /*100_000*/; i++) {
            int productIdx = RANDOM.nextInt(this.products.size());
            int quantity = 1 + RANDOM.nextInt(2);
            try {
                Product product = this.products.get(productIdx);
                placeOrder(url, quantity, product);
            } catch (Exception e) {
                StressTestUtils.incrementProgressBarFailure();
                System.err.println(e.toString());
                // e.printStackTrace();
            } finally {
                Thread.sleep(RANDOM.nextInt(SLEEP_MAX_DURATION_MILLIS));
            }
        }

    }

    public void placeOrder(String url, int quantity, Product product) throws IOException {
        Span placeOrderSpan = tracer.buildSpan("order_place").start();
        try (Scope scope = tracer.scopeManager().activate(placeOrderSpan)) {
            getProduct(url, product);
            createOrder(url, quantity, product);
        } finally {
            placeOrderSpan.finish();
        }
    }

    public void createOrder(String url, int quantity, Product product) throws IOException {
        Span getProductSpan = tracer.buildSpan("order_create").asChildOf(tracer.activeSpan()).start();
        try (Scope scope = tracer.scopeManager().activate(getProductSpan)) {
            URL createProductUrl = new URL(url + "/api/orders");
            HttpURLConnection createOrderConnection = (HttpURLConnection) createProductUrl.openConnection();
            createOrderConnection.setRequestMethod("POST");
            createOrderConnection.addRequestProperty("Accept", "application/json");
            createOrderConnection.addRequestProperty("Content-type", "application/json");

            Tags.SPAN_KIND.set(tracer.activeSpan(), Tags.SPAN_KIND_CLIENT);
            Tags.HTTP_METHOD.set(tracer.activeSpan(), "GET");
            Tags.HTTP_URL.set(tracer.activeSpan(), url.toString());
            tracer.inject(tracer.activeSpan().context(), Format.Builtin.HTTP_HEADERS, new HttpUrlConnectionCarrier(createOrderConnection));

            createOrderConnection.setDoOutput(true);
            String createOrderJsonPayload = product.toJson(quantity);
            try (OutputStream os = createOrderConnection.getOutputStream()) {
                byte[] createOrderJsonPayloadAsBytes = createOrderJsonPayload.getBytes("utf-8");
                os.write(createOrderJsonPayloadAsBytes, 0, createOrderJsonPayloadAsBytes.length);
            }

            int statusCode = createOrderConnection.getResponseCode();
            if (statusCode == HttpURLConnection.HTTP_CREATED) {
                StressTestUtils.incrementProgressBarSuccess();
                InputStream responseStream = createOrderConnection.getInputStream();
                InjectorUtils.toString(responseStream, "utf-8");
                responseStream.close();
            } else {
                StressTestUtils.incrementProgressBarFailure();
            }
        } finally {
            getProductSpan.finish();
        }
    }

    public void getProduct(String url, Product product) throws IOException {
        Span getProductSpan = tracer.buildSpan("getProduct").asChildOf(tracer.activeSpan()).start();
        try (Scope scope = tracer.scopeManager().activate(getProductSpan)) {
            URL getProductUrl = new URL(url + "/api/products/" + product.id);

            HttpURLConnection getProductConnection = (HttpURLConnection) getProductUrl.openConnection();
            getProductConnection.addRequestProperty("Accept", "application/json");

            Tags.SPAN_KIND.set(tracer.activeSpan(), Tags.SPAN_KIND_CLIENT);
            Tags.HTTP_METHOD.set(tracer.activeSpan(), "GET");
            Tags.HTTP_URL.set(tracer.activeSpan(), url.toString());
            tracer.inject(tracer.activeSpan().context(), Format.Builtin.HTTP_HEADERS, new HttpUrlConnectionCarrier(getProductConnection));

            int statusCode = getProductConnection.getResponseCode();
            if (statusCode == HttpURLConnection.HTTP_OK) {
                // success
            } else {
                // failure
            }
            InputStream responseStream = getProductConnection.getInputStream();
            InjectorUtils.toString(responseStream, "utf-8");
            responseStream.close();
        } finally {
            getProductSpan.finish();
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        Tracer tracer =  OpenTracingShim.createTracerShim();

        FrontendMonitor frontendMonitor = new FrontendMonitor(tracer);
        frontendMonitor.post("http://localhost:8080");
        tracer.close();
    }

    private static class Product {
        public Product(long id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        long id;
        String name;
        double price;

        String toJson(int quantity) {
            return "{\"productOrders\":[" +
                    "{\"product\":{\"id\":" + id + "," +
                    "\"name\":\"" + name + "\"," +
                    "\"price\":" + price + "," +
                    "\"pictureUrl\":\"http://placehold.it/200x100\"}," +
                    "\"quantity\":" + quantity + "}" +
                    "]}";
        }
    }


    public static class HttpUrlConnectionCarrier implements TextMap {
        private final HttpURLConnection connection;

        HttpUrlConnectionCarrier(HttpURLConnection connection) {
            this.connection = connection;
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            throw new UnsupportedOperationException("carrier is write-only");
        }

        @Override
        public void put(String key, String value) {
            connection.addRequestProperty(key, value);
        }
    }
}
