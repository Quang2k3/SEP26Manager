package org.example.sep26management.application.enums;

/**
 * Outbound document status lifecycle:
 * DRAFT → PENDING_APPROVAL → APPROVED / REJECTED
 * INTERNAL_TRANSFER may skip PENDING_APPROVAL if auto-approve enabled (BR-OUT-12)
 */
public enum OutboundStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CONFIRMED,   // shipment confirmed (future UC-OUT-05)
    CANCELLED
}