ALTER TABLE widgets ADD COLUMN require_double_opt_in BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE submissions ADD COLUMN confirmed_at TIMESTAMPTZ NULL;
ALTER TABLE submissions ADD COLUMN consent_given BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE submissions ADD COLUMN consent_at TIMESTAMPTZ NULL;
UPDATE submissions SET confirmed_at = created_at WHERE confirmed_at IS NULL;