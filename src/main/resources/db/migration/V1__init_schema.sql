CREATE TABLE owners (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE widgets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES owners(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    fields JSONB NOT NULL,
    button_text VARCHAR(100) NOT NULL DEFAULT 'Submit',
    display_options JSONB,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_widgets_owner_id ON widgets(owner_id);

CREATE TABLE submissions (
    id UUID PRIMARY KEY,
    widget_id UUID NOT NULL REFERENCES widgets(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES owners(id) ON DELETE CASCADE,
    payload JSONB NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    geo_country VARCHAR(100),
    geo_city VARCHAR(100),
    geo_provider_used VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_submissions_widget_id ON submissions(widget_id);
CREATE INDEX idx_submissions_owner_created ON submissions(owner_id, created_at);
