package com.mycompany.ecommerce.controller;

import co.elastic.apm.api.ElasticApm;
import com.mycompany.ecommerce.dto.OrderProductDto;
import com.mycompany.ecommerce.exception.ResourceNotFoundException;
import com.mycompany.ecommerce.model.Order;
import com.mycompany.ecommerce.model.OrderProduct;
import com.mycompany.ecommerce.model.OrderStatus;
import com.mycompany.ecommerce.service.OrderProductService;
import com.mycompany.ecommerce.service.OrderService;
import com.mycompany.ecommerce.service.ProductService;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    final static Random RANDOM = new Random();

    final Logger logger = LoggerFactory.getLogger(getClass());

    ProductService productService;
    OrderService orderService;
    OrderProductService orderProductService;
    RestTemplate restTemplate;
    String antiFraudServiceBaseUrl;

    Summary orderSummary = Summary.build("order", "Orders")
            .quantile(0.75, 0.05).quantile(0.95, 0.05)
            .register();
    Summary orderWithLabelsSummary = Summary.build("order_with_tags", "Orders with tags")
            .quantile(0.75, 0.05).quantile(0.95, 0.05)
            .labelNames("shipping_country", "shipping_method", "payment_method")
            .register();
    Counter orderValueCounter = Counter.build("order_value_counter", "Value of the orders in USD").register();
    Counter orderCountCounter = Counter.build("order_count_counter", "Count of orders").register();

    public OrderController(ProductService productService, OrderService orderService, OrderProductService orderProductService) {
        this.productService = productService;
        this.orderService = orderService;
        this.orderProductService = orderProductService;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public @NotNull Iterable<Order> list() {
        return this.orderService.getAllOrders();
    }


    @PostMapping
    public ResponseEntity<Order> create(@RequestBody OrderForm form, HttpServletRequest request) {
        ElasticApm.currentSpan().setName("createOrder");
        List<OrderProductDto> formDtos = form.getProductOrders();
        validateProductsExistence(formDtos);

        String customerId = "customer-" + RANDOM.nextInt(100); // TODO better demo
        ElasticApm.currentSpan().addLabel("customerId", customerId);

        double orderPrice = formDtos.stream().mapToDouble(po -> po.getQuantity() * po.getProduct().getPrice()).sum();
        ElasticApm.currentSpan().addLabel("orderPrice", orderPrice);
        String priceRange = getPriceRange(orderPrice);
        ElasticApm.currentSpan().addLabel("orderPriceRange", priceRange);

        String shippingCountryCode = getCountryCode(request.getRemoteAddr());
        ElasticApm.currentSpan().addLabel("shippingCountry", shippingCountryCode);

        String shippingMethod = randomShippingMethod();
        ElasticApm.currentSpan().addLabel("shippingMethod", shippingMethod);

        String paymentMethod = randomPaymentMethod();
        ElasticApm.currentSpan().addLabel("paymentMethod", paymentMethod);

        ResponseEntity<String> antiFraudResult;
        try {
            antiFraudResult = restTemplate.getForEntity(
                    this.antiFraudServiceBaseUrl + "fraud/checkOrder?orderPrice={q}&customerIpAddress={q}&shippingCountry={q}",
                    String.class,
                    orderPrice, request.getRemoteAddr(), shippingCountryCode);

        } catch (RestClientException e) {
            String exceptionShortDescription = e.getClass().getName();
            ElasticApm.currentSpan().addLabel("antiFraud.exception", exceptionShortDescription);
            ElasticApm.currentSpan().captureException(e);
            if (e.getCause() != null) { // capture SockerTimeoutException...
                ElasticApm.currentSpan().addLabel("antiFraud.exception.cause", e.getCause().getClass().getName());
                exceptionShortDescription += " / " + e.getCause().getClass().getName();
            }
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add("x-orderCreationFailureCause", "auti-fraud_" + exceptionShortDescription);
            logger.info("Failure createOrder({}): orderPrice: {}, fraud.exception:{}", form, orderPrice, exceptionShortDescription);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (antiFraudResult.getStatusCode() != HttpStatus.OK) {
            String exceptionShortDescription = "status-" + antiFraudResult.getStatusCode();
            ElasticApm.currentSpan().addLabel("antiFraud.exception", exceptionShortDescription);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add("x-orderCreationFailureCause", "auti-fraud_" + exceptionShortDescription);
            logger.info("Failure createOrder({}): orderPrice: {}, fraud.exception:{}", form, orderPrice, exceptionShortDescription);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (!"OK".equals(antiFraudResult.getBody())) {
            String exceptionShortDescription = "response-" + antiFraudResult.getBody();
            ElasticApm.currentSpan().addLabel("antiFraud.exception", exceptionShortDescription);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add("x-orderCreationFailureCause", "auti-fraud_" + exceptionShortDescription);
            logger.info("Failure createOrder({}): orderPrice: {}, fraud.exception:{}", form, orderPrice, exceptionShortDescription);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Order order = new Order();
        order.setStatus(OrderStatus.PAID.name());
        order = this.orderService.create(order);

        List<OrderProduct> orderProducts = new ArrayList<>();
        for (OrderProductDto dto : formDtos) {
            orderProducts.add(orderProductService.create(new OrderProduct(order, productService.getProduct(dto
                    .getProduct()
                    .getId()), dto.getQuantity())));
        }

        order.setOrderProducts(orderProducts);

        this.orderService.update(order);

        orderSummary.observe(orderPrice);
        orderWithLabelsSummary.labels(shippingCountryCode, shippingMethod, paymentMethod).observe(orderPrice);
        orderValueCounter.inc(orderPrice);
        orderCountCounter.inc();

        logger.info("SUCCESS createOrder({}): price: {}, id:{}", form, orderPrice, order.getId());

        String uri = ServletUriComponentsBuilder
                .fromCurrentServletMapping()
                .path("/orders/{id}")
                .buildAndExpand(order.getId())
                .toString();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", uri);

        return new ResponseEntity<>(order, headers, HttpStatus.CREATED);

    }

    private void validateProductsExistence(List<OrderProductDto> orderProducts) {
        List<OrderProductDto> list = orderProducts
                .stream()
                .filter(op -> Objects.isNull(productService.getProduct(op
                        .getProduct()
                        .getId())))
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(list)) {
            new ResourceNotFoundException("Product not found");
        }
    }

    public String getCountryCode(String ip) {
        String[] countries = {"US", "FR", "GB",};
        return countries[RANDOM.nextInt(countries.length)];
    }

    public String randomPaymentMethod() {
        String[] paymentMethods = {"credit_cart", "paypal"};
        return paymentMethods[RANDOM.nextInt(paymentMethods.length)];
    }

    public String randomShippingMethod() {
        String[] shippingMethods = {"standard", "express"};
        return shippingMethods[RANDOM.nextInt(shippingMethods.length)];
    }

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${antiFraudService.baseUrl}")
    public void setAntiFraudServiceBaseUrl(String antiFraudServiceBaseUrl) {
        this.antiFraudServiceBaseUrl = antiFraudServiceBaseUrl;
    }

    public String getPriceRange(double price) {
        if (price < 10) {
            return "small";
        } else if (price < 100) {
            return "medium";
        } else {
            return "large";
        }
    }

    public static class OrderForm {

        private List<OrderProductDto> productOrders;

        public List<OrderProductDto> getProductOrders() {
            return productOrders;
        }

        public void setProductOrders(List<OrderProductDto> productOrders) {
            this.productOrders = productOrders;
        }

        @Override
        public String toString() {
            return new ToStringCreator(this).append(this.productOrders).toString();
        }
    }
}