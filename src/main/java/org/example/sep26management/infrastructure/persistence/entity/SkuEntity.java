package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "skus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkuEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sku_id")
    private Long skuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    @Column(name = "sku_code", nullable = false, unique = true, length = 100)
    private String skuCode;

    @Column(name = "sku_name", nullable = false, length = 300)
    private String skuName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "brand", length = 200)
    private String brand;

    @Column(name = "package_type", length = 100)
    private String packageType;

    @Column(name = "volume_ml", precision = 12, scale = 2)
    private BigDecimal volumeMl;

    @Column(name = "weight_g", precision = 12, scale = 2)
    private BigDecimal weightG;

    @Column(name = "barcode", unique = true, length = 100)
    private String barcode;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Column(name = "scent", length = 200)
    private String scent;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "storage_temp_min", precision = 5, scale = 2)
    private BigDecimal storageTempMin;

    @Column(name = "storage_temp_max", precision = 5, scale = 2)
    private BigDecimal storageTempMax;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null)
            active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}