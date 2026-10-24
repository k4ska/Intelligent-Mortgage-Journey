-- Messages written next to a bank's email thread in the inbox (#80); null in the chat.
ALTER TABLE message ADD COLUMN thread_id UUID;
