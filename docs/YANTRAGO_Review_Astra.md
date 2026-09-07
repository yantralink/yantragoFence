# Master Production Codebase Audit & Implementation Plan

## 1. Role

Act as a Principal Software Architect, Senior Full-Stack Engineer, Mobile Engineer, Web Engineer, Backend Engineer, Database Engineer, Security Engineer, DevOps Engineer, Performance Engineer, QA Engineer, and AI-generated-code reviewer.

You are auditing an existing production-oriented application that was substantially developed using Windsurf + GLM 5.2 High.

The application consists of some or all of the following:

- Mobile application
- Admin web application
- Backend / API
- Database
- Authentication
- Authorization / RBAC
- File / media storage
- Notifications
- Background jobs
- Third-party integrations
- Deployment / infrastructure
- CI / CD
- Monitoring / logging

The application is intended to support approximately 20,000 users.

Your task is to perform a deep, evidence-based audit of the entire repository and create **one** comprehensive Markdown document containing the audit results and a practical implementation plan.

---

## 2. Audit-Only Mode

During this task, **do not modify the application code**.

Do **not**:

- Modify source files
- Delete files
- Rename files
- Refactor code
- Install dependencies
- Remove dependencies
- Change configuration
- Modify environment files
- Change database schemas
- Create migrations
- Change APIs
- Change infrastructure
- Automatically fix bugs
- Rewrite components
- Change architecture

This is an **audit only**.

You may inspect the repository and run safe, read-only analysis commands where appropriate.

Your only required output is:

```
CODEBASE_AUDIT_AND_IMPLEMENTATION_PLAN.md
```

Create this file at the repository root.

---

## 3. Primary Objective

Determine whether the current application is genuinely production-ready and whether it can reasonably support approximately 20,000 users.

The audit must answer:

- What is already good?
- What is wrong?
- What is risky?
- What is missing?
- What could break in production?
- What could become a scalability bottleneck?
- What security vulnerabilities exist?
- What performance problems exist?
- What database problems exist?
- What API problems exist?
- What mobile problems exist?
- What admin-web problems exist?
- What reliability problems exist?
- What testing gaps exist?
- What DevOps / production gaps exist?
- What should be fixed first?
- What should **not** be changed?
- What requires real-world / load testing?
- Can the architecture realistically support approximately 20,000 users?
- What exact implementation sequence should be followed?

Do not provide generic advice. Base findings on the actual repository.

---

## 4. Understand the Entire Repository First

Before producing recommendations, inspect the repository thoroughly.

Identify:

- Root structure
- Applications
- Packages
- Mobile project
- Admin project
- Backend
- Database
- API routes
- Services
- Models
- Schemas
- Migrations
- Authentication
- Authorization
- State management
- Shared libraries
- Utilities
- Configuration
- Environment examples
- Tests
- Build configuration
- Deployment configuration
- CI / CD
- Logging
- Monitoring
- Background jobs
- Cron jobs
- Storage
- Notifications
- Third-party integrations

Do not review only the obvious files. Read important implementation files. Trace important functionality from UI to API to database and back.

---

## 5. Evidence Rule

Every important finding must be supported by repository evidence.

Classify findings as:

| Classification | Meaning |
|---|---|
| **CONFIRMED** | Directly verified from code or configuration. |
| **LIKELY** | Strongly indicated by the implementation but cannot be completely verified statically. |
| **UNKNOWN** | Cannot be determined without runtime testing, production metrics, infrastructure access, or load testing. |

Never present an assumption as a confirmed fact. When something cannot be verified, explicitly state:

> UNKNOWN — requires runtime / load / production verification.

Never invent:

- Performance numbers
- Capacity numbers
- User counts
- Request rates
- Database limits
- Infrastructure specifications
- Security vulnerabilities that are not supported by evidence

---

## 6. Production Target

The target is approximately **20,000 users**.

Important: Do **not** assume this means 20,000 concurrent users.

Analyze realistic scenarios involving:

- Registered users
- Daily active users
- Monthly active users
- Peak concurrent users
- Requests per second
- Peak requests per second
- Database queries
- Read / write ratio
- File uploads
- Notifications
- Background jobs
- Traffic spikes

If the repository does not contain enough information, identify what is missing.

Separate:

- **Known** — from repository
- **Assumptions** — for capacity analysis

---

## 7. Current Architecture Review

Document the actual architecture.

Review:

- Application boundaries
- Module boundaries
- Separation of concerns
- Dependency direction
- Coupling
- Cohesion
- Business logic placement
- Shared code
- API architecture
- Database architecture
- Authentication architecture
- Authorization architecture
- Storage architecture
- Background processing
- External services
- Deployment architecture

Create an actual architecture diagram using Markdown or text.

Example:

```
Mobile Application
       |
       v
    API Layer
       |
       +------ Authentication
       |
       +------ Authorization
       |
       +------ Business Logic
       |
       +------ Database
       |
       +------ Storage
       |
       +------ External Services
       |
       v
Admin Web Application
```

Do not blindly use this example. Build the diagram based on the actual repository.

---

## 8. End-to-End Data Flow

Trace important business flows.

For each major flow, explain:

```
User
  ↓
Mobile / Admin UI
  ↓
State Management
  ↓
API Client
  ↓
Backend Endpoint
  ↓
Authentication
  ↓
Authorization
  ↓
Validation
  ↓
Business Logic
  ↓
Database
  ↓
External Services
  ↓
Response
  ↓
UI
```

Identify:

- Unnecessary requests
- Duplicate logic
- Missing validation
- Authorization gaps
- Data inconsistency
- Race conditions
- Incorrect state handling
- Excessive database queries
- External-service dependencies
- Error-handling gaps
- Retry problems

---

## 9. Mobile Application Audit

Review the complete mobile application.

Check:

- Project structure
- Architecture
- Navigation
- Screens
- Components
- State management
- API layer
- Authentication
- Authorization
- Token handling
- Secure storage
- Local storage
- Caching
- Offline behavior
- Network handling
- Loading states
- Empty states
- Error states
- Form handling
- Validation
- Image handling
- File handling
- Push notifications
- Deep linking
- Permissions
- Background tasks
- Memory usage
- Rendering performance
- Re-rendering
- API request frequency
- Payload size
- Accessibility
- Device compatibility
- Crash risks
- Sensitive-data exposure

Specifically look for:

- Race conditions
- Stale state
- Duplicate API calls
- Unhandled asynchronous operations
- Memory leaks
- Lifecycle bugs
- Insecure local storage
- Hardcoded secrets
- Poor network failure handling
- Incorrect retry logic
- Incorrect caching

---

## 10. Admin Web Application Audit

Review the entire admin application.

Check:

- Architecture
- Routing
- Authentication
- Authorization
- RBAC
- Permissions
- API communication
- State management
- Tables
- Pagination
- Search
- Filtering
- Sorting
- Forms
- Bulk actions
- File uploads
- Dashboard queries
- Loading states
- Empty states
- Error states
- Validation
- Accessibility
- Responsive behavior
- Browser compatibility
- Security

Pay special attention to authorization. A UI that merely hides an admin button is **not** authorization. Verify whether permissions are actually enforced server-side.

---

## 11. Backend / API Audit

Review important backend endpoints and services.

Check:

- API architecture
- Request validation
- Response validation
- Authentication
- Authorization
- RBAC
- Business logic
- Error handling
- Rate limiting
- Pagination
- Filtering
- Sorting
- Database queries
- N+1 queries
- Transactions
- Concurrency
- Idempotency
- Caching
- File processing
- External API calls
- Timeouts
- Retries
- Logging
- Monitoring

For important endpoints, document:

```
Endpoint
  ↓
Authentication
  ↓
Authorization
  ↓
Validation
  ↓
Business Logic
  ↓
Database Queries
  ↓
External Calls
  ↓
Response
```

Identify expensive endpoints and likely bottlenecks.

---

## 12. Database Audit

Review:

- Schema
- Tables
- Relationships
- Foreign keys
- Constraints
- Unique constraints
- Indexes
- ORM usage
- Query construction
- Query efficiency
- N+1 queries
- Transactions
- Locking
- Race conditions
- Connection pooling
- Connection exhaustion
- Pagination
- Sorting
- Filtering
- Large-table behavior
- Soft deletion
- Data integrity
- Migration strategy
- Backup / recovery considerations

Consider production data sizes such as:

- 100K rows
- 1M rows
- 10M+ rows

where relevant. Do not claim exact performance without measurement.

---

## 13. Security Audit

Perform a dedicated security audit.

Check:

- Authentication
- Authorization
- RBAC
- Permission enforcement
- IDOR / BOLA
- Privilege escalation
- Session management
- Access tokens
- Refresh tokens
- Token storage
- Logout
- Password handling
- Password reset
- OTP
- Brute-force protection
- Rate limiting
- Account enumeration
- Input validation
- Output encoding
- XSS
- CSRF (where applicable)
- SQL injection
- NoSQL injection (where applicable)
- SSRF (where applicable)
- Path traversal
- File upload security
- Secrets
- API keys
- Environment variables
- Sensitive logs
- PII exposure
- Encryption
- Webhook security
- Third-party integrations

For every security issue, document:

- Vulnerability
- Location
- Evidence
- Attack scenario
- Impact
- Severity
- Recommended mitigation
- Testing approach

Classify as:

| Severity | Meaning |
|---|---|
| **P0 — Critical** | Immediate production risk. |
| **P1 — High** | Serious vulnerability requiring prompt fix. |
| **P2 — Medium** | Meaningful risk, not immediately exploitable. |
| **P3 — Low** | Minor concern. |

Do not exaggerate severity.

---

## 14. Performance Audit

Review:

- API latency risks
- Database queries
- Network requests
- Response size
- Image optimization
- File handling
- Caching
- Rendering
- Re-renders
- Bundle size
- Code splitting
- Lazy loading
- Memory usage
- CPU-intensive work
- Background work

For every important performance issue:

```
Current behavior
  ↓
Why it is expensive
  ↓
Potential production impact
  ↓
Recommended improvement
  ↓
How to measure improvement
```

---

## 15. 20,000-User Scalability Audit

This is a critical part of the audit. Determine whether the current architecture can reasonably support approximately 20,000 users.

### Backend

Analyze:

- Statelessness
- Horizontal scaling
- Request concurrency
- CPU
- Memory
- Blocking operations
- Connection handling
- Timeouts
- Retries
- Rate limits

### Database

Analyze:

- Connection pool
- Query complexity
- Indexes
- Heavy queries
- Transactions
- Locks
- Pagination
- Connection exhaustion

### API

For important endpoints, analyze:

- Requests per user
- Queries per request
- External calls
- Response size
- Request frequency
- Expensive operations

### Caching

Determine whether caching is actually required. If recommended, specify:

- What should be cached
- Where
- TTL
- Invalidation
- Stale-data risks

Do not recommend caching everything.

### Background Jobs

Identify operations that should potentially execute asynchronously:

- Emails
- Push notifications
- Reports
- Image processing
- File processing
- Exports
- External API calls
- Large database operations

Only recommend queues / workers where justified.

---

## 16. Traffic Spike Analysis

Evaluate what could happen under:

- 2× normal traffic
- 5× normal traffic
- 10× normal traffic

For each, identify:

- First bottleneck
- Second bottleneck
- Failure mode
- Database behavior
- API behavior
- External service behavior
- Retry amplification
- Recovery behavior

Do not claim exact capacity without load testing.

---

## 17. Reliability & Failure Analysis

Analyze:

- Database unavailable
- Database slow
- External API unavailable
- Network failure
- File storage failure
- Notification provider failure
- Background job failure
- Request timeout
- Server restart
- Deployment failure
- Database migration failure
- Duplicate requests
- Concurrent updates
- Old mobile app calling newer API
- Traffic spike

For each:

```
Failure
  ↓
Current behavior
  ↓
Risk
  ↓
Recommended mitigation
  ↓
Recovery strategy
```

---

## 18. Error Handling Audit

Review:

- Global error handling
- API errors
- Network errors
- Validation errors
- Authentication errors
- Authorization errors
- Timeouts
- Retries
- Loading states
- Empty states
- Crash handling
- Logging
- Monitoring
- Recovery

Identify silent failures.

---

## 19. Code Quality Audit

Review:

- Naming
- File organization
- Type safety
- Duplicate code
- Dead code
- Unused dependencies
- Large files
- Large functions
- Magic values
- Hardcoded configuration
- Abstraction quality
- Testability
- Maintainability
- Architecture consistency

Do not recommend refactoring merely for personal preference. Only recommend changes that provide meaningful value.

---

## 20. AI-Generated Code Audit

Because AI-assisted development was used heavily, specifically inspect for:

- Duplicate implementations
- Copy-pasted logic
- Contradictory patterns
- Inconsistent architecture
- Dead code
- Unused code
- Hallucinated assumptions
- Missing edge cases
- Incorrect async handling
- Race conditions
- Security mistakes
- Over-abstraction
- Under-abstraction
- Unnecessary dependencies
- Large components
- Functions doing too many things
- Inconsistent API handling
- Silent failures

Do not assume AI-generated code is bad. Only report actual or strongly supported issues.

---

## 21. Testing Audit

Review all existing tests.

Determine coverage and missing tests for:

- Unit tests
- Integration tests
- API tests
- Database tests
- Authentication tests
- Authorization tests
- Mobile tests
- Admin tests
- End-to-end tests
- Regression tests
- Critical business flows

Prioritize tests based on business risk.

---

## 22. Load Testing Plan

Create a practical load-testing strategy.

Include:

- Normal load
- Peak load
- 2× spike
- 5× spike
- 10× spike
- Stress test
- Soak test

For each, specify:

- Virtual users
- Target RPS
- Duration
- Important endpoints
- Metrics
- Pass / fail criteria

If values cannot be determined from the repository, write:

> TO BE DETERMINED FROM REAL TRAFFIC / BUSINESS METRICS

---

## 23. Observability Audit

Check:

- Structured logging
- Error tracking
- Application metrics
- API latency
- Error rate
- Database metrics
- CPU
- Memory
- Request rate
- Background jobs
- Authentication failures
- Rate-limit events
- External service failures
- Alerts

Determine the minimum observability required for a 20,000-user production system.

---

## 24. DevOps / Production Audit

Review:

- Build process
- Environment management
- Secrets management
- CI / CD
- Deployment
- Rollback
- Database migrations
- Health checks
- Graceful shutdown
- Backups
- Disaster recovery
- Monitoring
- Autoscaling
- Load balancing
- CDN
- Object storage
- Infrastructure configuration

Do not recommend unnecessary infrastructure. Avoid premature adoption of:

- Microservices
- Kubernetes
- Multiple databases
- Complex event-driven architecture

Prefer the simplest architecture that can reliably support the actual workload.

---

## 25. UX & Accessibility Audit

Review meaningful technical UX issues:

- Navigation
- Loading experience
- Empty states
- Error messages
- Forms
- Validation
- Feedback
- Accessibility
- Mobile usability
- Admin usability
- Consistency

Do not turn this into a generic design critique.

---

## 26. Finding Priorities

Every finding must have one of the following priorities:

| Priority | Meaning |
|---|---|
| **P0 — Critical** | Security vulnerability, data-loss risk, privilege escalation, severe production failure, or major correctness issue. |
| **P1 — High** | Important security, reliability, scalability, performance, or business issue. |
| **P2 — Medium** | Meaningful improvement that is not immediately dangerous. |
| **P3 — Low** | Nice-to-have cleanup, minor optimization, or low-impact refactoring. |

Do not inflate severity.

---

## 27. Finding Format

Every significant finding must use the following format:

```markdown
## [ID] [P0/P1/P2/P3] — Finding Title

**Area:**
Security / Mobile / Admin / Backend / Database / Performance / Architecture / Testing / DevOps / UX

**Confidence:**
Confirmed / Likely / Unknown

**Location:**
Exact file path and line or function where possible.

**Current Behavior:**
What the code currently does.

**Problem:**
What is wrong.

**Evidence:**
Specific code or configuration evidence.

**Production Impact:**
What could happen in production.

**Recommendation:**
What should change.

**Implementation Approach:**
Step-by-step implementation guidance.

**Testing:**
How to verify the fix.

**Risk of Change:**
Low / Medium / High
```

---

## 28. Master Findings Table

At the beginning of the document, create a master findings table:

| ID | Priority | Area | Finding | Impact | Confidence | Effort | Order |
|----|----------|------|---------|--------|------------|--------|-------|
| | | | | | | | |

Sort by recommended implementation order.

---

## 29. Keep — Do Not Change

Create a dedicated section titled:

```
KEEP — DO NOT CHANGE
```

Identify:

- Good architecture
- Good components
- Good services
- Good security practices
- Good database decisions
- Good API patterns
- Good testing
- Good infrastructure
- Good code patterns

Explain why they should remain unchanged. This section is mandatory. The future coding agent must not rewrite working parts unnecessarily.

---

## 30. Implementation Roadmap

Create a sequential implementation plan using the following phases:

| Phase | Focus |
|-------|-------|
| Phase 0 | Critical Security & Production Blockers |
| Phase 1 | Correctness & Reliability |
| Phase 2 | Database & API |
| Phase 3 | Performance & Scalability |
| Phase 4 | Architecture |
| Phase 5 | Testing |
| Phase 6 | Observability & DevOps |
| Phase 7 | UX & Accessibility |
| Phase 8 | Long-Term Improvements |

For each phase, provide:

- Objective
- Finding IDs addressed
- Tasks
- Files / modules affected
- Dependencies
- Complexity
- Testing requirements
- Definition of done

---

## 31. Windsurf Implementation Tasks

Convert the audit findings into individual implementation tasks.

Each task must follow this format:

```markdown
### TASK-001

**Title:**
Fix server-side authorization for resource access

**Priority:**
P0

**Related Findings:**
SEC-001

**Objective:**
Explain exactly what needs to be achieved.

**Likely Files:**
- path/to/file
- path/to/file

**Implementation:**
1. Step one
2. Step two
3. Step three

**Acceptance Criteria:**
- Criterion one
- Criterion two
- Criterion three

**Tests:**
- Test one
- Test two

**Dependencies:**
None
```

Create tasks in dependency order. The task list must be usable by another AI coding agent.

---

## 32. Breaking Changes & Migration Risks

Create a section titled:

```
BREAKING CHANGES & MIGRATION RISKS
```

Identify:

- API changes
- Database migrations
- Authentication changes
- Mobile compatibility
- Admin compatibility
- Data migration
- Deployment sequencing
- Backward compatibility

For each, explain the safest migration strategy.

---

## 33. Production Readiness Score

Create a scoring table:

| Category | Score / 100 | Reason |
|----------|-------------|--------|
| Architecture | | |
| Security | | |
| Mobile | | |
| Admin | | |
| Backend / API | | |
| Database | | |
| Performance | | |
| Reliability | | |
| Testing | | |
| DevOps | | |
| Scalability | | |
| Observability | | |

Then calculate:

```
OVERALL PRODUCTION READINESS: X / 100
```

Explain the score honestly. Do not artificially increase the score.

---

## 34. 20,000-User Capacity Verdict

Give exactly one verdict:

| Verdict | Meaning |
|---------|---------|
| 🟢 **YES — Ready** | Architecture can support 20,000 users as-is. |
| 🟡 **YES — With Specific Improvements** | Can support 20,000 users after identified fixes. |
| 🟠 **PARTIALLY — Significant Bottlenecks** | Some components can scale; others require rework. |
| 🔴 **NO — Major Changes Required** | Architecture cannot support 20,000 users without significant rework. |

Then explain:

- Why
- Main bottlenecks
- Required fixes
- Load-testing requirements
- What can remain unchanged

---

## 35. Capacity Table

Where evidence allows:

| Metric | Current / Estimated | Target | Status | Verification |
|--------|---------------------|--------|--------|--------------|
| Registered users | | 20,000 | | |
| Concurrent users | | | | |
| Requests / sec | | | | |
| Peak RPS | | | | |
| Database connections | | | | |
| API latency | | | | |
| Error rate | | | | |

If something cannot be determined, write:

> UNKNOWN — requires measurement / load testing

Never invent values.

---

## 36. Production Release Gate

Create a section titled:

```
PRODUCTION RELEASE GATE
```

| Status | Meaning |
|--------|---------|
| 🔴 **BLOCK RELEASE** | Issues that must be fixed before production. |
| 🟠 **FIX BEFORE SIGNIFICANT SCALE** | Issues that may become serious as usage grows. |
| 🟡 **MONITOR** | Acceptable now but requires monitoring. |
| 🟢 **GOOD** | No meaningful action required. |

---

## 37. Top 10 Actions

Finish with a section titled:

```
TOP 10 THINGS TO DO FIRST
```

For each, provide:

- Action
- Priority
- Why it matters
- Files / modules
- Implementation summary
- Verification method

These must be the highest-value actions. Do not simply choose the easiest tasks.

---

## 38. Final Executive Summary

End the document with:

- Current architecture quality
- Biggest strengths
- Biggest weaknesses
- Critical security issues
- Biggest scalability risks
- Biggest reliability risks
- Biggest performance issues
- Testing gaps
- DevOps gaps
- Production readiness
- 20,000-user readiness
- Most important next steps

---

## 39. Final Document Structure

The generated file must contain these sections in this order:

1. Executive Summary
2. Repository Overview
3. Current Architecture
4. Architecture Diagram
5. End-to-End Data Flow
6. Mobile Application Audit
7. Admin Web Application Audit
8. Backend / API Audit
9. Database Audit
10. Security Audit
11. Performance Audit
12. Scalability / 20,000-User Audit
13. Traffic Spike Analysis
14. Reliability & Failure Analysis
15. Error Handling Audit
16. Code Quality Audit
17. AI-Generated Code Audit
18. Testing Audit
19. Load Testing Plan
20. Observability Audit
21. DevOps / Production Audit
22. UX / Accessibility Audit
23. Master Findings Table
24. KEEP — Do Not Change
25. Implementation Roadmap
26. Detailed Windsurf Implementation Tasks
27. Breaking Changes & Migration Risks
28. Production Readiness Score
29. 20,000-User Capacity Verdict
30. Capacity Table
31. Production Release Gate
32. Top 10 Things to Do First
33. Final Executive Summary

---

## 40. Final Quality Check

Before finishing, verify that the document:

- [ ] Is based on actual repository inspection
- [ ] Does not contain invented findings
- [ ] Clearly separates facts from assumptions
- [ ] Identifies exact files where possible
- [ ] Prioritizes findings
- [ ] Includes security review
- [ ] Includes mobile review
- [ ] Includes admin review
- [ ] Includes backend review
- [ ] Includes database review
- [ ] Includes performance review
- [ ] Includes scalability review
- [ ] Includes 20,000-user assessment
- [ ] Includes reliability review
- [ ] Includes testing review
- [ ] Includes DevOps review
- [ ] Includes AI-generated-code review
- [ ] Includes load-testing plan
- [ ] Includes observability review
- [ ] Includes KEEP / DO NOT CHANGE section
- [ ] Includes implementation roadmap
- [ ] Includes individual implementation tasks
- [ ] Includes migration risks
- [ ] Includes production readiness score
- [ ] Includes 20,000-user verdict
- [ ] Includes top 10 actions

---

## 41. Most Important Principles

Follow these principles throughout the audit:

**Principle 1 — Do not rewrite unnecessarily.**
A working production system should not be rewritten simply because another architecture is theoretically better.

**Principle 2 — Security before style.**
Fix security, authorization, correctness, and data-integrity problems before cosmetic refactoring.

**Principle 3 — Evidence over assumptions.**
Never invent a problem.

**Principle 4 — Simplicity over over-engineering.**
Do not introduce microservices, Kubernetes, Redis, queues, replicas, or other infrastructure unless the actual workload justifies it.

**Principle 5 — Production behavior matters.**
A system that works locally is not automatically production-ready.

**Principle 6 — 20,000 users is a capacity target, not a magic number.**
Distinguish registered users from active users and concurrent users.

**Principle 7 — Static analysis has limits.**
Clearly identify everything that requires:

- Load testing
- Runtime testing
- Production metrics
- Infrastructure inspection
- Security testing
- Real production data

**Principle 8 — Implementation must be incremental.**
Prefer small, testable, reversible changes.

**Principle 9 — Protect existing functionality.**
Do not recommend changes without considering regression risk.

**Principle 10 — The document is the source of truth.**
The final Markdown document should be detailed enough that another senior engineer or AI coding agent can execute the implementation plan without needing to repeat the entire audit.

---

## 42. Final Instruction

Now inspect the entire repository.

1. Understand the architecture and important business flows.
2. Perform the complete audit described above.
3. Do not modify the application.
4. Do not fix anything.
5. Do not install anything.
6. Do not rewrite anything.
7. Create exactly one file at the repository root: `CODEBASE_AUDIT_AND_IMPLEMENTATION_PLAN.md`

This document must be the single source of truth for the next implementation phase.

The objective is not to make the codebase theoretically perfect. The objective is to determine:

- What **must** be changed
- What **should** be changed
- What **can remain** unchanged
- What **must be tested**

...so that this application can safely operate as a production system targeting approximately 20,000 users.

Be technically rigorous, practical, conservative about assumptions, and extremely specific about implementation.
