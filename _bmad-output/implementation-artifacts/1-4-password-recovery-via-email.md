# Story 1.4: Password Recovery via Email

Status: done

## Story

As a registered user,
I want to reset my password via email,
So that I can regain access if I forget my password.

## Acceptance Criteria

**Given** I am on the login page
**When** I click "Forgot password?" and enter my email
**Then** Rate limiting check prevents more than 3 password reset requests per hour per email (security)
**And** A password reset token is generated via Supabase Auth (expires in 1 hour per NFR19)
**And** Password reset email is sent by Supabase with link containing the token
**And** Clicking the link takes me to password reset page with token in URL
**And** I can enter new password (validated: min 8 chars, 1 uppercase, 1 lowercase, 1 number)
**And** New password is updated via Supabase Auth (automatically hashed with bcrypt)
**And** Reset token is automatically invalidated by Supabase after use
**And** Backend audit log entry is created for "password_reset_requested" and "password_reset_completed" events (NFR20)
**And** All active sessions are invalidated by Supabase (user must login again after password reset)
**And** If token is expired (>1 hour), error message is shown: "Reset link expired. Please request a new one."
**And** If email doesn't exist, generic success message is shown (security: don't reveal if email exists)
**And** Success message: "If that email exists, you will receive reset instructions within 5 minutes."

## Tasks / Subtasks

### Backend Tasks

- [x] Task 1: Add password reset audit logging endpoints (AC: audit log)
  - [x] 1.1: Create `POST /api/v1/auth/password-reset/requested` endpoint - logs when user requests password reset
  - [x] 1.2: Create `POST /api/v1/auth/password-reset/completed` endpoint - logs after successful password reset
  - [x] 1.3: Create PasswordResetRequest DTO with email field
  - [x] 1.4: Extract user_id from JWT (@AuthenticationPrincipal) for completed event (or use email for requested event)
  - [x] 1.5: Call auditLogService.log() with event type and metadata (email, IP address)
  - [x] 1.6: Include IP address from HttpServletRequest (handle X-Forwarded-For header)
  - [x] 1.7: Return 200 OK (no body needed) or 429 on rate limit

- [x] Task 2: Implement rate limiting for password reset (AC: rate limiting)
  - [x] 2.1: Reuse RateLimitService from Story 1.3 (already implemented with Redis)
  - [x] 2.2: Implement rate limiting: 3 reset requests per 60 minutes (3600 seconds) per email
  - [x] 2.3: Integrate rate limiting into /password-reset/requested endpoint
  - [x] 2.4: Return 429 Too Many Requests with Retry-After header when limit exceeded
  - [x] 2.5: Store rate limit key: `rate_limit:password_reset:{email}`
  - [x] 2.6: Add unit test for password reset rate limiting

- [x] Task 3: Write backend unit tests (AC: test coverage)
  - [x] 3.1: Add AuthControllerTest.shouldLogPasswordResetRequested() test
  - [x] 3.2: Add AuthControllerTest.shouldLogPasswordResetCompleted() test
  - [x] 3.3: Add AuthControllerTest.shouldReturn429WhenPasswordResetRateLimited() test
  - [x] 3.4: Mock Redis connection in tests using Mockito

### Frontend Tasks

- [x] Task 4: Create ForgotPasswordView.vue page (AC: forgot password UI)
  - [x] 4.1: Create src/views/ForgotPasswordView.vue with Card layout
  - [x] 4.2: Add email input field with type="email", required, aria-label
  - [x] 4.3: Add "Send Reset Link" button with loading state (disabled when isLoading)
  - [x] 4.4: Add back link to "/login" with "← Back to Login"
  - [x] 4.5: Show success message (generic) after submission regardless of email existence
  - [x] 4.6: WCAG 2.1 AA compliance: min-h-[44px], aria-labels, focus-visible, role="alert"
  - [x] 4.7: Use Purple 600 for primary button, Gray for secondary elements
  - [x] 4.8: Add informative text: "Enter your email and we'll send you a link to reset your password"

- [x] Task 5: Create ResetPasswordView.vue page (AC: reset password UI)
  - [x] 5.1: Create src/views/ResetPasswordView.vue with Card layout
  - [x] 5.2: Extract reset token from URL query parameter (?token=xxx or Supabase's token format)
  - [x] 5.3: Add new password input with type="password", required, password visibility toggle
  - [x] 5.4: Add confirm password input with type="password", required, validation match
  - [x] 5.5: Add "Reset Password" button with loading state (disabled when isLoading)
  - [x] 5.6: Show password requirements: min 8 chars, 1 uppercase, 1 lowercase, 1 number
  - [x] 5.7: Validate passwords match in real-time
  - [x] 5.8: Handle expired token error (show message and link to request new reset)
  - [x] 5.9: WCAG 2.1 AA compliance: aria-labels, focus indicators, semantic HTML
  - [x] 5.10: Use Purple 600 for primary button

- [x] Task 6: Implement forgot password flow (AC: request password reset)
  - [x] 6.1: Add requestPasswordReset(email) method to useAuth composable
  - [x] 6.2: Call supabase.auth.resetPasswordForEmail({ email, options: { redirectTo } })
  - [x] 6.3: Supabase generates reset token and sends email automatically
  - [x] 6.4: Call authService.logPasswordResetRequested(email) to create audit log
  - [x] 6.5: Show generic success message: "If that email exists, you will receive reset instructions"
  - [x] 6.6: Handle rate limiting (429) - show error "Too many password reset requests. Please wait 1 hour."
  - [x] 6.7: Handle errors gracefully (network errors, validation errors)
  - [x] 6.8: Do NOT reveal if email exists in database (security best practice)

- [x] Task 7: Implement reset password flow (AC: update password)
  - [x] 7.1: Add updatePassword(newPassword) method to useAuth composable
  - [x] 7.2: Call supabase.auth.updateUser({ password: newPassword })
  - [x] 7.3: Supabase validates token, hashes password, and updates auth.users table
  - [x] 7.4: Supabase automatically invalidates all sessions after password change
  - [x] 7.5: Call authService.logPasswordResetCompleted() to create audit log
  - [x] 7.6: Show success message and redirect to /login after 2 seconds
  - [x] 7.7: Handle expired token error (show message with link to request new reset)
  - [x] 7.8: Handle validation errors (weak password, passwords don't match)

- [x] Task 8: Update API service for password reset (AC: backend communication)
  - [x] 8.1: Add logPasswordResetRequested(email) method to authService.ts
  - [x] 8.2: POST to /api/v1/auth/password-reset/requested (no auth required)
  - [x] 8.3: Add logPasswordResetCompleted() method to authService.ts
  - [x] 8.4: POST to /api/v1/auth/password-reset/completed with Authorization: Bearer {jwt}
  - [x] 8.5: Body: { email: string } for both endpoints
  - [x] 8.6: Return void (no response body needed)
  - [x] 8.7: Handle 429 Too Many Requests (rate limit exceeded)

- [x] Task 9: Update router for password reset (AC: navigation)
  - [x] 9.1: Add /auth/forgot-password route to router with ForgotPasswordView component
  - [x] 9.2: Add /auth/reset-password route to router with ResetPasswordView component
  - [x] 9.3: Both routes should be accessible without authentication (public routes)
  - [x] 9.4: Configure Supabase redirectTo: `${window.location.origin}/auth/reset-password`

### Testing Tasks

- [ ] Task 10: Manual testing checklist (AC: integration testing)
  - [ ] 10.1: Test forgot password with valid email (check email received)
  - [ ] 10.2: Test forgot password with invalid email (generic success message shown)
  - [ ] 10.3: Test forgot password rate limiting (3 requests allowed, 4th blocked for 1 hour)
  - [ ] 10.4: Test reset password with valid token
  - [ ] 10.5: Test reset password with expired token (>1 hour)
  - [ ] 10.6: Test reset password with invalid token
  - [ ] 10.7: Test password validation (weak password rejected)
  - [ ] 10.8: Test passwords must match validation
  - [ ] 10.9: Test successful password reset invalidates all sessions
  - [ ] 10.10: Test login with new password after reset
  - [ ] 10.11: Test WCAG compliance and keyboard navigation
  - [ ] 10.12: Test audit log entries created for requested and completed events

## Dev Notes

### Critical Architecture Context

**Password Reset Flow (Supabase Auth):**

1. **Request Password Reset:**
   - Frontend → Supabase Auth (resetPasswordForEmail)
   - Supabase generates password reset token (expires in 1 hour per NFR19)
   - Supabase sends email with reset link containing token
   - Frontend calls backend /api/v1/auth/password-reset/requested for audit log (no auth required)
   - Generic success message shown regardless of email existence (security)

2. **Reset Password:**
   - User clicks link in email → redirected to /auth/reset-password?token=xxx
   - User enters new password on ResetPasswordView
   - Frontend → Supabase Auth (updateUser with new password)
   - Supabase validates token (checks expiration, checks if already used)
   - Supabase hashes new password with bcrypt and updates auth.users table
   - Supabase automatically invalidates all user sessions
   - Frontend calls backend /api/v1/auth/password-reset/completed for audit log (requires JWT from the reset token session)
   - User redirected to /login to sign in with new password

**Important: Backend Does NOT Handle Password Reset Directly**
- Backend does NOT generate reset tokens (Supabase handles this)
- Backend does NOT validate reset tokens (Supabase handles this)
- Backend does NOT update passwords (Supabase handles this)
- Backend does NOT send reset emails (Supabase handles this)
- Backend ONLY logs audit events for tracking purposes
- Actual password reset logic is 100% handled by Supabase Auth

**Supabase Email Configuration:**
- Password reset email template can be customized in Supabase Dashboard
- Default subject: "Reset Your Password"
- Link format: `https://[PROJECT-REF].supabase.co/auth/v1/verify?token=xxx&type=recovery&redirect_to=https://yourdomain.com/auth/reset-password`
- Supabase handles token generation, validation, and expiration automatically
- Token is single-use (cannot be reused after password reset)

**Security Considerations (Critical):**

1. **Generic Success Message:** Always show "If that email exists, you will receive reset instructions" whether email exists or not. This prevents email enumeration attacks.

2. **Rate Limiting:** 3 password reset requests per hour per email (stricter than login rate limit). This prevents abuse and spam.

3. **Token Expiration:** Supabase tokens expire after 1 hour (NFR19). After expiration, user must request new reset link.

4. **Single-Use Tokens:** Supabase tokens are invalidated after first use. Cannot be reused.

5. **Session Invalidation:** All user sessions are automatically invalidated by Supabase after password change. User must login again.

6. **Password Validation:** Frontend and Supabase both validate password strength (min 8 chars, 1 uppercase, 1 lowercase, 1 number).

7. **No Email Existence Reveal:** Backend audit log uses email from request, not user lookup. This prevents revealing if email exists in database.

**Database Schema:**
- No new database tables or migrations required for this story
- Password reset is handled entirely by Supabase Auth (auth.users table)
- Backend only creates audit log entries in existing audit_logs table (from Story 1.1)
- Backend reuses RateLimitService from Story 1.3 (Redis-based)

**API Design:**

Backend provides 2 audit-only endpoints:

1. `POST /api/v1/auth/password-reset/requested` (public, no auth required)
   - Request body: `{ "email": "user@example.com" }`
   - Response: 200 OK (no body), 429 Too Many Requests
   - Purpose: Audit log for password reset requests
   - Rate limiting: 3 requests per 60 minutes per email

2. `POST /api/v1/auth/password-reset/completed` (authenticated, requires JWT)
   - Request body: `{ "email": "user@example.com" }` (optional, can extract from JWT)
   - Response: 200 OK (no body)
   - Purpose: Audit log for completed password resets
   - No rate limiting (user already has valid JWT from Supabase reset flow)

**Frontend Stack:**
- Reuse existing Supabase client (src/services/supabase.ts)
- Reuse existing useAuth composable (src/composables/useAuth.ts) - add new methods
- Create 2 new views: ForgotPasswordView.vue, ResetPasswordView.vue
- Reuse existing authService.ts (src/services/authService.ts) - add new methods
- Router updates: Add 2 new public routes

### Implementation Patterns to Follow

**Backend (Spring Boot):**

```java
// AuthController.java - Add password reset audit endpoints
@PostMapping("/password-reset/requested")
public ResponseEntity<Void> logPasswordResetRequested(
    @RequestBody @Valid PasswordResetRequest request,
    HttpServletRequest httpRequest
) {
    // Check rate limit (3 requests per hour per email)
    if (!rateLimitService.allowRequest("password_reset:" + request.getEmail(), 3, 3600)) {
        throw new RateLimitExceededException("Too many password reset requests. Please wait 1 hour.");
    }

    // Log password reset request (no user lookup - don't reveal if email exists)
    String ipAddress = extractIpAddress(httpRequest);
    Map<String, Object> metadata = Map.of(
        "email", request.getEmail(),
        "ip_address", ipAddress,
        "user_agent", httpRequest.getHeader("User-Agent")
    );

    // Use null as userId because we don't want to reveal if email exists
    // This is a security feature - prevents email enumeration
    auditLogService.logAnonymous("password_reset_requested", metadata);

    return ResponseEntity.ok().build();
}

@PostMapping("/password-reset/completed")
public ResponseEntity<Void> logPasswordResetCompleted(
    @AuthenticationPrincipal String userId,
    HttpServletRequest httpRequest
) {
    // User is authenticated via JWT from Supabase reset flow
    String ipAddress = extractIpAddress(httpRequest);
    Map<String, Object> metadata = Map.of(
        "ip_address", ipAddress,
        "user_agent", httpRequest.getHeader("User-Agent")
    );

    auditLogService.log(UUID.fromString(userId), "password_reset_completed", metadata);

    return ResponseEntity.ok().build();
}

// PasswordResetRequest.java - New DTO
public class PasswordResetRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    // getters and setters
}

// AuditLogService.java - Add logAnonymous method for security
public void logAnonymous(String eventType, Map<String, Object> metadata) {
    // Log event without user_id (for security-sensitive operations like password reset requests)
    // This prevents revealing if an email exists in the database
    AuditLog log = new AuditLog();
    log.setEventType(eventType);
    log.setMetadata(metadata);
    log.setCreatedAt(Instant.now());
    auditLogRepository.save(log);
}
```

**Frontend (Vue + TypeScript):**

```typescript
// useAuth.ts - Add password reset methods
export function useAuth() {
  const requestPasswordReset = async (email: string) => {
    const { data, error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/auth/reset-password`
    })
    if (error) throw error
    return data
  }

  const updatePassword = async (newPassword: string) => {
    const { data, error } = await supabase.auth.updateUser({
      password: newPassword
    })
    if (error) throw error
    return data
  }

  return {
    // existing methods
    signUpWithGoogle,
    signUpWithEmail,
    signInWithEmail,
    signInWithGoogle,
    getSession,
    signOut,
    // new methods
    requestPasswordReset,
    updatePassword
  }
}

// authService.ts - Add password reset audit methods
export default {
  async logPasswordResetRequested(email: string): Promise<void> {
    await api.post('/auth/password-reset/requested', { email })
  },

  async logPasswordResetCompleted(): Promise<void> {
    // This call requires authentication (JWT from Supabase reset flow)
    await api.post('/auth/password-reset/completed')
  }
}

// ForgotPasswordView.vue
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import authService from '@/services/authService'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'

const router = useRouter()
const { requestPasswordReset } = useAuth()

const email = ref('')
const isLoading = ref(false)
const isSuccess = ref(false)
const errorMessage = ref('')

async function handleSubmit() {
  if (!email.value) return

  isLoading.value = true
  errorMessage.value = ''

  try {
    // Request password reset via Supabase
    await requestPasswordReset(email.value)

    // Log audit event (no auth required)
    await authService.logPasswordResetRequested(email.value)

    // Show generic success message (don't reveal if email exists)
    isSuccess.value = true
  } catch (error: any) {
    if (error.status === 429) {
      errorMessage.value = 'Too many password reset requests. Please wait 1 hour.'
    } else {
      // Still show success message even on error (security)
      isSuccess.value = true
    }
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 px-4">
    <Card class="w-full max-w-md">
      <CardHeader>
        <CardTitle class="text-2xl">Forgot your password?</CardTitle>
        <CardDescription>
          Enter your email and we'll send you a link to reset your password
        </CardDescription>
      </CardHeader>

      <CardContent>
        <!-- Success message -->
        <div
          v-if="isSuccess"
          role="alert"
          class="mb-4 p-4 bg-green-50 border border-green-200 text-green-700 rounded-md"
        >
          <p class="font-medium">Check your email</p>
          <p class="text-sm mt-1">
            If that email exists, you will receive reset instructions within 5 minutes.
          </p>
        </div>

        <!-- Error message -->
        <div
          v-if="errorMessage"
          role="alert"
          class="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-md"
        >
          {{ errorMessage }}
        </div>

        <!-- Form -->
        <form @submit.prevent="handleSubmit" class="space-y-4">
          <div>
            <label for="email" class="block text-sm font-medium text-gray-900 mb-1">
              Email
            </label>
            <Input
              id="email"
              v-model="email"
              type="email"
              required
              aria-label="Email address"
              placeholder="seu@email.com"
              class="min-h-[44px]"
              :disabled="isSuccess"
            />
          </div>

          <Button
            type="submit"
            :disabled="isLoading || !email || isSuccess"
            class="w-full min-h-[44px] bg-purple-600 hover:bg-purple-700"
          >
            {{ isLoading ? 'Sending...' : 'Send Reset Link' }}
          </Button>
        </form>

        <!-- Back to login -->
        <div class="mt-6 text-center">
          <router-link
            to="/login"
            class="text-sm text-purple-600 hover:text-purple-700 flex items-center justify-center gap-1"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
            Back to Login
          </router-link>
        </div>
      </CardContent>
    </Card>
  </div>
</template>

// ResetPasswordView.vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import authService from '@/services/authService'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'

const router = useRouter()
const route = useRoute()
const { updatePassword } = useAuth()

const newPassword = ref('')
const confirmPassword = ref('')
const isLoading = ref(false)
const errorMessage = ref('')
const passwordsMatch = ref(true)

// Password validation
const passwordRequirements = [
  { label: 'At least 8 characters', validator: (pwd: string) => pwd.length >= 8 },
  { label: 'One uppercase letter', validator: (pwd: string) => /[A-Z]/.test(pwd) },
  { label: 'One lowercase letter', validator: (pwd: string) => /[a-z]/.test(pwd) },
  { label: 'One number', validator: (pwd: string) => /\d/.test(pwd) }
]

function validatePassword(password: string): boolean {
  return passwordRequirements.every(req => req.validator(password))
}

function checkPasswordsMatch() {
  if (confirmPassword.value) {
    passwordsMatch.value = newPassword.value === confirmPassword.value
  }
}

async function handleSubmit() {
  if (!newPassword.value || !confirmPassword.value) return

  // Check passwords match
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = 'Passwords do not match'
    return
  }

  // Validate password strength
  if (!validatePassword(newPassword.value)) {
    errorMessage.value = 'Password does not meet requirements'
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    // Update password via Supabase (validates token automatically)
    await updatePassword(newPassword.value)

    // Log audit event (authenticated via JWT from reset token)
    await authService.logPasswordResetCompleted()

    // Show success message
    alert('Password reset successfully! Redirecting to login...')

    // Redirect to login after 2 seconds
    setTimeout(() => {
      router.push('/login')
    }, 2000)
  } catch (error: any) {
    if (error.message?.includes('expired')) {
      errorMessage.value = 'Reset link expired. Please request a new one.'
    } else if (error.message?.includes('invalid')) {
      errorMessage.value = 'Invalid or already used reset link.'
    } else {
      errorMessage.value = 'Failed to reset password. Please try again.'
    }
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  // Supabase handles the token validation automatically
  // The token is in the URL fragment and Supabase session will be set
})
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 px-4">
    <Card class="w-full max-w-md">
      <CardHeader>
        <CardTitle class="text-2xl">Reset your password</CardTitle>
        <CardDescription>
          Enter a new password for your account
        </CardDescription>
      </CardHeader>

      <CardContent>
        <!-- Error message -->
        <div
          v-if="errorMessage"
          role="alert"
          class="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-md"
        >
          {{ errorMessage }}
          <router-link
            v-if="errorMessage.includes('expired')"
            to="/auth/forgot-password"
            class="block mt-2 text-sm underline"
          >
            Request new reset link
          </router-link>
        </div>

        <!-- Form -->
        <form @submit.prevent="handleSubmit" class="space-y-4">
          <div>
            <label for="new-password" class="block text-sm font-medium text-gray-900 mb-1">
              New Password
            </label>
            <Input
              id="new-password"
              v-model="newPassword"
              type="password"
              required
              aria-label="New password"
              placeholder="Enter new password"
              class="min-h-[44px]"
              @input="checkPasswordsMatch"
            />
          </div>

          <div>
            <label for="confirm-password" class="block text-sm font-medium text-gray-900 mb-1">
              Confirm Password
            </label>
            <Input
              id="confirm-password"
              v-model="confirmPassword"
              type="password"
              required
              aria-label="Confirm password"
              placeholder="Confirm new password"
              class="min-h-[44px]"
              :class="{ 'border-red-500': !passwordsMatch && confirmPassword }"
              @input="checkPasswordsMatch"
            />
            <p v-if="!passwordsMatch && confirmPassword" class="text-sm text-red-600 mt-1">
              Passwords do not match
            </p>
          </div>

          <!-- Password requirements -->
          <div class="bg-gray-50 p-3 rounded-md">
            <p class="text-sm font-medium text-gray-900 mb-2">Password must contain:</p>
            <ul class="space-y-1">
              <li
                v-for="req in passwordRequirements"
                :key="req.label"
                class="text-sm flex items-center gap-2"
                :class="req.validator(newPassword) ? 'text-green-600' : 'text-gray-600'"
              >
                <span v-if="req.validator(newPassword)">✓</span>
                <span v-else>○</span>
                {{ req.label }}
              </li>
            </ul>
          </div>

          <Button
            type="submit"
            :disabled="isLoading || !newPassword || !confirmPassword || !passwordsMatch"
            class="w-full min-h-[44px] bg-purple-600 hover:bg-purple-700"
          >
            {{ isLoading ? 'Resetting...' : 'Reset Password' }}
          </Button>
        </form>
      </CardContent>
    </Card>
  </div>
</template>
```

### File Structure Guidance

**Backend files to create/modify:**
```
porquinho-backend/
├── src/main/java/com/porquinho/
│   ├── controller/
│   │   └── AuthController.java (ADD 2 password reset endpoints)
│   ├── service/
│   │   ├── RateLimitService.java (REUSE from Story 1.3)
│   │   └── AuditLogService.java (ADD logAnonymous method)
│   ├── dto/
│   │   └── PasswordResetRequest.java (CREATE)
│   └── exception/
│       └── RateLimitExceededException.java (REUSE from Story 1.3)
└── src/test/java/com/porquinho/
    └── controller/
        └── AuthControllerTest.java (ADD password reset tests)
```

**Frontend files to create/modify:**
```
porquinho-frontend/
├── src/
│   ├── views/
│   │   ├── ForgotPasswordView.vue (CREATE)
│   │   └── ResetPasswordView.vue (CREATE)
│   ├── composables/
│   │   └── useAuth.ts (ADD requestPasswordReset, updatePassword)
│   ├── services/
│   │   └── authService.ts (ADD logPasswordResetRequested, logPasswordResetCompleted)
│   └── router/
│       └── index.ts (ADD 2 new public routes)
```

### Security Requirements

**NFR19 - Token Expiration:** Supabase password reset tokens expire after 1 hour. This is configured in Supabase Auth settings and cannot be changed from the application.

**Rate Limiting:**
- Maximum 3 password reset requests per hour (3600 seconds) per email
- More strict than login rate limiting (which is 5 per minute)
- Prevents abuse and spam
- Implemented with Redis sliding window (reuse RateLimitService from Story 1.3)
- Return 429 Too Many Requests with Retry-After header

**Email Enumeration Prevention (CRITICAL):**
- ALWAYS show generic success message: "If that email exists, you will receive reset instructions"
- NEVER reveal if email exists in database
- Backend audit log uses email from request without user lookup
- Return same response whether email exists or not
- This prevents attackers from discovering valid email addresses

**NFR20 - Audit Logging:** Create audit log entries for:
1. "password_reset_requested" - when user requests password reset (anonymous log, no user_id)
2. "password_reset_completed" - after successful password reset (authenticated log with user_id)
- Include email, IP address, User-Agent in metadata
- IP address handling: Extract from X-Forwarded-For header if behind proxy

**Session Invalidation:**
- Supabase automatically invalidates all user sessions after password change
- User must login again with new password
- This is a security feature to prevent unauthorized access if password was compromised

**Password Validation:**
- Minimum 8 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 number
- Enforced by both frontend (UX) and Supabase (backend)
- Show real-time validation feedback to user

### Accessibility Requirements (WCAG 2.1 AA)

From Stories 0.9, 1.1, 1.2, and 1.3 learnings:
- Semantic HTML5: `<form>`, `<label>`, proper input types
- Focus indicators: 2px solid purple outline, 3:1 contrast
- Touch targets: minimum 44x44px on buttons and inputs
- Keyboard navigation: All form fields focusable with Tab
- ARIA labels: `aria-label="Email address"`, `aria-label="New password"`
- Error messages: Use `role="alert"` for dynamic error feedback
- Screen reader: Test with NVDA/VoiceOver
- Color contrast: Purple 600 (#9333EA) on white meets 4.5:1 ratio
- Password visibility toggle: Button to show/hide password (accessibility + usability)
- Success messages: Green background with sufficient contrast

### Design System Usage

**From visual-design-foundation.md:**
- Primary color: Purple 600 (#9333EA) for primary buttons
- Success: Green 600 (#16A34A) for success messages
- Error: Red 600 (#DC2626) for error messages
- Text: Gray 900 (#18181B) for labels, Gray 600 for descriptions
- Border: Gray 200 (#E4E4E7) for input borders
- Background: Gray 50 (#F9FAFB) for page background

**shadcn-vue components to use:**
- Input: For email and password fields
- Button: For submit buttons and back link
- Card: For form container
- Alert: For success/error messages

### Environment Variables (Already Configured from Story 1.1)

**Backend (application-dev.yml):**
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

**Frontend (.env):**
```
VITE_SUPABASE_URL=https://[PROJECT-REF].supabase.co
VITE_SUPABASE_ANON_KEY=your-anon-key
VITE_API_BASE_URL=http://localhost:8080
```

**Supabase Email Configuration:**
- Go to Supabase Dashboard → Authentication → Email Templates
- Customize "Reset Password" template if needed
- Redirect URL: Configure in Supabase → Authentication → URL Configuration
- Add `https://yourdomain.com/auth/reset-password` to allowed redirect URLs

### Testing Guidance

**Backend Testing:**
- Test rate limiting: 3 requests pass, 4th blocked (429)
- Test audit log creation for password_reset_requested (anonymous)
- Test audit log creation for password_reset_completed (authenticated)
- Mock Redis with Testcontainers or embedded Redis
- Mock JWT with `@WithMockUser(username = "user-id")` for completed endpoint

**Frontend Testing:**
- Manual testing preferred for Supabase email flows
- Test forgot password flow (check email received)
- Test forgot password with rate limiting (3 requests, then blocked)
- Test reset password with valid token
- Test reset password with expired token (>1 hour)
- Test password validation (weak password rejected)
- Test passwords must match validation
- Test successful password reset and redirect to login
- Test WCAG compliance and keyboard navigation

**Email Testing:**
- Use Supabase Email Preview feature in development
- Check email arrives within 5 minutes (usually < 1 minute)
- Verify reset link works and redirects correctly
- Test expired token (manually expire in Supabase or wait 1 hour)
- Test single-use token (try using same link twice)

**Rate Limiting Testing:**
- Use Redis CLI or Redis Insight to inspect rate limit keys
- Key format: `rate_limit:password_reset:{email}`
- TTL should be 3600 seconds (1 hour)
- Counter should increment with each request
- 4th request within 1 hour should return 429

### References

- [Architecture: Authentication Flow] `_bmad-output/planning-artifacts/architecture/core-architectural-decisions.md` - JWT Validation section
- [Architecture: Rate Limiting] `_bmad-output/planning-artifacts/architecture/core-architectural-decisions.md` - Redis Caching section
- [Architecture: API Patterns] `_bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md` - API Naming Conventions, Error Handling Patterns
- [PRD: FR4] `_bmad-output/planning-artifacts/prd/functional-requirements.md` - Password recovery via email
- [PRD: NFR19] `_bmad-output/planning-artifacts/prd/non-functional-requirements.md` - Password reset token expiration (1 hour)
- [PRD: NFR20] `_bmad-output/planning-artifacts/prd/non-functional-requirements.md` - Audit logging
- [Epic 1: Story 1.4] `_bmad-output/planning-artifacts/epics/epic-1-user-authentication-account-management.md`
- [Story 1.1] `_bmad-output/implementation-artifacts/1-1-user-registration-with-google-oauth.md` - Supabase Auth foundation, AuditLogService
- [Story 1.3] `_bmad-output/implementation-artifacts/1-3-user-login-with-any-configured-method.md` - RateLimitService, AccountLockService, Auth patterns

### Previous Story Learnings (Stories 1.1, 1.2, 1.3)

**Critical Learnings:**

1. **Supabase Auth Handles Everything:** All authentication operations (password validation, token generation, email sending) are handled by Supabase. Backend only validates JWTs and logs audit events.

2. **Backend Role:** Backend does NOT authenticate users. Backend only:
   - Validates JWT tokens (Spring Security automatic)
   - Logs audit events for tracking
   - Enforces rate limiting via Redis

3. **Rate Limiting Pattern:** Use RateLimitService from Story 1.3 (already implemented with Redis). Pattern: `rateLimitService.allowRequest(key, maxAttempts, windowSeconds)`.

4. **Audit Logging Pattern:** Use AuditLogService.log() for authenticated events, add logAnonymous() method for anonymous events (like password reset requests).

5. **Security Best Practice:** Never reveal if email exists in database. Always show generic success message on password reset request.

6. **Error Handling:** Use RFC 7807 Problem Details format (already configured in GlobalExceptionHandler from Story 1.1).

7. **Loading States:** Disable buttons during API calls, show loading text ("Sending...", "Resetting...").

8. **WCAG Compliance:** All form inputs must be 44x44px minimum, have aria-labels, and be keyboard accessible.

9. **Design Consistency:** Use Purple 600 for primary buttons, Green 600 for success, Red 600 for errors (from visual-design-foundation.md).

10. **Testing:** Manual testing preferred for Supabase email flows. Use Redis CLI to inspect rate limiting keys.

**Code Patterns Established:**
- AuthController endpoints: `@PostMapping("/auth/{action}")`
- DTO naming: `{Action}Request`, always with `@Valid`
- Frontend composables: `useAuth()` returns auth functions
- Frontend services: `authService.{method}()` calls backend API
- Testing: `@WebMvcTest` for controllers with mocked services
- Error messages: User-friendly messages, never expose internal errors

**Files Already Created (Reuse/Extend):**
- Backend: AuthController (add 2 endpoints), AuditLogService (add logAnonymous), RateLimitService (reuse)
- Frontend: useAuth.ts (add 2 methods), authService.ts (add 2 methods)
- Frontend: LoginView.vue, RegisterView.vue (reuse Card/Input/Button patterns)
- Tests: AuthControllerTest exists (add password reset test cases)

**Integration Points:**
- Story 1.1: Reuse Supabase Auth setup, AuditLogService
- Story 1.3: Reuse RateLimitService (Redis), AuthController patterns
- Story 1.4 (this): Link from LoginView.vue "Forgot password?" (already exists from Story 1.3)

**Supabase Email Configuration Required:**
- Go to Supabase Dashboard → Authentication → Email Templates
- Verify "Reset Password" template is configured
- Add redirect URL to allowed URLs in Supabase settings
- Test email delivery in development mode (Supabase Email Preview)

### Known Constraints and Dependencies

**Dependencies:**
- Story 1.1 complete: ✅ (Supabase Auth foundation, AuditLogService)
- Story 1.3 complete: ✅ (RateLimitService with Redis)
- Supabase Auth configured: ✅ (Email provider enabled)
- Redis configured: ✅ (From Story 0.5)

**Blockers to resolve before dev:**
- None - all dependencies are satisfied

**Supabase Configuration Required:**
- Verify password reset email template in Supabase Dashboard
- Add `/auth/reset-password` to allowed redirect URLs in Supabase
- Configure email rate limiting in Supabase (optional, we handle our own rate limiting)

**Frontend Dependencies:**
- shadcn-vue components: Card, Input, Button (already installed from Stories 1.1, 1.2, 1.3)
- Vue Router (already configured with public routes)
- Pinia auth store (already exists)

### Critical Implementation Notes

**Do NOT Implement These (Supabase Handles Them):**
- ❌ Do NOT generate password reset tokens in backend
- ❌ Do NOT validate password reset tokens in backend
- ❌ Do NOT update passwords in backend
- ❌ Do NOT send password reset emails from backend
- ❌ Do NOT create password_reset_tokens table

**DO Implement These (Our Responsibility):**
- ✅ DO add 2 audit endpoints in AuthController
- ✅ DO implement rate limiting via RateLimitService (3 requests per hour)
- ✅ DO create ForgotPasswordView.vue and ResetPasswordView.vue
- ✅ DO add requestPasswordReset() and updatePassword() to useAuth
- ✅ DO show generic success messages (don't reveal email existence)
- ✅ DO implement password validation UI (show requirements)
- ✅ DO add AuditLogService.logAnonymous() method

**Password Reset Flow Summary:**
1. User enters email on ForgotPasswordView
2. Frontend calls Supabase resetPasswordForEmail()
3. Supabase generates token and sends email (automatic)
4. Frontend calls backend /password-reset/requested (audit only)
5. User clicks link in email → redirected to ResetPasswordView
6. User enters new password on ResetPasswordView
7. Frontend calls Supabase updateUser({ password })
8. Supabase validates token, updates password, invalidates sessions (automatic)
9. Frontend calls backend /password-reset/completed (audit only)
10. User redirected to /login

**Security Checklist:**
- ✅ Rate limiting: 3 requests per hour per email
- ✅ Generic success message (don't reveal email existence)
- ✅ Token expiration: 1 hour (Supabase default)
- ✅ Single-use tokens (Supabase automatic)
- ✅ Session invalidation after password change (Supabase automatic)
- ✅ Password validation (min 8 chars, complexity)
- ✅ Audit logging for tracking
- ✅ No email enumeration vulnerability

## Change Log

- **2026-03-18**: Story created with comprehensive context from Stories 1.1, 1.2, and 1.3 - ready for implementation
- **2026-03-18**: Epic 1 status confirmed as in-progress (not first story, no status update needed)
- **2026-03-18**: Backend implementation complete - 2 audit endpoints with rate limiting, 3 unit tests passing
- **2026-03-18**: Frontend implementation complete - ForgotPasswordView and ResetPasswordView with WCAG compliance
- **2026-03-18**: All tasks complete (1-9), TypeScript check passed, ready for code review
- **2026-03-18**: Code review complete - 5 issues fixed (3 Medium, 2 Low):
  - Fixed route inconsistency: `/forgot-password` → `/auth/forgot-password` (consistent with auth routes pattern)
  - Replaced `alert()` with visual success message in ResetPasswordView (better UX)
  - Updated File List to include SecurityConfig.java and api.ts
  - Updated LoginView link to use `/auth/forgot-password`
  - Updated ResetPasswordView link to use `/auth/forgot-password`

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (global.anthropic.claude-sonnet-4-5-20250929-v1:0)

### Debug Log References

No major debugging required. Implementation followed existing patterns from Stories 1.1, 1.2, and 1.3.

### Completion Notes List

**Backend Implementation:**
- Created `PasswordResetRequest` DTO with email validation
- Added `logAnonymous()` method to AuditLogService for security (no user_id leak)
- Added 2 new endpoints to AuthController:
  - `POST /api/v1/auth/password-reset/requested` (public, rate-limited: 3 per hour)
  - `POST /api/v1/auth/password-reset/completed` (authenticated via JWT)
- Reused RateLimitService from Story 1.3 with Redis
- Rate limiting: 3 password reset requests per 3600 seconds (1 hour) per email
- Security: Generic success message shown regardless of email existence (prevents email enumeration)
- All 16 unit tests passing (including 3 new password reset tests)

**Frontend Implementation:**
- Implemented ForgotPasswordView.vue with WCAG 2.1 AA compliance
- Created ResetPasswordView.vue with password validation UI
- Added `requestPasswordReset()` and `updatePassword()` methods to useAuth composable
- Added `logPasswordResetRequested()` and `logPasswordResetCompleted()` to authService
- Added `/auth/reset-password` route to router (public route)
- Uncommented "Forgot password?" link in LoginView
- Password validation: min 8 chars, 1 uppercase, 1 lowercase, 1 number (real-time feedback)
- Password visibility toggle buttons for UX
- Generic success messages for security (don't reveal if email exists)
- TypeScript type checking passed without errors

**Security Features Implemented:**
- Rate limiting: 3 requests per hour per email (stricter than login rate limit)
- Generic success message: "If that email exists, you will receive reset instructions"
- Anonymous audit logging for password reset requests (no user_id)
- Token expiration: 1 hour (Supabase default)
- Single-use tokens (Supabase automatic)
- Session invalidation after password change (Supabase automatic)
- Password validation enforced on frontend and backend
- No email enumeration vulnerability

### File List

**Backend files created:**
- porquinho-backend/src/main/java/com/porquinho/dto/PasswordResetRequest.java

**Backend files modified:**
- porquinho-backend/src/main/java/com/porquinho/controller/AuthController.java
- porquinho-backend/src/main/java/com/porquinho/service/AuditLogService.java
- porquinho-backend/src/main/java/com/porquinho/config/SecurityConfig.java
- porquinho-backend/src/test/java/com/porquinho/controller/AuthControllerTest.java

**Frontend files created:**
- porquinho-frontend/src/views/ResetPasswordView.vue

**Frontend files modified:**
- porquinho-frontend/src/views/ForgotPasswordView.vue
- porquinho-frontend/src/views/LoginView.vue
- porquinho-frontend/src/composables/useAuth.ts
- porquinho-frontend/src/services/authService.ts
- porquinho-frontend/src/services/api.ts
- porquinho-frontend/src/router/index.ts
