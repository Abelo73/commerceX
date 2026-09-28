-- Transactional outbox (TRD §11): events are written in the same transaction as the business change and
-- relayed to Kafka afterwards, so "database committed but Kafka publish failed" cannot lose an event.

CREATE SEQUENCE outbox_events_seq;

CREATE TABLE outbox_events (
    id              BIGINT       PRIMARY KEY DEFAULT nextval('outbox_events_seq'),
    event_id        UUID         NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    message_key     VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      VARCHAR(1000),
    CONSTRAINT uq_outbox_events_event_id UNIQUE (event_id)
);

CREATE INDEX ix_outbox_events_pending ON outbox_events (next_attempt_at, id) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_events_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;

-- Consumer-side deduplication: Kafka delivers at least once; each consumer processes an event once.
CREATE TABLE processed_events (
    consumer     VARCHAR(100) NOT NULL,
    event_id     UUID         NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
