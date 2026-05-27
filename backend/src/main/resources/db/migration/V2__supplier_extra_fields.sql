-- V2: Add contact_person, email, tax_id, bank_account to suppliers
ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS contact_person VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS tax_id         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bank_account   VARCHAR(255);
