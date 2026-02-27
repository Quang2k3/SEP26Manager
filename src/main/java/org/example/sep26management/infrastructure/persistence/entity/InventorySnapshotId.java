package org.example.sep26management.infrastructure.persistence.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InventorySnapshotId implements Serializable {
    private Long warehouseId;
    private Long skuId;
    private Long lotIdSafe;
    private Long locationId;
}