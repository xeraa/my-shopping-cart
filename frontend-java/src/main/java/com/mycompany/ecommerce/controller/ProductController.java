package com.mycompany.ecommerce.controller;

import com.mycompany.ecommerce.model.Product;
import com.mycompany.ecommerce.service.ProductService;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    ProductService productService;
    Tracer tracer;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping(value = {"", "/"})
    public @NotNull Iterable<Product> getProducts() {
        Span span = tracer.activeSpan().setOperationName("products_get");
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public @NotNull Product getProduct(@PathVariable long id) {
        Span span = tracer.activeSpan().setOperationName("product_get");
        span.setTag("product.id", id);
        return productService.getProduct(id);
    }

    @Autowired
    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }
}