-- ==============================================================================
-- Flyway Migration: V2__seed_test_contract_and_users.sql
-- Service: occupify-payment
-- Description: Seed initial mock employer, freelancer, wallets, and a pending contract
-- ==============================================================================

-- 1. Seed Users
INSERT INTO users (id, email, full_name, avatar_url)
SELECT '00000000-0000-0000-0000-000000000001', 'dev-employer@occupify.local', 'Tôi (Chủ dự án)', 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = '00000000-0000-0000-0000-000000000001');

INSERT INTO users (id, email, full_name, avatar_url)
SELECT '33333333-3333-3333-3333-333333333333', 'khoa.nguyen@gmail.com', 'Nguyễn Minh Khoa', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = '33333333-3333-3333-3333-333333333333');

-- 2. Seed Wallets
INSERT INTO wallets (id, user_id, available_balance, locked_balance, created_at, updated_at)
SELECT '33333333-3333-3333-3333-333333333331', '00000000-0000-0000-0000-000000000001', 50000000.0000, 0.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE id = '33333333-3333-3333-3333-333333333331');

INSERT INTO wallets (id, user_id, available_balance, locked_balance, created_at, updated_at)
SELECT '33333333-3333-3333-3333-333333333332', '33333333-3333-3333-3333-333333333333', 0.0000, 0.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE id = '33333333-3333-3333-3333-333333333332');

-- 3. Seed Test Contract (Pending acceptance)
INSERT INTO contracts (id, job_id, employer_id, employee_id, source_type, source_id, salary_type, status, started_at, finished_at)
SELECT
    'c1111111-1111-1111-1111-111111111111',
    'b0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '33333333-3333-3333-3333-333333333333',
    'JOB_BID',
    '66666666-6666-6666-6666-666666666666',
    'MONTHLY',
    'PENDING',
    NULL,
    NULL
WHERE NOT EXISTS (SELECT 1 FROM contracts WHERE id = 'c1111111-1111-1111-1111-111111111111');
