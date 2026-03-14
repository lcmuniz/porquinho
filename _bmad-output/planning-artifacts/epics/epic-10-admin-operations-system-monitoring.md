# Epic 10: Admin Operations & System Monitoring

Administradores podem visualizar métricas de health do sistema, taxa de erro de importação por banco, precisão da IA por banco, número de usuários ativos, e receber alertas automáticos quando métricas críticas são violadas para tomar ações proativas.

## Story 10.1: Admin Dashboard - System Health Metrics

As an administrator,
I want to view system health metrics on an admin dashboard,
So that I can monitor overall system performance.

**Acceptance Criteria:**

**Given** I am logged in with admin role (new field in users table: is_admin boolean)
**When** I navigate to Admin Dashboard (/admin)
**Then** Admin-only dashboard is displayed with system health metrics (FR85):
  - **Uptime:** Current uptime percentage, target: 99.5% (NFR33)
  - **Response Times:** Average API response times by endpoint
  - **Error Rates:** Total errors in last 24h/7d/30d
  - **Database Health:** Connection pool stats, slow queries count
  - **Redis Health:** Cache hit rate, memory usage
  - **Active Sessions:** Current active user sessions
**And** Spring Boot Actuator endpoints are utilized: /actuator/health, /actuator/metrics
**And** Metrics are displayed as:
  - Summary cards (uptime, error rate, active users)
  - Time-series charts (response times, error trends)
  - Status indicators (Green: Healthy, Yellow: Warning, Red: Critical)
**And** Auto-refresh every 30 seconds
**And** Access control: Only users with is_admin=true can access /admin routes
**And** If non-admin attempts access: 403 Forbidden error

---

## Story 10.2: Import Error Rate by Bank

As an administrator,
I want to see transaction import error rates broken down by bank,
So that I can identify problematic bank formats and prioritize parser improvements.

**Acceptance Criteria:**

**Given** I am on the Admin Dashboard
**When** I navigate to "Import Metrics" tab
**Then** Import error rate dashboard is displayed showing per-bank statistics (FR86):
  - Table with columns: Bank Name, Total Imports, Successful, Failed, Error Rate (%)
  - Bar chart: Error rate by bank (X-axis: Banks, Y-axis: Error %)
**And** Data is calculated from `transaction_imports` table:
  - Count imports per bank_name
  - Calculate: error_rate = (status='failed' count / total imports) * 100
**And** Clicking on a bank shows detailed error breakdown:
  - Common error types (parsing errors, format issues, encoding problems)
  - Recent failed imports with error messages
  - Time trend: Error rate over last 30 days
**And** Filters available: Date range, Bank, Status (All/Failed/Successful)
**And** Export error report as CSV for analysis
**And** Target benchmark displayed: "Target error rate: < 5%"
**And** Banks exceeding 5% error rate are highlighted in red

---

## Story 10.3: AI Classification Accuracy by Bank

As an administrator,
I want to see AI classification accuracy broken down by bank,
So that I can identify banks where AI performs poorly and improve training data.

**Acceptance Criteria:**

**Given** I am on the Admin Dashboard
**When** I navigate to "AI Metrics" tab
**Then** AI accuracy dashboard is displayed showing per-bank statistics (FR87):
  - Table: Bank Name, Total Classifications, User Corrections, Accuracy (%)
  - Line chart: Accuracy trend over time by bank
**And** Data is calculated from `ai_classification_history` table:
  - accuracy_per_bank = (1 - corrections_count / total_classifications) * 100
  - Only count transactions where was_corrected=true as errors
**And** Display shows:
  - Overall AI accuracy: X%
  - Per-bank accuracy: Nubank X%, BB Y%, etc.
  - Improvement trend: "Accuracy improved by X% this month"
**And** Clicking on a bank shows:
  - Most commonly miscategorized transaction types
  - Top correction patterns (from `user_classification_patterns`)
  - Suggested improvements for AI model
**And** Target benchmark: "Target accuracy: > 85%" (from NFR7 implicit goal)
**And** Banks below 85% accuracy are highlighted for attention
**And** Export AI metrics as CSV for ML team analysis

---

## Story 10.4: Active Users Metrics

As an administrator,
I want to see active user metrics and engagement statistics,
So that I can understand product adoption and usage patterns.

**Acceptance Criteria:**

**Given** I am on the Admin Dashboard
**When** I navigate to "User Metrics" tab
**Then** Active users dashboard is displayed (FR88):
  - **Total Users:** Count of all registered users (deleted_at IS NULL)
  - **Active Users (24h):** Users who logged in in last 24 hours
  - **Active Users (7d):** Users who logged in in last 7 days
  - **Active Users (30d):** Users who logged in in last 30 days (Monthly Active Users - MAU)
  - **Trial Users:** Users with subscription plan_type='trial'
  - **Paid Users:** Users with subscription plan_type='monthly' AND status='active'
  - **Churn Rate:** Cancelled subscriptions / total subscriptions (target: < 5% per NFR from PRD)
**And** Charts displayed:
  - User growth over time (line chart)
  - Active users trend (7d and 30d MAU)
  - Trial to paid conversion rate
**And** Engagement metrics:
  - Average weekly check-ins per user
  - Onboarding completion rate (from `onboarding_progress`, target: 70%+ per NFR74)
  - Feature adoption: % users using each major feature
**And** Data refreshes every 5 minutes
**And** Export user metrics as CSV

---

## Story 10.5: Automated Alerts for Critical Metrics

As an administrator,
I want to receive automated alerts when critical metrics are violated,
So that I can take proactive action before issues impact users.

**Acceptance Criteria:**

**Given** System monitoring is running
**When** A critical metric threshold is violated
**Then** Automated alert is sent to administrators (FR89)
**And** Alert conditions monitored:
  - **Error Rate > 10%:** System-wide error rate exceeds threshold
  - **Uptime < 99%:** Below target uptime (NFR33: 99.5%)
  - **Response Time > 5s:** API response times degrade
  - **Import Error Rate > 10%:** For any single bank
  - **AI Accuracy < 75%:** For any single bank
  - **Active Sessions = 0:** Possible system outage
  - **Redis Down:** Cache service unavailable
  - **Database Connection Pool Exhausted:** All connections in use
**And** Alert delivery methods:
  - Email to admin email addresses
  - Slack webhook (if configured)
  - System notification in admin dashboard
**And** Alert includes:
  - Metric name and current value
  - Threshold violated
  - Time of violation
  - Suggested action
  - Link to relevant admin dashboard section
**And** Alert frequency: Maximum 1 alert per metric per hour (prevent spam)
**And** Alerts are logged in `system_alerts` table (new migration V23) with: alert_id, alert_type, metric_value, threshold, timestamp, resolved_at
**And** Administrators can mark alerts as "Resolved"
**And** Resolved alerts stop triggering until metric violates again
**And** Alert history is viewable in admin dashboard
