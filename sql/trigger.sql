-- One-time migration: add NOTIFY trigger for cache invalidation.
-- Run on the production PostgreSQL database.
--
-- The webapp listens on the 'news_changed' channel and refreshes its
-- cache when new articles are inserted. PostgreSQL deduplicates NOTIFY
-- within a transaction, so a single notification is delivered per
-- ingestion run regardless of how many rows are inserted.

CREATE OR REPLACE FUNCTION notify_news_changed() RETURNS trigger AS $$
BEGIN
    NOTIFY news_changed;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER news_insert_notify
    AFTER INSERT ON news
    FOR EACH STATEMENT
    EXECUTE FUNCTION notify_news_changed();
