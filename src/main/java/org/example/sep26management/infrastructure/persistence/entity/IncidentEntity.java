package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sep26management.application.enums.IncidentType;
import org.example.sep26management.application.enums.IncidentCategory;

import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "incident_code", nullable = false, length = 100)
    private String incidentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 50)
    private IncidentType incidentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    @Builder.Default
    private IncidentCategory category = IncidentCategory.QUALITY; // Default to QUALITY for safety

    @Column(name = "severity", nullable = false, length = 50)
    @Builder.Default
    private String severity = "HIGH";

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "reported_by")
    private Long reportedBy;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** FK to receiving_orders — links this incident to a specific GRN */
    @Column(name = "receiving_id")
    private Long receivingId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (occurredAt == null)
            occurredAt = LocalDateTime.now();
    }
}
