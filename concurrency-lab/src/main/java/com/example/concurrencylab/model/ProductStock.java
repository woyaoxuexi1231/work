package com.example.concurrencylab.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "product_stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_stock_sku", columnNames = "sku")
)
public class ProductStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private long dbAvailable;

    @Column(nullable = false)
    private long dbReservedForRedis;

    @Column(nullable = false)
    private long dbSold;
}
