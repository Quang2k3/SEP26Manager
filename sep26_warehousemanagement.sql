-- ============================================
-- WAREHOUSE MANAGEMENT SYSTEM - FULL DATABASE SCHEMA
-- Version: 1.0.0
-- Database: PostgreSQL 14+
-- Purpose: Chemical Warehouse Management (SWAT, GRASSE)
-- ============================================

-- ============================================
-- ============================================
-- RESET DATABASE STRUCTURE (SAFE WAY)
-- ============================================

DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;


-- ============================================
-- EXTENSIONS
-- ============================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- ENUMS (Optional - for type safety)
-- ============================================
CREATE TYPE user_role_enum AS ENUM ('MANAGER', 'ACCOUNTANT', 'KEEPER');
CREATE TYPE user_status_enum AS ENUM ('ACTIVE', 'INACTIVE', 'PENDING_VERIFICATION', 'LOCKED');
CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE', 'OTHER');
CREATE TYPE otp_type_enum AS ENUM ('FIRST_LOGIN', 'RESET_PASSWORD');
CREATE TYPE document_type_enum AS ENUM ('INBOUND', 'OUTBOUND', 'RETURN', 'STOCK_CHECK', 'ADJUSTMENT');
CREATE TYPE document_status_enum AS ENUM ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'RECEIVED', 'CONFIRMED', 'CANCELLED');
CREATE TYPE transaction_type_enum AS ENUM ('IMPORT', 'EXPORT', 'RETURN', 'ADJUSTMENT', 'RESERVE', 'RELEASE');
CREATE TYPE alert_type_enum AS ENUM ('LOW_STOCK', 'OUT_OF_STOCK', 'OVERSTOCK', 'NEAR_EXPIRY', 'EXPIRED');
CREATE TYPE alert_severity_enum AS ENUM ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW');
CREATE TYPE lot_status_enum AS ENUM ('ACTIVE', 'EXPIRED', 'RECALLED', 'DEPLETED');

-- ============================================
-- TABLE 1: USERS
-- ============================================
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    
    -- Authentication
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    
    -- Profile
    full_name VARCHAR(200),
    phone VARCHAR(20),
    gender VARCHAR(10),
    date_of_birth DATE,
    address TEXT,
    avatar_url TEXT,
    
    -- Account Management
    role VARCHAR(50) NOT NULL DEFAULT 'KEEPER',
    status VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    
    -- Account Type
    is_permanent BOOLEAN DEFAULT TRUE,
    expire_date DATE,
    
    -- Security
    is_first_login BOOLEAN DEFAULT TRUE,
    last_login_at TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    password_changed_at TIMESTAMP,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    
    -- Constraints
    CONSTRAINT chk_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$'),
    CONSTRAINT chk_phone_format CHECK (phone IS NULL OR phone ~ '^[0-9]{9,15}$'),
    CONSTRAINT chk_role CHECK (role IN ('MANAGER', 'ACCOUNTANT', 'KEEPER')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING_VERIFICATION', 'LOCKED')),
    CONSTRAINT chk_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_expire_date CHECK (is_permanent = TRUE OR expire_date IS NOT NULL)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_created_at ON users(created_at DESC);

COMMENT ON TABLE users IS 'User accounts with authentication and profile information';
COMMENT ON COLUMN users.role IS 'User role: MANAGER, ACCOUNTANT, KEEPER';
COMMENT ON COLUMN users.status IS 'Account status: ACTIVE, INACTIVE, PENDING_VERIFICATION, LOCKED';

-- ============================================
-- TABLE 2: OTP (One-Time Password)
-- ============================================
CREATE TABLE otps (
    otp_id BIGSERIAL PRIMARY KEY,
    
    email VARCHAR(100) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    otp_type VARCHAR(50) NOT NULL,
    
    -- Security
    attempts_remaining INTEGER DEFAULT 5,
    is_used BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_otp_code CHECK (otp_code ~ '^[0-9]{6}$'),
    CONSTRAINT chk_otp_type CHECK (otp_type IN ('FIRST_LOGIN', 'RESET_PASSWORD')),
    CONSTRAINT chk_otp_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_otps_email_type ON otps(email, otp_type);
CREATE INDEX idx_otps_expires_at ON otps(expires_at);
CREATE INDEX idx_otps_created_at ON otps(created_at DESC);

COMMENT ON TABLE otps IS 'One-Time Passwords for email verification';

-- ============================================
-- TABLE 3: ACTIVE_SESSIONS
-- ============================================
CREATE TABLE active_sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    jwt_token TEXT NOT NULL,
    ip_address VARCHAR(50),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_active_sessions_user ON active_sessions(user_id);
CREATE INDEX idx_active_sessions_expires ON active_sessions(expires_at);

COMMENT ON TABLE active_sessions IS 'Active user sessions for single session enforcement';

-- ============================================
-- TABLE 4: CATEGORIES
-- ============================================
CREATE TABLE categories (
    category_id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) UNIQUE NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    description TEXT,
    parent_category_id BIGINT REFERENCES categories(category_id),
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(user_id)
);

CREATE INDEX idx_categories_code ON categories(category_code);
CREATE INDEX idx_categories_parent ON categories(parent_category_id);
CREATE INDEX idx_categories_active ON categories(is_active);

COMMENT ON TABLE categories IS 'Product categories (SWAT, GRASSE, etc.)';

-- ============================================
-- TABLE 5: WAREHOUSE_LOCATIONS
-- ============================================
CREATE TABLE warehouse_locations (
    location_id BIGSERIAL PRIMARY KEY,
    location_code VARCHAR(50) UNIQUE NOT NULL,
    location_name VARCHAR(200),
    location_type VARCHAR(50) NOT NULL,
    parent_location_id BIGINT REFERENCES warehouse_locations(location_id),
    
    -- Physical attributes
    capacity_volume DECIMAL(15,2),
    capacity_weight DECIMAL(15,2),
    
    -- Safety for chemicals
    is_hazardous_zone BOOLEAN DEFAULT FALSE,
    ventilation_available BOOLEAN DEFAULT FALSE,
    fire_suppression_available BOOLEAN DEFAULT FALSE,
    temperature_controlled BOOLEAN DEFAULT FALSE,
    
    -- Metadata
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_location_type CHECK (location_type IN ('ZONE', 'AISLE', 'RACK', 'SHELF', 'BIN'))
);

CREATE INDEX idx_location_code ON warehouse_locations(location_code);
CREATE INDEX idx_location_parent ON warehouse_locations(parent_location_id);
CREATE INDEX idx_location_type ON warehouse_locations(location_type);
CREATE INDEX idx_location_hazardous ON warehouse_locations(is_hazardous_zone);

COMMENT ON TABLE warehouse_locations IS 'Warehouse storage locations hierarchy';

-- ============================================
-- TABLE 6: SKUS (Stock Keeping Unit)
-- ============================================
CREATE TABLE skus (
    sku_id BIGSERIAL PRIMARY KEY,
    sku_code VARCHAR(100) UNIQUE NOT NULL,
    product_name VARCHAR(300) NOT NULL,
    category_id BIGINT REFERENCES categories(category_id),
    
    -- Chemical information
    chemical_formula VARCHAR(200),
    hazard_class VARCHAR(100),
    is_hazardous BOOLEAN DEFAULT FALSE,
    storage_temp_min DECIMAL(5,2),
    storage_temp_max DECIMAL(5,2),
    incompatible_chemicals TEXT[],
    
    -- Unit and packaging
    unit VARCHAR(50) NOT NULL,
    unit_volume DECIMAL(10,2),
    unit_weight DECIMAL(10,2),
    
    -- Inventory thresholds
    min_quantity INTEGER DEFAULT 10,
    max_quantity INTEGER DEFAULT 1000,
    reorder_point INTEGER DEFAULT 20,
    
    -- ABC Classification
    abc_class VARCHAR(1) DEFAULT 'C',
    cycle_count_frequency_days INTEGER DEFAULT 180,
    last_cycle_count_date DATE,
    next_cycle_count_date DATE,
    
    -- Barcode
    barcode VARCHAR(100) UNIQUE,
    barcode_type VARCHAR(20),
    
    -- Pricing
    cost_price DECIMAL(15,2),
    selling_price DECIMAL(15,2),
    
    -- Metadata
    description TEXT,
    image_url TEXT,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(user_id),
    
    -- Constraints
    CONSTRAINT chk_sku_unit CHECK (unit IN ('CAN', 'BOTTLE', 'BOX', 'PALLET', 'PIECE')),
    CONSTRAINT chk_sku_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED')),
    CONSTRAINT chk_sku_abc_class CHECK (abc_class IN ('A', 'B', 'C')),
    CONSTRAINT chk_sku_thresholds CHECK (min_quantity <= reorder_point AND reorder_point <= max_quantity)
);

CREATE INDEX idx_skus_code ON skus(sku_code);
CREATE INDEX idx_skus_category ON skus(category_id);
CREATE INDEX idx_skus_barcode ON skus(barcode);
CREATE INDEX idx_skus_abc_class ON skus(abc_class);
CREATE INDEX idx_skus_status ON skus(status);
CREATE INDEX idx_skus_name ON skus USING gin(to_tsvector('english', product_name));

COMMENT ON TABLE skus IS 'Product master data (SKU - Stock Keeping Unit)';

-- ============================================
-- TABLE 7: INVENTORY_LOTS
-- ============================================
CREATE TABLE inventory_lots (
    lot_id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_number VARCHAR(100) NOT NULL,
    
    -- Dates
    manufacture_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    received_date DATE NOT NULL,
    
    -- Quantities
    quantity_received INTEGER NOT NULL,
    quantity_available INTEGER NOT NULL,
    quantity_reserved INTEGER DEFAULT 0,
    
    -- Source
    supplier_name VARCHAR(200),
    supplier_batch_number VARCHAR(100),
    inbound_document_id BIGINT,
    
    -- Status
    status VARCHAR(50) DEFAULT 'ACTIVE',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_lot_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'RECALLED', 'DEPLETED')),
    CONSTRAINT chk_lot_quantities CHECK (
        quantity_available >= 0 AND 
        quantity_reserved >= 0 AND
        quantity_available + quantity_reserved <= quantity_received
    ),
    CONSTRAINT chk_lot_dates CHECK (expiry_date > manufacture_date),
    UNIQUE(sku_id, lot_number)
);

CREATE INDEX idx_lots_sku ON inventory_lots(sku_id);
CREATE INDEX idx_lots_number ON inventory_lots(lot_number);
CREATE INDEX idx_lots_expiry ON inventory_lots(expiry_date);
CREATE INDEX idx_lots_status ON inventory_lots(status);
CREATE INDEX idx_lots_available ON inventory_lots(quantity_available) WHERE quantity_available > 0;

COMMENT ON TABLE inventory_lots IS 'Lot/Batch tracking for products with expiry dates';

-- ============================================
-- TABLE 8: INVENTORY
-- ============================================
CREATE TABLE inventory (
    inventory_id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT NOT NULL UNIQUE REFERENCES skus(sku_id),
    
    available INTEGER DEFAULT 0,
    reserved INTEGER DEFAULT 0,
    in_transit INTEGER DEFAULT 0,
    defective INTEGER DEFAULT 0,
    quarantine INTEGER DEFAULT 0,
    
    total_value DECIMAL(15,2) DEFAULT 0,
    
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_inventory_quantities CHECK (
        available >= 0 AND 
        reserved >= 0 AND 
        in_transit >= 0 AND 
        defective >= 0 AND
        quarantine >= 0
    )
);

CREATE INDEX idx_inventory_sku ON inventory(sku_id);
CREATE INDEX idx_inventory_available ON inventory(available);

COMMENT ON TABLE inventory IS 'Aggregated inventory by SKU';

-- ============================================
-- TABLE 9: INVENTORY_BY_LOCATION
-- ============================================
CREATE TABLE inventory_by_location (
    id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    location_id BIGINT NOT NULL REFERENCES warehouse_locations(location_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    
    quantity INTEGER NOT NULL DEFAULT 0,
    
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(user_id),
    
    UNIQUE(sku_id, location_id, lot_id),
    CONSTRAINT chk_inv_loc_quantity CHECK (quantity >= 0)
);

CREATE INDEX idx_inv_loc_sku ON inventory_by_location(sku_id);
CREATE INDEX idx_inv_loc_location ON inventory_by_location(location_id);
CREATE INDEX idx_inv_loc_lot ON inventory_by_location(lot_id);

COMMENT ON TABLE inventory_by_location IS 'Inventory tracking by location and lot';

-- ============================================
-- TABLE 10: DOCUMENTS
-- ============================================
CREATE TABLE documents (
    document_id BIGSERIAL PRIMARY KEY,
    document_code VARCHAR(50) UNIQUE NOT NULL,
    doc_type VARCHAR(50) NOT NULL,
    sub_type VARCHAR(50),
    
    status VARCHAR(50) DEFAULT 'DRAFT',
    
    reference_code VARCHAR(100),
    notes TEXT,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    
    submitted_at TIMESTAMP,
    submitted_by BIGINT REFERENCES users(user_id),
    
    approved_at TIMESTAMP,
    approved_by BIGINT REFERENCES users(user_id),
    approval_notes TEXT,
    
    confirmed_at TIMESTAMP,
    confirmed_by BIGINT REFERENCES users(user_id),
    
    rejected_at TIMESTAMP,
    rejected_by BIGINT REFERENCES users(user_id),
    rejection_reason TEXT,
    
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_doc_type CHECK (doc_type IN ('INBOUND', 'OUTBOUND', 'RETURN', 'STOCK_CHECK', 'ADJUSTMENT')),
    CONSTRAINT chk_doc_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'RECEIVED', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_doc_code ON documents(document_code);
CREATE INDEX idx_doc_type ON documents(doc_type);
CREATE INDEX idx_doc_status ON documents(status);
CREATE INDEX idx_doc_created_at ON documents(created_at DESC);
CREATE INDEX idx_doc_created_by ON documents(created_by);

COMMENT ON TABLE documents IS 'All warehouse documents (inbound, outbound, returns, stock checks)';

-- ============================================
-- TABLE 11: DOCUMENT_ITEMS
-- ============================================
CREATE TABLE document_items (
    item_id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    location_id BIGINT REFERENCES warehouse_locations(location_id),
    
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(15,2),
    total_price DECIMAL(15,2),
    
    -- For stock checks
    expected_qty INTEGER,
    actual_qty INTEGER,
    discrepancy INTEGER,
    
    notes TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_doc_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_doc_items_doc ON document_items(document_id);
CREATE INDEX idx_doc_items_sku ON document_items(sku_id);
CREATE INDEX idx_doc_items_lot ON document_items(lot_id);

COMMENT ON TABLE document_items IS 'Line items for documents';

-- ============================================
-- TABLE 12: PICKING_TASKS
-- ============================================
CREATE TABLE picking_tasks (
    task_id BIGSERIAL PRIMARY KEY,
    task_code VARCHAR(50) UNIQUE NOT NULL,
    document_id BIGINT NOT NULL REFERENCES documents(document_id),
    
    assigned_to BIGINT REFERENCES users(user_id),
    status VARCHAR(50) DEFAULT 'PENDING',
    
    estimated_time_minutes INTEGER,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    
    CONSTRAINT chk_picking_status CHECK (status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_picking_task_doc ON picking_tasks(document_id);
CREATE INDEX idx_picking_task_assigned ON picking_tasks(assigned_to);
CREATE INDEX idx_picking_task_status ON picking_tasks(status);

COMMENT ON TABLE picking_tasks IS 'Picking tasks for warehouse execution';

-- ============================================
-- TABLE 13: PICKING_ITEMS
-- ============================================
CREATE TABLE picking_items (
    item_id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES picking_tasks(task_id) ON DELETE CASCADE,
    document_item_id BIGINT NOT NULL REFERENCES document_items(item_id),
    
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    location_id BIGINT NOT NULL REFERENCES warehouse_locations(location_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    
    quantity_required INTEGER NOT NULL,
    quantity_picked INTEGER DEFAULT 0,
    
    picking_sequence INTEGER,
    status VARCHAR(50) DEFAULT 'PENDING',
    
    picked_at TIMESTAMP,
    picked_by BIGINT REFERENCES users(user_id),
    
    CONSTRAINT chk_picking_item_status CHECK (status IN ('PENDING', 'PICKED', 'SHORT_PICKED'))
);

CREATE INDEX idx_picking_items_task ON picking_items(task_id);
CREATE INDEX idx_picking_items_location ON picking_items(location_id);
CREATE INDEX idx_picking_items_sequence ON picking_items(picking_sequence);

COMMENT ON TABLE picking_items IS 'Individual items to pick in a picking task';

-- ============================================
-- TABLE 14: PACKING_BOXES
-- ============================================
CREATE TABLE packing_boxes (
    box_id BIGSERIAL PRIMARY KEY,
    box_code VARCHAR(50) UNIQUE NOT NULL,
    document_id BIGINT NOT NULL REFERENCES documents(document_id),
    
    box_type VARCHAR(50),
    weight_kg DECIMAL(10,2),
    dimensions VARCHAR(50),
    
    tracking_number VARCHAR(100),
    carrier VARCHAR(100),
    
    status VARCHAR(50) DEFAULT 'PACKING',
    
    packed_by BIGINT REFERENCES users(user_id),
    packed_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_packing_status CHECK (status IN ('PACKING', 'SEALED', 'SHIPPED'))
);

CREATE INDEX idx_packing_box_doc ON packing_boxes(document_id);
CREATE INDEX idx_packing_box_tracking ON packing_boxes(tracking_number);

COMMENT ON TABLE packing_boxes IS 'Packing boxes for outbound shipments';

-- ============================================
-- TABLE 15: PACKING_ITEMS
-- ============================================
CREATE TABLE packing_items (
    id BIGSERIAL PRIMARY KEY,
    box_id BIGINT NOT NULL REFERENCES packing_boxes(box_id) ON DELETE CASCADE,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    quantity INTEGER NOT NULL
);

CREATE INDEX idx_packing_items_box ON packing_items(box_id);

COMMENT ON TABLE packing_items IS 'Items packed in each box';

-- ============================================
-- TABLE 16: INVENTORY_TRANSACTIONS
-- ============================================
CREATE TABLE inventory_transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    transaction_code VARCHAR(50) UNIQUE NOT NULL,
    
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    location_id BIGINT REFERENCES warehouse_locations(location_id),
    
    trans_type VARCHAR(50) NOT NULL,
    
    quantity_change INTEGER NOT NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    
    document_id BIGINT REFERENCES documents(document_id),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    
    CONSTRAINT chk_trans_type CHECK (trans_type IN ('IMPORT', 'EXPORT', 'RETURN', 'ADJUSTMENT', 'RESERVE', 'RELEASE'))
);

CREATE INDEX idx_trans_sku ON inventory_transactions(sku_id);
CREATE INDEX idx_trans_lot ON inventory_transactions(lot_id);
CREATE INDEX idx_trans_type ON inventory_transactions(trans_type);
CREATE INDEX idx_trans_date ON inventory_transactions(created_at DESC);
CREATE INDEX idx_trans_document ON inventory_transactions(document_id);

COMMENT ON TABLE inventory_transactions IS 'Immutable log of all inventory movements';

-- ============================================
-- TABLE 17: INVENTORY_ALERTS
-- ============================================
CREATE TABLE inventory_alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT NOT NULL REFERENCES skus(sku_id),
    lot_id BIGINT REFERENCES inventory_lots(lot_id),
    
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    
    message TEXT NOT NULL,
    current_value INTEGER,
    threshold_value INTEGER,
    
    status VARCHAR(50) DEFAULT 'ACTIVE',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    acknowledged_by BIGINT REFERENCES users(user_id),
    resolved_at TIMESTAMP,
    
    CONSTRAINT chk_alert_type CHECK (alert_type IN ('LOW_STOCK', 'OUT_OF_STOCK', 'OVERSTOCK', 'NEAR_EXPIRY', 'EXPIRED')),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_alert_status CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED'))
);

CREATE INDEX idx_alerts_sku ON inventory_alerts(sku_id);
CREATE INDEX idx_alerts_type ON inventory_alerts(alert_type);
CREATE INDEX idx_alerts_status ON inventory_alerts(status);
CREATE INDEX idx_alerts_severity ON inventory_alerts(severity);
CREATE INDEX idx_alerts_created ON inventory_alerts(created_at DESC);

COMMENT ON TABLE inventory_alerts IS 'System-generated alerts for inventory issues';

-- ============================================
-- TABLE 18: AUDIT_LOGS
-- ============================================
CREATE TABLE audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    action VARCHAR(100) NOT NULL,
    
    entity_type VARCHAR(100),
    entity_id BIGINT,
    
    description TEXT,
    
    ip_address VARCHAR(50),
    user_agent TEXT,
    
    old_value TEXT,
    new_value TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_action CHECK (action IN (
        'LOGIN', 'LOGOUT', 'LOGIN_FAILED', 'FIRST_LOGIN_VERIFY', 
        'PASSWORD_RESET', 'PASSWORD_CHANGE',
        'CREATE_USER', 'UPDATE_USER_ROLE', 'UPDATE_USER_STATUS', 'UPDATE_PROFILE',
        'CREATE_SKU', 'UPDATE_SKU', 'DELETE_SKU',
        'CREATE_DOCUMENT', 'APPROVE_DOCUMENT', 'REJECT_DOCUMENT', 'CONFIRM_DOCUMENT',
        'CREATE_LOCATION', 'UPDATE_LOCATION',
        'INVENTORY_ADJUSTMENT'
    ))
);

CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);

COMMENT ON TABLE audit_logs IS 'Audit trail for all system actions';

-- ============================================
-- TABLE 19: SUPPLIERS (Optional - for future)
-- ============================================
CREATE TABLE suppliers (
    supplier_id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(50) UNIQUE NOT NULL,
    supplier_name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(200),
    email VARCHAR(100),
    phone VARCHAR(20),
    address TEXT,
    tax_code VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suppliers_code ON suppliers(supplier_code);
CREATE INDEX idx_suppliers_active ON suppliers(is_active);

COMMENT ON TABLE suppliers IS 'Supplier master data';

-- ============================================
-- TABLE 20: PURCHASE_ORDERS (Optional - for future)
-- ============================================
CREATE TABLE purchase_orders (
    po_id BIGSERIAL PRIMARY KEY,
    po_code VARCHAR(50) UNIQUE NOT NULL,
    supplier_id BIGINT REFERENCES suppliers(supplier_id),
    
    status VARCHAR(50) DEFAULT 'DRAFT',
    total_value DECIMAL(15,2),
    
    expected_delivery_date DATE,
    actual_delivery_date DATE,
    
    notes TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    
    approved_at TIMESTAMP,
    approved_by BIGINT REFERENCES users(user_id),
    
    CONSTRAINT chk_po_status CHECK (status IN ('DRAFT', 'SENT', 'CONFIRMED', 'RECEIVED', 'CANCELLED'))
);

CREATE INDEX idx_po_code ON purchase_orders(po_code);
CREATE INDEX idx_po_supplier ON purchase_orders(supplier_id);
CREATE INDEX idx_po_status ON purchase_orders(status);

COMMENT ON TABLE purchase_orders IS 'Purchase orders to suppliers';

-- ============================================
-- FUNCTIONS & TRIGGERS
-- ============================================

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all tables with updated_at
CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_categories_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_skus_updated_at
    BEFORE UPDATE ON skus
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_inventory_lots_updated_at
    BEFORE UPDATE ON inventory_lots
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Auto-generate document code
CREATE OR REPLACE FUNCTION generate_document_code()
RETURNS TRIGGER AS $$
DECLARE
    prefix VARCHAR(10);
    date_part VARCHAR(8);
    sequence_num INTEGER;
    new_code VARCHAR(50);
BEGIN
    -- Determine prefix based on doc_type
    prefix := CASE NEW.doc_type
        WHEN 'INBOUND' THEN 'IMP'
        WHEN 'OUTBOUND' THEN 'EXP'
        WHEN 'RETURN' THEN 'RET'
        WHEN 'STOCK_CHECK' THEN 'CHK'
        WHEN 'ADJUSTMENT' THEN 'ADJ'
        ELSE 'DOC'
    END;
    
    -- Date part: YYYYMMDD
    date_part := TO_CHAR(CURRENT_DATE, 'YYYYMMDD');
    
    -- Get sequence for today
    SELECT COUNT(*) + 1 INTO sequence_num
    FROM documents
    WHERE doc_type = NEW.doc_type
      AND DATE(created_at) = CURRENT_DATE;
    
    -- Generate code: PREFIX-YYYYMMDD-NNNN
    new_code := prefix || '-' || date_part || '-' || LPAD(sequence_num::TEXT, 4, '0');
    
    NEW.document_code := new_code;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_generate_document_code
    BEFORE INSERT ON documents
    FOR EACH ROW
    WHEN (NEW.document_code IS NULL OR NEW.document_code = '')
    EXECUTE FUNCTION generate_document_code();

-- Auto-generate transaction code
CREATE OR REPLACE FUNCTION generate_transaction_code()
RETURNS TRIGGER AS $$
DECLARE
    new_code VARCHAR(50);
    sequence_num INTEGER;
BEGIN
    -- Get sequence for today
    SELECT COUNT(*) + 1 INTO sequence_num
    FROM inventory_transactions
    WHERE DATE(created_at) = CURRENT_DATE;
    
    -- Generate code: TXN-YYYYMMDD-NNNNNN
    new_code := 'TXN-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD(sequence_num::TEXT, 6, '0');
    
    NEW.transaction_code := new_code;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_generate_transaction_code
    BEFORE INSERT ON inventory_transactions
    FOR EACH ROW
    WHEN (NEW.transaction_code IS NULL OR NEW.transaction_code = '')
    EXECUTE FUNCTION generate_transaction_code();

-- Update inventory.last_updated when inventory_by_location changes
CREATE OR REPLACE FUNCTION update_inventory_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE inventory
    SET last_updated = CURRENT_TIMESTAMP
    WHERE sku_id = COALESCE(NEW.sku_id, OLD.sku_id);
    
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_inventory_timestamp
    AFTER INSERT OR UPDATE OR DELETE ON inventory_by_location
    FOR EACH ROW
    EXECUTE FUNCTION update_inventory_timestamp();

-- ============================================
-- UTILITY FUNCTIONS
-- ============================================

-- Clean expired OTPs
CREATE OR REPLACE FUNCTION clean_expired_otps()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM otps 
    WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '1 day';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Clean expired sessions
CREATE OR REPLACE FUNCTION clean_expired_sessions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM active_sessions 
    WHERE expires_at < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Calculate ABC classification
CREATE OR REPLACE FUNCTION calculate_abc_classification()
RETURNS void AS $$
DECLARE
    total_sales DECIMAL(15,2);
    sku_record RECORD;
    cumulative_percent DECIMAL(5,2) := 0;
BEGIN
    -- Calculate total sales value (last 12 months)
    SELECT COALESCE(SUM(di.quantity * di.unit_price), 0) INTO total_sales
    FROM document_items di
    JOIN documents d ON di.document_id = d.document_id
    WHERE d.doc_type = 'OUTBOUND'
      AND d.status = 'CONFIRMED'
      AND d.created_at >= CURRENT_DATE - INTERVAL '12 months';
    
    IF total_sales = 0 THEN
        RETURN;
    END IF;
    
    -- Loop SKUs ordered by sales value DESC
    FOR sku_record IN 
        SELECT 
            s.sku_id,
            COALESCE(SUM(di.quantity * di.unit_price), 0) AS sales_value,
            COALESCE(SUM(di.quantity * di.unit_price) / NULLIF(total_sales, 0) * 100, 0) AS percent_of_total
        FROM skus s
        LEFT JOIN document_items di ON s.sku_id = di.sku_id
        LEFT JOIN documents d ON di.document_id = d.document_id
        WHERE d.doc_type = 'OUTBOUND' 
          AND d.status = 'CONFIRMED'
          AND d.created_at >= CURRENT_DATE - INTERVAL '12 months'
        GROUP BY s.sku_id
        ORDER BY sales_value DESC
    LOOP
        cumulative_percent := cumulative_percent + sku_record.percent_of_total;
        
        IF cumulative_percent <= 80 THEN
            -- Top 80% sales → Class A
            UPDATE skus 
            SET abc_class = 'A', 
                cycle_count_frequency_days = 30
            WHERE sku_id = sku_record.sku_id;
        ELSIF cumulative_percent <= 95 THEN
            -- Next 15% → Class B
            UPDATE skus 
            SET abc_class = 'B', 
                cycle_count_frequency_days = 90
            WHERE sku_id = sku_record.sku_id;
        ELSE
            -- Bottom 5% → Class C
            UPDATE skus 
            SET abc_class = 'C', 
                cycle_count_frequency_days = 180
            WHERE sku_id = sku_record.sku_id;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- SEED DATA
-- ============================================

-- Insert default admin user (password: Admin@123)
INSERT INTO users (
    email, 
    password_hash, 
    full_name, 
    role, 
    status, 
    is_first_login,
    created_at
) VALUES (
    'admin@warehouse.com',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 
    -- This is bcrypt hash for "Admin@123"
    'System Administrator',
    'MANAGER',
    'ACTIVE',
    FALSE,
    CURRENT_TIMESTAMP
);

-- Insert categories
INSERT INTO categories (category_code, category_name, description, display_order) VALUES
('SWAT', 'SWAT - Hóa chất tẩy rửa', 'Dòng sản phẩm tẩy rửa công nghiệp SWAT', 1),
('SWAT_CLEAN', 'SWAT - Nước giặt xả', 'Nước giặt xả các loại', 2),
('SWAT_FLOOR', 'SWAT - Nước lau sàn', 'Nước lau sàn đa năng', 3),
('SWAT_GLASS', 'SWAT - Nước lau kính', 'Nước lau kính chuyên dụng', 4),
('SWAT_BATH', 'SWAT - Tẩy nhà tắm', 'Tẩy rửa nhà tắm và toilet', 5),
('SWAT_BLEACH', 'SWAT - Nước tẩy trắng', 'Nước tẩy trắng quần áo', 6),
('SWAT_HAND', 'SWAT - Nước rửa tay', 'Nước rửa tay diệt khuẩn', 7),
('SWAT_DISH', 'SWAT - Nước rửa chén', 'Nước rửa chén bát', 8),
('GRASSE', 'GRASSE - Chăm sóc cá nhân', 'Dòng sản phẩm chăm sóc cá nhân GRASSE', 9),
('GRASSE_SHAMPOO', 'GRASSE - Dầu gội', 'Dầu gội các loại', 10),
('GRASSE_CONDITIONER', 'GRASSE - Dầu xả', 'Dầu xả tóc', 11),
('GRASSE_TREATMENT', 'GRASSE - Dưỡng tóc', 'Kem ủ và dưỡng tóc', 12),
('GRASSE_SHOWER', 'GRASSE - Sữa tắm', 'Sữa tắm các loại', 13),
('GRASSE_SPRAY', 'GRASSE - Xịt quần áo', 'Xịt thơm quần áo', 14);

-- Insert sample warehouse locations
INSERT INTO warehouse_locations (location_code, location_name, location_type, is_hazardous_zone, ventilation_available, fire_suppression_available) VALUES
-- Zone A - For chemicals
('A', 'Zone A - Chemicals', 'ZONE', TRUE, TRUE, TRUE),
('A-01', 'Aisle 01', 'AISLE', TRUE, TRUE, TRUE),
('A-01-01', 'Rack 01', 'RACK', TRUE, TRUE, TRUE),
('A-01-01-01', 'Shelf 01', 'SHELF', TRUE, TRUE, TRUE),
('A-01-01-02', 'Shelf 02', 'SHELF', TRUE, TRUE, TRUE),
-- Zone B - For personal care
('B', 'Zone B - Personal Care', 'ZONE', FALSE, TRUE, FALSE),
('B-01', 'Aisle 01', 'AISLE', FALSE, TRUE, FALSE),
('B-01-01', 'Rack 01', 'RACK', FALSE, TRUE, FALSE),
('B-01-01-01', 'Shelf 01', 'SHELF', FALSE, TRUE, FALSE);

-- Update parent relationships
UPDATE warehouse_locations SET parent_location_id = (SELECT location_id FROM warehouse_locations WHERE location_code = 'A') WHERE location_code = 'A-01';
UPDATE warehouse_locations SET parent_location_id = (SELECT location_id FROM warehouse_locations WHERE location_code = 'A-01') WHERE location_code = 'A-01-01';
UPDATE warehouse_locations SET parent_location_id = (SELECT location_id FROM warehouse_locations WHERE location_code = 'A-01-01') WHERE location_code IN ('A-01-01-01', 'A-01-01-02');
UPDATE warehouse_locations SET parent_location_id = (SELECT location_id FROM warehouse_locations WHERE location_code = 'B') WHERE location_code = 'B-01';
UPDATE warehouse_locations SET parent_location_id = (SELECT location_id FROM warehouse_locations WHERE location_code = 'B-01') WHERE location_code = 'B-01-01';
UPDATE warehouse_locations SET parent_location_id = (SELECT location_id FROM warehouse_locations WHERE location_code = 'B-01-01') WHERE location_code = 'B-01-01-01';

-- Insert sample SKUs
INSERT INTO skus (sku_code, product_name, category_id, unit, is_hazardous, min_quantity, max_quantity, reorder_point, cost_price, selling_price, status, created_by) VALUES
('SWAT-CLEAN-001', 'SWAT - Nước giặt xả 5L', (SELECT category_id FROM categories WHERE category_code = 'SWAT_CLEAN'), 'CAN', TRUE, 20, 500, 50, 85000, 120000, 'ACTIVE', 1),
('SWAT-FLOOR-001', 'SWAT - Nước lau đa năng 5L', (SELECT category_id FROM categories WHERE category_code = 'SWAT_FLOOR'), 'CAN', TRUE, 20, 500, 50, 75000, 110000, 'ACTIVE', 1),
('GRASSE-SHAMPOO-001', 'GRASSE - Dầu gội 500ml', (SELECT category_id FROM categories WHERE category_code = 'GRASSE_SHAMPOO'), 'BOTTLE', FALSE, 50, 1000, 100, 45000, 75000, 'ACTIVE', 1),
('GRASSE-SHOWER-001', 'GRASSE - Sữa tắm 500ml', (SELECT category_id FROM categories WHERE category_code = 'GRASSE_SHOWER'), 'BOTTLE', FALSE, 50, 1000, 100, 40000, 70000, 'ACTIVE', 1);

-- ============================================
-- GRANT PERMISSIONS
-- ============================================
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO postgres;

-- ============================================
-- DATABASE STATISTICS
-- ============================================
SELECT 
    'Database created successfully!' AS status,
    COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE';

SELECT 
    table_name,
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_name = t.table_name) AS column_count
FROM information_schema.tables t
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- ============================================
-- VERIFICATION QUERIES
-- ============================================
SELECT 'Users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'Categories', COUNT(*) FROM categories
UNION ALL
SELECT 'Warehouse Locations', COUNT(*) FROM warehouse_locations
UNION ALL
SELECT 'SKUs', COUNT(*) FROM skus
UNION ALL
SELECT 'All Tables', COUNT(*) FROM information_schema.tables WHERE table_schema = 'public';

-- ============================================
-- END OF SCRIPT
-- ============================================-- ============================================
-- COMPLETE POSTGRESQL SETUP FOR ZONE MANAGEMENT
-- Execute this after main schema is created
-- ============================================

-- ============================================
-- STEP 1: CREATE ZONES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS zones (
    zone_id BIGSERIAL PRIMARY KEY,
    zone_code VARCHAR(50) UNIQUE NOT NULL,
    zone_name VARCHAR(200) NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    zone_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(user_id),
    CONSTRAINT chk_zone_type CHECK (zone_type IN ('INBOUND', 'STORAGE', 'OUTBOUND', 'HOLD', 'DEFECT'))
);

CREATE INDEX idx_zones_code ON zones(zone_code);
CREATE INDEX idx_zones_type ON zones(zone_type);
CREATE INDEX idx_zones_warehouse ON zones(warehouse_code);
CREATE INDEX idx_zones_active ON zones(is_active);

COMMENT ON TABLE zones IS 'Functional zones: ZHC, ZPC, Z-INB, Z-HOLD (MANAGER only)';

-- ============================================
-- STEP 2: CREATE CATEGORY_ZONE_MAPPING TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS category_zone_mapping (
    mapping_id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(category_id) ON DELETE CASCADE,
    zone_id BIGINT NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    priority INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(user_id),
    UNIQUE(category_id, zone_id)
);

CREATE INDEX idx_category_zone_category ON category_zone_mapping(category_id);
CREATE INDEX idx_category_zone_zone ON category_zone_mapping(zone_id);
CREATE INDEX idx_category_zone_active ON category_zone_mapping(is_active);

COMMENT ON TABLE category_zone_mapping IS 'Maps categories to zones (HC→ZHC, PC→ZPC)';

-- ============================================
-- STEP 3: ADD TRIGGERS
-- ============================================
CREATE TRIGGER trigger_zones_updated_at
    BEFORE UPDATE ON zones
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_category_zone_mapping_updated_at
    BEFORE UPDATE ON category_zone_mapping
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- STEP 4: INSERT SAMPLE ZONES
-- ============================================
INSERT INTO zones (zone_code, zone_name, warehouse_code, zone_type, created_by, updated_by) 
VALUES
    ('ZHC', 'Home Care Storage Zone', 'WH01', 'STORAGE', 1, 1),
    ('ZPC', 'Personal Care Storage Zone', 'WH01', 'STORAGE', 1, 1),
    ('Z-INB', 'Inbound Staging Area', 'WH01', 'INBOUND', 1, 1),
    ('Z-OUT', 'Outbound Staging Area', 'WH01', 'OUTBOUND', 1, 1),
    ('Z-HOLD', 'Quality Hold Area', 'WH01', 'HOLD', 1, 1),
    ('Z-DEFECT', 'Defective Items Area', 'WH01', 'DEFECT', 1, 1)
ON CONFLICT (zone_code) DO NOTHING;

-- ============================================
-- STEP 5: INSERT SAMPLE CATEGORIES (Full Hierarchy)
-- ============================================

-- Parent categories
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by) 
VALUES
    ('HC', 'Home Care', 'Home care products', NULL, 1, 1),
    ('PC', 'Personal Care', 'Personal care products', NULL, 1, 1)
ON CONFLICT (category_code) DO NOTHING;

-- Child categories for Home Care
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'HC-FLOOR', 'Nước lau sàn', 'Floor cleaning products', category_id, 1, 1
FROM categories WHERE category_code = 'HC'
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'HC-DISH', 'Nước rửa bát', 'Dishwashing products', category_id, 1, 1
FROM categories WHERE category_code = 'HC'
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'HC-DETERGENT', 'Bột giặt', 'Laundry detergent', category_id, 1, 1
FROM categories WHERE category_code = 'HC'
ON CONFLICT (category_code) DO NOTHING;

-- Child categories for Personal Care
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'PC-SHAMP', 'Dầu gội', 'Shampoo products', category_id, 1, 1
FROM categories WHERE category_code = 'PC'
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'PC-SHOWER', 'Sữa tắm', 'Shower gel products', category_id, 1, 1
FROM categories WHERE category_code = 'PC'
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'PC-TOOTHPASTE', 'Kem đánh răng', 'Toothpaste products', category_id, 1, 1
FROM categories WHERE category_code = 'PC'
ON CONFLICT (category_code) DO NOTHING;

-- ============================================
-- STEP 6: MAP CATEGORIES TO ZONES
-- ============================================

-- Map HC (Home Care) → ZHC
INSERT INTO category_zone_mapping (category_id, zone_id, priority, created_by, updated_by)
SELECT c.category_id, z.zone_id, 1, 1, 1
FROM categories c, zones z
WHERE c.category_code = 'HC' AND z.zone_code = 'ZHC'
ON CONFLICT (category_id, zone_id) DO NOTHING;

-- Map PC (Personal Care) → ZPC
INSERT INTO category_zone_mapping (category_id, zone_id, priority, created_by, updated_by)
SELECT c.category_id, z.zone_id, 1, 1, 1
FROM categories c, zones z
WHERE c.category_code = 'PC' AND z.zone_code = 'ZPC'
ON CONFLICT (category_id, zone_id) DO NOTHING;

-- ============================================
-- VERIFICATION QUERIES
-- ============================================

-- View all zones
SELECT zone_id, zone_code, zone_name, zone_type, is_active 
FROM zones 
ORDER BY zone_code;

-- View category hierarchy
SELECT 
    c.category_id,
    c.category_code,
    c.category_name,
    COALESCE(p.category_code, 'NULL') as parent_code,
    p.category_name as parent_name
FROM categories c
LEFT JOIN categories p ON c.parent_category_id = p.category_id
ORDER BY COALESCE(c.parent_category_id, 0), c.category_code;

-- View category-zone mappings
SELECT 
    c.category_code,
    c.category_name,
    z.zone_code,
    z.zone_name,
    czm.priority
FROM category_zone_mapping czm
JOIN categories c ON czm.category_id = c.category_id
JOIN zones z ON czm.zone_id = z.zone_id
WHERE czm.is_active = TRUE
ORDER BY z.zone_code, c.category_code;

-- ============================================
-- USEFUL QUERIES FOR APPLICATION
-- ============================================

-- Query 1: Find zone for a category (including inherited from parent)
-- Example: Find zone for 'HC-FLOOR'
SELECT z.zone_code, z.zone_name
FROM categories c
LEFT JOIN categories parent ON c.parent_category_id = parent.category_id
LEFT JOIN category_zone_mapping czm_direct ON c.category_id = czm_direct.category_id
LEFT JOIN category_zone_mapping czm_parent ON parent.category_id = czm_parent.category_id
LEFT JOIN zones z ON COALESCE(czm_direct.zone_id, czm_parent.zone_id) = z.zone_id
WHERE c.category_code = 'HC-FLOOR'
  AND z.zone_type = 'STORAGE'
  AND z.is_active = TRUE;

-- Query 2: Find all categories in a zone
SELECT c.category_code, c.category_name
FROM zones z
JOIN category_zone_mapping czm ON z.zone_id = czm.zone_id
JOIN categories c ON czm.category_id = c.category_id
WHERE z.zone_code = 'ZHC' AND czm.is_active = TRUE;

-- Query 3: Find zone for a SKU (through its category)
-- SELECT z.zone_code, z.zone_name, c.category_name
-- FROM skus s
-- JOIN categories c ON s.category_id = c.category_id
-- LEFT JOIN categories parent ON c.parent_category_id = parent.category_id
-- LEFT JOIN category_zone_mapping czm_direct ON c.category_id = czm_direct.category_id
-- LEFT JOIN category_zone_mapping czm_parent ON parent.category_id = czm_parent.category_id
-- JOIN zones z ON COALESCE(czm_direct.zone_id, czm_parent.zone_id) = z.zone_id
-- WHERE s.sku_code = 'SKU-FLOOR-001'
-- ORDER BY czm_direct.priority, czm_parent.priority
-- LIMIT 1;


-- ==========================================================================================
-- ============================================
-- ZONE MANAGEMENT - MANUAL DATABASE UPDATE
-- Copy this entire file and paste into your PostgreSQL client
-- Or append to sep26_warehousemanagement.sql
-- ============================================
-- ============================================
-- STEP 1: CREATE ZONES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS zones (
    zone_id BIGSERIAL PRIMARY KEY,
    zone_code VARCHAR(50) UNIQUE NOT NULL,
    zone_name VARCHAR(200) NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    zone_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(user_id),
    CONSTRAINT chk_zone_type CHECK (zone_type IN ('INBOUND', 'STORAGE', 'OUTBOUND', 'HOLD', 'DEFECT'))
);
CREATE INDEX idx_zones_code ON zones(zone_code);
CREATE INDEX idx_zones_type ON zones(zone_type);
CREATE INDEX idx_zones_warehouse ON zones(warehouse_code);
CREATE INDEX idx_zones_active ON zones(is_active);
COMMENT ON TABLE zones IS 'Functional zones: ZHC, ZPC, Z-INB, Z-HOLD (MANAGER only)';
-- ============================================
-- STEP 2: CREATE CATEGORY_ZONE_MAPPING TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS category_zone_mapping (
    mapping_id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(category_id) ON DELETE CASCADE,
    zone_id BIGINT NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    priority INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(user_id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(user_id),
    UNIQUE(category_id, zone_id)
);
CREATE INDEX idx_category_zone_category ON category_zone_mapping(category_id);
CREATE INDEX idx_category_zone_zone ON category_zone_mapping(zone_id);
CREATE INDEX idx_category_zone_active ON category_zone_mapping(is_active);
COMMENT ON TABLE category_zone_mapping IS 'Maps categories to zones (HC→ZHC, PC→ZPC)';
-- ============================================
-- STEP 3: ADD TRIGGERS
-- ============================================
CREATE TRIGGER trigger_zones_updated_at
    BEFORE UPDATE ON zones
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trigger_category_zone_mapping_updated_at
    BEFORE UPDATE ON category_zone_mapping
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
-- ============================================
-- STEP 4: INSERT SAMPLE ZONES
-- ============================================
INSERT INTO zones (zone_code, zone_name, warehouse_code, zone_type, created_by, updated_by) 
VALUES
    ('ZHC', 'Home Care Storage Zone', 'WH01', 'STORAGE', 1, 1),
    ('ZPC', 'Personal Care Storage Zone', 'WH01', 'STORAGE', 1, 1),
    ('Z-INB', 'Inbound Staging Area', 'WH01', 'INBOUND', 1, 1),
    ('Z-OUT', 'Outbound Staging Area', 'WH01', 'OUTBOUND', 1, 1),
    ('Z-HOLD', 'Quality Hold Area', 'WH01', 'HOLD', 1, 1),
    ('Z-DEFECT', 'Defective Items Area', 'WH01', 'DEFECT', 1, 1)
ON CONFLICT (zone_code) DO NOTHING;
-- ============================================
-- STEP 5: INSERT SAMPLE CATEGORIES (Full Hierarchy)
-- ============================================
-- Parent categories
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by) 
VALUES
    ('HC', 'Home Care', 'Home care products', NULL, 1, 1),
    ('PC', 'Personal Care', 'Personal care products', NULL, 1, 1)
ON CONFLICT (category_code) DO NOTHING;
-- Child categories for Home Care
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'HC-FLOOR', 'Nước lau sàn', 'Floor cleaning products', category_id, 1, 1
FROM categories WHERE category_code = 'HC'
ON CONFLICT (category_code) DO NOTHING;
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'HC-DISH', 'Nước rửa bát', 'Dishwashing products', category_id, 1, 1
FROM categories WHERE category_code = 'HC'
ON CONFLICT (category_code) DO NOTHING;
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'HC-DETERGENT', 'Bột giặt', 'Laundry detergent', category_id, 1, 1
FROM categories WHERE category_code = 'HC'
ON CONFLICT (category_code) DO NOTHING;
-- Child categories for Personal Care
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'PC-SHAMP', 'Dầu gội', 'Shampoo products', category_id, 1, 1
FROM categories WHERE category_code = 'PC'
ON CONFLICT (category_code) DO NOTHING;
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'PC-SHOWER', 'Sữa tắm', 'Shower gel products', category_id, 1, 1
FROM categories WHERE category_code = 'PC'
ON CONFLICT (category_code) DO NOTHING;
INSERT INTO categories (category_code, category_name, description, parent_category_id, created_by, updated_by)
SELECT 'PC-TOOTHPASTE', 'Kem đánh răng', 'Toothpaste products', category_id, 1, 1
FROM categories WHERE category_code = 'PC'
ON CONFLICT (category_code) DO NOTHING;
-- ============================================
-- STEP 6: MAP CATEGORIES TO ZONES
-- ============================================
-- Map HC (Home Care) → ZHC
INSERT INTO category_zone_mapping (category_id, zone_id, priority, created_by, updated_by)
SELECT c.category_id, z.zone_id, 1, 1, 1
FROM categories c, zones z
WHERE c.category_code = 'HC' AND z.zone_code = 'ZHC'
ON CONFLICT (category_id, zone_id) DO NOTHING;
-- Map PC (Personal Care) → ZPC
INSERT INTO category_zone_mapping (category_id, zone_id, priority, created_by, updated_by)
SELECT c.category_id, z.zone_id, 1, 1, 1
FROM categories c, zones z
WHERE c.category_code = 'PC' AND z.zone_code = 'ZPC'
ON CONFLICT (category_id, zone_id) DO NOTHING;
-- ============================================
-- VERIFICATION QUERIES
-- ============================================
-- View all zones
SELECT zone_id, zone_code, zone_name, zone_type, is_active 
FROM zones 
ORDER BY zone_code;
-- View category hierarchy
SELECT 
    c.category_id,
    c.category_code,
    c.category_name,
    COALESCE(p.category_code, 'NULL') as parent_code,
    p.category_name as parent_name
FROM categories c
LEFT JOIN categories p ON c.parent_category_id = p.category_id
ORDER BY COALESCE(c.parent_category_id, 0), c.category_code;
-- View category-zone mappings
SELECT 
    c.category_code,
    c.category_name,
    z.zone_code,
    z.zone_name,
    czm.priority
FROM category_zone_mapping czm
JOIN categories c ON czm.category_id = c.category_id
JOIN zones z ON czm.zone_id = z.zone_id
WHERE czm.is_active = TRUE
ORDER BY z.zone_code, c.category_code;
-- ============================================
-- USEFUL QUERIES FOR APPLICATION
-- ============================================
-- Query 1: Find zone for a category (including inherited from parent)
-- Example: Find zone for 'HC-FLOOR'
SELECT z.zone_code, z.zone_name
FROM categories c
LEFT JOIN categories parent ON c.parent_category_id = parent.category_id
LEFT JOIN category_zone_mapping czm_direct ON c.category_id = czm_direct.category_id
LEFT JOIN category_zone_mapping czm_parent ON parent.category_id = czm_parent.category_id
LEFT JOIN zones z ON COALESCE(czm_direct.zone_id, czm_parent.zone_id) = z.zone_id
WHERE c.category_code = 'HC-FLOOR'
  AND z.zone_type = 'STORAGE'
  AND z.is_active = TRUE;
-- Query 2: Find all categories in a zone
SELECT c.category_code, c.category_name
FROM zones z
JOIN category_zone_mapping czm ON z.zone_id = czm.zone_id
JOIN categories c ON czm.category_id = c.category_id
WHERE z.zone_code = 'ZHC' AND czm.is_active = TRUE;
-- Query 3: Find zone for a SKU (through its category)
-- SELECT z.zone_code, z.zone_name, c.category_name
-- FROM skus s
-- JOIN categories c ON s.category_id = c.category_id
-- LEFT JOIN categories parent ON c.parent_category_id = parent.category_id
-- LEFT JOIN category_zone_mapping czm_direct ON c.category_id = czm_direct.category_id
-- LEFT JOIN category_zone_mapping czm_parent ON parent.category_id = czm_parent.category_id
-- JOIN zones z ON COALESCE(czm_direct.zone_id, czm_parent.zone_id) = z.zone_id
-- WHERE s.sku_code = 'SKU-FLOOR-001'
-- ORDER BY czm_direct.priority, czm_parent.priority
-- LIMIT 1;
-- ============================================
-- END OF ZONE MANAGEMENT SCHEMA
-- ============================================