# YantraGO — Refined Requirements & Implementation Plan

## Overview

This document defines the refined flow for all modules after incorporating
the user's requirements, engineering suggestions, and a full review of the
existing codebase (database schema, models, services, controllers, security).

---

## 0. Critical Findings From Codebase Review

Before implementing, the following issues were found between the original
document and the actual codebase:

### 0.1 Machines vs Devices — Two Separate Tables

The current schema has TWO separate tables:

- **`machines`** — fencing machine units (name, model, serial_number, status, customer_id)
- **`devices`** — physical tracker devices (IMEI, SIM, protocol_type, firmware_version)

A machine can have a device bound to it via `devices.machine_id`.
The original document conflated these into one table, which would break
the existing architecture (AGENTS.md rule 10: no temporary architecture).

**Resolution**: Keep both tables. Add `machine_id` (human-readable) to `machines`.
When Super Admin creates a machine, they also specify IMEI/SIM/protocol,
which creates a `devices` record bound to the machine.

### 0.2 `machines.organization_id` is NOT NULL

The current schema requires every machine to have an organization.
The new flow requires machines to be created without an organization
(unassigned inventory).

**Resolution**: Migration to make `organization_id` nullable.

### 0.3 `machine_assignments` Table Already Exists

The schema already has a `machine_assignments` table for tracking
machine-to-customer assignment history. The document didn't mention it.

**Resolution**: Use this table for audit trail when assigning/unassigning
machines to customers.

### 0.4 `customers` Table Has No `user_id` Column

Customers are not linked to user accounts. The new flow requires
customers to log in to the mobile app.

**Resolution**: Add `user_id` column to `customers` table.

### 0.5 No Role-Based Access Control in SecurityConfig

The current `SecurityConfig` only checks authentication, not authorization.
All endpoints are open to any authenticated user. The new flow requires
role-based access (super_admin only for certain endpoints).

**Resolution**: Add `@PreAuthorize` annotations or `hasRole()` rules
in `SecurityConfig`.

### 0.6 Latest Migration is V16

The document mentioned V17-V19, which is correct (next available numbers).

### 0.7 No JUnit Tests Mentioned

AGENTS.md rule 12 requires JUnit 5 tests for every production feature.
The original document didn't mention tests.

**Resolution**: Add test requirements to each phase.

### 0.8 No Audit Logging Mentioned

AGENTS.md rule 6 requires all sensitive operations to be audited.
The `audit_logs` table exists but the document didn't mention logging
the new operations.

**Resolution**: All create/update/delete/activate/deactivate operations
must write to `audit_logs`.

---

## 1. Organization Module

### Who can do what

| Action | Super Admin | Org Admin | Operator | Viewer |
|--------|:-----------:|:---------:|:--------:|:------:|
| Add | YES | No | No | No |
| Edit | YES | No | No | No |
| Delete | YES | No | No | No |
| Activate/Deactivate | YES | No | No | No |
| View (all orgs) | YES | No | No | No |
| View (own org) | YES | YES | YES | YES |

### Fields

| Field | Type | Mandatory | Notes |
|-------|------|:---------:|-------|
| Name | String(255) | YES | Organization / wholesaler name |
| Slug | String(100) | YES (auto) | Auto-generated from name. User does NOT type this. |
| Status | Boolean | YES | Active / Inactive. Default: Active |

### Changes from current implementation

1. **Remove `slug` from the create form** — auto-generate it from name.
   Example: "Acme Wholesaler" → `acme-wholesaler`.
   If slug exists, append number: `acme-wholesaler-2`.
2. **Add Activate/Deactivate button** in the organization list.
   Deactivating an org sets `is_active = false` on the org AND
   sets `is_active = false` on all its users (blocks login).
3. **Remove `whiteLabelConfig`** from the create form (keep in DB).
4. **Add Edit functionality** (currently only create + list).
5. **Add Delete functionality** with confirmation.
6. **Audit log** every create/update/delete/activate/deactivate.

---

## 2. Machine Module (Inventory)

### Architecture Note

The system has two tables:
- `machines` — the fencing unit (machine_id, name, model, serial, status, org, customer)
- `devices` — the physical tracker (IMEI, SIM, protocol, firmware) bound to a machine

When Super Admin creates a machine with IMEI/SIM/protocol, the backend:
1. Creates a `machines` record (with auto-generated `machine_id`)
2. Creates a `devices` record with the IMEI/SIM/protocol
3. Binds the device to the machine via `devices.machine_id`

### Who can do what

| Action | Super Admin | Org Admin | Operator | Viewer |
|--------|:-----------:|:---------:|:--------:|:------:|
| Add | YES | No | No | No |
| Edit | YES | No | No | No |
| Delete | YES | No | No | No |
| Assign to Organization | YES | No | No | No |
| View (all machines) | YES | No | No | No |
| View (own org machines) | YES | YES | YES | YES |
| Assign to Customer | YES | YES | YES | No |
| Unassign from Customer | YES | YES | YES | No |
| Send Commands | YES | YES | YES | No |

### How machines flow through the system

```
1. Super Admin creates machine (no org) → status = IN_STOCK
2. Super Admin assigns machine to Organization → status = IN_STOCK, org set
3. Org Admin assigns machine to Customer → status = ACTIVE
4. Org Admin unassigns machine → status = IN_STOCK
5. Device connects via TCP → status = ACTIVE, is_online = true
6. Device disconnects → is_online = false (status stays ACTIVE)
7. Device reports fault → status = FAULT
```

### Fields

#### Machine table

| Field | Type | Mandatory | Editable | Notes |
|-------|------|:---------:|:--------:|-------|
| Machine ID | String(20) | YES | NO (locked) | Auto-generated: `YG` + 6-digit sequence. Example: `YG000001` |
| Name | String(255) | YES | YES | Display name |
| Serial Number | String(100) | NO | YES | Hardware serial |
| Model | String(100) | NO | YES | Device model |
| Organization | UUID | NO | YES (super admin) | NULL = unassigned inventory |
| Customer | UUID | NO | YES (org admin) | NULL = in stock |
| Status | Enum | YES | System-managed | `IN_STOCK`, `ACTIVE`, `OFFLINE`, `FAULT` |
| Is Online | Boolean | YES | System-managed | Updated by gateway on connect/disconnect |

#### Device table (created alongside machine)

| Field | Type | Mandatory | Editable | Notes |
|-------|------|:---------:|:--------:|-------|
| IMEI | String(20) | YES | YES | Must be unique across platform |
| SIM Number | String(30) | NO | YES | SIM phone number |
| Protocol Type | Enum | YES | YES | `YANTRAGO_FENCING`, `CONCOX_V5`, `JT808` |
| Firmware Version | String(50) | NO | YES | Firmware version |

### Machine ID Generation

- Format: `YG` followed by 6-digit zero-padded sequence
- Generated automatically on creation
- Never editable after save
- Implementation: query `MAX(machine_id)` from table, parse the number, increment, format
- Stored in new `machine_id` column (separate from UUID primary key)

### Changes from current implementation

1. **Add `machine_id` column** to `machines` table (VARCHAR(20), UNIQUE)
2. **Make `organization_id` nullable** in `machines` table (for unassigned inventory)
3. **Add `IN_STOCK` to status enum** (currently only ACTIVE/INACTIVE/FAULTY/RETIRED)
4. **Remove machine creation from Org Admin UI** — move to Super Admin only
5. **Add "Assign to Organization" action** for Super Admin
6. **Add "Assign to Customer" action** for Org Admin (in Customer form)
7. **Create `devices` record** when machine is created (with IMEI/SIM/protocol)
8. **Use `machine_assignments` table** to track assignment history (audit trail)
9. **Backend security**: `POST/PUT/DELETE /api/v1/machines` requires `super_admin` role
10. **Audit log** every create/update/delete/assign operation

---

## 3. Admin User Module

### Who can do what

| Action | Super Admin | Org Admin |
|--------|:-----------:|:---------:|
| Add | YES | No |
| Edit | YES | No |
| Delete | YES | No |
| Activate/Deactivate | YES | No |
| View (all users) | YES | No |
| View (own org users) | YES | YES |

### Fields

| Field | Type | Mandatory | Notes |
|-------|------|:---------:|-------|
| Email | Email | YES | Must be unique per organization |
| Password | String | YES | Min 8 characters |
| Full Name | String | YES | |
| Phone | String | NO | |
| Organization | UUID | YES | Selected from dropdown |
| Role | Enum | YES | `org_admin`, `operator`, `viewer` (super_admin excluded) |
| Active | Boolean | YES | Default: true |

### What Org Admin can do after login

1. Create / edit / delete / activate / deactivate **customers**
2. Assign / unassign **machines** to customers
3. View machines in their organization (read-only)
4. Send commands to machines
5. View alerts, telemetry, location data
6. View reports
7. Reset customer password (sets back to `yantrago`)

### Changes from current implementation

1. **Add Edit functionality** (currently only create)
2. **Add Delete functionality** (with confirmation)
3. **Add Activate/Deactivate toggle**
4. **Backend security**: `POST/PUT/DELETE /api/v1/users` requires `super_admin` role
5. **Audit log** every create/update/delete/activate/deactivate

---

## 4. Customer Module (End User / Mobile App User)

### Who can do what

| Action | Super Admin | Org Admin | Operator | Viewer |
|--------|:-----------:|:---------:|:--------:|:------:|
| Add | YES | YES | YES | No |
| Edit | YES | YES | YES | No |
| Delete | YES | YES | YES | No |
| Activate/Deactivate | YES | YES | YES | No |
| Reset Password | YES | YES | YES | No |
| View | YES | YES | YES | YES |

### Fields

| Field | Type | Mandatory | Notes |
|-------|------|:---------:|-------|
| Name | String(255) | YES | Customer / farmer name |
| Phone Number | String(50) | YES | Used as login ID for mobile app |
| Email | Email | NO | |
| Address | Text | NO | |
| Assigned Machine | Machine ID | NO | Searchable dropdown. Shows machines in the org that are `IN_STOCK` or already assigned to this customer. |
| Password | String | YES (auto) | Auto-assigned as `yantrago` (BCrypt hashed). Customer can change from mobile app later. |

### Customer as Mobile App User

When a customer is created:
1. A **user record** is created in `users` table with:
   - `email` = phone number (used as login identifier)
   - `password_hash` = BCrypt hash of `yantrago`
   - `organization_id` = the admin user's organization
   - `full_name` = customer name
   - `phone` = customer phone number
   - Role: `customer` (new role)
2. A **customer record** is created in `customers` table linked to the user via `user_id`
3. If `assignedMachineId` provided, assign the machine to this customer
4. The customer can now log in to the **mobile app** with:
   - Phone number (as username/email)
   - Password: `yantrago`

### Machine Assignment Flow

1. Super Admin creates machine → status = `IN_STOCK`, no org
2. Super Admin assigns machine to Organization → status = `IN_STOCK`, org set
3. Org Admin creates customer (with phone number)
4. Org Admin assigns machine to customer:
   - Search for machine by Machine ID (e.g. type "YG000" to filter)
   - Select the machine from the dropdown
   - A `machine_assignments` record is created (audit trail)
   - Machine `customer_id` is set, status changes to `ACTIVE`
5. Org Admin can unassign a machine from a customer:
   - `machine_assignments` record gets `unassigned_at` timestamp
   - Machine `customer_id` is set to NULL, status returns to `IN_STOCK`

### Changes from current implementation

1. **Add `phone` as mandatory field** (currently optional)
2. **Add `assigned_machine_id` field** with searchable dropdown
3. **Auto-create a user account** for the customer on creation
4. **Add static password `yantrago`** (BCrypt hashed)
5. **Add Edit / Delete / Activate-Deactivate** functionality
6. **Add "Reset Password" button** per customer
7. **Add `user_id` column** to `customers` table
8. **New role**: Add `customer` role to the roles table
9. **Use `machine_assignments` table** for assignment history
10. **Audit log** every create/update/delete/assign/activate/deactivate

---

## 5. Role Summary (Updated)

| Role | Description | Login Portal | Key Permissions |
|------|-------------|--------------|-----------------|
| super_admin | Platform owner | Admin Web (super-admin) | Manage orgs, machines, admin users |
| org_admin | Wholesaler/company admin | Admin Web (admin) | Manage customers, assign machines, send commands |
| operator | Operator | Admin Web (admin) | Send commands, view data, manage customers |
| viewer | Read-only | Admin Web (admin) | View all data in their org |
| customer | End user / farmer | Mobile App (Flutter) | View own machine, GPS, telemetry, send fencing on/off |

---

## 6. Implementation Steps (Ordered)

### Phase A: Database Changes

#### A1. `V17__machine_inventory_changes.sql`
```sql
-- Make organization_id nullable (for unassigned inventory)
ALTER TABLE machines ALTER COLUMN organization_id DROP NOT NULL;

-- Add human-readable machine_id column
ALTER TABLE machines ADD COLUMN machine_id VARCHAR(20);
CREATE UNIQUE INDEX idx_machines_machine_id ON machines(machine_id) WHERE machine_id IS NOT NULL;

-- Backfill existing machines with generated IDs
-- (done in application startup or migration using a DO block)

-- Add IN_STOCK to allowed status values (status is VARCHAR, no constraint change needed)
-- Update existing NULL org machines to IN_STOCK status
UPDATE machines SET status = 'IN_STOCK' WHERE organization_id IS NULL;
```

#### A2. `V18__add_customer_role.sql`
```sql
INSERT INTO roles (id, name, description, is_system_role, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000014',
    'customer',
    'End user / farmer. Mobile app access. View own machine, GPS, telemetry, fencing on/off.',
    true,
    now(),
    now()
) ON CONFLICT DO NOTHING;

-- Grant customer role permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'customer'
  AND p.name IN ('machine:read', 'command:write', 'telemetry:read', 'location:read', 'alert:read')
ON CONFLICT DO NOTHING;
```

#### A3. `V19__add_customer_user_link.sql`
```sql
ALTER TABLE customers ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_customers_user_id ON customers(user_id) WHERE user_id IS NOT NULL;
```

### Phase B: Backend Changes

#### B1. Machine Model & DTO
- Add `machineId` field (String, unique, nullable during creation)
- Update `MachineDto` to include `machineId`, `imei`, `simNumber`, `protocolType`, `firmwareVersion`
- These device fields are fetched from the joined `devices` table

#### B2. Machine Service
- `createMachine()`:
  - Auto-generate `machineId` (YG + 6-digit sequence)
  - Set `organizationId = null` (unassigned inventory)
  - Set `status = IN_STOCK`
  - Create a `Device` record with IMEI/SIM/protocol and bind to machine
  - Audit log the creation
- `assignMachineToOrganization(machineId, orgId)`:
  - Set `organization_id` on machine
  - Audit log the assignment
- `assignMachineToCustomer(machineId, customerId)`:
  - Validate machine belongs to caller's org (tenant guard)
  - Set `customer_id` on machine, status = ACTIVE
  - Create `machine_assignments` record
  - Audit log the assignment
- `unassignMachineFromCustomer(machineId)`:
  - Set `customer_id` to NULL, status = IN_STOCK
  - Update `machine_assignments` record with `unassigned_at`
  - Audit log the unassignment
- `listMachines()`:
  - Super admin: list ALL machines (no org filter)
  - Org user: list machines in their org only
- `updateMachine()`: super_admin only
- `deleteMachine()`: super_admin only

#### B3. Machine Controller
- `POST /api/v1/machines` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `PUT /api/v1/machines/{id}` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `DELETE /api/v1/machines/{id}` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `POST /api/v1/machines/{id}/assign-org` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `POST /api/v1/machines/{id}/assign-customer` — authenticated (org_admin+)
- `POST /api/v1/machines/{id}/unassign-customer` — authenticated (org_admin+)
- `GET /api/v1/machines` — authenticated (super_admin sees all, org users see own)
- `GET /api/v1/machines/{id}` — authenticated (tenant guard)

#### B4. Customer Service
- `createCustomer()`:
  - Create `User` record: email=phone, password=BCrypt("yantrago"), role=customer
  - Create `Customer` record linked to user via `user_id`
  - If `assignedMachineId` provided, assign machine to this customer
  - Audit log the creation
- `updateCustomer()`:
  - Update customer fields
  - Handle machine reassignment (unassign old, assign new)
  - Audit log the update
- `deleteCustomer()`:
  - Unassign any linked machines
  - Deactivate (not delete) the linked user account
  - Audit log the deletion
- `activateCustomer(id)` / `deactivateCustomer(id)`:
  - Toggle `is_active` on customer and linked user
  - Audit log
- `resetCustomerPassword(id)`:
  - Set user password back to BCrypt("yantrago")
  - Audit log

#### B5. Customer Controller
- `POST /api/v1/customers` — authenticated (org_admin+)
- `PUT /api/v1/customers/{id}` — authenticated (org_admin+)
- `DELETE /api/v1/customers/{id}` — authenticated (org_admin+)
- `PUT /api/v1/customers/{id}/activate` — authenticated (org_admin+)
- `PUT /api/v1/customers/{id}/deactivate` — authenticated (org_admin+)
- `POST /api/v1/customers/{id}/reset-password` — authenticated (org_admin+)
- Accept `assignedMachineId` in create/update requests

#### B6. Organization Service
- `createOrganization()`:
  - Auto-generate slug from name (with collision handling)
  - Audit log
- `updateOrganization()`:
  - Audit log
- `deleteOrganization()`:
  - Audit log
- `activateOrganization(id)`:
  - Set `is_active = true` on org
  - Do NOT auto-activate users (admin must do that manually)
  - Audit log
- `deactivateOrganization(id)`:
  - Set `is_active = false` on org
  - Set `is_active = false` on all users in the org (block login)
  - Audit log

#### B7. Organization Controller
- `POST /api/v1/organizations` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `PUT /api/v1/organizations/{id}` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `DELETE /api/v1/organizations/{id}` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `PUT /api/v1/organizations/{id}/activate` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `PUT /api/v1/organizations/{id}/deactivate` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `GET /api/v1/organizations` — super_admin sees all, org users see own
- Remove `slug` from `CreateOrganizationRequest` (auto-generated)

#### B8. User Service & Controller
- `activateUser(id)` / `deactivateUser(id)`:
  - Toggle `is_active`
  - Audit log
- `POST/PUT/DELETE /api/v1/users` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `PUT /api/v1/users/{id}/activate` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `PUT /api/v1/users/{id}/deactivate` — `@PreAuthorize("hasRole('SUPER_ADMIN')")`

#### B9. Security Config
- Enable method-level security: `@EnableMethodSecurity`
- Add `@PreAuthorize` annotations on controller methods (as listed above)
- Login endpoint: check `is_active` on user AND organization
  - If user is inactive → return 401 "Account deactivated"
  - If user's organization is inactive → return 401 "Organization deactivated"

#### B10. AuthService — Login Check
- On login, verify:
  - User `is_active = true`
  - User `is_locked = false`
  - If user has an organization, organization `is_active = true`
- If any check fails, return appropriate error message

#### B11. JUnit 5 Tests (AGENTS.md rule 12)
For each service method:
- `MachineServiceTest` — create, assign-org, assign-customer, unassign, list (super admin vs org user)
- `CustomerServiceTest` — create (with auto user), update, delete, activate/deactivate, reset-password
- `OrganizationServiceTest` — create (auto slug), activate/deactivate (cascading to users)
- `UserServiceTest` — create, activate/deactivate
- `SecurityConfigTest` — verify role-based access

### Phase C: Frontend Changes (Admin Web)

#### C1. Super Admin — Organizations Page
- Remove slug input from create form
- Add Edit button per row (opens edit form)
- Add Delete button per row (with confirmation)
- Add Activate/Deactivate button per row
- Show status column (Active/Inactive)

#### C2. Super Admin — Machines Page (NEW)
- Create `/super-admin/machines` page
- List all machines across all orgs (with org name and customer name columns)
- Create machine form:
  - Machine ID (auto, shown as "Will be auto-generated")
  - IMEI (mandatory)
  - SIM Number (optional)
  - Protocol Type (dropdown)
  - Name (mandatory)
  - Model (optional)
  - Serial Number (optional)
  - Firmware Version (optional)
- Edit machine form:
  - Machine ID (locked, read-only)
  - Other fields editable
- Assign to Organization dropdown per machine row
- Delete machine button (with confirmation)

#### C3. Super Admin — Admins Page
- Add Edit button per row
- Add Delete button per row (with confirmation)
- Add Activate/Deactivate toggle per row
- Show status column

#### C4. Admin — Machines Page (MODIFY)
- Remove "New Machine" button
- Make it read-only list of machines in their org
- Show assigned customer name if assigned
- Add "Assign to Customer" button per row (opens customer dropdown)
- Add "Unassign" button per row (if machine is assigned)

#### C5. Admin — Customers Page (MODIFY)
- Add mandatory Phone Number field
- Add "Assigned Machine" field with searchable dropdown (search by Machine ID)
- Show auto-generated password note: "Customer password: yantrago"
- Add Edit button per row
- Add Delete button per row (with confirmation)
- Add Activate/Deactivate toggle per row
- Add "Reset Password" button per row
- Show assigned machine name in the list

#### C6. Admin — Dashboard
- Show machine stats (total, active, in stock, offline, fault)
- Show customer count
- Show recent commands (last 5)

### Phase D: Testing

#### D1. End-to-end test flow:
1. Super Admin logs in → lands on super-admin dashboard
2. Super Admin creates organization "Acme Wholesaler"
3. Super Admin creates machine (IMEI=123456789012345, protocol=YANTRAGO_FENCING)
   → Machine ID auto-generated as YG000001
4. Super Admin assigns machine to "Acme Wholesaler" organization
5. Super Admin creates admin user (admin@acme.com, org_admin role) for Acme Wholesaler
6. Super Admin logs out
7. Admin user logs in → lands on admin dashboard
8. Admin user creates customer (name=John Farmer, phone=+919876543210)
   → Customer user account auto-created with password "yantrago"
9. Admin user assigns machine YG000001 to customer John Farmer
10. Admin user logs out
11. Customer logs in to mobile app with +919876543210 / yantrago
12. Customer sees their machine, GPS location, telemetry, fencing status
13. Customer sends FENCING_ON command from mobile app
14. Admin user logs in, views command history
15. Super Admin logs in, views audit logs (all operations logged)

#### D2. Edge cases to test:
- Create machine with duplicate IMEI → should fail
- Assign machine to customer in different org → should fail (tenant guard)
- Deactivate organization → all its users should be blocked from login
- Delete customer with assigned machine → machine should be unassigned
- Create customer with duplicate phone number in same org → should fail
- Org Admin tries to create machine → should fail (403)
- Org Admin tries to create admin user → should fail (403)
- Super Admin creates machine without org → status should be IN_STOCK

---

## 7. File Summary

### New Database Migrations
- `V17__machine_inventory_changes.sql` — machine_id column, nullable org_id, IN_STOCK status
- `V18__add_customer_role.sql` — customer role + permissions
- `V19__add_customer_user_link.sql` — user_id column on customers

### Backend Files to Create
- `DeviceRepository.java` — JPA repository for devices table
- `MachineAssignmentRepository.java` — JPA repository for machine_assignments table

### Backend Files to Modify
- `Machine.java` — add machineId field
- `MachineDto.java` — add machineId, imei, simNumber, protocolType, firmwareVersion
- `CreateMachineRequest.java` — add imei, simNumber, protocolType, firmwareVersion; remove organizationId (super admin assigns later)
- `UpdateMachineRequest.java` — add imei, simNumber, protocolType, firmwareVersion
- `MachineService.java` — auto machineId, device creation, assign-org, assign-customer, unassign-customer, super_admin listing
- `MachineController.java` — @PreAuthorize, assignment endpoints
- `Customer.java` — add userId field
- `CustomerDto.java` — add userId, assignedMachineId, assignedMachineName
- `CreateCustomerRequest.java` — add phone (mandatory), assignedMachineId
- `UpdateCustomerRequest.java` — add phone, assignedMachineId
- `CustomerService.java` — auto-create user, machine assignment, activate/deactivate, reset-password
- `CustomerController.java` — activate/deactivate/reset-password endpoints
- `OrganizationService.java` — auto slug, activate/deactivate (cascade to users)
- `OrganizationController.java` — @PreAuthorize, activate/deactivate endpoints
- `CreateOrganizationRequest.java` — remove slug field
- `UserService.java` — activate/deactivate methods
- `UserController.java` — @PreAuthorize, activate/deactivate endpoints
- `SecurityConfig.java` — @EnableMethodSecurity
- `AuthService.java` — check user active + org active on login
- `MachineRepository.java` — add findByMachineId, findAll (for super admin)

### Backend Test Files to Create
- `MachineServiceTest.java`
- `CustomerServiceTest.java`
- `OrganizationServiceTest.java`
- `UserServiceTest.java`

### Frontend Files to Create
- `super-admin/machines/page.tsx` — NEW: full machine CRUD + assign-org

### Frontend Files to Modify
- `super-admin/organizations/page.tsx` — remove slug, add edit/delete/activate-deactivate
- `super-admin/admins/page.tsx` — add edit/delete/activate-deactivate
- `(admin)/machines/page.tsx` — remove create, add assign-customer/unassign
- `(admin)/customers/page.tsx` — add phone, machine search, edit/delete/activate-deactivate/reset-password
- `(admin)/dashboard/page.tsx` — add customer count, in-stock count

---

## 8. AGENTS.md Compliance Checklist

| Rule | How this plan complies |
|------|----------------------|
| 1. Production, not MVP | Full CRUD, audit logging, tests, role-based access |
| 2. Java 17, Spring Boot 3.x | All backend changes use Spring Boot 3.x |
| 3. No TCP in REST controllers | No TCP changes in this plan |
| 4. Async device communication | No changes to RabbitMQ flow |
| 5. Command ack before success | No changes to command flow |
| 6. All commands auditable | machine_assignments + audit_logs for all operations |
| 7. Tenant isolation from JWT | All queries use OwnerContextService, never request body |
| 8. Never trust frontend tenant_id | organization_id comes from JWT context |
| 9. RBAC permission checks | @PreAuthorize on all sensitive endpoints |
| 10. No temporary architecture | Uses existing tables (machines, devices, machine_assignments) |
| 11. No new microservices | No new services, only backend + gateway |
| 12. Validation, error handling, logging, tests, security | All included in each phase |
| 13. No TCP protocol changes | No protocol changes |
| 14. Flyway versioned SQL | V17, V18, V19 in database/migrations/ |
| 15. Review architecture docs | Done — reviewed existing schema before planning |
| 16. No protocol refactoring | No protocol changes |
| 17. Shared RabbitMQ contracts | No RabbitMQ changes |
| 18. Partitioned time-series, batch inserts | No time-series changes |
| 19. UUIDs as primary keys | All new columns use UUID where applicable |
| 20. No secrets committed | Static password "yantrago" is a default, not a secret. Will be changed per-customer. |
