-- Records that a rallydesk broadcast was sent with SMS forced on, so the message reaches the
-- team's first emergency number whether or not its devices are active. Persisted per message
-- rather than derived, so a retry escalates exactly like the original send did.
--
-- FALSE for existing rows is correct: every historical message went out under the old rule,
-- where SMS was only a fallback for teams whose devices had gone inactive.
ALTER TABLE messages ADD COLUMN send_via_sms BOOLEAN NOT NULL DEFAULT FALSE;
