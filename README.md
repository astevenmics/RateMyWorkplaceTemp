# RateMyWork

**Provide feedback to the workplace, work condition, everything!**

RateMyWork is a complete Spring Boot web application for discovering workplaces and reading
**verified** employee feedback. Anyone can search and read; only members who verify their identity
**and** prove their employment can post ratings — keeping feedback honest and accountable.

> Stack: **Spring Boot 3 / Java 21**, **Spring Security** (simple session auth), **Spring Data JPA + MySQL**,
> a **plain HTML + CSS + JavaScript** frontend (no Thymeleaf), **Bucket4j** rate limiting, and
> Spring Data Web pagination via `@EnableSpringDataWebSupport`.

---

## Features

### Public (no login)
- **Home page** showcasing the platform, a global search (company, city, ZIP, keyword), live counters and **top-rated workplaces**.
- **Browse workplaces** — 30 per page, filter by category, sort alphabetically / newest / most-reviewed / top-rated.
- **Workplace detail page** — full company info, per-location feedback, rating breakdown, and a reserved **Google AdSense** slot.

### Members
- **Registration** with Display Name, Username, Email, Phone Number and Password.
- **Email + phone verification** — both required before submitting workplaces or feedback. (In dev mode the codes are printed to the server log; wire SMTP/SMS for production.)
- **Editable profile** (reachable from the header) — change details and password; changing email/phone re-triggers verification.
- **Suggest a workplace** for public listing — supports multiple **locations** (different addresses/ZIPs) and multiple **categories**.
- **Verify employment** — upload a **PDF/PNG/JPG** proof (offer letter, employment confirmation, …) scoped to a company *and* location.
- **Post feedback** — 1–5 stars with a short/detailed explanation, **only** for companies/locations where the member has an **approved** proof.

### Admin & Moderators
A full **admin panel** (`/admin.html`) with sections for:
- **Statistics dashboard** — total/verified users, companies, feedback, plus a 30-day traffic graph (page views / logins / signups).
- **Approve workplaces** suggested by users.
- **Approve employment proofs** (view the uploaded document inline).
- **Moderate feedback** — hide T&C-violating reviews or delete them.
- **Manage users** — search, flag suspicious usernames, enable/disable, delete.
- **Moderator access** — grant *limited* rights to any user by username (approve workplaces / approve proofs / moderate feedback / manage users) without full admin.
- **Categories** management.
- **Website feedback inbox** (submitted from the footer of every page).
- **"What's new" updates** — publish announcements shown in the footer.

Moderators only see the sections their granted permissions allow.

### Protection
- **Rate limiting** (Bucket4j token buckets per client IP) shields the server from floods/DDoS-style abuse — a generous global budget plus a strict budget for auth and write endpoints. Exceeding it returns **HTTP 429** with `Retry-After`.
- **CSRF protection** (cookie + SPA token pattern), **BCrypt** password hashing, session fixation protection, role/permission-based authorization, and upload content-type/size validation.

### Responsive UI
Mobile-first CSS adapts from phones to wide screens — a collapsible nav, fluid grids, horizontally scrollable admin tables, and stacked layouts on small screens.

---

## Running it

### Option A — quick try with the in-memory database (no MySQL needed)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Then open <http://localhost:8080>. Uses H2 and seeds demo data.

### Option B — with MySQL (default profile)
1. Create a database/user (or let the app create the DB):
   ```sql
   CREATE DATABASE ratemywork;
   CREATE USER 'ratemywork'@'%' IDENTIFIED BY 'ratemywork';
   GRANT ALL PRIVILEGES ON ratemywork.* TO 'ratemywork'@'%';
   ```
2. Run:
   ```bash
   mvn spring-boot:run
   ```
   Override via env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`.

### Build a runnable jar
```bash
mvn clean package
java -jar target/ratemywork.jar
```

### Default admin account
On first start an admin is created (configurable via `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL`):

| Username | Password      |
|----------|---------------|
| `admin`  | `Admin@12345` |

**Change this in production.** The `dev` profile also seeds a verified demo reviewer (`demo_reviewer` / `Demo@12345`).

---

## Configuration highlights (`application.properties`)
| Key | Purpose |
|-----|---------|
| `app.upload.dir` / `app.upload.allowed-content-types` | Where proofs are stored and which types are allowed (PDF/PNG/JPG). |
| `app.mail.enabled` | When `false`, verification codes are logged instead of e-mailed. |
| `app.ratelimit.*` | Token-bucket capacities/refill for global and sensitive endpoints. |
| `app.admin.*` | Bootstrap admin credentials. |
| `spring.data.web.pageable.*` | Default page size 30, max 100. |

To enable **Google AdSense**, drop your publisher/slot IDs into the commented `<ins class="adsbygoogle">` block in `company.html`.

---

## Project layout
```
src/main/java/com/ratemywork
├── RateMyWorkApplication.java      # @EnableSpringDataWebSupport, @EnableScheduling
├── config/                         # rate limiting, properties, web config, data seeding
├── domain/                         # JPA entities (User, Company, Location, Feedback, EmploymentProof, …)
├── repository/                     # Spring Data repositories
├── security/                       # Spring Security config, CSRF (SPA), login events
├── service/                        # business logic (verification, proofs, feedback, admin, analytics)
├── dto/                            # request/response records + mapper
└── web/                            # REST controllers + global error handling
src/main/resources/static/          # HTML + CSS + JS frontend
```

## API overview
| Area | Endpoints |
|------|-----------|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `POST /api/auth/verify` |
| Companies | `GET /api/companies` (search/filter/sort/paginate), `GET /api/companies/top`, `GET /api/companies/{id}`, `POST /api/companies` |
| Feedback | `GET /api/feedback/location/{id}`, `GET /api/feedback/eligibility/{id}`, `POST /api/feedback` |
| Proofs | `POST /api/proofs` (multipart), `GET /api/proofs/mine` |
| Site | `POST /api/site/feedback`, `GET /api/site/updates` |
| Moderation | `/api/mod/**` (permission-gated) |
| Admin | `/api/admin/**` (admin only) — users, moderators, categories, stats, updates, site-feedback |

---

Built as a complete, self-contained demo of a production-shaped Spring Boot application.
