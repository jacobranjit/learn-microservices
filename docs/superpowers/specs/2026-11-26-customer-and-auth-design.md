# Customer service + Auth service (JWT, RBAC) — design

Date: 2026-11-26. Status: draft, awaiting approval.

## Intent

The stack is about to be deployed to a server. Today every endpoint on every service is open to anyone who
can reach the ports. The goal is:

- **Users** (customers) register, log in, keep a profile, place orders and see **only their own** orders.
- **Business admins** manage products, inventory and locations, and see everything.
- Nobody unauthenticated can read or change business data.

Two new services are built **in this order**: first `customer`, then `auth`. The existing services
(product, inventory, order, generate-master-data) become token-checking "resource servers".

## Goals

1. `customer` service: one profile per registered user — name, email, phone, one delivery address.
2. `auth` service: registration, login with email + password, JWT issuing (RS256), roles `USER`, `ADMIN`, `SERVICE`.
3. Every service checks the JWT on every request and enforces the permission table below.
4. Orders record the `customerId` of the user who placed them, plus a **shipping** and a **billing** address
   sent in the order request.
5. Service-to-service calls keep working, using service tokens.
6. Everything configurable through `.env` (same pattern as the DB password), Docker Compose updated,
   Hoppscotch collection updated.
7. `generate-master-data` also seeds **300 customers** (each a registered USER login with a profile).

## Non-goals (this round)

- Refresh tokens, logout / token revocation, password reset, email verification.
- Changing a login email. (Email is fixed at registration.)
- Multiple addresses per customer; address book.
- API gateway; CORS for a browser frontend (add when the frontend exists).
- Key rotation (one signing key; rotation can come later because the JWKS format supports several keys).
- Flyway/Liquibase — the repo stays on `ddl-auto=update`.

## Anti-goals (would be a failure even if it "works")

- A user being able to read another user's orders or profile.
- A user being able to give themselves `ADMIN`, or to call the stock-deduction endpoint directly.
- Any service other than `auth` being able to create a valid token.
- **Deleting customers.** Neither a user nor an admin can delete a customer: orders reference `customerId`
  and must never point at a deleted customer.
- Registration leaving a half-created account (login without a customer, or customer without a login)
  in the normal failure cases (customer service down, duplicate email).

## Decisions made with the user

| Topic | Decision |
|---|---|
| Roles | `USER` places orders; `ADMIN` (business admin) manages products and inventory. |
| Order visibility | A USER sees only their own orders; ADMIN sees all. |
| Build order | Customer service first, then auth. |
| Customer fields | name, email, phone, **one** delivery address. |
| Registration | Registering an account automatically creates the customer. |
| Profile deletion | **Not allowed** for users (orders would lose their customer). |
| Order addresses | Every order request carries a **shipping address and a billing address**. The backend does **not** compare them to the customer's stored address (the frontend handles that). Order does **not** call the customer service. |
| Token checking | **Approach B**: RS256. Auth signs with a private key; publishes the public key (JWKS); every service verifies locally. |

## Approaches considered (token checking)

- **A. Shared HMAC secret (HS256)** in `.env`, every service verifies with it. Simplest, but every service
  holds the signing secret and could forge an admin token.
- **B. RS256 + JWKS — chosen.** Only auth holds the private key; services fetch the public key once from
  `GET /.well-known/jwks.json` and cache it. Supported natively by `spring-boot-starter-oauth2-resource-server`
  (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`). Slightly more setup in auth.
- Dropped: per-request token introspection (latency, auth becomes a single point of failure on every call);
  an API gateway (another ~1 GB container on an 8 GB host; services stay reachable on their own ports anyway).

## Architecture

```
                 register/login                      JWKS (public key, fetched once, cached)
  client ───────────────────────────► auth :8086 ◄───────────────────────────────┐
     │  Authorization: Bearer <jwt>      │ POST /api/customers (service token)     │
     ├──────────────► customer :8085 ◄───┘                                         │
     ├──────────────► product  :8082 ──────────────────────────────────────────────┤
     ├──────────────► inventory:8083 ──► product (service token) ──────────────────┤
     └──────────────► order    :8081 ──► product, inventory (service token) ───────┘
  generate-master-data :8084 ──► auth (login as ADMIN) ──► product, inventory
```

Ports: `customer` **8085**, `auth` **8086** (next free numbers). Databases: `customer_db`, `auth_db`
in the shared Postgres (database-per-service, as today).

## Tokens

- RS256, RSA 2048. `kid` header set to the key id.
- Issuer: `oletrex-auth` (configurable `AUTH_ISSUER`); resource servers validate issuer and expiry.
- **User token** claims: `sub` = auth user id, `email`, `roles` = `["USER"]` or `["ADMIN"]`,
  `customerId` (USER only; absent for admins without a profile). Lifetime **1 hour** (`JWT_TTL`, default `1h`).
- **Service token** claims: `sub` = client id (e.g. `order-service`), `roles` = `["SERVICE"]`. Lifetime 1 hour;
  clients cache the token and fetch a new one shortly before it expires (or on a 401).
- Resource servers map `roles` → Spring authorities `ROLE_<name>` (custom `JwtAuthenticationConverter`).
- Signing key: generated on first start and **stored in `auth_db`**, so restarts don't invalidate tokens and
  the key never sits in git or `.env`.

## Auth service (`auth`, port 8086, package `com.mc.oletrex.auth`)

Entities:
- `UserAccount`: id, email (unique, lower-cased), passwordHash (BCrypt), role (`USER`/`ADMIN`), enabled,
  customerId (nullable), createdAt.
- `SigningKey`: kid, public/private key (encoded), createdAt.
- Service clients are **configuration, not a table**: `SERVICE_CLIENTS` from `.env`
  (ids + secrets for `order-service`, `inventory-service`, `auth-service`).

Endpoints:

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/auth/register` | public | email, password (min 8), name, phone, delivery address → creates USER + customer; returns token (201) |
| `POST /api/auth/login` | public | email + password → `{accessToken, tokenType:"Bearer", expiresIn}`; 401 on bad credentials or disabled account |
| `POST /api/auth/token` | public (client secret) | service login: clientId + clientSecret → service token |
| `GET /.well-known/jwks.json` | public | public key(s) |
| `GET /api/auth/users` | ADMIN | cursor-paginated user list |
| `PUT /api/auth/users/{id}/role` | ADMIN | promote/demote (`USER`/`ADMIN`) |
| `PUT /api/auth/users/{id}/enabled` | ADMIN | enable/disable login (the "deactivate" — nothing is deleted) |

- **First admin**: on startup, if no account with `ADMIN_EMAIL` exists, create it with `ADMIN_PASSWORD`
  (both from `.env`). Admins created this way have no customer profile.
- **Registration flow**: validate + check email not taken (409) → call `POST /api/customers` on customer with
  auth's own service token → save the user with the returned `customerId`. If saving the user fails after
  the customer was created, compensate (delete that customer via the SERVICE-only endpoint below).
  Customer service down → 503, nothing created.
- Changing a role or disabling an account takes effect on the user's next login (tokens live ≤ 1 hour;
  accepted given no revocation in scope).

## Customer service (`customer`, port 8085, package `com.mc.oletrex.customer`)

Entity `Customer`: id, authUserId (unique), name, email (unique), phone, delivery address
(embedded `Address`), active, createdAt, updatedAt.

`Address` (shared shape, also used by order): line1 (required), line2, city (required), state,
postalCode (required), country (ISO-3166 alpha-2, required).

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/customers` | SERVICE | create (called by auth during registration); 409 on duplicate email/authUserId |
| `DELETE /api/customers/{id}` | SERVICE | **compensation only** for a failed registration; not exposed to users or admins |
| `GET /api/customers/me` | USER | own profile (customer id taken from the token, never from the URL) |
| `PUT /api/customers/me` | USER | update name, phone, delivery address (email is read-only) |
| `GET /api/customers` | ADMIN | cursor-paginated list |
| `GET /api/customers/{id}` | ADMIN | one customer |

## Changes to existing services

All four add `spring-boot-starter-oauth2-resource-server` + a `SecurityConfig`: stateless, CSRF off,
JWT via `jwk-set-uri=${AUTH_JWKS_URL:http://localhost:8086/.well-known/jwks.json}`, 401/403 returned as
RFC 9457 ProblemDetail like the rest of the errors.

Always public: `/actuator/health` (Docker healthchecks), Swagger UI and `/v3/api-docs` (with a bearer
"Authorize" button configured in springdoc).

### Permission table

| Endpoint | USER | ADMIN | SERVICE |
|---|---|---|---|
| `GET /api/products`, `GET /api/products/{sku}` | ✅ | ✅ | ✅ (order, inventory) |
| `POST/PUT/DELETE /api/products…` | ❌ | ✅ | ❌ |
| `GET /api/inventory…`, `GET /api/locations…` | ❌ | ✅ | ❌ |
| `POST /api/inventory` (upsert), `POST/PUT /api/locations…` | ❌ | ✅ | ❌ |
| `POST /api/inventory/deductions` | ❌ | ❌ | ✅ (order only) |
| `POST /api/orders` | ✅ | ❌ (admins have no customer profile) | ❌ |
| `GET /api/orders`, `GET /api/orders/{no}` | ✅ own only | ✅ all | ❌ |
| `DELETE /api/orders/{no}` | ❌ | ✅ | ❌ |
| `GET /api/master-data`, `POST /api/master-data/populate` | ❌ | ✅ | ❌ |
| customer + auth endpoints | as in their tables above | | |

A USER asking for someone else's order gets **404** (not 403), so order numbers can't be probed.

### Order
- `Order` gains `customerId` (from the token's `customerId` claim; never from the request body),
  `shippingAddress`, `billingAddress` (embedded `Address`, column prefixes `shipping_` / `billing_`).
- `OrderRequest` gains required `shippingAddress` and `billingAddress` (validated like `Address` above).
  `OrderResponse` returns both plus `customerId`.
- List/get filter by `customerId` for USER; unfiltered for ADMIN. Cursor pagination unchanged.
- Calls to product and inventory send order's service token.
- Existing orders in the local DB are test data without a customer: **reset the `orders` and
  `order_line_items` tables** on deploy (same reset approach used for locations), so `customerId` can be NOT NULL.

### Inventory
- Its product-existence check sends inventory's service token.

### Generate-master-data
- Logs in to auth with `ADMIN_EMAIL` / `ADMIN_PASSWORD` before populating and sends that token.
- Its own endpoints require ADMIN.
- **Seeds 300 customers** (user request, 2026-11-27) from a new committed `master-data/customers.csv`:
  email, name, phone, delivery address (line1, line2, city, state, postalCode, country). Generated once,
  like `products.csv`: fictional names, `@example.com` emails (reserved domain, never real people),
  US addresses to match the USD catalogue.
- Each customer is created through **`POST /api/auth/register`**, so every seeded customer is a real login
  with a profile — exactly what a test user would get.
- All seeded customers share one test password: `MASTERDATA_CUSTOMER_PASSWORD` in `.env`
  (dev default `Customer123!`). It is not stored in the CSV.
- **Re-run = reset** (same rule as products and inventory): if the email already exists (409), log in as that
  customer with the seed password and `PUT /api/customers/me` back to the CSV values. If that login fails
  (someone changed the password), count it as failed in the summary and carry on.
- Order of populate: locations → products → inventory → customers. Customers need only auth + customer,
  so a failure there doesn't affect the catalogue data.
- `GET /api/master-data` summary includes the customer count; the populate result reports
  customers created / reset / failed like the other sections.
- `generate-master-data` gains `AUTH_SERVICE_URL` (it already has product and inventory URLs) and
  `depends_on` auth and customer being healthy.

## Docker / configuration

- New services `customer` and `auth` in `docker-compose.yml`, same pattern (Dockerfile, healthcheck, limits).
  Limits: **0.5 CPU / 768M each** (default, env-overridable) → worst case 6.0 GB on the 8 GB host.
- `docker/postgres/init.sql` adds `customer_db`, `auth_db`. Existing volumes don't re-run it, so `.env.example`
  / deploy notes include the one-off `CREATE DATABASE` commands.
- New `.env` variables: `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `MASTERDATA_CUSTOMER_PASSWORD`, service client secrets
  (`ORDER_SERVICE_SECRET`, `INVENTORY_SERVICE_SECRET`, `AUTH_SERVICE_SECRET`), `JWT_TTL`, ports/limits for
  the two new services. Dev defaults exist (like the DB password) so a fresh clone still starts.
- `depends_on`: product, inventory, order, customer depend on auth being started.

## Hoppscotch

- New folders **Auth Service** and **Customer Service**; variables `authBaseUrl`, `customerBaseUrl`.
- Login / register requests store the token in a variable via `pw.env.set("accessToken", …)`; requests
  use Bearer `<<accessToken>>` (an "Admin login" request sets the same variable for admin calls).
- Master Data folder: summary/populate requests need the admin token; add a "Customer login (seeded)"
  request using a seeded email and the seed password.
- Order "create" requests include shipping and billing addresses; add 401 (no token) and 403 (wrong role)
  negative cases.

## Left to the builder

- Internal package layout, DTO names, how the service-token cache is implemented.
- Exact JWKS/issuer property wiring; BCrypt strength (default is fine).
- Error message wording.
- Verification follows the repo's practice: run the stack (spare ports, don't touch the user's IntelliJ
  instances on 8081–8083) and exercise the permission table end to end, including the anti-goals.
