-- ==============================================================================
-- Flyway Migration: V5__seed_test_contracts_wallets_and_escrow.sql
-- Service: occupify-payment
-- Description: Seed test contracts, user wallets, and escrow transactions
--              to verify Complete (Release) & Cancel (Refund) flows.
-- ==============================================================================

-- 1. Ensure User Hoa Le exists in payment replica
INSERT INTO users (id, email, full_name, avatar_url)
SELECT '11111111-1111-1111-1111-111111111111', 'hoa.le@smartlog.vn', 'Lê Thị Hoa', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = '11111111-1111-1111-1111-111111111111');

-- 2. Ensure Wallets exist with appropriate balances
-- Hoa Le Wallet (Employer for c4)
INSERT INTO wallets (id, user_id, available_balance, locked_balance, created_at, updated_at)
SELECT '11111111-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111111', 50000000.0000, 20000000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE id = '11111111-1111-1111-1111-111111111112');

UPDATE wallets
SET available_balance = 50000000.0000,
    locked_balance = 20000000.0000
WHERE id = '11111111-1111-1111-1111-111111111112';

-- Update Dev Mock User Wallet (locked 27M for c2: 15M + c3: 12M)
UPDATE wallets
SET locked_balance = 27000000.0000,
    available_balance = 50000000.0000
WHERE id = '33333333-3333-3333-3333-333333333331';

-- Update Khoa Nguyen Wallet (received 10M from completed c5)
UPDATE wallets
SET available_balance = 10000000.0000,
    locked_balance = 0.0000
WHERE id = '33333333-3333-3333-3333-333333333332';

-- 3. Seed Contracts
-- Contract c2 (IN_PROGRESS)
INSERT INTO contracts (
    id, job_id, employer_id, employee_id, source_type, source_id,
    salary_type, status, agreed_rate, total_released, started_at, finished_at
)
SELECT
    'c2222222-2222-2222-2222-222222222222',
    'b0000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    '33333333-3333-3333-3333-333333333333',
    'JOB_BID',
    '66666666-6666-6666-6666-666666666662',
    'MONTHLY',
    'IN_PROGRESS',
    15000000.0000,
    0.0000,
    TIMESTAMP '2026-10-22 00:00:00',
    NULL
WHERE NOT EXISTS (SELECT 1 FROM contracts WHERE id = 'c2222222-2222-2222-2222-222222222222');

-- Contract c3 (IN_PROGRESS)
INSERT INTO contracts (
    id, job_id, employer_id, employee_id, source_type, source_id,
    salary_type, status, agreed_rate, total_released, started_at, finished_at
)
SELECT
    'c3333333-3333-3333-3333-333333333333',
    'b0000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    '33333333-3333-3333-3333-333333333333',
    'JOB_BID',
    '66666666-6666-6666-6666-666666666663',
    'MONTHLY',
    'IN_PROGRESS',
    12000000.0000,
    0.0000,
    TIMESTAMP '2026-10-29 00:00:00',
    NULL
WHERE NOT EXISTS (SELECT 1 FROM contracts WHERE id = 'c3333333-3333-3333-3333-333333333333');

-- Contract c4 (IN_PROGRESS - Dev Mock as Freelancer)
INSERT INTO contracts (
    id, job_id, employer_id, employee_id, source_type, source_id,
    salary_type, status, agreed_rate, total_released, started_at, finished_at
)
SELECT
    'c4444444-4444-4444-4444-444444444444',
    'b0000000-0000-0000-0000-000000000005',
    '11111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000001',
    'JOB_BID',
    '66666666-6666-6666-6666-666666666665',
    'MONTHLY',
    'IN_PROGRESS',
    20000000.0000,
    0.0000,
    TIMESTAMP '2026-10-31 00:00:00',
    NULL
WHERE NOT EXISTS (SELECT 1 FROM contracts WHERE id = 'c4444444-4444-4444-4444-444444444444');

-- Contract c5 (COMPLETED)
INSERT INTO contracts (
    id, job_id, employer_id, employee_id, source_type, source_id,
    salary_type, status, agreed_rate, total_released, started_at, finished_at
)
SELECT
    'c5555555-5555-5555-5555-555555555555',
    'b0000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000001',
    '33333333-3333-3333-3333-333333333333',
    'JOB_BID',
    '66666666-6666-6666-6666-666666666664',
    'MONTHLY',
    'COMPLETED',
    10000000.0000,
    10000000.0000,
    TIMESTAMP '2026-10-06 00:00:00',
    TIMESTAMP '2026-11-04 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM contracts WHERE id = 'c5555555-5555-5555-5555-555555555555');

-- Contract c6 (PENDING)
INSERT INTO contracts (
    id, job_id, employer_id, employee_id, source_type, source_id,
    salary_type, status, agreed_rate, total_released, started_at, finished_at
)
SELECT
    'c6666666-6666-6666-6666-666666666666',
    'b0000000-0000-0000-0000-000000000006',
    '11111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000001',
    'JOB_BID',
    '66666666-6666-6666-6666-666666666666',
    'MONTHLY',
    'PENDING',
    25000000.0000,
    0.0000,
    NULL,
    NULL
WHERE NOT EXISTS (SELECT 1 FROM contracts WHERE id = 'c6666666-6666-6666-6666-666666666666');

-- 4. Seed Escrow Transactions
-- Escrow Hold for c2 (15M held from Dev Mock wallet)
INSERT INTO transactions (id, wallet_id, contract_id, amount, transaction_type, payment_method, status, created_at)
SELECT
    'e2222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333331',
    'c2222222-2222-2222-2222-222222222222',
    15000000.0000,
    'ESCROW_HOLD',
    'INTERNAL_WALLET',
    'SUCCESS',
    TIMESTAMP '2026-10-22 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE id = 'e2222222-2222-2222-2222-222222222222');

-- Escrow Hold for c3 (12M held from Dev Mock wallet)
INSERT INTO transactions (id, wallet_id, contract_id, amount, transaction_type, payment_method, status, created_at)
SELECT
    'e3333333-3333-3333-3333-333333333333',
    '33333333-3333-3333-3333-333333333331',
    'c3333333-3333-3333-3333-333333333333',
    12000000.0000,
    'ESCROW_HOLD',
    'INTERNAL_WALLET',
    'SUCCESS',
    TIMESTAMP '2026-10-29 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE id = 'e3333333-3333-3333-3333-333333333333');

-- Escrow Hold for c4 (20M held from Hoa Le wallet)
INSERT INTO transactions (id, wallet_id, contract_id, amount, transaction_type, payment_method, status, created_at)
SELECT
    'e4444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111112',
    'c4444444-4444-4444-4444-444444444444',
    20000000.0000,
    'ESCROW_HOLD',
    'INTERNAL_WALLET',
    'SUCCESS',
    TIMESTAMP '2026-10-31 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE id = 'e4444444-4444-4444-4444-444444444444');

-- Escrow Hold & Release for c5 (10M completed)
INSERT INTO transactions (id, wallet_id, contract_id, amount, transaction_type, payment_method, status, created_at)
SELECT
    'e5555555-5555-5555-5555-555555555551',
    '33333333-3333-3333-3333-333333333331',
    'c5555555-5555-5555-5555-555555555555',
    10000000.0000,
    'ESCROW_HOLD',
    'INTERNAL_WALLET',
    'SUCCESS',
    TIMESTAMP '2026-10-06 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE id = 'e5555555-5555-5555-5555-555555555551');

INSERT INTO transactions (id, wallet_id, contract_id, amount, transaction_type, payment_method, status, created_at)
SELECT
    'e5555555-5555-5555-5555-555555555552',
    '33333333-3333-3333-3333-333333333332',
    'c5555555-5555-5555-5555-555555555555',
    10000000.0000,
    'ESCROW_RELEASE',
    'INTERNAL_WALLET',
    'SUCCESS',
    TIMESTAMP '2026-11-04 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE id = 'e5555555-5555-5555-5555-555555555552');
