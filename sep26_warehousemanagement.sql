-- ============================================================
-- WAREHOUSE MANAGEMENT SYSTEM - PRODUCTION DATABASE
-- Database: PostgreSQL 14+
-- Author: Warehouse Management Team
-- Date: 2026-01-29
-- ============================================================

-- Drop existing database if needed (CAUTION!)
-- DROP DATABASE IF EXISTS warehouse_db;

-- Create database
-- CREATE DATABASE warehouse_db
--     WITH 
--     OWNER = postgres
--     ENCODING = 'UTF8'
--     LC_COLLATE = 'en_US.UTF-8'
--     LC_CTYPE = 'en_US.UTF-8'
--     TABLESPACE = pg_default
--     CONNECTION LIMIT = -1;

-- Connect to database
-- \c warehouse_db;

-- ============================================================
-- EXTENSIONS
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- 0) ENUM SYSTEM (FOUNDATION)
-- ============================================================

CREATE TABLE enum_types (
    enum_type_id BIGSERIAL PRIMARY KEY,
    enum_type_code VARCHAR(100) NOT NULL UNIQUE,
    enum_type_name VARCHAR(200) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_enum_type_code ON enum_types(enum_type_code);

COMMENT ON TABLE enum_types IS 'Master table for all enum types in system';

CREATE TABLE enum_values (
    enum_value_id BIGSERIAL PRIMARY KEY,
    enum_type_id BIGINT NOT NULL REFERENCES enum_types(enum_type_id) ON DELETE CASCADE,
    value_code VARCHAR(100) NOT NULL,
    value_name VARCHAR(200) NOT NULL,
    value_name_vi VARCHAR(200),
    display_order INT NOT NULL DEFAULT 0,
    color_code VARCHAR(20),
    icon VARCHAR(50),
    badge_style VARCHAR(100),
    is_default BOOLEAN DEFAULT FALSE,
    is_terminal BOOLEAN DEFAULT FALSE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(enum_type_id, value_code)
);

CREATE INDEX idx_enum_type_value ON enum_values(enum_type_id, value_code);
CREATE INDEX idx_enum_display_order ON enum_values(enum_type_id, display_order);
CREATE INDEX idx_enum_active ON enum_values(enum_type_id, active);

COMMENT ON TABLE enum_values IS 'Actual enum values for each type';

CREATE TABLE enum_transitions (
    transition_id BIGSERIAL PRIMARY KEY,
    enum_type_id BIGINT NOT NULL REFERENCES enum_types(enum_type_id) ON DELETE CASCADE,
    from_value_code VARCHAR(100) NOT NULL,
    to_value_code VARCHAR(100) NOT NULL,
    required_permission VARCHAR(100),
    required_role VARCHAR(100),
    is_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    UNIQUE(enum_type_id, from_value_code, to_value_code)
);

CREATE INDEX idx_enum_transitions ON enum_transitions(enum_type_id, from_value_code, to_value_code);

COMMENT ON TABLE enum_transitions IS 'Define valid status transitions (state machine)';

-- ============================================================
-- 1) TENANT / ORG
-- ============================================================

CREATE TABLE warehouses (
    warehouse_id BIGSERIAL PRIMARY KEY,
    warehouse_code VARCHAR(50) NOT NULL UNIQUE,
    warehouse_name VARCHAR(200) NOT NULL,
    address TEXT,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_warehouse_code ON warehouses(warehouse_code);
CREATE INDEX idx_warehouse_active ON warehouses(active);

COMMENT ON TABLE warehouses IS 'Warehouse master data';

-- ============================================================
-- 2) USERS / RBAC
-- ============================================================

CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name VARCHAR(200),
    phone VARCHAR(20),
    gender VARCHAR(10),
    date_of_birth DATE,
    address TEXT,
    avatar_url TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    is_first_login BOOLEAN NOT NULL DEFAULT TRUE,
    is_permanent BOOLEAN NOT NULL DEFAULT TRUE,
    expire_date DATE,
    last_login_at TIMESTAMP,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    password_changed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_by BIGINT
);

CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_status ON users(status);
CREATE INDEX idx_user_expire_date ON users(expire_date) WHERE expire_date IS NOT NULL;

COMMENT ON TABLE users IS 'System users';

CREATE TABLE roles (
    role_id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(200) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_role_code ON roles(role_code);

COMMENT ON TABLE roles IS 'User roles';

CREATE TABLE permissions (
    permission_id BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_permission_code ON permissions(permission_code);

COMMENT ON TABLE permissions IS 'System permissions';

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

CREATE TABLE user_warehouses (
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    assigned_by BIGINT REFERENCES users(user_id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, warehouse_id)
);

CREATE INDEX idx_user_warehouses_user ON user_warehouses(user_id);
CREATE INDEX idx_user_warehouses_warehouse ON user_warehouses(warehouse_id);

COMMENT ON TABLE user_warehouses IS 'User warehouse access control';

-- ============================================================
-- 3) ATTACHMENTS
-- ============================================================

CREATE TABLE attachments (
    attachment_id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    storage_url TEXT NOT NULL,
    checksum VARCHAR(100),
    uploaded_by BIGINT REFERENCES users(user_id),
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attachment_uploaded_by ON attachments(uploaded_by);

COMMENT ON TABLE attachments IS 'File attachments (SDS, photos, documents)';

-- ============================================================
-- 4) PRODUCT / SKU MASTER
-- ============================================================

CREATE TABLE categories (
    category_id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(200) NOT NULL,
    parent_category_id BIGINT REFERENCES categories(category_id),
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_category_code ON categories(category_code);
CREATE INDEX idx_category_parent ON categories(parent_category_id);

COMMENT ON TABLE categories IS 'Product categories';

CREATE TABLE sds_documents (
    sds_id BIGSERIAL PRIMARY KEY,
    sds_code VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL,
    issued_date DATE,
    language VARCHAR(10),
    attachment_id BIGINT REFERENCES attachments(attachment_id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(sds_code, version)
);

CREATE INDEX idx_sds_code_version ON sds_documents(sds_code, version);

COMMENT ON TABLE sds_documents IS 'Safety Data Sheets';

CREATE TABLE skus (
    sku_id BIGSERIAL PRIMARY KEY,
    category_id BIGINT REFERENCES categories(category_id),
    sku_code VARCHAR(100) NOT NULL UNIQUE,
    sku_name VARCHAR(300) NOT NULL,
    description TEXT,
    brand VARCHAR(200),
    package_type VARCHAR(100),
    volume_ml NUMERIC(12,2),
    weight_g NUMERIC(12,2),
    barcode VARCHAR(100) UNIQUE,
    unit VARCHAR(50) NOT NULL,
    origin_country VARCHAR(100),
    scent VARCHAR(200),
    image_url TEXT,
    storage_temp_min NUMERIC(5,2),
    storage_temp_max NUMERIC(5,2),
    shelf_life_days INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT REFERENCES users(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sku_code ON skus(sku_code);
CREATE INDEX idx_sku_barcode ON skus(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_sku_category_active ON skus(category_id, active);
CREATE INDEX idx_sku_active ON skus(active) WHERE deleted_at IS NULL;

COMMENT ON TABLE skus IS 'Stock Keeping Units (Product Master)';

CREATE TABLE sku_thresholds (
    threshold_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id) ON DELETE CASCADE,
    min_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    max_qty NUMERIC(12,2),
    reorder_point NUMERIC(12,2),
    reorder_qty NUMERIC(12,2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    note TEXT,
    created_by BIGINT REFERENCES users(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(user_id),
    UNIQUE(warehouse_id, sku_id)
);

CREATE INDEX idx_sku_threshold_warehouse ON sku_thresholds(warehouse_id);
CREATE INDEX idx_sku_threshold_sku ON sku_thresholds(sku_id);

COMMENT ON TABLE sku_thresholds IS 'SKU inventory thresholds per warehouse';

-- ============================================================
-- 5) PARTNERS
-- ============================================================

CREATE TABLE suppliers (
    supplier_id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(50) NOT NULL UNIQUE,
    supplier_name VARCHAR(300) NOT NULL,
    tax_code VARCHAR(50),
    email VARCHAR(255),
    phone VARCHAR(20),
    address TEXT,
    certifications TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_supplier_code ON suppliers(supplier_code);

COMMENT ON TABLE suppliers IS 'Supplier master data';

CREATE TABLE customers (
    customer_id BIGSERIAL PRIMARY KEY,
    customer_code VARCHAR(50) NOT NULL UNIQUE,
    customer_name VARCHAR(300) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    address TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_code ON customers(customer_code);

COMMENT ON TABLE customers IS 'Customer master data';

CREATE TABLE carriers (
    carrier_id BIGSERIAL PRIMARY KEY,
    carrier_code VARCHAR(50) NOT NULL UNIQUE,
    carrier_name VARCHAR(300) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_carrier_code ON carriers(carrier_code);

COMMENT ON TABLE carriers IS 'Shipping carrier master data';

-- ============================================================
-- 6) LOCATION MODEL
-- ============================================================

CREATE TABLE zones (
    zone_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    zone_code VARCHAR(50) NOT NULL,
    zone_name VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id, zone_code)
);

CREATE INDEX idx_zone_warehouse ON zones(warehouse_id);

COMMENT ON TABLE zones IS 'Warehouse zones';

CREATE TABLE locations (
    location_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    zone_id BIGINT REFERENCES zones(zone_id),
    location_code VARCHAR(100) NOT NULL,
    location_type VARCHAR(50) NOT NULL,
    parent_location_id BIGINT REFERENCES locations(location_id),
    max_weight_kg NUMERIC(12,2),
    max_volume_m3 NUMERIC(12,3),
    is_picking_face BOOLEAN NOT NULL DEFAULT FALSE,
    is_staging BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id, location_code)
);

CREATE INDEX idx_location_warehouse ON locations(warehouse_id);
CREATE INDEX idx_location_zone ON locations(zone_id);
CREATE INDEX idx_location_type ON locations(location_type);
CREATE INDEX idx_location_staging ON locations(warehouse_id, is_staging);

COMMENT ON TABLE locations IS 'Storage locations';

CREATE TABLE storage_policies (
    policy_id BIGSERIAL PRIMARY KEY,
    policy_code VARCHAR(50) NOT NULL UNIQUE,
    policy_name VARCHAR(200) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_storage_policy_code ON storage_policies(policy_code);

COMMENT ON TABLE storage_policies IS 'Storage policy definitions';

CREATE TABLE storage_policy_rules (
    rule_id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES storage_policies(policy_id) ON DELETE CASCADE,
    zone_id BIGINT REFERENCES zones(zone_id),
    location_type VARCHAR(50),
    min_distance_m NUMERIC(8,2),
    max_stack_height INT,
    max_qty_per_bin NUMERIC(12,2),
    note TEXT
);

CREATE INDEX idx_policy_rules_policy ON storage_policy_rules(policy_id);

COMMENT ON TABLE storage_policy_rules IS 'Storage policy rules';

-- ============================================================
-- 7) PROCUREMENT: RECEIVING
-- ============================================================

CREATE TABLE receiving_orders (
    receiving_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    receiving_code VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    source_type VARCHAR(50) NOT NULL,
    source_warehouse_id BIGINT REFERENCES warehouses(warehouse_id),
    supplier_id BIGINT REFERENCES suppliers(supplier_id),
    source_reference_code VARCHAR(100),
    received_at TIMESTAMP,
    created_by BIGINT NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    putaway_created_at TIMESTAMP,
    putaway_done_by BIGINT REFERENCES users(user_id),
    putaway_done_at TIMESTAMP,
    approved_by BIGINT REFERENCES users(user_id),
    approved_at TIMESTAMP,
    confirmed_by BIGINT REFERENCES users(user_id),
    confirmed_at TIMESTAMP,
    note TEXT,
    UNIQUE(warehouse_id, receiving_code)
);

CREATE INDEX idx_receiving_warehouse ON receiving_orders(warehouse_id);
CREATE INDEX idx_receiving_status ON receiving_orders(status);
CREATE INDEX idx_receiving_supplier ON receiving_orders(supplier_id);
CREATE INDEX idx_receiving_created_at ON receiving_orders(created_at);

COMMENT ON TABLE receiving_orders IS 'Inbound receiving orders';

CREATE TABLE receiving_items (
    receiving_item_id BIGSERIAL PRIMARY KEY,
    receiving_id BIGINT NOT NULL REFERENCES receiving_orders(receiving_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    expected_qty NUMERIC(12,2),
    received_qty NUMERIC(12,2) NOT NULL,
    lot_number VARCHAR(100),
    manufacture_date DATE,
    expiry_date DATE,
    weight_kg NUMERIC(12,2),
    note TEXT,
    qc_required BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_receiving_items_receiving ON receiving_items(receiving_id);
CREATE INDEX idx_receiving_items_sku ON receiving_items(sku_id);

COMMENT ON TABLE receiving_items IS 'Receiving order line items';

-- ============================================================
-- 7.x) PUTAWAY TASKS
-- ============================================================

CREATE TABLE putaway_tasks (
    putaway_task_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    receiving_id BIGINT NOT NULL REFERENCES receiving_orders(receiving_id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    from_location_id BIGINT REFERENCES locations(location_id),
    assigned_to BIGINT REFERENCES users(user_id),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by BIGINT NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    note TEXT,
    UNIQUE(warehouse_id, receiving_id)
);

CREATE INDEX idx_putaway_task_warehouse ON putaway_tasks(warehouse_id);
CREATE INDEX idx_putaway_task_receiving ON putaway_tasks(receiving_id);
CREATE INDEX idx_putaway_task_assigned ON putaway_tasks(assigned_to, status);

COMMENT ON TABLE putaway_tasks IS 'Putaway tasks for received goods';

CREATE TABLE putaway_task_items (
    putaway_task_item_id BIGSERIAL PRIMARY KEY,
    putaway_task_id BIGINT NOT NULL REFERENCES putaway_tasks(putaway_task_id) ON DELETE CASCADE,
    receiving_item_id BIGINT NOT NULL REFERENCES receiving_items(receiving_item_id),
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT,
    quantity NUMERIC(12,2) NOT NULL,
    putaway_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    suggested_location_id BIGINT REFERENCES locations(location_id),
    actual_location_id BIGINT REFERENCES locations(location_id),
    note TEXT,
    UNIQUE(putaway_task_id, receiving_item_id)
);

CREATE INDEX idx_putaway_item_task ON putaway_task_items(putaway_task_id);
CREATE INDEX idx_putaway_item_sku_lot ON putaway_task_items(sku_id, lot_id);

COMMENT ON TABLE putaway_task_items IS 'Putaway task line items';

-- ============================================================
-- 8) SALES / DISPATCH
-- ============================================================

CREATE TABLE sales_orders (
    so_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    so_code VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    required_ship_date DATE,
    created_by BIGINT REFERENCES users(user_id),
    approved_by BIGINT REFERENCES users(user_id),
    approved_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id, so_code)
);

CREATE INDEX idx_so_warehouse ON sales_orders(warehouse_id);
CREATE INDEX idx_so_customer ON sales_orders(customer_id);
CREATE INDEX idx_so_status ON sales_orders(status);

COMMENT ON TABLE sales_orders IS 'Sales orders';

CREATE TABLE sales_order_items (
    so_item_id BIGSERIAL PRIMARY KEY,
    so_id BIGINT NOT NULL REFERENCES sales_orders(so_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    ordered_qty NUMERIC(12,2) NOT NULL,
    note TEXT
);

CREATE INDEX idx_so_items_so ON sales_order_items(so_id);
CREATE INDEX idx_so_items_sku ON sales_order_items(sku_id);

COMMENT ON TABLE sales_order_items IS 'Sales order line items';

CREATE TABLE shipments (
    shipment_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    so_id BIGINT NOT NULL REFERENCES sales_orders(so_id),
    shipment_code VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    ship_to_name VARCHAR(300),
    ship_to_phone VARCHAR(20),
    ship_to_address TEXT,
    carrier_id BIGINT REFERENCES carriers(carrier_id),
    tracking_number VARCHAR(200),
    shipped_at TIMESTAMP,
    created_by BIGINT NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    dispatched_by BIGINT REFERENCES users(user_id),
    note TEXT,
    UNIQUE(warehouse_id, shipment_code)
);

CREATE INDEX idx_shipment_warehouse ON shipments(warehouse_id);
CREATE INDEX idx_shipment_so ON shipments(so_id);
CREATE INDEX idx_shipment_status ON shipments(status);

COMMENT ON TABLE shipments IS 'Outbound shipments';

CREATE TABLE shipment_items (
    shipment_item_id BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES shipments(shipment_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT,
    from_location_id BIGINT REFERENCES locations(location_id),
    quantity NUMERIC(12,2) NOT NULL,
    note TEXT
);

CREATE INDEX idx_shipment_items_shipment ON shipment_items(shipment_id);
CREATE INDEX idx_shipment_items_sku_lot ON shipment_items(sku_id, lot_id);
CREATE INDEX idx_shipment_items_location ON shipment_items(from_location_id);

COMMENT ON TABLE shipment_items IS 'Shipment line items';

-- ============================================================
-- 9) WAREHOUSE OPERATIONS: LOTS + QC + QUARANTINE
-- ============================================================

CREATE TABLE inventory_lots (
    lot_id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_number VARCHAR(100) NOT NULL,
    manufacture_date DATE,
    expiry_date DATE,
    qc_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    quarantine_status VARCHAR(50) NOT NULL DEFAULT 'NONE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    receiving_item_id BIGINT REFERENCES receiving_items(receiving_item_id),
    UNIQUE(sku_id, lot_number)
);

CREATE INDEX idx_lot_sku ON inventory_lots(sku_id);
CREATE INDEX idx_lot_number ON inventory_lots(lot_number);
CREATE INDEX idx_lot_expiry ON inventory_lots(expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_lot_qc_status ON inventory_lots(qc_status);

COMMENT ON TABLE inventory_lots IS 'Inventory lot tracking';

CREATE TABLE qc_inspections (
    inspection_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    lot_id BIGINT NOT NULL REFERENCES inventory_lots(lot_id),
    inspection_code VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    inspected_by BIGINT REFERENCES users(user_id),
    inspected_at TIMESTAMP,
    remarks TEXT,
    attachment_id BIGINT REFERENCES attachments(attachment_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id, inspection_code)
);

CREATE INDEX idx_qc_warehouse ON qc_inspections(warehouse_id);
CREATE INDEX idx_qc_lot ON qc_inspections(lot_id);
CREATE INDEX idx_qc_status ON qc_inspections(status);

COMMENT ON TABLE qc_inspections IS 'Quality control inspections';

CREATE TABLE quarantine_holds (
    hold_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    lot_id BIGINT NOT NULL REFERENCES inventory_lots(lot_id),
    hold_reason VARCHAR(500) NOT NULL,
    hold_note TEXT,
    hold_by BIGINT REFERENCES users(user_id),
    hold_at TIMESTAMP NOT NULL DEFAULT NOW(),
    release_by BIGINT REFERENCES users(user_id),
    release_at TIMESTAMP,
    release_note TEXT
);

CREATE INDEX idx_quarantine_warehouse ON quarantine_holds(warehouse_id);
CREATE INDEX idx_quarantine_lot ON quarantine_holds(lot_id);

COMMENT ON TABLE quarantine_holds IS 'Quarantine holds';

-- ============================================================
-- 10) INVENTORY: LEDGER-FIRST
-- ============================================================

CREATE TABLE inventory_transactions (
    txn_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    location_id BIGINT NOT NULL REFERENCES locations(location_id),
    quantity NUMERIC(12,2) NOT NULL,
    txn_type VARCHAR(50) NOT NULL,
    reference_table VARCHAR(100),
    reference_id BIGINT,
    reason_code VARCHAR(100),
    created_by BIGINT NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inv_txn_warehouse ON inventory_transactions(warehouse_id);
CREATE INDEX idx_inv_txn_sku_lot_loc ON inventory_transactions(warehouse_id, sku_id, lot_id, location_id);
CREATE INDEX idx_inv_txn_reference ON inventory_transactions(reference_table, reference_id);
CREATE INDEX idx_inv_txn_created_at ON inventory_transactions(created_at);

COMMENT ON TABLE inventory_transactions IS 'Inventory transaction ledger (single source of truth)';

CREATE TABLE inventory_snapshot (
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    lot_id_safe BIGINT GENERATED ALWAYS AS (COALESCE(lot_id, 0)) STORED,
    location_id BIGINT NOT NULL REFERENCES locations(location_id),
    quantity NUMERIC(12,2) NOT NULL DEFAULT 0,
    reserved_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (warehouse_id, sku_id, lot_id_safe, location_id)
);

CREATE INDEX idx_inv_snapshot_warehouse ON inventory_snapshot(warehouse_id);
CREATE INDEX idx_inv_snapshot_sku ON inventory_snapshot(sku_id);
CREATE INDEX idx_inv_snapshot_location ON inventory_snapshot(location_id);
CREATE INDEX idx_inv_snapshot_lot ON inventory_snapshot(lot_id) WHERE lot_id IS NOT NULL;

COMMENT ON TABLE inventory_snapshot IS 'Current inventory snapshot (derived from transactions)';

CREATE TABLE reservations (
    reservation_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    quantity NUMERIC(12,2) NOT NULL,
    reference_table VARCHAR(100),
    reference_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reservation_warehouse ON reservations(warehouse_id);
CREATE INDEX idx_reservation_sku_lot ON reservations(sku_id, lot_id);
CREATE INDEX idx_reservation_reference ON reservations(reference_table, reference_id);
CREATE INDEX idx_reservation_status ON reservations(status);

COMMENT ON TABLE reservations IS 'Inventory reservations';

-- ============================================================
-- 11) TRANSFER / RETURNS / ADJUSTMENTS
-- ============================================================

CREATE TABLE transfers (
    transfer_id BIGSERIAL PRIMARY KEY,
    from_warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id),
    to_warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id),
    transfer_code VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT REFERENCES users(user_id),
    approved_by BIGINT REFERENCES users(user_id),
    approved_at TIMESTAMP,
    shipped_at TIMESTAMP,
    received_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transfer_from ON transfers(from_warehouse_id);
CREATE INDEX idx_transfer_to ON transfers(to_warehouse_id);
CREATE INDEX idx_transfer_status ON transfers(status);

COMMENT ON TABLE transfers IS 'Inter-warehouse transfers';

CREATE TABLE transfer_items (
    transfer_item_id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL REFERENCES transfers(transfer_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    quantity NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_transfer_items_transfer ON transfer_items(transfer_id);
CREATE INDEX idx_transfer_items_sku ON transfer_items(sku_id);

COMMENT ON TABLE transfer_items IS 'Transfer line items';

CREATE TABLE adjustments (
    adjustment_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    adjustment_code VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    reason VARCHAR(500) NOT NULL,
    created_by BIGINT REFERENCES users(user_id),
    approved_by BIGINT REFERENCES users(user_id),
    approved_at TIMESTAMP,
    posted_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id, adjustment_code)
);

CREATE INDEX idx_adjustment_warehouse ON adjustments(warehouse_id);
CREATE INDEX idx_adjustment_status ON adjustments(status);

COMMENT ON TABLE adjustments IS 'Inventory adjustments';

CREATE TABLE adjustment_items (
    adjustment_item_id BIGSERIAL PRIMARY KEY,
    adjustment_id BIGINT NOT NULL REFERENCES adjustments(adjustment_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    location_id BIGINT NOT NULL REFERENCES locations(location_id),
    delta_qty NUMERIC(12,2) NOT NULL,
    note TEXT
);

CREATE INDEX idx_adjustment_items_adjustment ON adjustment_items(adjustment_id);
CREATE INDEX idx_adjustment_items_sku ON adjustment_items(sku_id);

COMMENT ON TABLE adjustment_items IS 'Adjustment line items';

CREATE TABLE returns (
    return_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    return_code VARCHAR(100) NOT NULL,
    return_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    reference_table VARCHAR(100),
    reference_id BIGINT,
    created_by BIGINT REFERENCES users(user_id),
    approved_by BIGINT REFERENCES users(user_id),
    approved_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id, return_code)
);

CREATE INDEX idx_return_warehouse ON returns(warehouse_id);
CREATE INDEX idx_return_status ON returns(status);
CREATE INDEX idx_return_type ON returns(return_type);

COMMENT ON TABLE returns IS 'Returns (customer/supplier)';

CREATE TABLE return_items (
    return_item_id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL REFERENCES returns(return_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    quantity NUMERIC(12,2) NOT NULL,
    note TEXT
);

CREATE INDEX idx_return_items_return ON return_items(return_id);
CREATE INDEX idx_return_items_sku ON return_items(sku_id);

COMMENT ON TABLE return_items IS 'Return line items';

-- ============================================================
-- 12) PICKING / PACKING
-- ============================================================

CREATE TABLE picking_tasks (
    picking_task_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    so_id BIGINT REFERENCES sales_orders(so_id),
    shipment_id BIGINT REFERENCES shipments(shipment_id),
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority INT NOT NULL DEFAULT 3,
    assigned_to BIGINT REFERENCES users(user_id),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_picking_task_warehouse ON picking_tasks(warehouse_id);
CREATE INDEX idx_picking_task_shipment ON picking_tasks(shipment_id);
CREATE INDEX idx_picking_task_assigned ON picking_tasks(assigned_to, status);

COMMENT ON TABLE picking_tasks IS 'Picking tasks';

CREATE TABLE picking_task_items (
    picking_task_item_id BIGSERIAL PRIMARY KEY,
    picking_task_id BIGINT NOT NULL REFERENCES picking_tasks(picking_task_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    from_location_id BIGINT NOT NULL REFERENCES locations(location_id),
    required_qty NUMERIC(12,2) NOT NULL,
    picked_qty NUMERIC(12,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_picking_item_task ON picking_task_items(picking_task_id);
CREATE INDEX idx_picking_item_sku_lot ON picking_task_items(sku_id, lot_id);

COMMENT ON TABLE picking_task_items IS 'Picking task line items';

-- ============================================================
-- 13) CYCLE COUNT / STOCKTAKE
-- ============================================================

CREATE TABLE stocktakes (
    stocktake_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    stocktake_code VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    start_at TIMESTAMP,
    end_at TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    posted_by BIGINT REFERENCES users(user_id),
    posted_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id, stocktake_code)
);

CREATE INDEX idx_stocktake_warehouse ON stocktakes(warehouse_id);
CREATE INDEX idx_stocktake_status ON stocktakes(status);

COMMENT ON TABLE stocktakes IS 'Stocktake / cycle count';

CREATE TABLE stocktake_items (
    stocktake_item_id BIGSERIAL PRIMARY KEY,
    stocktake_id BIGINT NOT NULL REFERENCES stocktakes(stocktake_id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES locations(location_id),
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    expected_qty NUMERIC(12,2),
    counted_qty NUMERIC(12,2),
    discrepancy_qty NUMERIC(12,2),
    counted_by BIGINT REFERENCES users(user_id),
    counted_at TIMESTAMP,
    note TEXT
);

CREATE INDEX idx_stocktake_items_stocktake ON stocktake_items(stocktake_id);
CREATE INDEX idx_stocktake_items_location ON stocktake_items(location_id);
CREATE INDEX idx_stocktake_items_sku ON stocktake_items(sku_id);

COMMENT ON TABLE stocktake_items IS 'Stocktake line items';

-- ============================================================
-- 14) ALERTS / INCIDENTS / ENVIRONMENT
-- ============================================================

CREATE TABLE expiry_rules (
    rule_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    warn_days INT NOT NULL DEFAULT 30,
    severity VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES users(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id)
);

CREATE INDEX idx_expiry_rules_warehouse ON expiry_rules(warehouse_id);

COMMENT ON TABLE expiry_rules IS 'Expiry alert rules';

CREATE TABLE inventory_alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    sku_id BIGINT REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    location_id BIGINT REFERENCES locations(location_id),
    message TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP,
    resolved_by BIGINT REFERENCES users(user_id),
    dedupe_key VARCHAR(200),
    reference_table VARCHAR(100),
    reference_id BIGINT
);

CREATE INDEX idx_alert_warehouse_status_type ON inventory_alerts(warehouse_id, status, alert_type);
CREATE INDEX idx_alert_sku_type_status ON inventory_alerts(sku_id, alert_type, status);
CREATE INDEX idx_alert_dedupe ON inventory_alerts(dedupe_key) WHERE dedupe_key IS NOT NULL;

COMMENT ON TABLE inventory_alerts IS 'Inventory alerts (expiry, low stock, etc.)';

CREATE TABLE incidents (
    incident_id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    incident_code VARCHAR(100) NOT NULL,
    incident_type VARCHAR(50) NOT NULL,
    severity VARCHAR(50) NOT NULL DEFAULT 'HIGH',
    occurred_at TIMESTAMP NOT NULL,
    location_id BIGINT REFERENCES locations(location_id),
    description TEXT,
    reported_by BIGINT REFERENCES users(user_id),
    attachment_id BIGINT REFERENCES attachments(attachment_id),
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(warehouse_id, incident_code)
);

CREATE INDEX idx_incident_warehouse ON incidents(warehouse_id);
CREATE INDEX idx_incident_status ON incidents(status);
CREATE INDEX idx_incident_type ON incidents(incident_type);

COMMENT ON TABLE incidents IS 'Safety incidents';

-- ============================================================
-- 15) AUDIT
-- ============================================================

CREATE TABLE audit_logs (
    audit_id BIGSERIAL PRIMARY KEY,
    entity_name VARCHAR(200) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_data JSONB,
    new_data JSONB,
    action_by BIGINT NOT NULL REFERENCES users(user_id),
    action_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_name, entity_id);
CREATE INDEX idx_audit_action_by ON audit_logs(action_by);
CREATE INDEX idx_audit_action_at ON audit_logs(action_at);

COMMENT ON TABLE audit_logs IS 'Audit trail for all changes';

-- ============================================================
-- INSERT INITIAL ENUM DATA
-- ============================================================

-- USER_STATUS
INSERT INTO enum_types (enum_type_code, enum_type_name, description) VALUES 
('USER_STATUS', 'User Status', 'Status of user accounts');

INSERT INTO enum_values (enum_type_id, value_code, value_name, value_name_vi, display_order, color_code, icon, is_default) VALUES 
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'USER_STATUS'), 'ACTIVE', 'Active', 'Hoạt động', 1, '#28a745', 'fa-check-circle', true),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'USER_STATUS'), 'INACTIVE', 'Inactive', 'Không hoạt động', 2, '#6c757d', 'fa-times-circle', false),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'USER_STATUS'), 'LOCKED', 'Locked', 'Bị khóa', 3, '#dc3545', 'fa-lock', false);

-- RECEIVING_STATUS
INSERT INTO enum_types (enum_type_code, enum_type_name, description) VALUES 
('RECEIVING_STATUS', 'Receiving Order Status', 'Status flow for receiving orders');

INSERT INTO enum_values (enum_type_id, value_code, value_name, value_name_vi, display_order, color_code, is_terminal) VALUES 
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'RECEIVING_STATUS'), 'DRAFT', 'Draft', 'Nháp', 1, '#6c757d', false),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'RECEIVING_STATUS'), 'SUBMITTED', 'Submitted', 'Đã gửi', 2, '#17a2b8', false),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'RECEIVING_STATUS'), 'APPROVED', 'Approved', 'Đã duyệt', 3, '#007bff', false),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'RECEIVING_STATUS'), 'POSTED', 'Posted', 'Đã ghi nhận', 4, '#28a745', false),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'RECEIVING_STATUS'), 'PUTAWAY_DONE', 'Putaway Done', 'Đã cất kho', 5, '#20c997', true),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'RECEIVING_STATUS'), 'CANCELLED', 'Cancelled', 'Đã hủy', 6, '#dc3545', true);

-- SALES_ORDER_STATUS
INSERT INTO enum_types (enum_type_code, enum_type_name, description) VALUES 
('SALES_ORDER_STATUS', 'Sales Order Status', 'Status flow for sales orders');

INSERT INTO enum_values (enum_type_id, value_code, value_name, value_name_vi, display_order, color_code) VALUES 
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'SALES_ORDER_STATUS'), 'DRAFT', 'Draft', 'Nháp', 1, '#6c757d'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'SALES_ORDER_STATUS'), 'APPROVED', 'Approved', 'Đã duyệt', 2, '#007bff'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'SALES_ORDER_STATUS'), 'PICKING', 'Picking', 'Đang lấy hàng', 3, '#ffc107'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'SALES_ORDER_STATUS'), 'SHIPPED', 'Shipped', 'Đã giao', 4, '#28a745'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'SALES_ORDER_STATUS'), 'CLOSED', 'Closed', 'Đã đóng', 5, '#20c997'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'SALES_ORDER_STATUS'), 'CANCELLED', 'Cancelled', 'Đã hủy', 6, '#dc3545');

-- QC_STATUS
INSERT INTO enum_types (enum_type_code, enum_type_name, description) VALUES 
('QC_STATUS', 'Quality Control Status', 'QC inspection status');

INSERT INTO enum_values (enum_type_id, value_code, value_name, value_name_vi, display_order, color_code) VALUES 
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'QC_STATUS'), 'NOT_REQUIRED', 'Not Required', 'Không yêu cầu', 1, '#6c757d'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'QC_STATUS'), 'PENDING', 'Pending', 'Chờ kiểm tra', 2, '#ffc107'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'QC_STATUS'), 'PASSED', 'Passed', 'Đạt', 3, '#28a745'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'QC_STATUS'), 'FAILED', 'Failed', 'Không đạt', 4, '#dc3545');

-- ALERT_TYPE
INSERT INTO enum_types (enum_type_code, enum_type_name, description) VALUES 
('ALERT_TYPE', 'Alert Type', 'Types of inventory alerts');

INSERT INTO enum_values (enum_type_id, value_code, value_name, value_name_vi, display_order, color_code, icon) VALUES 
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'ALERT_TYPE'), 'EXPIRY', 'Expiry', 'Sắp hết hạn', 1, '#dc3545', 'fa-calendar-times'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'ALERT_TYPE'), 'LOW_STOCK', 'Low Stock', 'Tồn kho thấp', 2, '#ffc107', 'fa-exclamation-triangle'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'ALERT_TYPE'), 'OVERSTOCK', 'Overstock', 'Tồn kho cao', 3, '#17a2b8', 'fa-boxes'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'ALERT_TYPE'), 'REORDER', 'Reorder', 'Cần đặt hàng', 4, '#fd7e14', 'fa-shopping-cart');

-- ALERT_SEVERITY
INSERT INTO enum_types (enum_type_code, enum_type_name, description) VALUES 
('ALERT_SEVERITY', 'Alert Severity', 'Severity levels for alerts');

INSERT INTO enum_values (enum_type_id, value_code, value_name, value_name_vi, display_order, color_code) VALUES 
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'ALERT_SEVERITY'), 'LOW', 'Low', 'Thấp', 1, '#6c757d'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'ALERT_SEVERITY'), 'MEDIUM', 'Medium', 'Trung bình', 2, '#ffc107'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'ALERT_SEVERITY'), 'HIGH', 'High', 'Cao', 3, '#fd7e14'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'ALERT_SEVERITY'), 'CRITICAL', 'Critical', 'Nghiêm trọng', 4, '#dc3545');

-- TXN_TYPE
INSERT INTO enum_types (enum_type_code, enum_type_name, description) VALUES 
('TXN_TYPE', 'Transaction Type', 'Inventory transaction types');

INSERT INTO enum_values (enum_type_id, value_code, value_name, value_name_vi, display_order, color_code) VALUES 
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'TXN_TYPE'), 'RECEIPT', 'Receipt', 'Nhập kho', 1, '#28a745'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'TXN_TYPE'), 'ISSUE', 'Issue', 'Xuất kho', 2, '#dc3545'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'TXN_TYPE'), 'MOVE', 'Move', 'Chuyển kho', 3, '#17a2b8'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'TXN_TYPE'), 'ADJUST', 'Adjust', 'Điều chỉnh', 4, '#ffc107'),
((SELECT enum_type_id FROM enum_types WHERE enum_type_code = 'TXN_TYPE'), 'COUNT', 'Count', 'Kiểm kê', 5, '#6f42c1');

-- ============================================================
-- INSERT SAMPLE DATA
-- ============================================================

-- ============================================================
-- SEED DATA: roles, users, user_roles, skus
-- Safe to re-run (uses ON CONFLICT DO NOTHING)
-- ============================================================

-- 1) ROLES
INSERT INTO roles (role_code, role_name, description, active)
VALUES
  ('MANAGER',     'Warehouse Manager', 'Manage warehouse operations', true),
  ('KEEPER',      'Warehouse Keeper',  'Daily warehouse operations',  true),
  ('ACCOUNTANT',  'Accountant',        'Financial and reporting',     true)
ON CONFLICT (role_code) DO NOTHING;


-- 2) USERS
-- NOTE: Replace password_hash values with your real BCrypt hashes
INSERT INTO users (
  email, password_hash, full_name, phone, gender, date_of_birth, address,
  status, is_first_login, is_permanent, expire_date
)
VALUES
  ('quangnphe170355@fpt.edu.vn',    '$2a$10$3HB4DcPQ/JF.PqC6YZgkb.XXoDqAtCSF2xgtU2d7iCDuZyRh7DrAi', 'Nguyễn Phước Quang', '0900000001', 'MALE',   '1998-01-01', 'HCM', 'ACTIVE',  false, true,  NULL),
  ('nguyen.quang6633@gmail.com', '$2a$10$2m//9tx7/QAH5WgcNyTRS.zkLyXOZSNC/Sx2KtD1nFgT3Wqw6sjaG', 'Trương Tuấn Anh',  '0900000002', 'FEMALE', '1999-02-02', 'HCM', 'ACTIVE',  true,  true,  NULL),
  ('quangnphe170355@gmail.com',  '$2a$10$2m//9tx7/QAH5WgcNyTRS.zkLyXOZSNC/Sx2KtD1nFgT3Wqw6sjaG', 'Lưu Tiến Nhật',   '0900000003', 'MALE',   '2000-03-03', 'HCM', 'ACTIVE',  true,  true,  NULL),
  ('tranhoang1112003@gmail.com',     '$2a$10$2m//9tx7/QAH5WgcNyTRS.zkLyXOZSNC/Sx2KtD1nFgT3Wqw6sjaG', 'Trần Huy Hoàng',         '0900000004', 'FEMALE', '2001-04-04', 'HCM', 'ACTIVE',  false,  true,  NULL)
ON CONFLICT (email) DO NOTHING;


-- 3) USER_ROLES (gán role cho user)
INSERT INTO user_roles (user_id, role_id)
VALUES
  ((SELECT user_id FROM users WHERE email = 'nguyen.quang6633@gmail.com'),
   (SELECT role_id FROM roles WHERE role_code = 'MANAGER')),

  ((SELECT user_id FROM users WHERE email = 'quangnphe170355@fpt.edu.vn'),
   (SELECT role_id FROM roles WHERE role_code = 'MANAGER')),

  ((SELECT user_id FROM users WHERE email = 'quangnphe170355@gmail.com'),
   (SELECT role_id FROM roles WHERE role_code = 'KEEPER')),

  ((SELECT user_id FROM users WHERE email = 'tranhoang1112003@gmail.com'),
   (SELECT role_id FROM roles WHERE role_code = 'ACCOUNTANT'))
ON CONFLICT (user_id, role_id) DO NOTHING;


-- -- (Khuyến nghị) 4) USER_WAREHOUSES (gán user vào kho) - nếu bạn muốn phân quyền theo kho
-- -- Nếu bạn chưa cần thì bỏ block này cũng được
-- INSERT INTO user_warehouses (user_id, warehouse_id, assigned_by, active)
-- VALUES
--   ((SELECT user_id FROM users WHERE email = 'admin@wms.local'),
--    (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH001'),
--    (SELECT user_id FROM users WHERE email = 'admin@wms.local'), true),
--
--   ((SELECT user_id FROM users WHERE email = 'manager1@wms.local'),
--    (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH001'),
--    (SELECT user_id FROM users WHERE email = 'admin@wms.local'), true),
--
--   ((SELECT user_id FROM users WHERE email = 'keeper1@wms.local'),
--    (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH001'),
--    (SELECT user_id FROM users WHERE email = 'admin@wms.local'), true),
--
--   ((SELECT user_id FROM users WHERE email = 'acc1@wms.local'),
--    (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH001'),
--    (SELECT user_id FROM users WHERE email = 'admin@wms.local'), true)
-- ON CONFLICT (user_id, warehouse_id) DO NOTHING;


-- 5) SKUS (thêm nhiều SKU mẫu)
-- Lưu ý: barcode UNIQUE, nếu chưa có thì để NULL
INSERT INTO skus (
  category_id, sku_code, sku_name, description, brand,
  package_type, volume_ml, weight_g, barcode, unit, origin_country,
  scent, image_url, shelf_life_days, active
)
VALUES
  ((SELECT category_id FROM categories WHERE category_code = 'HC'),
   'SKU001', 'Nước giặt Luxury 3.8kg (4 can)', 'Nước giặt xả quần áo hương Luxury', 'SWAT',
   'Thùng', NULL, NULL, NULL, '4', 'VN',
   'Luxury', NULL, 730, true),

  ((SELECT category_id FROM categories WHERE category_code = 'HC'),
   'SKU002', 'Nước rửa chén Lemon 3.5kg (4 can)', 'Nước rửa chén hương chanh', 'SWAT',
   'Thùng', NULL, NULL, NULL, '4', 'VN',
   'Lemon', NULL, 730, true),

  ((SELECT category_id FROM categories WHERE category_code = 'PC'),
   'SKU003', 'Sữa tắm Fresh 900ml', 'Sữa tắm hương Fresh', 'SWAT',
   'Chai', 900, NULL, NULL, 'Chai', 'VN',
   'Fresh', NULL, 1095, true)
ON CONFLICT (sku_code) DO NOTHING;


-- 0) WAREHOUSE (bắt buộc nếu chưa có)
INSERT INTO warehouses (warehouse_code, warehouse_name, address, timezone)
VALUES ('WH01', 'Kho Chính', 'Quận 9, TP.HCM', 'Asia/Ho_Chi_Minh')
ON CONFLICT (warehouse_code) DO NOTHING;

-- 0.1) CATEGORIES (bắt buộc - SKU cần FK)
INSERT INTO categories (category_code, category_name, description)
VALUES
  ('HC', 'Household Care', 'Sản phẩm gia dụng'),
  ('PC', 'Personal Care',  'Sản phẩm chăm sóc cá nhân')
ON CONFLICT (category_code) DO NOTHING;


-- 6) ZONES
INSERT INTO zones (warehouse_id, zone_code, zone_name)
VALUES
  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'), 'Z-INB', 'Khu nhận hàng'),
  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'), 'Z-HC',  'Khu Household Care'),
  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'), 'Z-PC',  'Khu Personal Care')
ON CONFLICT (warehouse_id, zone_code) DO NOTHING;


-- 7) LOCATIONS
INSERT INTO locations (warehouse_id, zone_id, location_code, location_type, is_staging)
VALUES
  -- Z-INB: nơi để hàng mới nhận về
  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'),
   (SELECT zone_id FROM zones WHERE zone_code = 'Z-INB' AND warehouse_id = (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01')),
   'WH01-INB-STAGE', 'STAGING', true),

  -- Z-HC bins
  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'),
   (SELECT zone_id FROM zones WHERE zone_code = 'Z-HC' AND warehouse_id = (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01')),
   'WH01-ZHC-A01-R01-B01', 'BIN', false),

  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'),
   (SELECT zone_id FROM zones WHERE zone_code = 'Z-HC' AND warehouse_id = (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01')),
   'WH01-ZHC-A01-R01-B02', 'BIN', false),

  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'),
   (SELECT zone_id FROM zones WHERE zone_code = 'Z-HC' AND warehouse_id = (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01')),
   'WH01-ZHC-A01-R02-B01', 'BIN', false),

  -- Z-PC bins
  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'),
   (SELECT zone_id FROM zones WHERE zone_code = 'Z-PC' AND warehouse_id = (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01')),
   'WH01-ZPC-A01-R01-B01', 'BIN', false),

  ((SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01'),
   (SELECT zone_id FROM zones WHERE zone_code = 'Z-PC' AND warehouse_id = (SELECT warehouse_id FROM warehouses WHERE warehouse_code = 'WH01')),
   'WH01-ZPC-A01-R01-B02', 'BIN', false)
ON CONFLICT (warehouse_id, location_code) DO NOTHING;


-- 8) SUPPLIERS
INSERT INTO suppliers (supplier_code, supplier_name, phone, email, active)
VALUES
  ('SUP001', 'Công ty TNHH SWAT', '0281234567', 'supply@swat.vn', true)
ON CONFLICT (supplier_code) DO NOTHING;
