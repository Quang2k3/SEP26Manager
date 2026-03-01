package org.example.sep26management.application.enums;

/**
 * BR-OUT-01: Order type determines workflow and required fields
 * BR-OUT-05: Document code format: EXP-SAL-YYYYMMDD-NNNN or EXP-INT-YYYYMMDD-NNNN
 * BR-OUT-11: Sales orders require manager approval
 * BR-OUT-12: Internal transfers may auto-approve
 */
public enum OutboundType {
    SALES_ORDER,       // → requires customer info, delivery date, manager approval
    INTERNAL_TRANSFER  // → requires destination warehouse, may auto-approve
}