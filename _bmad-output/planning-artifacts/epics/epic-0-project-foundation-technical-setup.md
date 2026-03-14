# Epic 0: Project Foundation & Technical Setup

Estabelecer a fundação técnica do projeto (frontend Vue, backend Spring Boot, database PostgreSQL, design system Tailwind+shadcn-vue) para permitir desenvolvimento de features com arquitetura dual separada, caching Redis, e compliance de acessibilidade WCAG 2.1 AA.

## Story 0.1: Initialize Vue Frontend Project

As a developer,
I want to initialize the Vue 3.5+ frontend project with Vite 6,
So that I have a working development environment for building the SPA.

**Acceptance Criteria:**

**Given** I have Node.js 22+ installed
**When** I run `npm create vue@latest porquinho-frontend` with options: TypeScript (Yes), Router (Yes), Pinia (Yes), Vitest (Yes), E2E Playwright (Yes), ESLint (Yes), Prettier (Yes)
**Then** A complete Vue 3.5+ project with Vite 6 is created
**And** Running `npm run dev` starts the dev server on http://localhost:5173
**And** The project includes Vue Router, Pinia state management, TypeScript support
**And** ESLint and Prettier are configured for code quality
**And** Vitest is configured for unit testing
**And** Playwright is configured for E2E testing

---

## Story 0.2: Initialize Spring Boot Backend Project

As a developer,
I want to initialize the Spring Boot 3.4.x backend project with JDK 21 LTS,
So that I have a working REST API server with all required dependencies.

**Acceptance Criteria:**

**Given** I have JDK 21 LTS installed
**When** I run Spring Initializr with dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Spring Security, OAuth2 Client, Validation, Lombok, Actuator, DevTools
**Then** A complete Spring Boot 3.4.x Maven project is created in `porquinho-backend` directory
**And** Running `./mvnw spring-boot:run` starts the server on http://localhost:8080
**And** Accessing http://localhost:8080/actuator/health returns {"status":"UP"}
**And** All required dependencies are configured in pom.xml
**And** Application uses JDK 21 LTS as target version

---

## Story 0.3: Setup PostgreSQL Database with Flyway

As a developer,
I want to setup PostgreSQL 16+ via Supabase with Flyway migrations,
So that I have a versioned database schema management system.

**Acceptance Criteria:**

**Given** I have a Supabase project created
**When** I configure Spring Boot datasource with Supabase connection string
**Then** Backend connects successfully to PostgreSQL via Supabase
**And** Flyway is configured in pom.xml and application.yml
**And** Flyway baseline-on-migrate is enabled
**And** Migrations directory exists at src/main/resources/db/migration/
**And** Application logs show "Flyway successfully migrated" on startup
**And** Database connection pool (HikariCP) is configured with maximum-pool-size: 20, minimum-idle: 5

---

## Story 0.4: Configure Tailwind CSS & shadcn-vue Design System

As a developer,
I want to install and configure Tailwind CSS and shadcn-vue components,
So that I have a complete design system ready for building UI.

**Acceptance Criteria:**

**Given** The Vue frontend project is initialized
**When** I install Tailwind CSS with `npm install -D tailwindcss postcss autoprefixer @tailwindcss/vite`
**Then** Tailwind is configured via tailwind.config.js and postcss.config.js
**And** Running `npx shadcn-vue@latest init` configures shadcn-vue with style "Default" and base color "Purple"
**And** The components directory is created at src/components/ui/
**And** Base components (Button, Input, Card, Select, Dialog) are installed via `npx shadcn-vue@latest add`
**And** Tailwind directives (@tailwind base, components, utilities) are added to main CSS file
**And** Running dev server shows Tailwind styles are working
**And** shadcn-vue components render correctly with purple accent color

---

## Story 0.5: Setup Redis for Caching

As a developer,
I want to configure Redis for dashboard caching,
So that I can implement TTL-based cache invalidation strategy.

**Acceptance Criteria:**

**Given** Redis 7-alpine Docker image is available
**When** I add Redis dependency to Spring Boot pom.xml (spring-boot-starter-data-redis)
**Then** Redis is configured in application.yml with host, port, and connection settings
**And** RedisTemplate and CacheManager beans are configured
**And** Cache configuration includes key patterns: dashboard:layer1:{userId}, dashboard:layer2:{userId}, dashboard:layer3:{userId}
**And** TTL settings are defined: Layer 1 (30s), Layer 2 (5min), Layer 3 (15min)
**And** Spring Boot can connect to Redis successfully on startup
**And** @Cacheable annotation can be used in services

---

## Story 0.6: Configure CI/CD Pipeline & Docker Containers

As a developer,
I want to configure Docker containers and basic CI/CD setup,
So that the application can be built and deployed consistently.

**Acceptance Criteria:**

**Given** Docker is installed locally
**When** I create Dockerfile for frontend (multi-stage build: Node 22-alpine → Nginx alpine)
**Then** Frontend Dockerfile builds successfully and creates production-ready image
**And** Frontend container exposes port 80 and serves static files via Nginx
**And** I create Dockerfile for backend (OpenJDK 21-slim, jar execution)
**And** Backend Dockerfile builds successfully with Maven-built jar
**And** Backend container exposes port 8080
**And** docker-compose.yml defines all services: frontend, backend, redis
**And** Running `docker-compose up` starts all containers successfully
**And** Environment variables are configured via .env file (not hardcoded)

---

## Story 0.7: Implement Design Tokens & Color System

As a developer,
I want to configure design tokens for the color system in Tailwind,
So that the UI uses the approved color palette consistently.

**Acceptance Criteria:**

**Given** Tailwind CSS is installed and configured
**When** I extend tailwind.config.js with custom colors
**Then** Primary purple color (Purple 600: #9333EA) is defined as primary
**And** Neutral grays (Gray 50-900) are defined for UI base
**And** State colors are defined: Success Green 600, Warning Yellow 600, Danger Red 600
**And** Chart colors (6 vibrant colors) are defined: Chart 1-6
**And** Semantic color mappings are created: primary, secondary, success, warning, error, info
**And** All color definitions match the UX specification exactly
**And** CSS variables are generated by shadcn-vue for dynamic theming
**And** Color contrast ratios meet WCAG 2.1 AA standards (tested manually or with tool)

---

## Story 0.8: Implement Typography & Spacing Foundation

As a developer,
I want to configure typography (Inter font) and spacing system,
So that the UI has consistent text hierarchy and layout spacing.

**Acceptance Criteria:**

**Given** Tailwind CSS is configured with design tokens
**When** I add Inter font from Google Fonts or local files
**Then** Inter is defined as default font family in Tailwind config
**And** Font weights are configured: 400 (regular), 500 (medium), 600 (semibold), 700 (bold)
**And** Type scale uses Tailwind defaults: text-xs to text-5xl
**And** Spacing base is 4px (Tailwind default rem-based system)
**And** Common spacing patterns are documented: Cards (p-6, gap-4), Dashboard layers (py-12)
**And** Border radius default is rounded-lg (12px)
**And** Line heights are optimized for readability
**And** Typography renders correctly across all target browsers

---

## Story 0.9: Setup Accessibility Foundation (WCAG 2.1 AA)

As a developer,
I want to configure accessibility foundation to meet WCAG 2.1 Level AA,
So that the application is usable by people with disabilities.

**Acceptance Criteria:**

**Given** shadcn-vue components are installed (built on Radix UI primitives)
**When** I configure accessibility best practices
**Then** All shadcn-vue components include ARIA roles, labels, and keyboard navigation
**And** Focus indicators are visible and meet 3:1 contrast ratio
**And** Tab order is logical (tested with keyboard-only navigation)
**And** Touch targets are minimum 44x44px on mobile (configured via Tailwind)
**And** HTML5 semantic markup is used (header, nav, main, section, article, footer)
**And** Lighthouse accessibility audit scores > 90 on initial setup
**And** Screen reader testing setup is documented (NVDA/JAWS/VoiceOver)
**And** Color contrast checker is integrated or documented for ongoing validation

---
