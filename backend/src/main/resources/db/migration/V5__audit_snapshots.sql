-- Change forensics for the audit trail (SOW 2.2.14): before/after entity
-- snapshots for rule / knowledgebase / user mutations, plus indexes for the
-- filterable audit query surface.
ALTER TABLE audit_log ADD COLUMN before_json text;
ALTER TABLE audit_log ADD COLUMN after_json  text;

CREATE INDEX idx_audit_action ON audit_log (action);
CREATE INDEX idx_audit_actor  ON audit_log (actor_id);
