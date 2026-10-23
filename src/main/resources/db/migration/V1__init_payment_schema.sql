-- ==============================================================================
-- Flyway Migration: V1__init_payment_schema.sql
-- Service: occupify-payment
-- Description: Initialize payment schema (users, contracts, wallets, transactions)
--              matching docs/ERD.md
-- ==============================================================================

-- 1. Table: users (Local replicated entity synchronized from Identity service)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    avatar_url VARCHAR(500),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- 2. Table: contracts (Local replicated entity synchronized from Core service)
CREATE TABLE IF NOT EXISTS contracts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    employer_id UUID NOT NULL,
    employee_id UUID,
    source_type VARCHAR(50),
    source_id UUID,
    agreed_rate DECIMAL(19, 4) NOT NULL,
    salary_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_status VARCHAR(50),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    CONSTRAINT fk_contracts_employer FOREIGN KEY (employer_id) REFERENCES users(id),
    CONSTRAINT fk_contracts_employee FOREIGN KEY (employee_id) REFERENCES users(id),
    CONSTRAINT chk_contracts_agreed_rate_positive CHECK (agreed_rate > 0)
);

-- 3. Table: wallets (Digital wallet for user accounts)
CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    available_balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    locked_balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_wallets_available_positive CHECK (available_balance >= 0),
    CONSTRAINT chk_wallets_locked_positive CHECK (locked_balance >= 0)
);

-- 4. Table: transactions (Append-only financial transaction ledger)
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_transactions_contract FOREIGN KEY (contract_id) REFERENCES contracts(id),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

-- 5. Indexes for Query Performance and Foreign Keys
CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);
CREATE INDEX IF NOT EXISTS idx_contracts_job_id ON contracts(job_id);
CREATE INDEX IF NOT EXISTS idx_contracts_employer_id ON contracts(employer_id);
CREATE INDEX IF NOT EXISTS idx_contracts_employee_id ON contracts(employee_id);
CREATE INDEX IF NOT EXISTS idx_contracts_status ON contracts(status);
CREATE INDEX IF NOT EXISTS idx_transactions_wallet_id ON transactions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_transactions_contract_id ON transactions(contract_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
