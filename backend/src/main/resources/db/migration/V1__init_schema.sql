-- =====================================================
-- BizControl.uz - Initial Schema
-- =====================================================

-- Companies
CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    address TEXT,
    business_type VARCHAR(50),
    main_currency VARCHAR(10) DEFAULT 'UZS',
    secondary_currency VARCHAR(10),
    logo_url VARCHAR(500),
    tax_id VARCHAR(100),
    default_payment_methods VARCHAR(255),
    default_stock_unit VARCHAR(50) DEFAULT 'piece',
    starting_cash_balance DECIMAL(20,2) DEFAULT 0,
    starting_bank_balance DECIMAL(20,2) DEFAULT 0,
    cash_balance DECIMAL(20,2) DEFAULT 0,
    bank_balance DECIMAL(20,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(50) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Roles
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,  -- OWNER, ADMIN, STAFF
    description VARCHAR(255),
    company_id BIGINT REFERENCES companies(id)
);

-- Company Users (join table with role)
CREATE TABLE company_users (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'STAFF',  -- OWNER, ADMIN, STAFF
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(company_id, user_id)
);

-- Categories
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT REFERENCES categories(id),
    description TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

-- Suppliers
CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    country VARCHAR(100),
    city VARCHAR(100),
    address TEXT,
    currency VARCHAR(10) DEFAULT 'UZS',
    current_debt DECIMAL(20,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'active',
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

-- Products
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(100),
    barcode VARCHAR(100),
    category_id BIGINT REFERENCES categories(id),
    brand VARCHAR(100),
    unit VARCHAR(50) DEFAULT 'piece',
    purchase_price DECIMAL(20,2) DEFAULT 0,
    selling_price DECIMAL(20,2) DEFAULT 0,
    wholesale_price DECIMAL(20,2),
    min_stock_level DECIMAL(20,3) DEFAULT 0,
    current_stock DECIMAL(20,3) DEFAULT 0,
    supplier_id BIGINT REFERENCES suppliers(id),
    currency VARCHAR(10) DEFAULT 'UZS',
    description TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id),
    UNIQUE(company_id, sku)
);

-- Customers
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    phone2 VARCHAR(50),
    city VARCHAR(100),
    address TEXT,
    customer_type VARCHAR(30) DEFAULT 'retail',  -- retail, wholesale, vip, risky
    debt_limit DECIMAL(20,2),
    current_debt DECIMAL(20,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'active',  -- active, blocked, risky
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

-- Sales
CREATE TABLE sales (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    sale_number VARCHAR(50) NOT NULL,
    customer_id BIGINT REFERENCES customers(id),
    sale_date TIMESTAMP NOT NULL,
    total_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(20,2) DEFAULT 0,
    paid_amount DECIMAL(20,2) DEFAULT 0,
    unpaid_amount DECIMAL(20,2) DEFAULT 0,
    payment_method VARCHAR(30) DEFAULT 'cash',  -- cash, bank, debt, mixed
    currency VARCHAR(10) DEFAULT 'UZS',
    status VARCHAR(20) DEFAULT 'active',  -- active, cancelled
    note TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id),
    UNIQUE(company_id, sale_number)
);

-- Sale Items
CREATE TABLE sale_items (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL REFERENCES sales(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity DECIMAL(20,3) NOT NULL,
    selling_price DECIMAL(20,2) NOT NULL,
    purchase_cost DECIMAL(20,2) DEFAULT 0,
    discount_amount DECIMAL(20,2) DEFAULT 0,
    total_amount DECIMAL(20,2) NOT NULL,
    profit_amount DECIMAL(20,2) DEFAULT 0
);

-- Purchases
CREATE TABLE purchases (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    purchase_number VARCHAR(50) NOT NULL,
    supplier_id BIGINT REFERENCES suppliers(id),
    purchase_date TIMESTAMP NOT NULL,
    total_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(20,2) DEFAULT 0,
    additional_cost DECIMAL(20,2) DEFAULT 0,
    paid_amount DECIMAL(20,2) DEFAULT 0,
    unpaid_amount DECIMAL(20,2) DEFAULT 0,
    payment_method VARCHAR(30) DEFAULT 'cash',
    currency VARCHAR(10) DEFAULT 'UZS',
    status VARCHAR(20) DEFAULT 'active',
    note TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id),
    UNIQUE(company_id, purchase_number)
);

-- Purchase Items
CREATE TABLE purchase_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL REFERENCES purchases(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity DECIMAL(20,3) NOT NULL,
    purchase_price DECIMAL(20,2) NOT NULL,
    discount_amount DECIMAL(20,2) DEFAULT 0,
    total_amount DECIMAL(20,2) NOT NULL
);

-- Stock Movements
CREATE TABLE stock_movements (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    movement_type VARCHAR(30) NOT NULL,  -- purchase, sale, adjustment, return
    quantity DECIMAL(20,3) NOT NULL,
    previous_stock DECIMAL(20,3) NOT NULL,
    new_stock DECIMAL(20,3) NOT NULL,
    reference_id BIGINT,
    reference_type VARCHAR(30),  -- sale, purchase, adjustment
    note TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id)
);

-- Cash Transactions
CREATE TABLE cash_transactions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    transaction_type VARCHAR(20) NOT NULL,  -- income, expense
    payment_source VARCHAR(20) DEFAULT 'cash',  -- cash, bank
    amount DECIMAL(20,2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'UZS',
    category VARCHAR(100),
    related_sale_id BIGINT REFERENCES sales(id),
    related_purchase_id BIGINT REFERENCES purchases(id),
    related_customer_id BIGINT REFERENCES customers(id),
    related_supplier_id BIGINT REFERENCES suppliers(id),
    transaction_date TIMESTAMP NOT NULL,
    note TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

-- Bank Transactions
CREATE TABLE bank_transactions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    transaction_type VARCHAR(20) NOT NULL,  -- income, expense
    amount DECIMAL(20,2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'UZS',
    category VARCHAR(100),
    related_sale_id BIGINT REFERENCES sales(id),
    related_purchase_id BIGINT REFERENCES purchases(id),
    related_customer_id BIGINT REFERENCES customers(id),
    related_supplier_id BIGINT REFERENCES suppliers(id),
    transaction_date TIMESTAMP NOT NULL,
    note TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

-- Debts
CREATE TABLE debts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    debt_type VARCHAR(20) NOT NULL,  -- customer, supplier
    customer_id BIGINT REFERENCES customers(id),
    supplier_id BIGINT REFERENCES suppliers(id),
    related_sale_id BIGINT REFERENCES sales(id),
    related_purchase_id BIGINT REFERENCES purchases(id),
    original_amount DECIMAL(20,2) NOT NULL,
    paid_amount DECIMAL(20,2) DEFAULT 0,
    remaining_amount DECIMAL(20,2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'UZS',
    due_date DATE,
    status VARCHAR(20) DEFAULT 'open',  -- open, partial, closed, overdue
    note TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

-- Debt Payments
CREATE TABLE debt_payments (
    id BIGSERIAL PRIMARY KEY,
    debt_id BIGINT NOT NULL REFERENCES debts(id),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    amount DECIMAL(20,2) NOT NULL,
    payment_method VARCHAR(30) DEFAULT 'cash',
    currency VARCHAR(10) DEFAULT 'UZS',
    payment_date TIMESTAMP NOT NULL,
    note TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id)
);

-- Audit Logs
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),
    user_id BIGINT REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,  -- CREATE, UPDATE, DELETE, LOGIN, etc.
    entity_type VARCHAR(50) NOT NULL,  -- Product, Sale, Purchase, etc.
    entity_id BIGINT,
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(50),
    note TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Alerts
CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    alert_type VARCHAR(50) NOT NULL,  -- low_stock, out_of_stock, customer_debt, etc.
    title VARCHAR(255) NOT NULL,
    message TEXT,
    related_entity_type VARCHAR(50),
    related_entity_id BIGINT,
    status VARCHAR(20) DEFAULT 'new',  -- new, seen, resolved
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Settings
CREATE TABLE settings (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(company_id, setting_key)
);

-- Indexes for performance
CREATE INDEX idx_products_company ON products(company_id);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_supplier ON products(supplier_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_sales_company ON sales(company_id);
CREATE INDEX idx_sales_customer ON sales(customer_id);
CREATE INDEX idx_sales_date ON sales(sale_date);
CREATE INDEX idx_sales_status ON sales(status);
CREATE INDEX idx_purchases_company ON purchases(company_id);
CREATE INDEX idx_purchases_supplier ON purchases(supplier_id);
CREATE INDEX idx_purchases_date ON purchases(purchase_date);
CREATE INDEX idx_stock_movements_company ON stock_movements(company_id);
CREATE INDEX idx_stock_movements_product ON stock_movements(product_id);
CREATE INDEX idx_cash_transactions_company ON cash_transactions(company_id);
CREATE INDEX idx_cash_transactions_date ON cash_transactions(transaction_date);
CREATE INDEX idx_debts_company ON debts(company_id);
CREATE INDEX idx_debts_type ON debts(debt_type);
CREATE INDEX idx_debts_status ON debts(status);
CREATE INDEX idx_audit_logs_company ON audit_logs(company_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_alerts_company ON alerts(company_id);
CREATE INDEX idx_alerts_status ON alerts(status);
