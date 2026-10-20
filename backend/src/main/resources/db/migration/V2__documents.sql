CREATE TABLE document (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    doc_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    file_name VARCHAR(255),
    read_values TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_document_user_type UNIQUE (user_id, doc_type)
);

ALTER TABLE profile_field ADD COLUMN source_document VARCHAR(255);
