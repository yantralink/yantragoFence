## QR-Based Fencing Machine Customer Onboarding

We have an app-based fencing machine system where every physical fencing machine has a unique **IMEI number**.

We want to implement a QR-code-based onboarding process for new customers/wholesalers.

### 1. Machine Registration

When a new fencing machine is added to the system:

- Generate a unique QR code for that machine.
- Map the QR code to the machine's unique IMEI number in the backend.
- The IMEI should not be displayed directly in the QR code.
- Each machine should have only one active onboarding QR code.
- The QR code should contain a secure machine identifier/token.

Example:

`QR Code → Secure Machine Token → Machine/IMEI`

### 2. Customer/Wholesaler Scans QR Code

The customer or wholesaler scans the QR code using their mobile phone.

The QR code opens a web-based onboarding page, for example:

`https://app.example.com/machine-onboarding/<secure-token>`

The page should display:

**Fencing Machine Onboarding**

Machine: `Fencing Machine`

The customer should enter:

- Customer Mobile Number
- Optionally Customer Name
- Optionally Customer/Company Name

The mobile number should be validated before submission.

### 3. Submit Onboarding Request

When the customer submits the form:

- Validate the mobile number.
- Verify that the QR code/token is valid.
- Verify that the machine is not already assigned to another customer.
- Create an **Onboarding Request** in the backend.
- Set request status as:

`PENDING_ADMIN_APPROVAL`

The customer should see:

> "Your onboarding request has been submitted successfully. Our team will activate your machine shortly."

### 4. Admin Notification

The admin receives a notification that a new machine-assignment request has been created.

Admin dashboard should show:

| Field | Example |
|---|---|
| Request ID | REQ-10025 |
| Machine | Fencing Machine |
| IMEI | 356XXXXXXXXXXXX |
| Customer Mobile | 98XXXXXXXX |
| Customer Name | ABC Farm |
| Wholesaler | XYZ Distributor |
| Request Date | 08-Sep-2026 |
| Status | Pending |

### 5. Admin Approval and Assignment

The admin reviews the request.

Admin can:

- Approve
- Reject
- Edit customer details
- Assign/reassign the machine
- Cancel the request

When the admin approves:

`Machine IMEI → Customer Mobile Number`

The machine status changes from:

`UNASSIGNED → ASSIGNED`

The customer account/mobile number is now linked to that specific machine.

### 6. Customer Account

If the customer already has an account:

- Link the machine to the existing customer account.

If the customer does not have an account:

- Create a customer account using the mobile number.
- Send OTP for verification/login.

After successful assignment, the customer can log in to the mobile app and see the assigned fencing machine.

### 7. Machine Status

Each machine should have a lifecycle status:

`AVAILABLE`
→ `ONBOARDING_REQUESTED`
→ `ASSIGNED`
→ `ACTIVE`

Other possible states:

`SUSPENDED`
`MAINTENANCE`
`DEACTIVATED`

### 8. Important Security Requirements

The QR code should NOT contain the actual IMEI as plain text.

Instead:

`QR Code → Secure Token → Backend → IMEI`

The backend should perform all mapping and validation.

The QR token should also support:

- Expiration/revocation
- Duplicate scan detection
- Machine assignment validation
- Prevention of unauthorized reassignment

### 9. Recommended Overall Flow

**Machine Created**

↓  

**IMEI Registered in Backend**

↓

**Unique QR Code Generated**

↓

**QR Sticker Attached to Machine**

↓

**Customer/Wholesaler Scans QR**

↓

**Onboarding Web Page Opens**

↓

**Customer Enters Mobile Number**

↓

**Request Created**

↓

**Admin Receives Request**

↓

**Admin Reviews Request**

↓

**Admin Approves**

↓

**IMEI Assigned to Customer**

↓

**Customer Receives OTP/Activation Notification**

↓

**Customer Logs Into App**

↓

**Assigned Fencing Machine Appears in App**

### 10. Recommended Database Relationship

The important relationship should be:

`Machine`
→ `IMEI`
→ `QR Token`
→ `Onboarding Request`
→ `Customer`
→ `Mobile Number`

Do not directly use the mobile number as the primary machine identifier. The **Machine ID/IMEI should remain the permanent identity of the physical machine**, while the customer assignment can change over time.