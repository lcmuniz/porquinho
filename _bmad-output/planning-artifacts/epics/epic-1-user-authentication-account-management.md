# Epic 1: User Authentication & Account Management

Usuários podem se cadastrar usando Google OAuth ou email/senha, fazer login de forma segura, recuperar senha, gerenciar sessões ativas, e exercer seus direitos LGPD (exportar e excluir dados).

## Story 1.1: User Registration with Google OAuth

As a new user,
I want to register for an account using my Google account,
So that I can quickly sign up without creating a new password.

**Acceptance Criteria:**

**Given** I am on the registration page
**When** I click "Sign up with Google" button
**Then** I am redirected to Google OAuth consent screen
**And** After granting permissions, I am redirected back to the application
**And** A new user account is created with my Google email and profile info
**And** A `users` table is created via Flyway migration (V1__create_users_table.sql) with fields: user_id (UUID, PK), email, auth_provider, google_id, created_at, updated_at, deleted_at, deleted_by
**And** User record is inserted with auth_provider='google', google_id populated
**And** JWT token is generated and stored in httpOnly cookie (NFR14)
**And** I am logged in and redirected to onboarding page
**And** Audit log entry is created for "user_registration" event (NFR20)
**And** LGPD consent is recorded with timestamp (NFR25)

---

## Story 1.2: User Registration with Email & Password

As a new user,
I want to register for an account using my email and password,
So that I can create an account without using a third-party OAuth provider.

**Acceptance Criteria:**

**Given** I am on the registration page
**When** I enter my email, password (min 8 chars, at least 1 uppercase, 1 lowercase, 1 number), and confirm password
**Then** My password is hashed using bcrypt with salt (NFR13)
**And** A new user record is created in `users` table with auth_provider='email', password_hash populated
**And** Email verification email is sent to my email address
**And** JWT token is generated and stored in httpOnly cookie
**And** I am logged in and redirected to onboarding page
**And** Audit log entry is created for "user_registration" event
**And** LGPD consent is recorded with timestamp
**And** If email already exists, error message is shown: "Email already registered"
**And** If password is weak, validation errors are shown

---

## Story 1.3: User Login with Any Configured Method

As a registered user,
I want to login using any authentication method I have configured (Google OAuth or email/password),
So that I can access my account securely.

**Acceptance Criteria:**

**Given** I am on the login page
**When** I click "Login with Google" or enter email/password and click "Login"
**Then** My credentials are validated against the database
**And** If valid, JWT token is generated with user_id, email, and expiration (7 days per NFR16)
**And** JWT is stored in httpOnly cookie (NFR14)
**And** I am redirected to dashboard page
**And** Audit log entry is created for "user_login" event with timestamp and IP (NFR20)
**And** If invalid, error message is shown after 1 second delay (security)
**And** Rate limiting prevents more than 5 login attempts per minute (NFR17)
**And** After 5 failed attempts, account is temporarily locked for 15 minutes

---

## Story 1.4: Password Recovery via Email

As a registered user,
I want to reset my password via email,
So that I can regain access if I forget my password.

**Acceptance Criteria:**

**Given** I am on the login page
**When** I click "Forgot password?" and enter my email
**Then** A password reset token is generated (UUID, expires in 1 hour per NFR19)
**And** Password reset email is sent with link containing the token
**And** Clicking the link takes me to password reset page
**And** I can enter new password (validated: min 8 chars, complexity rules)
**And** New password is hashed with bcrypt and saved
**And** Reset token is invalidated after use
**And** Audit log entry is created for "password_reset" event
**And** All active sessions are invalidated (user must login again)
**And** If token is expired (>1 hour), error message is shown
**And** If email doesn't exist, generic message is shown (security: don't reveal if email exists)

---

## Story 1.5: Change Account Email Address

As a logged-in user,
I want to change my account email address,
So that I can update my contact information.

**Acceptance Criteria:**

**Given** I am logged in and on account settings page
**When** I enter a new email address and click "Change Email"
**Then** Verification email is sent to the new email address
**And** Clicking verification link confirms the email change
**And** User record in `users` table is updated with new email
**And** JWT token is regenerated with new email
**And** Audit log entry is created for "email_changed" event
**And** Notification is sent to old email address informing of change (security)
**And** If new email is already in use, error message is shown
**And** If verification link expires (24 hours), change request is cancelled

---

## Story 1.6: Add/Remove Authentication Methods

As a logged-in user,
I want to add or remove authentication methods (Google OAuth, email/password),
So that I can manage how I access my account.

**Acceptance Criteria:**

**Given** I am logged in and on account settings page
**When** I click "Add Google OAuth" and I currently only have email/password
**Then** I am redirected to Google OAuth consent screen
**And** After granting permissions, `users` table is updated with google_id
**And** I can now login with either method
**And** Audit log entry is created for "auth_method_added" event
**And** When I click "Remove Google OAuth"
**Then** Confirmation dialog is shown
**And** After confirming, google_id is set to NULL in `users` table
**And** Audit log entry is created for "auth_method_removed" event
**And** Cannot remove last authentication method (validation error)
**And** If email/password is not set, cannot remove Google OAuth

---

## Story 1.7: View Active Sessions & Remote Logout

As a logged-in user,
I want to view all my active sessions and perform remote logout,
So that I can secure my account if I suspect unauthorized access.

**Acceptance Criteria:**

**Given** I am logged in and on account settings page
**When** I navigate to "Sessions" tab
**Then** I see a list of all active sessions with: device/browser, IP address, location (approximate), last activity time
**And** A `user_sessions` table is created via migration with fields: session_id (UUID, PK), user_id (FK), jwt_token_hash, device_info, ip_address, created_at, last_activity, expires_at
**And** Current session is marked with "Current device" badge
**And** Each session has a "Logout" button
**And** When I click "Logout" on a session
**Then** That session's JWT token is invalidated (added to blacklist or deleted from `user_sessions`)
**And** That device is logged out immediately
**And** Audit log entry is created for "remote_logout" event
**And** When I click "Logout all other sessions"
**Then** All sessions except current are invalidated
**And** Sessions automatically expire after 7 days of inactivity (NFR16)

---

## Story 1.8: Export All User Data (LGPD Compliance)

As a logged-in user,
I want to export all my data in a portable format,
So that I can exercise my LGPD right to data portability.

**Acceptance Criteria:**

**Given** I am logged in and on account settings page
**When** I click "Export My Data"
**Then** A background job is triggered to collect all my data
**And** Data collected includes: user profile, accounts, cards, transactions, budgets, categories, goals, reports
**And** Data is exported in JSON format (structured, machine-readable)
**And** Export includes metadata: export_date, user_id, data_version
**And** Download link is generated (valid for 24 hours)
**And** Email notification is sent with download link
**And** Audit log entry is created for "data_export_requested" event with timestamp and IP (NFR20)
**And** Download file is deleted after 24 hours or after first download (security)
**And** Export complies with LGPD portability requirements (NFR23)

---

## Story 1.9: Request Permanent Account Deletion (LGPD Compliance)

As a logged-in user,
I want to request permanent deletion of my account and all associated data,
So that I can exercise my LGPD right to erasure.

**Acceptance Criteria:**

**Given** I am logged in and on account settings page
**When** I click "Delete Account" and confirm with password
**Then** Confirmation modal is shown with warning about data loss
**And** I must type "DELETE MY ACCOUNT" to confirm (safety measure)
**And** After confirmation, account deletion is scheduled
**And** All user data is soft-deleted (deleted_at = NOW(), deleted_by = user_id) per ARCH-REQ-8
**And** User is logged out immediately (session invalidated)
**And** Email confirmation is sent to user's email
**And** Audit log entry is created for "account_deletion_requested" event (NFR20)
**And** Soft-deleted data is retained for 30 days (recovery period)
**And** After 30 days, hard deletion job purges all user data permanently
**And** Subscription is cancelled immediately if active
**And** LGPD consent logs are updated with deletion timestamp (NFR25)

---
