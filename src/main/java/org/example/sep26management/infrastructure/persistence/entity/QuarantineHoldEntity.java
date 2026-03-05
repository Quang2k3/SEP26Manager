package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "quarantine_holds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuarantineHoldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_id")
    private Long holdId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(name = "hold_reason", nullable = false, length = 500)
    private String holdReason;

    @Column(name = "hold_note", columnDefinition = "TEXT")
    private String holdNote;

    @Column(name = "hold_by")
    private Long holdBy;

    @Column(name = "hold_at", nullable = false)
    @Builder.Default
    private LocalDateTime holdAt = LocalDateTime.now();

    @Column(name = "release_by")
    private Long releaseBy;

    @Column(name = "release_at")
    private LocalDateTime releaseAt;

    @Column(name = "release_note", columnDefinition = "TEXT")
    private String releaseNote;
}
