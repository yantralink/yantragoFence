-- V15__add_performance_indexes.sql
-- Indexes on foreign keys, tenant columns, and time columns that were not
-- already created inline in earlier migrations. Partitioned tables already
-- have per-partition indexes from V6/V7/V11.

-- ===== Foreign key indexes (not auto-indexed by PostgreSQL) =====
CREATE INDEX idx_users_organization_id ON users(organization_id);
CREATE INDEX idx_roles_organization_id ON roles(organization_id);
CREATE INDEX idx_customers_organization_id ON customers(organization_id);
CREATE INDEX idx_machines_organization_id ON machines(organization_id);
CREATE INDEX idx_machines_customer_id ON machines(customer_id);
CREATE INDEX idx_devices_organization_id ON devices(organization_id);
CREATE INDEX idx_devices_machine_id ON devices(machine_id);
CREATE INDEX idx_devices_imei ON devices(imei);
CREATE INDEX idx_machine_assignments_organization_id ON machine_assignments(organization_id);
CREATE INDEX idx_machine_assignments_machine_id ON machine_assignments(machine_id);
CREATE INDEX idx_machine_assignments_customer_id ON machine_assignments(customer_id);
CREATE INDEX idx_device_states_organization_id ON device_states(organization_id);
CREATE INDEX idx_device_locations_organization_id ON device_locations(organization_id);
CREATE INDEX idx_device_locations_machine_id ON device_locations(machine_id);
CREATE INDEX idx_machine_commands_organization_id ON machine_commands(organization_id);
CREATE INDEX idx_machine_commands_machine_id ON machine_commands(machine_id);
CREATE INDEX idx_machine_commands_status ON machine_commands(status);
CREATE INDEX idx_machine_commands_created_at ON machine_commands(created_at DESC);
CREATE INDEX idx_command_attempts_command_id ON command_attempts(command_id);
CREATE INDEX idx_alert_rules_organization_id ON alert_rules(organization_id);
CREATE INDEX idx_alert_rules_machine_id ON alert_rules(machine_id);
CREATE INDEX idx_alerts_organization_id ON alerts(organization_id);
CREATE INDEX idx_alerts_machine_id ON alerts(machine_id);
CREATE INDEX idx_alerts_triggered_at ON alerts(triggered_at DESC);
CREATE INDEX idx_alerts_is_acknowledged ON alerts(is_acknowledged) WHERE is_acknowledged = FALSE;
CREATE INDEX idx_notifications_organization_id ON notifications(organization_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notification_preferences_user_id ON notification_preferences(user_id);
CREATE INDEX idx_recharges_organization_id ON recharges(organization_id);
CREATE INDEX idx_recharges_device_id ON recharges(device_id);
CREATE INDEX idx_recharges_recharged_at ON recharges(recharged_at DESC);
CREATE INDEX idx_machine_settings_organization_id ON machine_settings(organization_id);
CREATE INDEX idx_machine_settings_machine_id ON machine_settings(machine_id);
CREATE INDEX idx_system_settings_organization_id ON system_settings(organization_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_revoked_at ON refresh_tokens(revoked_at) WHERE revoked_at IS NULL;
CREATE INDEX idx_login_sessions_user_id ON login_sessions(user_id);
CREATE INDEX idx_login_sessions_ended_at ON login_sessions(ended_at) WHERE ended_at IS NULL;
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- ===== PostGIS spatial index on device_locations (current location) =====
CREATE INDEX idx_device_locations_geo ON device_locations USING GIST(location_geo);

-- ===== Updated_at trigger helper (reusable function) =====
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at triggers to all tables with updated_at columns.
CREATE TRIGGER trg_organizations_updated_at BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_customers_updated_at BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_machines_updated_at BEFORE UPDATE ON machines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_devices_updated_at BEFORE UPDATE ON devices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_machine_assignments_updated_at BEFORE UPDATE ON machine_assignments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_device_states_updated_at BEFORE UPDATE ON device_states
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_device_locations_updated_at BEFORE UPDATE ON device_locations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_machine_commands_updated_at BEFORE UPDATE ON machine_commands
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_alert_rules_updated_at BEFORE UPDATE ON alert_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_alerts_updated_at BEFORE UPDATE ON alerts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_notifications_updated_at BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_notification_preferences_updated_at BEFORE UPDATE ON notification_preferences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recharges_updated_at BEFORE UPDATE ON recharges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_machine_settings_updated_at BEFORE UPDATE ON machine_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_system_settings_updated_at BEFORE UPDATE ON system_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
