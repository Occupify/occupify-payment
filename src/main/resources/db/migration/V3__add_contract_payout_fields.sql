-- ==============================================================================
-- Flyway Migration: V3__add_contract_payout_fields.sql
-- Service: occupify-payment
-- Description: Add payout tracking fields (agreed_rate, last_payout_at, total_released)
-- ==============================================================================

ALTER TABLE contracts ADD COLUMN IF NOT EXISTS agreed_rate DECIMAL(19, 4);
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS last_payout_at TIMESTAMP;
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS total_released DECIMAL(19, 4) DEFAULT 0;

-- Backfill total_released
UPDATE contracts SET total_released = 0 WHERE total_released IS NULL;

-- Backfill agreed_rate from previous escrow hold transactions if available
UPDATE contracts c
SET agreed_rate = (
    SELECT t.amount FROM transactions t
    WHERE t.contract_id = c.id AND t.transaction_type = 'ESCROW_HOLD'
    ORDER BY t.created_at DESC LIMIT 1
)
WHERE c.agreed_rate IS NULL;
