-- V2__create_users_and_roles.sql
-- Users + RBAC: roles, permissions, role_permissions, user_roles.
-- super_admin users have NULL organization_id (platform-level).
-- All other users belong to exactly one organization.

CREATE TABLE IF NOT EXISTS users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID REFERENCES organizations(id) ON DELETE CASCADE,
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    full_name         VARCHAR(255),
    phone             VARCHAR(50),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    is_locked         BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, email)
);
-- super_admin (platform-level) emails must be globally unique too.
-- Enforced via partial unique index so multiple orgs can reuse an email
-- but a NULL-org email is globally unique.
CREATE UNIQUE INDEX uq_users_email_global ON users(email) WHERE organization_id IS NULL;

COMMENT ON TABLE users IS 'All platform users (super_admin, admin, customer). super_admin has NULL organization_id.';
COMMENT ON COLUMN users.organization_id IS 'NULL for super_admin (platform-level) users.';

CREATE TABLE IF NOT EXISTS roles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID REFERENCES organizations(id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(500),
    is_system_role    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, name)
);
-- System roles (super_admin, etc.) have NULL organization_id and are globally unique.
CREATE UNIQUE INDEX uq_roles_name_global ON roles(name) WHERE organization_id IS NULL;

COMMENT ON TABLE roles IS 'Role definitions. System roles have NULL organization_id.';
COMMENT ON COLUMN roles.is_system_role IS 'TRUE for built-in roles that cannot be deleted.';

CREATE TABLE IF NOT EXISTS permissions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(100) NOT NULL UNIQUE,
    description       VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE permissions IS 'Permission definitions (e.g. machine:read, command:write).';

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id           UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id     UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

COMMENT ON TABLE role_permissions IS 'Many-to-many: roles to permissions.';

CREATE TABLE IF NOT EXISTS user_roles (
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id           UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE user_roles IS 'Many-to-many: users to roles.';
