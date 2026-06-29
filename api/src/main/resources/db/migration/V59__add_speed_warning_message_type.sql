-- Add SPEED_WARNING to the message_type check constraints
ALTER TABLE messages DROP CONSTRAINT chk_message_type;
ALTER TABLE messages ADD CONSTRAINT chk_message_type
    CHECK (message_type IN ('GENERAL', 'COUNTDOWN', 'CHECKPOINT', 'RALLYDESK', 'ACHIEVEMENT', 'RIVAL', 'SPEED_WARNING'));

ALTER TABLE message_templates DROP CONSTRAINT chk_template_message_type;
ALTER TABLE message_templates ADD CONSTRAINT chk_template_message_type
    CHECK (message_type IN ('GENERAL', 'COUNTDOWN', 'CHECKPOINT', 'RALLYDESK', 'ACHIEVEMENT', 'RIVAL', 'SPEED_WARNING'));
