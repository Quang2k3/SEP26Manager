--
-- PostgreSQL database dump
--

\restrict DLupeTyv82GatHcqDa9Y0xv7EThKlj8dW3GV5VR8D1pL1Er27LeeSJScibf1s6o

-- Dumped from database version 17.7
-- Dumped by pg_dump version 17.7

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: adjustment_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.adjustment_items (
    adjustment_item_id bigint NOT NULL,
    adjustment_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    location_id bigint NOT NULL,
    delta_qty numeric(12,2) NOT NULL,
    note text
);


--
-- Name: TABLE adjustment_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.adjustment_items IS 'Adjustment line items';


--
-- Name: adjustment_items_adjustment_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.adjustment_items_adjustment_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: adjustment_items_adjustment_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.adjustment_items_adjustment_item_id_seq OWNED BY public.adjustment_items.adjustment_item_id;


--
-- Name: adjustments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.adjustments (
    adjustment_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    adjustment_code character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    reason character varying(500) NOT NULL,
    created_by bigint,
    approved_by bigint,
    approved_at timestamp without time zone,
    posted_at timestamp without time zone,
    note text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE adjustments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.adjustments IS 'Inventory adjustments';


--
-- Name: adjustments_adjustment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.adjustments_adjustment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: adjustments_adjustment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.adjustments_adjustment_id_seq OWNED BY public.adjustments.adjustment_id;


--
-- Name: attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attachments (
    attachment_id bigint NOT NULL,
    file_name character varying(500) NOT NULL,
    file_type character varying(100),
    file_size bigint,
    storage_url text NOT NULL,
    checksum character varying(100),
    uploaded_by bigint,
    uploaded_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE attachments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.attachments IS 'File attachments (SDS, photos, documents)';


--
-- Name: attachments_attachment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.attachments_attachment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: attachments_attachment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.attachments_attachment_id_seq OWNED BY public.attachments.attachment_id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    audit_id bigint NOT NULL,
    entity_name character varying(200) NOT NULL,
    entity_id bigint NOT NULL,
    action character varying(50) NOT NULL,
    old_data jsonb,
    new_data jsonb,
    action_by bigint NOT NULL,
    action_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE audit_logs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_logs IS 'Audit trail for all changes';


--
-- Name: audit_logs_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_audit_id_seq OWNED BY public.audit_logs.audit_id;


--
-- Name: carriers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carriers (
    carrier_id bigint NOT NULL,
    carrier_code character varying(50) NOT NULL,
    carrier_name character varying(300) NOT NULL,
    phone character varying(20),
    email character varying(255),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE carriers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.carriers IS 'Shipping carrier master data';


--
-- Name: carriers_carrier_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.carriers_carrier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: carriers_carrier_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.carriers_carrier_id_seq OWNED BY public.carriers.carrier_id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    category_id bigint NOT NULL,
    category_code character varying(50) NOT NULL,
    category_name character varying(200) NOT NULL,
    parent_category_id bigint,
    description text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.categories IS 'Product categories';


--
-- Name: categories_category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.categories_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.categories_category_id_seq OWNED BY public.categories.category_id;


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    customer_id bigint NOT NULL,
    customer_code character varying(50) NOT NULL,
    customer_name character varying(300) NOT NULL,
    email character varying(255),
    phone character varying(20),
    address text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE customers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.customers IS 'Customer master data';


--
-- Name: customers_customer_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customers_customer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customers_customer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customers_customer_id_seq OWNED BY public.customers.customer_id;


--
-- Name: enum_transitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enum_transitions (
    transition_id bigint NOT NULL,
    enum_type_id bigint NOT NULL,
    from_value_code character varying(100) NOT NULL,
    to_value_code character varying(100) NOT NULL,
    required_permission character varying(100),
    required_role character varying(100),
    is_allowed boolean DEFAULT true NOT NULL,
    description text
);


--
-- Name: TABLE enum_transitions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.enum_transitions IS 'Define valid status transitions (state machine)';


--
-- Name: enum_transitions_transition_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.enum_transitions_transition_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: enum_transitions_transition_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.enum_transitions_transition_id_seq OWNED BY public.enum_transitions.transition_id;


--
-- Name: enum_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enum_types (
    enum_type_id bigint NOT NULL,
    enum_type_code character varying(100) NOT NULL,
    enum_type_name character varying(200) NOT NULL,
    description text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE enum_types; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.enum_types IS 'Master table for all enum types in system';


--
-- Name: enum_types_enum_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.enum_types_enum_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: enum_types_enum_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.enum_types_enum_type_id_seq OWNED BY public.enum_types.enum_type_id;


--
-- Name: enum_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enum_values (
    enum_value_id bigint NOT NULL,
    enum_type_id bigint NOT NULL,
    value_code character varying(100) NOT NULL,
    value_name character varying(200) NOT NULL,
    value_name_vi character varying(200),
    display_order integer DEFAULT 0 NOT NULL,
    color_code character varying(20),
    icon character varying(50),
    badge_style character varying(100),
    is_default boolean DEFAULT false,
    is_terminal boolean DEFAULT false,
    description text,
    active boolean DEFAULT true NOT NULL,
    metadata jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE enum_values; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.enum_values IS 'Actual enum values for each type';


--
-- Name: enum_values_enum_value_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.enum_values_enum_value_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: enum_values_enum_value_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.enum_values_enum_value_id_seq OWNED BY public.enum_values.enum_value_id;


--
-- Name: expiry_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expiry_rules (
    rule_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    warn_days integer DEFAULT 30 NOT NULL,
    severity character varying(50) DEFAULT 'MEDIUM'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE expiry_rules; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.expiry_rules IS 'Expiry alert rules';


--
-- Name: expiry_rules_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.expiry_rules_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: expiry_rules_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.expiry_rules_rule_id_seq OWNED BY public.expiry_rules.rule_id;


--
-- Name: incidents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.incidents (
    incident_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    incident_code character varying(100) NOT NULL,
    incident_type character varying(50) NOT NULL,
    severity character varying(50) DEFAULT 'HIGH'::character varying NOT NULL,
    occurred_at timestamp without time zone NOT NULL,
    location_id bigint,
    description text,
    reported_by bigint,
    attachment_id bigint,
    status character varying(50) DEFAULT 'OPEN'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE incidents; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.incidents IS 'Safety incidents';


--
-- Name: incidents_incident_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.incidents_incident_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: incidents_incident_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.incidents_incident_id_seq OWNED BY public.incidents.incident_id;


--
-- Name: inventory_alerts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_alerts (
    alert_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    alert_type character varying(50) NOT NULL,
    severity character varying(50) DEFAULT 'MEDIUM'::character varying NOT NULL,
    sku_id bigint,
    lot_id bigint,
    location_id bigint,
    message text NOT NULL,
    status character varying(50) DEFAULT 'OPEN'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    resolved_at timestamp without time zone,
    resolved_by bigint,
    dedupe_key character varying(200),
    reference_table character varying(100),
    reference_id bigint
);


--
-- Name: TABLE inventory_alerts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.inventory_alerts IS 'Inventory alerts (expiry, low stock, etc.)';


--
-- Name: inventory_alerts_alert_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_alerts_alert_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_alerts_alert_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_alerts_alert_id_seq OWNED BY public.inventory_alerts.alert_id;


--
-- Name: inventory_lots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_lots (
    lot_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_number character varying(100) NOT NULL,
    manufacture_date date,
    expiry_date date,
    qc_status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    quarantine_status character varying(50) DEFAULT 'NONE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    receiving_item_id bigint
);


--
-- Name: TABLE inventory_lots; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.inventory_lots IS 'Inventory lot tracking';


--
-- Name: inventory_lots_lot_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_lots_lot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_lots_lot_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_lots_lot_id_seq OWNED BY public.inventory_lots.lot_id;


--
-- Name: inventory_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_snapshot (
    warehouse_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    lot_id_safe bigint GENERATED ALWAYS AS (COALESCE(lot_id, (0)::bigint)) STORED NOT NULL,
    location_id bigint NOT NULL,
    quantity numeric(12,2) DEFAULT 0 NOT NULL,
    reserved_qty numeric(12,2) DEFAULT 0 NOT NULL,
    last_updated timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE inventory_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.inventory_snapshot IS 'Current inventory snapshot (derived from transactions)';


--
-- Name: inventory_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_transactions (
    txn_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    location_id bigint NOT NULL,
    quantity numeric(12,2) NOT NULL,
    txn_type character varying(50) NOT NULL,
    reference_table character varying(100),
    reference_id bigint,
    reason_code character varying(100),
    created_by bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE inventory_transactions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.inventory_transactions IS 'Inventory transaction ledger (single source of truth)';


--
-- Name: inventory_transactions_txn_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_transactions_txn_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_transactions_txn_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_transactions_txn_id_seq OWNED BY public.inventory_transactions.txn_id;


--
-- Name: locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locations (
    location_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    zone_id bigint,
    location_code character varying(100) NOT NULL,
    location_type character varying(50) NOT NULL,
    parent_location_id bigint,
    max_weight_kg numeric(12,2),
    max_volume_m3 numeric(12,3),
    is_picking_face boolean DEFAULT false NOT NULL,
    is_staging boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE locations; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.locations IS 'Storage locations';


--
-- Name: locations_location_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.locations_location_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: locations_location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.locations_location_id_seq OWNED BY public.locations.location_id;


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    permission_id bigint NOT NULL,
    permission_code character varying(100) NOT NULL,
    description text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE permissions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.permissions IS 'System permissions';


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.permissions_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.permissions_permission_id_seq OWNED BY public.permissions.permission_id;


--
-- Name: picking_task_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.picking_task_items (
    picking_task_item_id bigint NOT NULL,
    picking_task_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    from_location_id bigint NOT NULL,
    required_qty numeric(12,2) NOT NULL,
    picked_qty numeric(12,2) DEFAULT 0 NOT NULL
);


--
-- Name: TABLE picking_task_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.picking_task_items IS 'Picking task line items';


--
-- Name: picking_task_items_picking_task_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.picking_task_items_picking_task_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: picking_task_items_picking_task_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.picking_task_items_picking_task_item_id_seq OWNED BY public.picking_task_items.picking_task_item_id;


--
-- Name: picking_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.picking_tasks (
    picking_task_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    so_id bigint,
    shipment_id bigint,
    status character varying(50) DEFAULT 'OPEN'::character varying NOT NULL,
    priority integer DEFAULT 3 NOT NULL,
    assigned_to bigint,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE picking_tasks; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.picking_tasks IS 'Picking tasks';


--
-- Name: picking_tasks_picking_task_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.picking_tasks_picking_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: picking_tasks_picking_task_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.picking_tasks_picking_task_id_seq OWNED BY public.picking_tasks.picking_task_id;


--
-- Name: putaway_task_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.putaway_task_items (
    putaway_task_item_id bigint NOT NULL,
    putaway_task_id bigint NOT NULL,
    receiving_item_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    quantity numeric(12,2) NOT NULL,
    putaway_qty numeric(12,2) DEFAULT 0 NOT NULL,
    suggested_location_id bigint,
    actual_location_id bigint,
    note text
);


--
-- Name: TABLE putaway_task_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.putaway_task_items IS 'Putaway task line items';


--
-- Name: putaway_task_items_putaway_task_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.putaway_task_items_putaway_task_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: putaway_task_items_putaway_task_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.putaway_task_items_putaway_task_item_id_seq OWNED BY public.putaway_task_items.putaway_task_item_id;


--
-- Name: putaway_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.putaway_tasks (
    putaway_task_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    receiving_id bigint NOT NULL,
    status character varying(50) DEFAULT 'OPEN'::character varying NOT NULL,
    from_location_id bigint,
    assigned_to bigint,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    created_by bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    note text
);


--
-- Name: TABLE putaway_tasks; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.putaway_tasks IS 'Putaway tasks for received goods';


--
-- Name: putaway_tasks_putaway_task_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.putaway_tasks_putaway_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: putaway_tasks_putaway_task_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.putaway_tasks_putaway_task_id_seq OWNED BY public.putaway_tasks.putaway_task_id;


--
-- Name: qc_inspections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qc_inspections (
    inspection_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    lot_id bigint NOT NULL,
    inspection_code character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    inspected_by bigint,
    inspected_at timestamp without time zone,
    remarks text,
    attachment_id bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE qc_inspections; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.qc_inspections IS 'Quality control inspections';


--
-- Name: qc_inspections_inspection_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.qc_inspections_inspection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: qc_inspections_inspection_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.qc_inspections_inspection_id_seq OWNED BY public.qc_inspections.inspection_id;


--
-- Name: quarantine_holds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quarantine_holds (
    hold_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    lot_id bigint NOT NULL,
    hold_reason character varying(500) NOT NULL,
    hold_note text,
    hold_by bigint,
    hold_at timestamp without time zone DEFAULT now() NOT NULL,
    release_by bigint,
    release_at timestamp without time zone,
    release_note text
);


--
-- Name: TABLE quarantine_holds; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.quarantine_holds IS 'Quarantine holds';


--
-- Name: quarantine_holds_hold_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.quarantine_holds_hold_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: quarantine_holds_hold_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.quarantine_holds_hold_id_seq OWNED BY public.quarantine_holds.hold_id;


--
-- Name: receiving_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.receiving_items (
    receiving_item_id bigint NOT NULL,
    receiving_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    expected_qty numeric(12,2),
    received_qty numeric(12,2) NOT NULL,
    lot_number character varying(100),
    manufacture_date date,
    expiry_date date,
    weight_kg numeric(12,2),
    note text,
    qc_required boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE receiving_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.receiving_items IS 'Receiving order line items';


--
-- Name: receiving_items_receiving_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.receiving_items_receiving_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: receiving_items_receiving_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.receiving_items_receiving_item_id_seq OWNED BY public.receiving_items.receiving_item_id;


--
-- Name: receiving_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.receiving_orders (
    receiving_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    receiving_code character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    source_type character varying(50) NOT NULL,
    source_warehouse_id bigint,
    supplier_id bigint,
    source_reference_code character varying(100),
    received_at timestamp without time zone,
    created_by bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    putaway_created_at timestamp without time zone,
    putaway_done_by bigint,
    putaway_done_at timestamp without time zone,
    approved_by bigint,
    approved_at timestamp without time zone,
    confirmed_by bigint,
    confirmed_at timestamp without time zone,
    note text
);


--
-- Name: TABLE receiving_orders; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.receiving_orders IS 'Inbound receiving orders';


--
-- Name: receiving_orders_receiving_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.receiving_orders_receiving_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: receiving_orders_receiving_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.receiving_orders_receiving_id_seq OWNED BY public.receiving_orders.receiving_id;


--
-- Name: reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservations (
    reservation_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    quantity numeric(12,2) NOT NULL,
    reference_table character varying(100),
    reference_id bigint,
    status character varying(50) DEFAULT 'OPEN'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE reservations; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.reservations IS 'Inventory reservations';


--
-- Name: reservations_reservation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reservations_reservation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservations_reservation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reservations_reservation_id_seq OWNED BY public.reservations.reservation_id;


--
-- Name: return_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.return_items (
    return_item_id bigint NOT NULL,
    return_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    quantity numeric(12,2) NOT NULL,
    note text
);


--
-- Name: TABLE return_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.return_items IS 'Return line items';


--
-- Name: return_items_return_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.return_items_return_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: return_items_return_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.return_items_return_item_id_seq OWNED BY public.return_items.return_item_id;


--
-- Name: returns; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.returns (
    return_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    return_code character varying(100) NOT NULL,
    return_type character varying(50) NOT NULL,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    reference_table character varying(100),
    reference_id bigint,
    created_by bigint,
    approved_by bigint,
    approved_at timestamp without time zone,
    note text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE returns; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.returns IS 'Returns (customer/supplier)';


--
-- Name: returns_return_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.returns_return_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: returns_return_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.returns_return_id_seq OWNED BY public.returns.return_id;


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    role_id bigint NOT NULL,
    role_code character varying(50) NOT NULL,
    role_name character varying(200) NOT NULL,
    description text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE roles; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.roles IS 'User roles';


--
-- Name: roles_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_role_id_seq OWNED BY public.roles.role_id;


--
-- Name: sales_order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_order_items (
    so_item_id bigint NOT NULL,
    so_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    ordered_qty numeric(12,2) NOT NULL,
    note text
);


--
-- Name: TABLE sales_order_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sales_order_items IS 'Sales order line items';


--
-- Name: sales_order_items_so_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_order_items_so_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_order_items_so_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_order_items_so_item_id_seq OWNED BY public.sales_order_items.so_item_id;


--
-- Name: sales_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_orders (
    so_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    so_code character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    required_ship_date date,
    created_by bigint,
    approved_by bigint,
    approved_at timestamp without time zone,
    note text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE sales_orders; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sales_orders IS 'Sales orders';


--
-- Name: sales_orders_so_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_orders_so_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_orders_so_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_orders_so_id_seq OWNED BY public.sales_orders.so_id;


--
-- Name: sds_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sds_documents (
    sds_id bigint NOT NULL,
    sds_code character varying(100) NOT NULL,
    version character varying(50) NOT NULL,
    issued_date date,
    language character varying(10),
    attachment_id bigint,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE sds_documents; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sds_documents IS 'Safety Data Sheets';


--
-- Name: sds_documents_sds_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sds_documents_sds_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sds_documents_sds_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sds_documents_sds_id_seq OWNED BY public.sds_documents.sds_id;


--
-- Name: shipment_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shipment_items (
    shipment_item_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    from_location_id bigint,
    quantity numeric(12,2) NOT NULL,
    note text
);


--
-- Name: TABLE shipment_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.shipment_items IS 'Shipment line items';


--
-- Name: shipment_items_shipment_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.shipment_items_shipment_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shipment_items_shipment_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.shipment_items_shipment_item_id_seq OWNED BY public.shipment_items.shipment_item_id;


--
-- Name: shipments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shipments (
    shipment_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    so_id bigint NOT NULL,
    shipment_code character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    ship_to_name character varying(300),
    ship_to_phone character varying(20),
    ship_to_address text,
    carrier_id bigint,
    tracking_number character varying(200),
    shipped_at timestamp without time zone,
    created_by bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    dispatched_by bigint,
    note text
);


--
-- Name: TABLE shipments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.shipments IS 'Outbound shipments';


--
-- Name: shipments_shipment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.shipments_shipment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shipments_shipment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.shipments_shipment_id_seq OWNED BY public.shipments.shipment_id;


--
-- Name: sku_thresholds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sku_thresholds (
    threshold_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    min_qty numeric(12,2) DEFAULT 0 NOT NULL,
    max_qty numeric(12,2),
    reorder_point numeric(12,2),
    reorder_qty numeric(12,2),
    active boolean DEFAULT true NOT NULL,
    note text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint
);


--
-- Name: TABLE sku_thresholds; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sku_thresholds IS 'SKU inventory thresholds per warehouse';


--
-- Name: sku_thresholds_threshold_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sku_thresholds_threshold_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sku_thresholds_threshold_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sku_thresholds_threshold_id_seq OWNED BY public.sku_thresholds.threshold_id;


--
-- Name: skus; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.skus (
    sku_id bigint NOT NULL,
    category_id bigint,
    sku_code character varying(100) NOT NULL,
    sku_name character varying(300) NOT NULL,
    description text,
    brand character varying(200),
    package_type character varying(100),
    volume_ml numeric(12,2),
    weight_g numeric(12,2),
    barcode character varying(100),
    unit character varying(50) NOT NULL,
    origin_country character varying(100),
    scent character varying(200),
    image_url text,
    storage_temp_min numeric(5,2),
    storage_temp_max numeric(5,2),
    shelf_life_days integer,
    active boolean DEFAULT true NOT NULL,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE skus; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.skus IS 'Stock Keeping Units (Product Master)';


--
-- Name: skus_sku_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.skus_sku_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: skus_sku_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.skus_sku_id_seq OWNED BY public.skus.sku_id;


--
-- Name: stocktake_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stocktake_items (
    stocktake_item_id bigint NOT NULL,
    stocktake_id bigint NOT NULL,
    location_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    expected_qty numeric(12,2),
    counted_qty numeric(12,2),
    discrepancy_qty numeric(12,2),
    counted_by bigint,
    counted_at timestamp without time zone,
    note text
);


--
-- Name: TABLE stocktake_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.stocktake_items IS 'Stocktake line items';


--
-- Name: stocktake_items_stocktake_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stocktake_items_stocktake_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stocktake_items_stocktake_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stocktake_items_stocktake_item_id_seq OWNED BY public.stocktake_items.stocktake_item_id;


--
-- Name: stocktakes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stocktakes (
    stocktake_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    stocktake_code character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'PLANNED'::character varying NOT NULL,
    start_at timestamp without time zone,
    end_at timestamp without time zone,
    created_by bigint,
    posted_by bigint,
    posted_at timestamp without time zone,
    note text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE stocktakes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.stocktakes IS 'Stocktake / cycle count';


--
-- Name: stocktakes_stocktake_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stocktakes_stocktake_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stocktakes_stocktake_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stocktakes_stocktake_id_seq OWNED BY public.stocktakes.stocktake_id;


--
-- Name: storage_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_policies (
    policy_id bigint NOT NULL,
    policy_code character varying(50) NOT NULL,
    policy_name character varying(200) NOT NULL,
    description text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE storage_policies; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.storage_policies IS 'Storage policy definitions';


--
-- Name: storage_policies_policy_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_policies_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_policies_policy_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.storage_policies_policy_id_seq OWNED BY public.storage_policies.policy_id;


--
-- Name: storage_policy_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_policy_rules (
    rule_id bigint NOT NULL,
    policy_id bigint NOT NULL,
    zone_id bigint,
    location_type character varying(50),
    min_distance_m numeric(8,2),
    max_stack_height integer,
    max_qty_per_bin numeric(12,2),
    note text
);


--
-- Name: TABLE storage_policy_rules; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.storage_policy_rules IS 'Storage policy rules';


--
-- Name: storage_policy_rules_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_policy_rules_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_policy_rules_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.storage_policy_rules_rule_id_seq OWNED BY public.storage_policy_rules.rule_id;


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
    supplier_id bigint NOT NULL,
    supplier_code character varying(50) NOT NULL,
    supplier_name character varying(300) NOT NULL,
    tax_code character varying(50),
    email character varying(255),
    phone character varying(20),
    address text,
    certifications text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE suppliers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.suppliers IS 'Supplier master data';


--
-- Name: suppliers_supplier_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.suppliers_supplier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: suppliers_supplier_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.suppliers_supplier_id_seq OWNED BY public.suppliers.supplier_id;


--
-- Name: transfer_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transfer_items (
    transfer_item_id bigint NOT NULL,
    transfer_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    lot_id bigint,
    quantity numeric(12,2) NOT NULL
);


--
-- Name: TABLE transfer_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.transfer_items IS 'Transfer line items';


--
-- Name: transfer_items_transfer_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.transfer_items_transfer_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transfer_items_transfer_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.transfer_items_transfer_item_id_seq OWNED BY public.transfer_items.transfer_item_id;


--
-- Name: transfers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transfers (
    transfer_id bigint NOT NULL,
    from_warehouse_id bigint NOT NULL,
    to_warehouse_id bigint NOT NULL,
    transfer_code character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    created_by bigint,
    approved_by bigint,
    approved_at timestamp without time zone,
    shipped_at timestamp without time zone,
    received_at timestamp without time zone,
    note text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE transfers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.transfers IS 'Inter-warehouse transfers';


--
-- Name: transfers_transfer_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.transfers_transfer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transfers_transfer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.transfers_transfer_id_seq OWNED BY public.transfers.transfer_id;


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    assigned_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: user_warehouses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_warehouses (
    user_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    assigned_at timestamp without time zone DEFAULT now() NOT NULL,
    assigned_by bigint,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: TABLE user_warehouses; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.user_warehouses IS 'User warehouse access control';


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password_hash text NOT NULL,
    full_name character varying(200),
    phone character varying(20),
    gender character varying(10),
    date_of_birth date,
    address text,
    avatar_url text,
    status character varying(50) DEFAULT 'INACTIVE'::character varying NOT NULL,
    is_first_login boolean DEFAULT true NOT NULL,
    is_permanent boolean DEFAULT true NOT NULL,
    expire_date date,
    last_login_at timestamp without time zone,
    failed_login_attempts integer DEFAULT 0 NOT NULL,
    locked_until timestamp without time zone,
    password_changed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_by bigint
);


--
-- Name: TABLE users; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.users IS 'System users';


--
-- Name: users_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_user_id_seq OWNED BY public.users.user_id;


--
-- Name: warehouses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warehouses (
    warehouse_id bigint NOT NULL,
    warehouse_code character varying(50) NOT NULL,
    warehouse_name character varying(200) NOT NULL,
    address text,
    timezone character varying(50) DEFAULT 'Asia/Ho_Chi_Minh'::character varying NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE warehouses; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.warehouses IS 'Warehouse master data';


--
-- Name: warehouses_warehouse_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.warehouses_warehouse_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: warehouses_warehouse_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.warehouses_warehouse_id_seq OWNED BY public.warehouses.warehouse_id;


--
-- Name: zones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.zones (
    zone_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    zone_code character varying(50) NOT NULL,
    zone_name character varying(200),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE zones; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.zones IS 'Warehouse zones';


--
-- Name: zones_zone_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.zones_zone_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zones_zone_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.zones_zone_id_seq OWNED BY public.zones.zone_id;


--
-- Name: adjustment_items adjustment_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_items ALTER COLUMN adjustment_item_id SET DEFAULT nextval('public.adjustment_items_adjustment_item_id_seq'::regclass);


--
-- Name: adjustments adjustment_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustments ALTER COLUMN adjustment_id SET DEFAULT nextval('public.adjustments_adjustment_id_seq'::regclass);


--
-- Name: attachments attachment_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments ALTER COLUMN attachment_id SET DEFAULT nextval('public.attachments_attachment_id_seq'::regclass);


--
-- Name: audit_logs audit_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN audit_id SET DEFAULT nextval('public.audit_logs_audit_id_seq'::regclass);


--
-- Name: carriers carrier_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carriers ALTER COLUMN carrier_id SET DEFAULT nextval('public.carriers_carrier_id_seq'::regclass);


--
-- Name: categories category_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories ALTER COLUMN category_id SET DEFAULT nextval('public.categories_category_id_seq'::regclass);


--
-- Name: customers customer_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers ALTER COLUMN customer_id SET DEFAULT nextval('public.customers_customer_id_seq'::regclass);


--
-- Name: enum_transitions transition_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_transitions ALTER COLUMN transition_id SET DEFAULT nextval('public.enum_transitions_transition_id_seq'::regclass);


--
-- Name: enum_types enum_type_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_types ALTER COLUMN enum_type_id SET DEFAULT nextval('public.enum_types_enum_type_id_seq'::regclass);


--
-- Name: enum_values enum_value_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_values ALTER COLUMN enum_value_id SET DEFAULT nextval('public.enum_values_enum_value_id_seq'::regclass);


--
-- Name: expiry_rules rule_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expiry_rules ALTER COLUMN rule_id SET DEFAULT nextval('public.expiry_rules_rule_id_seq'::regclass);


--
-- Name: incidents incident_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incidents ALTER COLUMN incident_id SET DEFAULT nextval('public.incidents_incident_id_seq'::regclass);


--
-- Name: inventory_alerts alert_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_alerts ALTER COLUMN alert_id SET DEFAULT nextval('public.inventory_alerts_alert_id_seq'::regclass);


--
-- Name: inventory_lots lot_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_lots ALTER COLUMN lot_id SET DEFAULT nextval('public.inventory_lots_lot_id_seq'::regclass);


--
-- Name: inventory_transactions txn_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ALTER COLUMN txn_id SET DEFAULT nextval('public.inventory_transactions_txn_id_seq'::regclass);


--
-- Name: locations location_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations ALTER COLUMN location_id SET DEFAULT nextval('public.locations_location_id_seq'::regclass);


--
-- Name: permissions permission_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions ALTER COLUMN permission_id SET DEFAULT nextval('public.permissions_permission_id_seq'::regclass);


--
-- Name: picking_task_items picking_task_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_task_items ALTER COLUMN picking_task_item_id SET DEFAULT nextval('public.picking_task_items_picking_task_item_id_seq'::regclass);


--
-- Name: picking_tasks picking_task_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_tasks ALTER COLUMN picking_task_id SET DEFAULT nextval('public.picking_tasks_picking_task_id_seq'::regclass);


--
-- Name: putaway_task_items putaway_task_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_task_items ALTER COLUMN putaway_task_item_id SET DEFAULT nextval('public.putaway_task_items_putaway_task_item_id_seq'::regclass);


--
-- Name: putaway_tasks putaway_task_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_tasks ALTER COLUMN putaway_task_id SET DEFAULT nextval('public.putaway_tasks_putaway_task_id_seq'::regclass);


--
-- Name: qc_inspections inspection_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qc_inspections ALTER COLUMN inspection_id SET DEFAULT nextval('public.qc_inspections_inspection_id_seq'::regclass);


--
-- Name: quarantine_holds hold_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quarantine_holds ALTER COLUMN hold_id SET DEFAULT nextval('public.quarantine_holds_hold_id_seq'::regclass);


--
-- Name: receiving_items receiving_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_items ALTER COLUMN receiving_item_id SET DEFAULT nextval('public.receiving_items_receiving_item_id_seq'::regclass);


--
-- Name: receiving_orders receiving_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders ALTER COLUMN receiving_id SET DEFAULT nextval('public.receiving_orders_receiving_id_seq'::regclass);


--
-- Name: reservations reservation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations ALTER COLUMN reservation_id SET DEFAULT nextval('public.reservations_reservation_id_seq'::regclass);


--
-- Name: return_items return_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items ALTER COLUMN return_item_id SET DEFAULT nextval('public.return_items_return_item_id_seq'::regclass);


--
-- Name: returns return_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.returns ALTER COLUMN return_id SET DEFAULT nextval('public.returns_return_id_seq'::regclass);


--
-- Name: roles role_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN role_id SET DEFAULT nextval('public.roles_role_id_seq'::regclass);


--
-- Name: sales_order_items so_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_items ALTER COLUMN so_item_id SET DEFAULT nextval('public.sales_order_items_so_item_id_seq'::regclass);


--
-- Name: sales_orders so_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders ALTER COLUMN so_id SET DEFAULT nextval('public.sales_orders_so_id_seq'::regclass);


--
-- Name: sds_documents sds_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sds_documents ALTER COLUMN sds_id SET DEFAULT nextval('public.sds_documents_sds_id_seq'::regclass);


--
-- Name: shipment_items shipment_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipment_items ALTER COLUMN shipment_item_id SET DEFAULT nextval('public.shipment_items_shipment_item_id_seq'::regclass);


--
-- Name: shipments shipment_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments ALTER COLUMN shipment_id SET DEFAULT nextval('public.shipments_shipment_id_seq'::regclass);


--
-- Name: sku_thresholds threshold_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sku_thresholds ALTER COLUMN threshold_id SET DEFAULT nextval('public.sku_thresholds_threshold_id_seq'::regclass);


--
-- Name: skus sku_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skus ALTER COLUMN sku_id SET DEFAULT nextval('public.skus_sku_id_seq'::regclass);


--
-- Name: stocktake_items stocktake_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items ALTER COLUMN stocktake_item_id SET DEFAULT nextval('public.stocktake_items_stocktake_item_id_seq'::regclass);


--
-- Name: stocktakes stocktake_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes ALTER COLUMN stocktake_id SET DEFAULT nextval('public.stocktakes_stocktake_id_seq'::regclass);


--
-- Name: storage_policies policy_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_policies ALTER COLUMN policy_id SET DEFAULT nextval('public.storage_policies_policy_id_seq'::regclass);


--
-- Name: storage_policy_rules rule_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_policy_rules ALTER COLUMN rule_id SET DEFAULT nextval('public.storage_policy_rules_rule_id_seq'::regclass);


--
-- Name: suppliers supplier_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers ALTER COLUMN supplier_id SET DEFAULT nextval('public.suppliers_supplier_id_seq'::regclass);


--
-- Name: transfer_items transfer_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfer_items ALTER COLUMN transfer_item_id SET DEFAULT nextval('public.transfer_items_transfer_item_id_seq'::regclass);


--
-- Name: transfers transfer_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers ALTER COLUMN transfer_id SET DEFAULT nextval('public.transfers_transfer_id_seq'::regclass);


--
-- Name: users user_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN user_id SET DEFAULT nextval('public.users_user_id_seq'::regclass);


--
-- Name: warehouses warehouse_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses ALTER COLUMN warehouse_id SET DEFAULT nextval('public.warehouses_warehouse_id_seq'::regclass);


--
-- Name: zones zone_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zones ALTER COLUMN zone_id SET DEFAULT nextval('public.zones_zone_id_seq'::regclass);


--
-- Name: adjustment_items adjustment_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_items
    ADD CONSTRAINT adjustment_items_pkey PRIMARY KEY (adjustment_item_id);


--
-- Name: adjustments adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustments
    ADD CONSTRAINT adjustments_pkey PRIMARY KEY (adjustment_id);


--
-- Name: adjustments adjustments_warehouse_id_adjustment_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustments
    ADD CONSTRAINT adjustments_warehouse_id_adjustment_code_key UNIQUE (warehouse_id, adjustment_code);


--
-- Name: attachments attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_pkey PRIMARY KEY (attachment_id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (audit_id);


--
-- Name: carriers carriers_carrier_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carriers
    ADD CONSTRAINT carriers_carrier_code_key UNIQUE (carrier_code);


--
-- Name: carriers carriers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carriers
    ADD CONSTRAINT carriers_pkey PRIMARY KEY (carrier_id);


--
-- Name: categories categories_category_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_category_code_key UNIQUE (category_code);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (category_id);


--
-- Name: customers customers_customer_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_customer_code_key UNIQUE (customer_code);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (customer_id);


--
-- Name: enum_transitions enum_transitions_enum_type_id_from_value_code_to_value_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_transitions
    ADD CONSTRAINT enum_transitions_enum_type_id_from_value_code_to_value_code_key UNIQUE (enum_type_id, from_value_code, to_value_code);


--
-- Name: enum_transitions enum_transitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_transitions
    ADD CONSTRAINT enum_transitions_pkey PRIMARY KEY (transition_id);


--
-- Name: enum_types enum_types_enum_type_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_types
    ADD CONSTRAINT enum_types_enum_type_code_key UNIQUE (enum_type_code);


--
-- Name: enum_types enum_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_types
    ADD CONSTRAINT enum_types_pkey PRIMARY KEY (enum_type_id);


--
-- Name: enum_values enum_values_enum_type_id_value_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_values
    ADD CONSTRAINT enum_values_enum_type_id_value_code_key UNIQUE (enum_type_id, value_code);


--
-- Name: enum_values enum_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_values
    ADD CONSTRAINT enum_values_pkey PRIMARY KEY (enum_value_id);


--
-- Name: expiry_rules expiry_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expiry_rules
    ADD CONSTRAINT expiry_rules_pkey PRIMARY KEY (rule_id);


--
-- Name: expiry_rules expiry_rules_warehouse_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expiry_rules
    ADD CONSTRAINT expiry_rules_warehouse_id_key UNIQUE (warehouse_id);


--
-- Name: incidents incidents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT incidents_pkey PRIMARY KEY (incident_id);


--
-- Name: incidents incidents_warehouse_id_incident_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT incidents_warehouse_id_incident_code_key UNIQUE (warehouse_id, incident_code);


--
-- Name: inventory_alerts inventory_alerts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_alerts
    ADD CONSTRAINT inventory_alerts_pkey PRIMARY KEY (alert_id);


--
-- Name: inventory_lots inventory_lots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_lots
    ADD CONSTRAINT inventory_lots_pkey PRIMARY KEY (lot_id);


--
-- Name: inventory_lots inventory_lots_sku_id_lot_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_lots
    ADD CONSTRAINT inventory_lots_sku_id_lot_number_key UNIQUE (sku_id, lot_number);


--
-- Name: inventory_snapshot inventory_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_snapshot
    ADD CONSTRAINT inventory_snapshot_pkey PRIMARY KEY (warehouse_id, sku_id, lot_id_safe, location_id);


--
-- Name: inventory_transactions inventory_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_pkey PRIMARY KEY (txn_id);


--
-- Name: locations locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_pkey PRIMARY KEY (location_id);


--
-- Name: locations locations_warehouse_id_location_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_warehouse_id_location_code_key UNIQUE (warehouse_id, location_code);


--
-- Name: permissions permissions_permission_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_permission_code_key UNIQUE (permission_code);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permission_id);


--
-- Name: picking_task_items picking_task_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_task_items
    ADD CONSTRAINT picking_task_items_pkey PRIMARY KEY (picking_task_item_id);


--
-- Name: picking_tasks picking_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_tasks
    ADD CONSTRAINT picking_tasks_pkey PRIMARY KEY (picking_task_id);


--
-- Name: putaway_task_items putaway_task_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_task_items
    ADD CONSTRAINT putaway_task_items_pkey PRIMARY KEY (putaway_task_item_id);


--
-- Name: putaway_task_items putaway_task_items_putaway_task_id_receiving_item_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_task_items
    ADD CONSTRAINT putaway_task_items_putaway_task_id_receiving_item_id_key UNIQUE (putaway_task_id, receiving_item_id);


--
-- Name: putaway_tasks putaway_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_tasks
    ADD CONSTRAINT putaway_tasks_pkey PRIMARY KEY (putaway_task_id);


--
-- Name: putaway_tasks putaway_tasks_warehouse_id_receiving_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_tasks
    ADD CONSTRAINT putaway_tasks_warehouse_id_receiving_id_key UNIQUE (warehouse_id, receiving_id);


--
-- Name: qc_inspections qc_inspections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qc_inspections
    ADD CONSTRAINT qc_inspections_pkey PRIMARY KEY (inspection_id);


--
-- Name: qc_inspections qc_inspections_warehouse_id_inspection_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qc_inspections
    ADD CONSTRAINT qc_inspections_warehouse_id_inspection_code_key UNIQUE (warehouse_id, inspection_code);


--
-- Name: quarantine_holds quarantine_holds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quarantine_holds
    ADD CONSTRAINT quarantine_holds_pkey PRIMARY KEY (hold_id);


--
-- Name: receiving_items receiving_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_items
    ADD CONSTRAINT receiving_items_pkey PRIMARY KEY (receiving_item_id);


--
-- Name: receiving_orders receiving_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_pkey PRIMARY KEY (receiving_id);


--
-- Name: receiving_orders receiving_orders_warehouse_id_receiving_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_warehouse_id_receiving_code_key UNIQUE (warehouse_id, receiving_code);


--
-- Name: reservations reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_pkey PRIMARY KEY (reservation_id);


--
-- Name: return_items return_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT return_items_pkey PRIMARY KEY (return_item_id);


--
-- Name: returns returns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.returns
    ADD CONSTRAINT returns_pkey PRIMARY KEY (return_id);


--
-- Name: returns returns_warehouse_id_return_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.returns
    ADD CONSTRAINT returns_warehouse_id_return_code_key UNIQUE (warehouse_id, return_code);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: roles roles_role_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_role_code_key UNIQUE (role_code);


--
-- Name: sales_order_items sales_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_items
    ADD CONSTRAINT sales_order_items_pkey PRIMARY KEY (so_item_id);


--
-- Name: sales_orders sales_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders
    ADD CONSTRAINT sales_orders_pkey PRIMARY KEY (so_id);


--
-- Name: sales_orders sales_orders_warehouse_id_so_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders
    ADD CONSTRAINT sales_orders_warehouse_id_so_code_key UNIQUE (warehouse_id, so_code);


--
-- Name: sds_documents sds_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sds_documents
    ADD CONSTRAINT sds_documents_pkey PRIMARY KEY (sds_id);


--
-- Name: sds_documents sds_documents_sds_code_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sds_documents
    ADD CONSTRAINT sds_documents_sds_code_version_key UNIQUE (sds_code, version);


--
-- Name: shipment_items shipment_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipment_items
    ADD CONSTRAINT shipment_items_pkey PRIMARY KEY (shipment_item_id);


--
-- Name: shipments shipments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT shipments_pkey PRIMARY KEY (shipment_id);


--
-- Name: shipments shipments_warehouse_id_shipment_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT shipments_warehouse_id_shipment_code_key UNIQUE (warehouse_id, shipment_code);


--
-- Name: sku_thresholds sku_thresholds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sku_thresholds
    ADD CONSTRAINT sku_thresholds_pkey PRIMARY KEY (threshold_id);


--
-- Name: sku_thresholds sku_thresholds_warehouse_id_sku_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sku_thresholds
    ADD CONSTRAINT sku_thresholds_warehouse_id_sku_id_key UNIQUE (warehouse_id, sku_id);


--
-- Name: skus skus_barcode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skus
    ADD CONSTRAINT skus_barcode_key UNIQUE (barcode);


--
-- Name: skus skus_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skus
    ADD CONSTRAINT skus_pkey PRIMARY KEY (sku_id);


--
-- Name: skus skus_sku_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skus
    ADD CONSTRAINT skus_sku_code_key UNIQUE (sku_code);


--
-- Name: stocktake_items stocktake_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT stocktake_items_pkey PRIMARY KEY (stocktake_item_id);


--
-- Name: stocktakes stocktakes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes
    ADD CONSTRAINT stocktakes_pkey PRIMARY KEY (stocktake_id);


--
-- Name: stocktakes stocktakes_warehouse_id_stocktake_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes
    ADD CONSTRAINT stocktakes_warehouse_id_stocktake_code_key UNIQUE (warehouse_id, stocktake_code);


--
-- Name: storage_policies storage_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_policies
    ADD CONSTRAINT storage_policies_pkey PRIMARY KEY (policy_id);


--
-- Name: storage_policies storage_policies_policy_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_policies
    ADD CONSTRAINT storage_policies_policy_code_key UNIQUE (policy_code);


--
-- Name: storage_policy_rules storage_policy_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_policy_rules
    ADD CONSTRAINT storage_policy_rules_pkey PRIMARY KEY (rule_id);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (supplier_id);


--
-- Name: suppliers suppliers_supplier_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_supplier_code_key UNIQUE (supplier_code);


--
-- Name: transfer_items transfer_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT transfer_items_pkey PRIMARY KEY (transfer_item_id);


--
-- Name: transfers transfers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_pkey PRIMARY KEY (transfer_id);


--
-- Name: transfers transfers_transfer_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_transfer_code_key UNIQUE (transfer_code);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: user_warehouses user_warehouses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_warehouses
    ADD CONSTRAINT user_warehouses_pkey PRIMARY KEY (user_id, warehouse_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: warehouses warehouses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_pkey PRIMARY KEY (warehouse_id);


--
-- Name: warehouses warehouses_warehouse_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_warehouse_code_key UNIQUE (warehouse_code);


--
-- Name: zones zones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zones
    ADD CONSTRAINT zones_pkey PRIMARY KEY (zone_id);


--
-- Name: zones zones_warehouse_id_zone_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zones
    ADD CONSTRAINT zones_warehouse_id_zone_code_key UNIQUE (warehouse_id, zone_code);


--
-- Name: idx_adjustment_items_adjustment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_adjustment_items_adjustment ON public.adjustment_items USING btree (adjustment_id);


--
-- Name: idx_adjustment_items_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_adjustment_items_sku ON public.adjustment_items USING btree (sku_id);


--
-- Name: idx_adjustment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_adjustment_status ON public.adjustments USING btree (status);


--
-- Name: idx_adjustment_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_adjustment_warehouse ON public.adjustments USING btree (warehouse_id);


--
-- Name: idx_alert_dedupe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alert_dedupe ON public.inventory_alerts USING btree (dedupe_key) WHERE (dedupe_key IS NOT NULL);


--
-- Name: idx_alert_sku_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alert_sku_type_status ON public.inventory_alerts USING btree (sku_id, alert_type, status);


--
-- Name: idx_alert_warehouse_status_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_alert_warehouse_status_type ON public.inventory_alerts USING btree (warehouse_id, status, alert_type);


--
-- Name: idx_attachment_uploaded_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_uploaded_by ON public.attachments USING btree (uploaded_by);


--
-- Name: idx_audit_action_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_action_at ON public.audit_logs USING btree (action_at);


--
-- Name: idx_audit_action_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_action_by ON public.audit_logs USING btree (action_by);


--
-- Name: idx_audit_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_entity ON public.audit_logs USING btree (entity_name, entity_id);


--
-- Name: idx_carrier_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_carrier_code ON public.carriers USING btree (carrier_code);


--
-- Name: idx_category_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_category_code ON public.categories USING btree (category_code);


--
-- Name: idx_category_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_category_parent ON public.categories USING btree (parent_category_id);


--
-- Name: idx_customer_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_code ON public.customers USING btree (customer_code);


--
-- Name: idx_enum_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_enum_active ON public.enum_values USING btree (enum_type_id, active);


--
-- Name: idx_enum_display_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_enum_display_order ON public.enum_values USING btree (enum_type_id, display_order);


--
-- Name: idx_enum_transitions; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_enum_transitions ON public.enum_transitions USING btree (enum_type_id, from_value_code, to_value_code);


--
-- Name: idx_enum_type_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_enum_type_code ON public.enum_types USING btree (enum_type_code);


--
-- Name: idx_enum_type_value; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_enum_type_value ON public.enum_values USING btree (enum_type_id, value_code);


--
-- Name: idx_expiry_rules_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expiry_rules_warehouse ON public.expiry_rules USING btree (warehouse_id);


--
-- Name: idx_incident_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incident_status ON public.incidents USING btree (status);


--
-- Name: idx_incident_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incident_type ON public.incidents USING btree (incident_type);


--
-- Name: idx_incident_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_incident_warehouse ON public.incidents USING btree (warehouse_id);


--
-- Name: idx_inv_snapshot_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_snapshot_location ON public.inventory_snapshot USING btree (location_id);


--
-- Name: idx_inv_snapshot_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_snapshot_lot ON public.inventory_snapshot USING btree (lot_id) WHERE (lot_id IS NOT NULL);


--
-- Name: idx_inv_snapshot_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_snapshot_sku ON public.inventory_snapshot USING btree (sku_id);


--
-- Name: idx_inv_snapshot_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_snapshot_warehouse ON public.inventory_snapshot USING btree (warehouse_id);


--
-- Name: idx_inv_txn_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_txn_created_at ON public.inventory_transactions USING btree (created_at);


--
-- Name: idx_inv_txn_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_txn_reference ON public.inventory_transactions USING btree (reference_table, reference_id);


--
-- Name: idx_inv_txn_sku_lot_loc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_txn_sku_lot_loc ON public.inventory_transactions USING btree (warehouse_id, sku_id, lot_id, location_id);


--
-- Name: idx_inv_txn_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_txn_warehouse ON public.inventory_transactions USING btree (warehouse_id);


--
-- Name: idx_location_staging; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_staging ON public.locations USING btree (warehouse_id, is_staging);


--
-- Name: idx_location_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_type ON public.locations USING btree (location_type);


--
-- Name: idx_location_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_warehouse ON public.locations USING btree (warehouse_id);


--
-- Name: idx_location_zone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_zone ON public.locations USING btree (zone_id);


--
-- Name: idx_lot_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lot_expiry ON public.inventory_lots USING btree (expiry_date) WHERE (expiry_date IS NOT NULL);


--
-- Name: idx_lot_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lot_number ON public.inventory_lots USING btree (lot_number);


--
-- Name: idx_lot_qc_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lot_qc_status ON public.inventory_lots USING btree (qc_status);


--
-- Name: idx_lot_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lot_sku ON public.inventory_lots USING btree (sku_id);


--
-- Name: idx_permission_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_code ON public.permissions USING btree (permission_code);


--
-- Name: idx_picking_item_sku_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_picking_item_sku_lot ON public.picking_task_items USING btree (sku_id, lot_id);


--
-- Name: idx_picking_item_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_picking_item_task ON public.picking_task_items USING btree (picking_task_id);


--
-- Name: idx_picking_task_assigned; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_picking_task_assigned ON public.picking_tasks USING btree (assigned_to, status);


--
-- Name: idx_picking_task_shipment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_picking_task_shipment ON public.picking_tasks USING btree (shipment_id);


--
-- Name: idx_picking_task_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_picking_task_warehouse ON public.picking_tasks USING btree (warehouse_id);


--
-- Name: idx_policy_rules_policy; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_policy_rules_policy ON public.storage_policy_rules USING btree (policy_id);


--
-- Name: idx_putaway_item_sku_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_putaway_item_sku_lot ON public.putaway_task_items USING btree (sku_id, lot_id);


--
-- Name: idx_putaway_item_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_putaway_item_task ON public.putaway_task_items USING btree (putaway_task_id);


--
-- Name: idx_putaway_task_assigned; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_putaway_task_assigned ON public.putaway_tasks USING btree (assigned_to, status);


--
-- Name: idx_putaway_task_receiving; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_putaway_task_receiving ON public.putaway_tasks USING btree (receiving_id);


--
-- Name: idx_putaway_task_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_putaway_task_warehouse ON public.putaway_tasks USING btree (warehouse_id);


--
-- Name: idx_qc_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qc_lot ON public.qc_inspections USING btree (lot_id);


--
-- Name: idx_qc_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qc_status ON public.qc_inspections USING btree (status);


--
-- Name: idx_qc_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qc_warehouse ON public.qc_inspections USING btree (warehouse_id);


--
-- Name: idx_quarantine_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quarantine_lot ON public.quarantine_holds USING btree (lot_id);


--
-- Name: idx_quarantine_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quarantine_warehouse ON public.quarantine_holds USING btree (warehouse_id);


--
-- Name: idx_receiving_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receiving_created_at ON public.receiving_orders USING btree (created_at);


--
-- Name: idx_receiving_items_receiving; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receiving_items_receiving ON public.receiving_items USING btree (receiving_id);


--
-- Name: idx_receiving_items_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receiving_items_sku ON public.receiving_items USING btree (sku_id);


--
-- Name: idx_receiving_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receiving_status ON public.receiving_orders USING btree (status);


--
-- Name: idx_receiving_supplier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receiving_supplier ON public.receiving_orders USING btree (supplier_id);


--
-- Name: idx_receiving_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_receiving_warehouse ON public.receiving_orders USING btree (warehouse_id);


--
-- Name: idx_reservation_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_reference ON public.reservations USING btree (reference_table, reference_id);


--
-- Name: idx_reservation_sku_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_sku_lot ON public.reservations USING btree (sku_id, lot_id);


--
-- Name: idx_reservation_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_status ON public.reservations USING btree (status);


--
-- Name: idx_reservation_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_warehouse ON public.reservations USING btree (warehouse_id);


--
-- Name: idx_return_items_return; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_items_return ON public.return_items USING btree (return_id);


--
-- Name: idx_return_items_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_items_sku ON public.return_items USING btree (sku_id);


--
-- Name: idx_return_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_status ON public.returns USING btree (status);


--
-- Name: idx_return_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_type ON public.returns USING btree (return_type);


--
-- Name: idx_return_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_warehouse ON public.returns USING btree (warehouse_id);


--
-- Name: idx_role_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_code ON public.roles USING btree (role_code);


--
-- Name: idx_role_permissions_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_permissions_permission ON public.role_permissions USING btree (permission_id);


--
-- Name: idx_role_permissions_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_permissions_role ON public.role_permissions USING btree (role_id);


--
-- Name: idx_sds_code_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sds_code_version ON public.sds_documents USING btree (sds_code, version);


--
-- Name: idx_shipment_items_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipment_items_location ON public.shipment_items USING btree (from_location_id);


--
-- Name: idx_shipment_items_shipment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipment_items_shipment ON public.shipment_items USING btree (shipment_id);


--
-- Name: idx_shipment_items_sku_lot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipment_items_sku_lot ON public.shipment_items USING btree (sku_id, lot_id);


--
-- Name: idx_shipment_so; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipment_so ON public.shipments USING btree (so_id);


--
-- Name: idx_shipment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipment_status ON public.shipments USING btree (status);


--
-- Name: idx_shipment_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipment_warehouse ON public.shipments USING btree (warehouse_id);


--
-- Name: idx_sku_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sku_active ON public.skus USING btree (active) WHERE (deleted_at IS NULL);


--
-- Name: idx_sku_barcode; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sku_barcode ON public.skus USING btree (barcode) WHERE (barcode IS NOT NULL);


--
-- Name: idx_sku_category_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sku_category_active ON public.skus USING btree (category_id, active);


--
-- Name: idx_sku_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sku_code ON public.skus USING btree (sku_code);


--
-- Name: idx_sku_threshold_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sku_threshold_sku ON public.sku_thresholds USING btree (sku_id);


--
-- Name: idx_sku_threshold_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sku_threshold_warehouse ON public.sku_thresholds USING btree (warehouse_id);


--
-- Name: idx_so_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_so_customer ON public.sales_orders USING btree (customer_id);


--
-- Name: idx_so_items_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_so_items_sku ON public.sales_order_items USING btree (sku_id);


--
-- Name: idx_so_items_so; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_so_items_so ON public.sales_order_items USING btree (so_id);


--
-- Name: idx_so_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_so_status ON public.sales_orders USING btree (status);


--
-- Name: idx_so_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_so_warehouse ON public.sales_orders USING btree (warehouse_id);


--
-- Name: idx_stocktake_items_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_items_location ON public.stocktake_items USING btree (location_id);


--
-- Name: idx_stocktake_items_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_items_sku ON public.stocktake_items USING btree (sku_id);


--
-- Name: idx_stocktake_items_stocktake; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_items_stocktake ON public.stocktake_items USING btree (stocktake_id);


--
-- Name: idx_stocktake_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_status ON public.stocktakes USING btree (status);


--
-- Name: idx_stocktake_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stocktake_warehouse ON public.stocktakes USING btree (warehouse_id);


--
-- Name: idx_storage_policy_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_storage_policy_code ON public.storage_policies USING btree (policy_code);


--
-- Name: idx_supplier_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supplier_code ON public.suppliers USING btree (supplier_code);


--
-- Name: idx_transfer_from; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transfer_from ON public.transfers USING btree (from_warehouse_id);


--
-- Name: idx_transfer_items_sku; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transfer_items_sku ON public.transfer_items USING btree (sku_id);


--
-- Name: idx_transfer_items_transfer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transfer_items_transfer ON public.transfer_items USING btree (transfer_id);


--
-- Name: idx_transfer_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transfer_status ON public.transfers USING btree (status);


--
-- Name: idx_transfer_to; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transfer_to ON public.transfers USING btree (to_warehouse_id);


--
-- Name: idx_user_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_email ON public.users USING btree (email);


--
-- Name: idx_user_expire_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_expire_date ON public.users USING btree (expire_date) WHERE (expire_date IS NOT NULL);


--
-- Name: idx_user_roles_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_roles_role ON public.user_roles USING btree (role_id);


--
-- Name: idx_user_roles_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_roles_user ON public.user_roles USING btree (user_id);


--
-- Name: idx_user_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_status ON public.users USING btree (status);


--
-- Name: idx_user_warehouses_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_warehouses_user ON public.user_warehouses USING btree (user_id);


--
-- Name: idx_user_warehouses_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_warehouses_warehouse ON public.user_warehouses USING btree (warehouse_id);


--
-- Name: idx_warehouse_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warehouse_active ON public.warehouses USING btree (active);


--
-- Name: idx_warehouse_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_warehouse_code ON public.warehouses USING btree (warehouse_code);


--
-- Name: idx_zone_warehouse; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_zone_warehouse ON public.zones USING btree (warehouse_id);


--
-- Name: adjustment_items adjustment_items_adjustment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_items
    ADD CONSTRAINT adjustment_items_adjustment_id_fkey FOREIGN KEY (adjustment_id) REFERENCES public.adjustments(adjustment_id) ON DELETE CASCADE;


--
-- Name: adjustment_items adjustment_items_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_items
    ADD CONSTRAINT adjustment_items_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: adjustment_items adjustment_items_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_items
    ADD CONSTRAINT adjustment_items_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: adjustment_items adjustment_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustment_items
    ADD CONSTRAINT adjustment_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: adjustments adjustments_approved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustments
    ADD CONSTRAINT adjustments_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES public.users(user_id);


--
-- Name: adjustments adjustments_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustments
    ADD CONSTRAINT adjustments_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: adjustments adjustments_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjustments
    ADD CONSTRAINT adjustments_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: attachments attachments_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES public.users(user_id);


--
-- Name: audit_logs audit_logs_action_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_action_by_fkey FOREIGN KEY (action_by) REFERENCES public.users(user_id);


--
-- Name: categories categories_parent_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_parent_category_id_fkey FOREIGN KEY (parent_category_id) REFERENCES public.categories(category_id);


--
-- Name: enum_transitions enum_transitions_enum_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_transitions
    ADD CONSTRAINT enum_transitions_enum_type_id_fkey FOREIGN KEY (enum_type_id) REFERENCES public.enum_types(enum_type_id) ON DELETE CASCADE;


--
-- Name: enum_values enum_values_enum_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enum_values
    ADD CONSTRAINT enum_values_enum_type_id_fkey FOREIGN KEY (enum_type_id) REFERENCES public.enum_types(enum_type_id) ON DELETE CASCADE;


--
-- Name: expiry_rules expiry_rules_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expiry_rules
    ADD CONSTRAINT expiry_rules_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: expiry_rules expiry_rules_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expiry_rules
    ADD CONSTRAINT expiry_rules_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: incidents incidents_attachment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT incidents_attachment_id_fkey FOREIGN KEY (attachment_id) REFERENCES public.attachments(attachment_id);


--
-- Name: incidents incidents_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT incidents_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: incidents incidents_reported_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT incidents_reported_by_fkey FOREIGN KEY (reported_by) REFERENCES public.users(user_id);


--
-- Name: incidents incidents_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.incidents
    ADD CONSTRAINT incidents_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: inventory_alerts inventory_alerts_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_alerts
    ADD CONSTRAINT inventory_alerts_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: inventory_alerts inventory_alerts_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_alerts
    ADD CONSTRAINT inventory_alerts_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: inventory_alerts inventory_alerts_resolved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_alerts
    ADD CONSTRAINT inventory_alerts_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES public.users(user_id);


--
-- Name: inventory_alerts inventory_alerts_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_alerts
    ADD CONSTRAINT inventory_alerts_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: inventory_alerts inventory_alerts_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_alerts
    ADD CONSTRAINT inventory_alerts_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: inventory_lots inventory_lots_receiving_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_lots
    ADD CONSTRAINT inventory_lots_receiving_item_id_fkey FOREIGN KEY (receiving_item_id) REFERENCES public.receiving_items(receiving_item_id);


--
-- Name: inventory_lots inventory_lots_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_lots
    ADD CONSTRAINT inventory_lots_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: inventory_snapshot inventory_snapshot_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_snapshot
    ADD CONSTRAINT inventory_snapshot_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: inventory_snapshot inventory_snapshot_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_snapshot
    ADD CONSTRAINT inventory_snapshot_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: inventory_snapshot inventory_snapshot_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_snapshot
    ADD CONSTRAINT inventory_snapshot_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: inventory_snapshot inventory_snapshot_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_snapshot
    ADD CONSTRAINT inventory_snapshot_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: inventory_transactions inventory_transactions_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: inventory_transactions inventory_transactions_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: inventory_transactions inventory_transactions_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: inventory_transactions inventory_transactions_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: inventory_transactions inventory_transactions_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: locations locations_parent_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_parent_location_id_fkey FOREIGN KEY (parent_location_id) REFERENCES public.locations(location_id);


--
-- Name: locations locations_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: locations locations_zone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES public.zones(zone_id);


--
-- Name: picking_task_items picking_task_items_from_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_task_items
    ADD CONSTRAINT picking_task_items_from_location_id_fkey FOREIGN KEY (from_location_id) REFERENCES public.locations(location_id);


--
-- Name: picking_task_items picking_task_items_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_task_items
    ADD CONSTRAINT picking_task_items_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: picking_task_items picking_task_items_picking_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_task_items
    ADD CONSTRAINT picking_task_items_picking_task_id_fkey FOREIGN KEY (picking_task_id) REFERENCES public.picking_tasks(picking_task_id) ON DELETE CASCADE;


--
-- Name: picking_task_items picking_task_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_task_items
    ADD CONSTRAINT picking_task_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: picking_tasks picking_tasks_assigned_to_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_tasks
    ADD CONSTRAINT picking_tasks_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES public.users(user_id);


--
-- Name: picking_tasks picking_tasks_shipment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_tasks
    ADD CONSTRAINT picking_tasks_shipment_id_fkey FOREIGN KEY (shipment_id) REFERENCES public.shipments(shipment_id);


--
-- Name: picking_tasks picking_tasks_so_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_tasks
    ADD CONSTRAINT picking_tasks_so_id_fkey FOREIGN KEY (so_id) REFERENCES public.sales_orders(so_id);


--
-- Name: picking_tasks picking_tasks_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.picking_tasks
    ADD CONSTRAINT picking_tasks_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: putaway_task_items putaway_task_items_actual_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_task_items
    ADD CONSTRAINT putaway_task_items_actual_location_id_fkey FOREIGN KEY (actual_location_id) REFERENCES public.locations(location_id);


--
-- Name: putaway_task_items putaway_task_items_putaway_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_task_items
    ADD CONSTRAINT putaway_task_items_putaway_task_id_fkey FOREIGN KEY (putaway_task_id) REFERENCES public.putaway_tasks(putaway_task_id) ON DELETE CASCADE;


--
-- Name: putaway_task_items putaway_task_items_receiving_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_task_items
    ADD CONSTRAINT putaway_task_items_receiving_item_id_fkey FOREIGN KEY (receiving_item_id) REFERENCES public.receiving_items(receiving_item_id);


--
-- Name: putaway_task_items putaway_task_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_task_items
    ADD CONSTRAINT putaway_task_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: putaway_task_items putaway_task_items_suggested_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_task_items
    ADD CONSTRAINT putaway_task_items_suggested_location_id_fkey FOREIGN KEY (suggested_location_id) REFERENCES public.locations(location_id);


--
-- Name: putaway_tasks putaway_tasks_assigned_to_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_tasks
    ADD CONSTRAINT putaway_tasks_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES public.users(user_id);


--
-- Name: putaway_tasks putaway_tasks_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_tasks
    ADD CONSTRAINT putaway_tasks_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: putaway_tasks putaway_tasks_from_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_tasks
    ADD CONSTRAINT putaway_tasks_from_location_id_fkey FOREIGN KEY (from_location_id) REFERENCES public.locations(location_id);


--
-- Name: putaway_tasks putaway_tasks_receiving_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_tasks
    ADD CONSTRAINT putaway_tasks_receiving_id_fkey FOREIGN KEY (receiving_id) REFERENCES public.receiving_orders(receiving_id) ON DELETE CASCADE;


--
-- Name: putaway_tasks putaway_tasks_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.putaway_tasks
    ADD CONSTRAINT putaway_tasks_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: qc_inspections qc_inspections_attachment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qc_inspections
    ADD CONSTRAINT qc_inspections_attachment_id_fkey FOREIGN KEY (attachment_id) REFERENCES public.attachments(attachment_id);


--
-- Name: qc_inspections qc_inspections_inspected_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qc_inspections
    ADD CONSTRAINT qc_inspections_inspected_by_fkey FOREIGN KEY (inspected_by) REFERENCES public.users(user_id);


--
-- Name: qc_inspections qc_inspections_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qc_inspections
    ADD CONSTRAINT qc_inspections_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: qc_inspections qc_inspections_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qc_inspections
    ADD CONSTRAINT qc_inspections_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: quarantine_holds quarantine_holds_hold_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quarantine_holds
    ADD CONSTRAINT quarantine_holds_hold_by_fkey FOREIGN KEY (hold_by) REFERENCES public.users(user_id);


--
-- Name: quarantine_holds quarantine_holds_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quarantine_holds
    ADD CONSTRAINT quarantine_holds_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: quarantine_holds quarantine_holds_release_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quarantine_holds
    ADD CONSTRAINT quarantine_holds_release_by_fkey FOREIGN KEY (release_by) REFERENCES public.users(user_id);


--
-- Name: quarantine_holds quarantine_holds_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quarantine_holds
    ADD CONSTRAINT quarantine_holds_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: receiving_items receiving_items_receiving_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_items
    ADD CONSTRAINT receiving_items_receiving_id_fkey FOREIGN KEY (receiving_id) REFERENCES public.receiving_orders(receiving_id) ON DELETE CASCADE;


--
-- Name: receiving_items receiving_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_items
    ADD CONSTRAINT receiving_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: receiving_orders receiving_orders_approved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES public.users(user_id);


--
-- Name: receiving_orders receiving_orders_confirmed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_confirmed_by_fkey FOREIGN KEY (confirmed_by) REFERENCES public.users(user_id);


--
-- Name: receiving_orders receiving_orders_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: receiving_orders receiving_orders_putaway_done_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_putaway_done_by_fkey FOREIGN KEY (putaway_done_by) REFERENCES public.users(user_id);


--
-- Name: receiving_orders receiving_orders_source_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_source_warehouse_id_fkey FOREIGN KEY (source_warehouse_id) REFERENCES public.warehouses(warehouse_id);


--
-- Name: receiving_orders receiving_orders_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(supplier_id);


--
-- Name: receiving_orders receiving_orders_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_orders
    ADD CONSTRAINT receiving_orders_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: reservations reservations_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: reservations reservations_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: reservations reservations_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: return_items return_items_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT return_items_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: return_items return_items_return_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT return_items_return_id_fkey FOREIGN KEY (return_id) REFERENCES public.returns(return_id) ON DELETE CASCADE;


--
-- Name: return_items return_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT return_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: returns returns_approved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.returns
    ADD CONSTRAINT returns_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES public.users(user_id);


--
-- Name: returns returns_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.returns
    ADD CONSTRAINT returns_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: returns returns_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.returns
    ADD CONSTRAINT returns_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(permission_id) ON DELETE CASCADE;


--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: sales_order_items sales_order_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_items
    ADD CONSTRAINT sales_order_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: sales_order_items sales_order_items_so_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order_items
    ADD CONSTRAINT sales_order_items_so_id_fkey FOREIGN KEY (so_id) REFERENCES public.sales_orders(so_id) ON DELETE CASCADE;


--
-- Name: sales_orders sales_orders_approved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders
    ADD CONSTRAINT sales_orders_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES public.users(user_id);


--
-- Name: sales_orders sales_orders_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders
    ADD CONSTRAINT sales_orders_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: sales_orders sales_orders_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders
    ADD CONSTRAINT sales_orders_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id);


--
-- Name: sales_orders sales_orders_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_orders
    ADD CONSTRAINT sales_orders_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: sds_documents sds_documents_attachment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sds_documents
    ADD CONSTRAINT sds_documents_attachment_id_fkey FOREIGN KEY (attachment_id) REFERENCES public.attachments(attachment_id);


--
-- Name: shipment_items shipment_items_from_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipment_items
    ADD CONSTRAINT shipment_items_from_location_id_fkey FOREIGN KEY (from_location_id) REFERENCES public.locations(location_id);


--
-- Name: shipment_items shipment_items_shipment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipment_items
    ADD CONSTRAINT shipment_items_shipment_id_fkey FOREIGN KEY (shipment_id) REFERENCES public.shipments(shipment_id) ON DELETE CASCADE;


--
-- Name: shipment_items shipment_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipment_items
    ADD CONSTRAINT shipment_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: shipments shipments_carrier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT shipments_carrier_id_fkey FOREIGN KEY (carrier_id) REFERENCES public.carriers(carrier_id);


--
-- Name: shipments shipments_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT shipments_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: shipments shipments_dispatched_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT shipments_dispatched_by_fkey FOREIGN KEY (dispatched_by) REFERENCES public.users(user_id);


--
-- Name: shipments shipments_so_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT shipments_so_id_fkey FOREIGN KEY (so_id) REFERENCES public.sales_orders(so_id);


--
-- Name: shipments shipments_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipments
    ADD CONSTRAINT shipments_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: sku_thresholds sku_thresholds_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sku_thresholds
    ADD CONSTRAINT sku_thresholds_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: sku_thresholds sku_thresholds_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sku_thresholds
    ADD CONSTRAINT sku_thresholds_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id) ON DELETE CASCADE;


--
-- Name: sku_thresholds sku_thresholds_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sku_thresholds
    ADD CONSTRAINT sku_thresholds_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(user_id);


--
-- Name: sku_thresholds sku_thresholds_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sku_thresholds
    ADD CONSTRAINT sku_thresholds_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: skus skus_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skus
    ADD CONSTRAINT skus_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(category_id);


--
-- Name: skus skus_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skus
    ADD CONSTRAINT skus_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(user_id);


--
-- Name: stocktake_items stocktake_items_counted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT stocktake_items_counted_by_fkey FOREIGN KEY (counted_by) REFERENCES public.users(user_id);


--
-- Name: stocktake_items stocktake_items_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT stocktake_items_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: stocktake_items stocktake_items_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT stocktake_items_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: stocktake_items stocktake_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT stocktake_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: stocktake_items stocktake_items_stocktake_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktake_items
    ADD CONSTRAINT stocktake_items_stocktake_id_fkey FOREIGN KEY (stocktake_id) REFERENCES public.stocktakes(stocktake_id) ON DELETE CASCADE;


--
-- Name: stocktakes stocktakes_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes
    ADD CONSTRAINT stocktakes_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: stocktakes stocktakes_posted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes
    ADD CONSTRAINT stocktakes_posted_by_fkey FOREIGN KEY (posted_by) REFERENCES public.users(user_id);


--
-- Name: stocktakes stocktakes_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocktakes
    ADD CONSTRAINT stocktakes_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE RESTRICT;


--
-- Name: storage_policy_rules storage_policy_rules_policy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_policy_rules
    ADD CONSTRAINT storage_policy_rules_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES public.storage_policies(policy_id) ON DELETE CASCADE;


--
-- Name: storage_policy_rules storage_policy_rules_zone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_policy_rules
    ADD CONSTRAINT storage_policy_rules_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES public.zones(zone_id);


--
-- Name: transfer_items transfer_items_lot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT transfer_items_lot_id_fkey FOREIGN KEY (lot_id) REFERENCES public.inventory_lots(lot_id);


--
-- Name: transfer_items transfer_items_sku_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT transfer_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES public.skus(sku_id);


--
-- Name: transfer_items transfer_items_transfer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT transfer_items_transfer_id_fkey FOREIGN KEY (transfer_id) REFERENCES public.transfers(transfer_id) ON DELETE CASCADE;


--
-- Name: transfers transfers_approved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES public.users(user_id);


--
-- Name: transfers transfers_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(user_id);


--
-- Name: transfers transfers_from_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_from_warehouse_id_fkey FOREIGN KEY (from_warehouse_id) REFERENCES public.warehouses(warehouse_id);


--
-- Name: transfers transfers_to_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_to_warehouse_id_fkey FOREIGN KEY (to_warehouse_id) REFERENCES public.warehouses(warehouse_id);


--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: user_warehouses user_warehouses_assigned_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_warehouses
    ADD CONSTRAINT user_warehouses_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES public.users(user_id);


--
-- Name: user_warehouses user_warehouses_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_warehouses
    ADD CONSTRAINT user_warehouses_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: user_warehouses user_warehouses_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_warehouses
    ADD CONSTRAINT user_warehouses_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- Name: zones zones_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.zones
    ADD CONSTRAINT zones_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(warehouse_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict DLupeTyv82GatHcqDa9Y0xv7EThKlj8dW3GV5VR8D1pL1Er27LeeSJScibf1s6o

