-- V19__add_customer_user_link.sql
-- Link customers to their mobile app user account.
-- When a customer is created, a user record is auto-created with role 'customer'
-- so they can log in to the Flutter mobile app with their phone number.

ALTER TABLE customers ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_customers_user_id ON customers(user_id) WHERE user_id IS NOT NULL;

COMMENT ON COLUMN customers.user_id IS 'Linked user account for mobile app login. Auto-created when customer is created.';
