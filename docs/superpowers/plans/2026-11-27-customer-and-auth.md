# Customer + Auth Services (JWT, RBAC) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `customer` service and an `auth` service that issues RS256 JWTs, and make every service enforce role-based access (USER / ADMIN / SERVICE), with orders owned by customers and 300 seeded customers.

**Architecture:** Auth signs tokens with a private key kept in `auth_db` and publishes the public key at `/.well-known/jwks.json`. Every other service is a Spring Security OAuth2 resource server that verifies tokens locally against that JWKS. Service-to-service calls carry a SERVICE token obtained from auth with a client id + secret.

**Tech Stack:** Spring Boot 4.1.2, Java 21, Maven wrapper, Spring Security (`spring-boot-starter-security-oauth2-resource-server`, Nimbus JOSE for signing), Spring Data JPA, PostgreSQL 18, springdoc 3.2.1, Lombok, JUnit 5 + Mockito + `spring-boot-starter-security-test`, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-11-26-customer-and-auth-design.md`

## Global Constraints

- Base package `com.mc.oletrex.<service>`; `customer` → `com.mc.oletrex.customer` port **8085** db `customer_db`; `auth` → `com.mc.oletrex.auth` port **8086** db `auth_db`.
- New services copy the shape of `product/` exactly: `pom.xml` (parent `spring-boot-starter-parent` 4.1.2, springdoc `${springdoc.version}`, Lombok annotation-processor config, actuator), `mvnw`, `.mvn/`, `Dockerfile` (change `EXPOSE`), `.dockerignore`, `application.properties` layout, `GlobalExceptionHandler extends ResponseEntityExceptionHandler` returning RFC 9457 `ProblemDetail`, keyset `CursorPage` + opaque base64 `id:<n>` cursor.
- Each service is an independent Maven project (no parent module). The security package below is **copied** into each service, same file names, same behaviour.
- JWT: RS256, RSA 2048, `kid` header, issuer `${AUTH_ISSUER:oletrex-auth}`, claims `sub`, `roles` (JSON array, one of `USER`/`ADMIN`/`SERVICE`), `email` (users), `customerId` (USER only, number). TTL `${JWT_TTL:1h}`.
- Resource-server property: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${AUTH_JWKS_URL:http://localhost:8086/.well-known/jwks.json}`; issuer property `security.jwt.issuer=${AUTH_ISSUER:oletrex-auth}`.
- Always public on every service: `/actuator/health/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`.
- 401 and 403 are `application/problem+json` ProblemDetail (titles `Unauthorized` / `Forbidden`).
- A USER asking for another customer's order gets **404**.
- No deletes of customers by users or admins. `DELETE /api/customers/{id}` exists for SERVICE only (registration compensation).
- Email is lower-cased and trimmed everywhere it is stored or compared; not editable after registration.
- Passwords: BCrypt; min length 8.
- `.env` variables (all with dev defaults in compose and properties): `ADMIN_EMAIL` (`admin@oletrex.local`), `ADMIN_PASSWORD` (`Admin123!`), `ORDER_SERVICE_SECRET` (`order-dev-secret`), `INVENTORY_SERVICE_SECRET` (`inventory-dev-secret`), `MASTERDATA_CUSTOMER_PASSWORD` (`Customer123!`), `JWT_TTL`, `AUTH_ISSUER`, `CUSTOMER_PORT`/`AUTH_PORT`, `CUSTOMER_CPUS`/`CUSTOMER_MEMORY` (`0.5`/`768M`), `AUTH_CPUS`/`AUTH_MEMORY` (`0.5`/`768M`).
- Hoppscotch collection `hoppscotch/oletrex-collection.json` (collection/folder `"v": 12`, request `"v": "17"`) is updated in the same task as any controller change.
- **Do not commit.** The user commits only on an explicit "commit and push". Each task ends at a green build instead.
- Never stop or reuse the user's processes on ports 8081–8083 (IntelliJ). Run anything ad hoc on spare ports (1808x, 2808x) or in a throwaway compose project; never `down -v` the `oletrex` project.

## Review Focus

1. **USER token without `customerId`** (e.g. an ADMIN token, or a malformed token) calling `POST /api/orders` or `/api/customers/me` → 403, never a NullPointerException/500. Test in Tasks 1 and 7.
2. **Expired or wrong-issuer token** → 401 ProblemDetail, not 500 or 403. Test in Task 1 (`JwtDecoder` validator) and Task 2 (issuer round-trip).
3. **Email in different case / with spaces at registration and login** (`Alice@Example.com ` vs `alice@example.com`) → same account; duplicate registration → 409. Test in Task 3.
4. **Customer service down during registration** → 503 and no `UserAccount` row left behind; user save failing after customer creation → customer deleted. Test in Task 3.
5. **Service token expiring while a service is running** → the next call fetches a new token (cache refreshes ≥ 60 s before `exp`, and once more on a 401). Test in Task 6 (`ServiceTokenProviderTest`).

---

## Shared security package (referenced by Tasks 1, 2, 5, 6, 7, 8)

Files per service, in `<base>/security/`:

- `SecurityConfig` — `@Configuration @EnableWebSecurity @EnableMethodSecurity`.
  - `SecurityFilterChain securityFilterChain(HttpSecurity http, JwtRolesConverter converter, ProblemDetailAuthHandlers handlers)`: stateless, CSRF off, the public paths above `permitAll`, then the service's own rules (given in each task) via `authorizeHttpRequests`, `anyRequest().authenticated()`, `oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)).authenticationEntryPoint(handlers).accessDeniedHandler(handlers))`, plus `exceptionHandling` with the same handlers.
  - `JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri, @Value("${security.jwt.issuer}") String issuer)`: `NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build()` with `JwtValidators.createDefaultWithIssuer(issuer)`. (Lazy: the JWKS is fetched on the first request, so services start without auth.)
- `JwtRolesConverter implements Converter<Jwt, AbstractAuthenticationToken>` — authorities `ROLE_<role>` from the `roles` claim; principal name = `sub`; missing/empty `roles` → no authorities.
- `ProblemDetailAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler` — writes ProblemDetail JSON (401 `Unauthorized`, 403 `Forbidden`), `Content-Type: application/problem+json`.
- `CurrentUser` — `static Optional<Long> customerId(Jwt jwt)` (reads numeric `customerId` claim; absent/non-numeric → empty) and `static boolean isAdmin(Authentication a)`. Only in services that need it (customer, order).
- `OpenApiConfig` (existing, in `config/`) gains a `bearerAuth` HTTP bearer JWT `SecurityScheme` and a global `SecurityRequirement`.
- pom: add `spring-boot-starter-security-oauth2-resource-server`; test scope `spring-boot-starter-security-test`.

Permission tests use `@WebMvcTest(<Controller>.class)` + `@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})`, `@MockitoBean` for the service, and `SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.claim("roles", List.of("USER")).claim("customerId", 42L)).authorities(new JwtRolesConverter().convert(...).getAuthorities())` — wrap that in a test helper `TestTokens` with `static RequestPostProcessor user(long customerId)`, `admin()`, `service()`, `noRoles()`.

Service-token client (Task 6 creates it in `inventory`, Task 7 copies it into `order`): `<base>/client/ServiceTokenProvider` — `String token()` (cached; refresh when `< 60s` to `exp`), `void invalidate()`; calls `POST {services.auth.base-url}/api/auth/token` with `{clientId, clientSecret}`. `<base>/client/ServiceTokenInterceptor implements ClientHttpRequestInterceptor` — sets `Authorization: Bearer <token>`; on a 401 response calls `invalidate()` and retries once. Properties: `services.auth.base-url=${AUTH_SERVICE_URL:http://localhost:8086}`, `services.auth.client-id=<order-service|inventory-service>`, `services.auth.client-secret=${<ORDER|INVENTORY>_SERVICE_SECRET:<dev default>}`.

---

### Task 1: Customer service

**Files:**
- Create: `customer/` (copy of `product/` skeleton: `pom.xml` artifactId `customer`, `mvnw`, `mvnw.cmd`, `.mvn/`, `Dockerfile` with `EXPOSE 8085`, `.dockerignore`)
- Create: `customer/src/main/java/com/mc/oletrex/customer/CustomerApplication.java`
- Create: `.../customer/model/Customer.java`, `.../customer/model/Address.java` (`@Embeddable`)
- Create: `.../customer/dto/AddressDto.java`, `CustomerCreateRequest.java`, `CustomerUpdateRequest.java`, `CustomerResponse.java`, `CursorPage.java`
- Create: `.../customer/repository/CustomerRepository.java`
- Create: `.../customer/service/CustomerService.java`, `.../customer/service/impl/CustomerServiceImpl.java`, `.../service/impl/IdCursor.java`
- Create: `.../customer/controller/CustomerController.java`
- Create: `.../customer/exception/GlobalExceptionHandler.java`, `CustomerNotFoundException.java`, `CustomerAlreadyExistsException.java`, `InvalidCursorException.java`
- Create: `.../customer/security/*` (shared package), `.../customer/config/OpenApiConfig.java`
- Create: `customer/src/main/resources/application.properties`
- Modify: `docker/postgres/init.sql` (add `CREATE DATABASE customer_db;`)
- Modify: `docker-compose.yml` (service `customer`, container `oletrex-customer`, `${CUSTOMER_PORT:-8085}:8085`, `DB_URL: jdbc:postgresql://postgres:5432/customer_db`, `AUTH_JWKS_URL: http://auth:8086/.well-known/jwks.json`, limits `${CUSTOMER_CPUS:-0.5}`/`${CUSTOMER_MEMORY:-768M}`, healthcheck like product)
- Modify: `hoppscotch/oletrex-collection.json` (folder "Customer Service", variable `customerBaseUrl` = `http://localhost:8085`)
- Test: `customer/src/test/java/com/mc/oletrex/customer/controller/CustomerControllerTest.java`, `.../security/TestTokens.java`, `.../security/JwtDecoderTest.java`

**Interfaces:**
- Produces (HTTP, consumed by Task 3 and Task 9):
  - `POST /api/customers` (SERVICE) body `CustomerCreateRequest(Long authUserId, String name, String email, String phone, AddressDto deliveryAddress)` → 201 `CustomerResponse`, `Location: /api/customers/{id}`; 409 on duplicate email or authUserId.
  - `DELETE /api/customers/{id}` (SERVICE) → 204 / 404.
  - `GET /api/customers/me` (USER) → 200 `CustomerResponse`; `PUT /api/customers/me` (USER) body `CustomerUpdateRequest(String name, String phone, AddressDto deliveryAddress)` → 200.
  - `GET /api/customers?cursor=&limit=` (ADMIN, limit 1–100 default 20) → `CursorPage<CustomerResponse>`; `GET /api/customers/{id}` (ADMIN).
- `AddressDto(String line1, String line2, String city, String state, String postalCode, String country)` — `line1`, `city`, `postalCode`, `country` `@NotBlank`; `country` `@Pattern("^[A-Z]{2}$")`; max lengths 200/200/100/100/20/2.
- `CustomerResponse(Long id, Long authUserId, String name, String email, String phone, AddressDto deliveryAddress, Instant createdAt, Instant updatedAt)`.
- Validation: name `@NotBlank @Size(max=200)`, email `@NotBlank @Email`, phone `@NotBlank @Pattern("^\\+?[0-9 ()-]{7,20}$")`, `deliveryAddress @NotNull @Valid`.
- `CustomerService`: `CustomerResponse create(CustomerCreateRequest)`, `void delete(Long id)`, `CustomerResponse get(Long id)`, `CustomerResponse update(Long id, CustomerUpdateRequest)`, `CursorPage<CustomerResponse> list(String cursor, int limit)`.

Decision vs. spec: the spec lists an `active` field on `Customer`; nothing sets it (deactivation lives in auth's `enabled`), so it is **left out** (YAGNI). Mention this in the handoff.

Security rules for this service: `POST /api/customers` and `DELETE /api/customers/*` → `hasRole("SERVICE")`; `/api/customers/me` → `hasRole("USER")`; `GET /api/customers`, `GET /api/customers/*` → `hasRole("ADMIN")`. `/me` resolves the customer from `CurrentUser.customerId(jwt)`; empty → 403 via `AccessDeniedException`.

- [ ] **Step 1: Scaffold `customer/` from `product/`** (pom, wrapper, Dockerfile, application.properties with `server.port=${SERVER_PORT:8085}`, `customer_db` URL, the JWT properties from Global Constraints). Run `cd customer && ./mvnw -q -B compile` → BUILD SUCCESS.

- [ ] **Step 2: Write `CustomerControllerTest` (failing)** — tests and their assertions:
  - `createAsServiceReturns201` — `service()` POST valid body → 201, `Location` ends with `/api/customers/7`, `$.email == "alice@example.com"`.
  - `createAsUserIs403` — `user(42)` POST → 403, `Content-Type` `application/problem+json`, `$.title == "Forbidden"`.
  - `createWithoutTokenIs401` — no token → 401, `$.title == "Unauthorized"`.
  - `createInvalidBodyIs400` — `service()`, country `"usa"`, phone `"abc"` → 400.
  - `duplicateEmailIs409` — service throws `CustomerAlreadyExistsException` → 409.
  - `meReturnsOwnProfile` — `user(42)` GET `/me` → 200; verify `customerService.get(42L)`.
  - `meWithoutCustomerIdClaimIs403` — `admin()` and `noRoles()` with role USER but no `customerId` → 403; service never called.
  - `updateMeIgnoresEmail` — `user(42)` PUT with body containing `"email":"x@y.com"` → 200; verify `update(eq(42L), argThat(r -> true))` and the record type has no email component.
  - `listAsAdmin200AsUser403` / `getByIdAsAdmin200AsUser403`.
  - `deleteAsService204AsAdmin403`.
  - `healthAndSwaggerArePublic` — GET `/v3/api-docs` and `/actuator/health` without a token → status is **not** 401 (404 is expected in the MVC slice; the point is that security lets them through).

  `JwtDecoderTest` (plain unit test): builds an RSA key, a `NimbusJwtDecoder.withPublicKey(...)` configured with the **same** validator factory used in `SecurityConfig` (extract it as `static OAuth2TokenValidator<Jwt> validator(String issuer)`), and asserts: wrong issuer → `JwtValidationException`; expired → `JwtValidationException`; correct → decodes.

- [ ] **Step 3: Run** `cd customer && ./mvnw -q -B test -Dtest='CustomerControllerTest,JwtDecoderTest'` → FAIL (classes missing).

- [ ] **Step 4: Implement** the entity (`@Table(name="customers")`, unique constraints on `email` and `auth_user_id`, `@PrePersist/@PreUpdate` timestamps, email lower-cased+trimmed in service), repository (`existsByEmail`, `existsByAuthUserId`, `findByIdGreaterThanOrderByIdAsc(Long, Limit)`), service, controller (springdoc `@Operation`/`@ApiResponse` codes listed like `ProductController`), exception handler, security package, OpenApiConfig. Keep a `CustomerApplicationTests` context test like the other services (it needs a running DB, so it is not part of the `-Dtest` runs).

- [ ] **Step 5: Run** the same test command → PASS.

- [ ] **Step 6: Wire Docker + Hoppscotch** — init.sql, compose service, `.env.example` (`CUSTOMER_PORT`, `CUSTOMER_CPUS`, `CUSTOMER_MEMORY`), Hoppscotch folder with: Get my profile, Update my profile, List customers (admin), Get customer (admin), and negative cases (no token 401, user on list 403). Requests use Bearer `<<accessToken>>` / `<<adminToken>>` (variables created in Task 3). Run `docker compose config -q` → exit 0; `python3 -I -c "import json;json.load(open('hoppscotch/oletrex-collection.json'))"` → exit 0.

---

### Task 2: Auth — signing key, token issuing, JWKS, service tokens

**Files:**
- Create: `auth/` skeleton (copy of `product/`, artifactId `auth`, `EXPOSE 8086`), `AuthApplication.java`
- Create: `.../auth/model/SigningKey.java`, `.../auth/repository/SigningKeyRepository.java`
- Create: `.../auth/token/SigningKeys.java`, `.../auth/token/JwtIssuer.java`, `.../auth/token/AuthProperties.java`
- Create: `.../auth/controller/JwksController.java`, `.../auth/controller/ServiceTokenController.java`
- Create: `.../auth/dto/TokenResponse.java`, `ServiceTokenRequest.java`
- Create: `.../auth/exception/GlobalExceptionHandler.java`, `InvalidCredentialsException.java`
- Create: `auth/src/main/resources/application.properties`
- Test: `.../auth/token/JwtIssuerTest.java`, `.../auth/controller/ServiceTokenControllerTest.java`

**Interfaces:**
- `SigningKey` entity: `String kid` (id, UUID), `@Lob String publicKeyPem`, `@Lob String privateKeyPem`, `Instant createdAt`.
- `SigningKeys` (`@Component`): on startup loads the newest `SigningKey` or generates RSA 2048 and saves it. `RSAKey current()` (Nimbus, with kid) and `JWKSet publicJwkSet()`.
- `AuthProperties` (`@ConfigurationProperties("auth")`, record): `String issuer`, `Duration jwtTtl`, `Admin admin(String email, String password)`, `Map<String,String> serviceClients` (clientId → secret).
  Properties: `auth.issuer=${AUTH_ISSUER:oletrex-auth}`, `auth.jwt-ttl=${JWT_TTL:1h}`, `auth.admin.email=${ADMIN_EMAIL:admin@oletrex.local}`, `auth.admin.password=${ADMIN_PASSWORD:Admin123!}`, `auth.service-clients.order-service=${ORDER_SERVICE_SECRET:order-dev-secret}`, `auth.service-clients.inventory-service=${INVENTORY_SERVICE_SECRET:inventory-dev-secret}`.
- `JwtIssuer` (`@Component`, uses `NimbusJwtEncoder` from `spring-security-oauth2-jose`):
  - `TokenResponse issueUserToken(long userId, String email, Role role, Long customerId)` — claims per Global Constraints; omit `customerId` when null.
  - `TokenResponse issueServiceToken(String clientId)` — `sub=clientId`, `roles=["SERVICE"]`.
- `TokenResponse(String accessToken, String tokenType /* "Bearer" */, long expiresIn /* seconds */)`.
- HTTP: `GET /.well-known/jwks.json` → `publicJwkSet().toJSONObject()` (public only); `POST /api/auth/token` body `ServiceTokenRequest(@NotBlank String clientId, @NotBlank String clientSecret)` → 200 `TokenResponse`, wrong id/secret → 401 ProblemDetail `Invalid client credentials`. Secret comparison with `MessageDigest.isEqual`.
- `Role` enum `USER, ADMIN` lives in `.../auth/model/Role.java` (created here, used by Task 3).

- [ ] **Step 1: Scaffold `auth/`** (pom adds `spring-boot-starter-security-oauth2-resource-server` — brings Nimbus + `NimbusJwtEncoder` — and `spring-security-crypto` comes with it). `./mvnw -q -B compile` → BUILD SUCCESS.
- [ ] **Step 2: Write failing tests.**
  - `JwtIssuerTest.userTokenVerifiesWithPublishedKey` — issue for `(5, "a@b.c", USER, 42L)`; decode with `NimbusJwtDecoder.withJwkSetUri` replaced by `withPublicKey(signingKeys.current().toRSAPublicKey())`; assert `sub=="5"`, `roles==["USER"]`, `customerId==42`, `iss=="oletrex-auth"`, header `kid == current().getKeyID()`, `exp - iat == 3600`.
  - `JwtIssuerTest.adminTokenHasNoCustomerId` — `customerId` claim absent.
  - `JwtIssuerTest.serviceToken` — `sub=="order-service"`, `roles==["SERVICE"]`.
  - `JwtIssuerTest.jwksContainsNoPrivateParts` — JSON of `publicJwkSet()` has no `"d"`, `"p"`, `"q"` keys.
  - `ServiceTokenControllerTest.validClientGetsToken` (200, `$.tokenType=="Bearer"`), `wrongSecretIs401`, `unknownClientIs401`, `blankFieldsAre400`.
  (`SigningKeys` in unit tests: construct with a mocked repository returning empty → generates.)
- [ ] **Step 3: Run** `cd auth && ./mvnw -q -B test -Dtest='JwtIssuerTest,ServiceTokenControllerTest'` → FAIL.
- [ ] **Step 4: Implement** as per Interfaces. Security rules for auth (full set, used by Tasks 3–4 too): `permitAll` for `/.well-known/jwks.json`, `POST /api/auth/token`, `POST /api/auth/register`, `POST /api/auth/login`; `/api/auth/users/**` → `hasRole("ADMIN")`. Auth's own `JwtDecoder` uses `NimbusJwtDecoder.withPublicKey(signingKeys.current().toRSAPublicKey())` + the issuer validator (no HTTP call to itself).
- [ ] **Step 5: Run** the test command → PASS.

---

### Task 3: Auth — registration, login, first admin

**Files:**
- Create: `.../auth/model/UserAccount.java`, `.../auth/repository/UserAccountRepository.java`
- Create: `.../auth/client/CustomerClient.java`, `.../auth/client/CustomerServiceUnavailableException.java`
- Create: `.../auth/service/AccountService.java`, `.../auth/service/impl/AccountServiceImpl.java`, `.../auth/service/AdminBootstrap.java`
- Create: `.../auth/controller/AuthController.java`
- Create: `.../auth/dto/RegisterRequest.java`, `LoginRequest.java`, `AddressDto.java` (same shape/validation as Task 1)
- Create: `.../auth/exception/EmailAlreadyRegisteredException.java`
- Modify: `auth/src/main/resources/application.properties` (`services.customer.base-url=${CUSTOMER_SERVICE_URL:http://localhost:8085}`, timeouts as order)
- Modify: `docker/postgres/init.sql` (`CREATE DATABASE auth_db;`), `docker-compose.yml` (service `auth`, `oletrex-auth`, `${AUTH_PORT:-8086}:8086`, `DB_URL .../auth_db`, `CUSTOMER_SERVICE_URL: http://customer:8085`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ORDER_SERVICE_SECRET`, `INVENTORY_SERVICE_SECRET`, `JWT_TTL`, `AUTH_ISSUER` passed through from `.env` with defaults; limits `0.5`/`768M`; `depends_on` postgres healthy), `.env.example`
- Modify: `hoppscotch/oletrex-collection.json` (folder "Auth Service", variables `authBaseUrl` = `http://localhost:8086`, `accessToken`, `adminToken`)
- Test: `.../auth/service/AccountServiceImplTest.java`, `.../auth/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `JwtIssuer.issueUserToken(...)`, `Role`, `AuthProperties.admin()` (Task 2); Task 1's `POST /api/customers` and `DELETE /api/customers/{id}`.
- `UserAccount` (`@Table(name="user_accounts")`): `Long id`, `String email` (unique), `String passwordHash`, `Role role`, `boolean enabled`, `Long customerId` (nullable), `Instant createdAt`.
- `CustomerClient`: `long createCustomer(long authUserId, RegisterRequest r)` (returns new customer id from the response body) and `void deleteCustomer(long customerId)`; each request carries `Authorization: Bearer ` + `jwtIssuer.issueServiceToken("auth-service").accessToken()` (cache per token lifetime). Connection failure / 5xx → `CustomerServiceUnavailableException`; 409 → `EmailAlreadyRegisteredException`.
- `AccountService`: `TokenResponse register(RegisterRequest)`, `TokenResponse login(LoginRequest)`.
- `RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min=8, max=100) String password, name, phone, @NotNull @Valid AddressDto deliveryAddress)` (name/phone validation as Task 1). `LoginRequest(@NotBlank String email, @NotBlank String password)`.
- HTTP: `POST /api/auth/register` → 201 `TokenResponse`; 409 email taken; 503 customer service down; 400 invalid. `POST /api/auth/login` → 200 `TokenResponse`; 401 `Invalid email or password` for unknown email, wrong password **or disabled account** (same message).

Registration algorithm (`register`, the order matters for the anti-goal "no half-created account"):
```
email = normalise(r.email)
if repo.existsByEmail(email) -> EmailAlreadyRegisteredException
account = repo.saveAndFlush(new UserAccount(email, bcrypt(pw), USER, enabled=true, customerId=null))   // gets id
try   customerId = customerClient.createCustomer(account.id, r)
catch (any) { repo.delete(account); rethrow }                          // customer down / 409 -> no account left
try   account.customerId = customerId; repo.saveAndFlush(account)
catch (any) { customerClient.deleteCustomer(customerId); repo.delete(account); rethrow }
return jwtIssuer.issueUserToken(account.id, email, USER, customerId)
```
`register` is **not** `@Transactional` as a whole (remote call in between); each repository call commits on its own.

`AdminBootstrap` (`ApplicationRunner`): if no account with `auth.admin.email` exists, create it as `ADMIN`, enabled, `customerId=null`. Never overwrites an existing account's password.

- [ ] **Step 1: Write failing `AccountServiceImplTest`** (Mockito; real `BCryptPasswordEncoder`, mocked repo/client/issuer):
  - `registerNormalisesEmailAndCreatesCustomer` — input `" Alice@Example.COM "` → saved email `"alice@example.com"`, `createCustomer(id, …)` called, issuer called with `customerId=7`.
  - `registerDuplicateEmailIs409NoCustomerCall` — `existsByEmail` true → throws `EmailAlreadyRegisteredException`; `verifyNoInteractions(customerClient)`.
  - `customerServiceDownDeletesAccount` — `createCustomer` throws `CustomerServiceUnavailableException` → rethrown; `repo.delete(account)` called.
  - `secondSaveFailsCompensatesCustomer` — second `saveAndFlush` throws `DataIntegrityViolationException` → `deleteCustomer(7)` and `repo.delete(account)` called; exception rethrown.
  - `loginIsCaseInsensitive` — stored `alice@example.com`, login `"ALICE@example.com"` → token issued.
  - `loginWrongPasswordUnknownEmailOrDisabled` — three cases each → `InvalidCredentialsException`.
  - `adminBootstrapCreatesOnceOnly` — run twice with repo stateful fake → one save; existing account untouched.
- [ ] **Step 2: Write failing `AuthControllerTest`** — register 201/400/409/503 mapping; login 200/401 with `$.detail == "Invalid email or password"`; both reachable without a token.
- [ ] **Step 3: Run** `cd auth && ./mvnw -q -B test -Dtest='AccountServiceImplTest,AuthControllerTest'` → FAIL.
- [ ] **Step 4: Implement** per Interfaces. `CustomerServiceUnavailableException` → 503 ProblemDetail, `EmailAlreadyRegisteredException` → 409.
- [ ] **Step 5: Run** the test command → PASS; then `./mvnw -q -B test -Dtest='*Test'` (all auth unit tests) → PASS.
- [ ] **Step 6: Docker + Hoppscotch.** Compose service, init.sql, `.env.example` section "Auth" with every new variable and a note: on an existing volume run `docker exec -it oletrex-postgres psql -U oletrex -d postgres -c "CREATE DATABASE auth_db;" -c "CREATE DATABASE customer_db;"`. Add `customer` `depends_on: auth: condition: service_started`. Hoppscotch "Auth Service" folder: Register (test script `pw.env.set("accessToken", pw.response.body.accessToken)`), Login, Admin login (sets `adminToken`), Service token, JWKS, negatives (duplicate 409, bad password 401, short password 400). `docker compose config -q` → exit 0; JSON parses.

---

### Task 4: Auth — admin user management

**Files:**
- Create: `.../auth/controller/UserAdminController.java`, `.../auth/dto/UserResponse.java`, `RoleUpdateRequest.java`, `EnabledUpdateRequest.java`, `CursorPage.java`, `.../auth/service/impl/IdCursor.java`, `.../auth/exception/UserNotFoundException.java`, `InvalidCursorException.java`
- Modify: `AccountService` / `AccountServiceImpl`
- Modify: `hoppscotch/oletrex-collection.json` (Auth Service folder)
- Test: `.../auth/controller/UserAdminControllerTest.java`

**Interfaces:**
- `UserResponse(Long id, String email, Role role, boolean enabled, Long customerId, Instant createdAt)` (never the hash).
- `AccountService`: `CursorPage<UserResponse> listUsers(String cursor, int limit)`, `UserResponse updateRole(long id, Role role)`, `UserResponse updateEnabled(long id, boolean enabled)`.
- HTTP (ADMIN): `GET /api/auth/users?cursor=&limit=`, `PUT /api/auth/users/{id}/role` body `{"role":"ADMIN"}`, `PUT /api/auth/users/{id}/enabled` body `{"enabled":false}`.
- Guard: an admin cannot demote or disable **their own** account (`sub` == id) → 409 `You cannot change your own role or disable yourself` (prevents locking out the last admin).

- [ ] **Step 1: Write failing `UserAdminControllerTest`**: list as admin 200 / as user 403 / no token 401; role update 200 with `$.role=="ADMIN"`; invalid role `"ROOT"` → 400; unknown id → 404; self-demote (`admin()` with `sub="1"`, PUT `/users/1/role` USER) → 409; disable 200 with `$.enabled==false`; response never contains `passwordHash`.
- [ ] **Step 2: Run** `cd auth && ./mvnw -q -B test -Dtest=UserAdminControllerTest` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS; Hoppscotch requests (List users, Promote, Disable, Enable) added; JSON parses.

---

### Task 5: Product — resource server

**Files:**
- Modify: `product/pom.xml`, `product/src/main/resources/application.properties` (JWT properties)
- Create: `product/src/main/java/com/mc/oletrex/product/security/*` (shared package, no `CurrentUser`)
- Modify: `.../product/config/OpenApiConfig.java`
- Modify: `docker-compose.yml` (`AUTH_JWKS_URL` env on product; `depends_on: auth: condition: service_started`)
- Modify: `hoppscotch/oletrex-collection.json` (Product folder: Bearer auth on every request; writes use `<<adminToken>>`; add "Create product as user → 403" and "List without token → 401")
- Test: `product/src/test/java/com/mc/oletrex/product/controller/ProductSecurityTest.java`, `.../security/TestTokens.java`

Rules: `GET /api/products/**` → `hasAnyRole("USER","ADMIN","SERVICE")`; `POST/PUT/DELETE /api/products/**` → `hasRole("ADMIN")`.

- [ ] **Step 1: Write failing `ProductSecurityTest`** (`@WebMvcTest(ProductController.class)`, mocked `ProductService`): GET list/one as user, admin, service → 200; no token → 401 ProblemDetail; POST as admin → 201 (service mocked), as user → 403, as service → 403; PUT/DELETE as user → 403.
- [ ] **Step 2: Run** `cd product && ./mvnw -q -B test -Dtest=ProductSecurityTest` → FAIL.
- [ ] **Step 3: Implement** (pom, properties, security package, OpenApiConfig).
- [ ] **Step 4: Run** → PASS. Compose + Hoppscotch updated; `docker compose config -q` → 0; JSON parses.

---

### Task 6: Inventory — resource server + service token to product

**Files:**
- Modify: `inventory/pom.xml`, `inventory/src/main/resources/application.properties` (JWT properties + `services.auth.*` with `client-id=inventory-service`, secret `${INVENTORY_SERVICE_SECRET:inventory-dev-secret}`)
- Create: `.../inventory/security/*` (shared package, no `CurrentUser`)
- Create: `.../inventory/client/ServiceTokenProvider.java`, `.../inventory/client/ServiceTokenInterceptor.java`
- Modify: `.../inventory/client/ProductClient.java` (add the interceptor to its `RestClient`)
- Modify: `.../inventory/config/OpenApiConfig.java`
- Modify: `docker-compose.yml` (inventory env: `AUTH_JWKS_URL`, `AUTH_SERVICE_URL: http://auth:8086`, `INVENTORY_SERVICE_SECRET`; `depends_on` auth)
- Modify: `hoppscotch/oletrex-collection.json` (Inventory + Location folders use `<<adminToken>>`; Deductions request uses a service token variable `serviceToken` set by Auth folder's "Service token (order)" request; add "Deduction as admin → 403")
- Test: `.../inventory/controller/InventorySecurityTest.java`, `.../inventory/client/ServiceTokenProviderTest.java`, `.../security/TestTokens.java`

**Interfaces:**
- `ServiceTokenProvider(RestClient authApi, String clientId, String clientSecret, Clock clock)`: `String token()`, `void invalidate()`. Thread-safe (`synchronized` refresh). Expiry from `expiresIn` in the response, refresh when `now >= fetchedAt + expiresIn - 60s`.
- `ServiceTokenInterceptor(ServiceTokenProvider)` — per Shared section.

Rules: `POST /api/inventory/deductions` → `hasRole("SERVICE")`; every other `/api/inventory/**` and `/api/locations/**` → `hasRole("ADMIN")`.

- [ ] **Step 1: Write failing tests.**
  - `InventorySecurityTest`: upsert/list/get as admin → 2xx; as user → 403; deductions as service → 200, as admin → 403, as user → 403; locations GET/POST/PUT as admin 2xx, as service 403; no token 401.
  - `ServiceTokenProviderTest` (MockRestServiceServer on the auth `RestClient`, fixed/mutable `Clock`): first `token()` calls auth once; second call within lifetime makes no request; after advancing clock to `expiresIn - 59s` a new token is fetched; `invalidate()` forces a fetch; auth returns 401 → `token()` throws `DownstreamServiceException`.
  - `ServiceTokenInterceptorTest`: downstream 401 once → invalidate + one retry with the new token; 401 twice → second 401 returned (no loop).
- [ ] **Step 2: Run** `cd inventory && ./mvnw -q -B test -Dtest='InventorySecurityTest,ServiceTokenProviderTest,ServiceTokenInterceptorTest'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS. Compose + Hoppscotch updated; `docker compose config -q` → 0; JSON parses.

---

### Task 7: Order — ownership, addresses, service tokens, resource server

**Files:**
- Modify: `order/pom.xml`, `order/src/main/resources/application.properties` (JWT + `services.auth.*`, `client-id=order-service`, secret `${ORDER_SERVICE_SECRET:order-dev-secret}`)
- Create: `.../order/security/*` (shared package **with** `CurrentUser`)
- Create: `.../order/client/ServiceTokenProvider.java`, `ServiceTokenInterceptor.java` (copies of Task 6)
- Modify: `.../order/client/RestClients.java` (`create(baseUrl, connectTimeout, readTimeout, ClientHttpRequestInterceptor interceptor)`), `ProductClient.java`, `InventoryClient.java`
- Create: `.../order/model/OrderAddress.java` (`@Embeddable`: line1, line2, city, state, postalCode, country), `.../order/dto/AddressDto.java` (as Task 1), `.../order/service/Caller.java`
- Modify: `.../order/model/Order.java` (`@Column(nullable=false) Long customerId`; `shippingAddress`, `billingAddress` with `@AttributeOverrides` → columns `shipping_line1` … `billing_country`, shipping/billing `line1, city, postal_code, country` NOT NULL)
- Modify: `.../order/dto/OrderRequest.java` (+ `@NotNull @Valid AddressDto shippingAddress`, `@NotNull @Valid AddressDto billingAddress`), `OrderResponse.java` (+ `Long customerId`, `AddressDto shippingAddress`, `AddressDto billingAddress`)
- Modify: `.../order/repository/OrderRepository.java`, `.../order/service/OrderService.java`, `.../order/service/impl/OrderServiceImpl.java`, `.../order/controller/OrderController.java`, `.../order/config/OpenApiConfig.java`
- Modify: `docker-compose.yml` (order env: `AUTH_JWKS_URL`, `AUTH_SERVICE_URL`, `ORDER_SERVICE_SECRET`; `depends_on` auth), `.env.example` (one-off reset note: `docker exec -it oletrex-postgres psql -U oletrex -d order_db -c "DROP TABLE order_line_items, orders;"` before first start of the new order version — existing orders are test data without a customer)
- Modify: `hoppscotch/oletrex-collection.json` (Order folder: create uses `<<accessToken>>` and includes both addresses; list/get as user; list as admin; delete as admin; negatives: missing billingAddress 400, admin creates order 403, user deletes 403, other user's order 404)
- Test: `.../order/controller/OrderControllerSecurityTest.java`, `.../order/service/impl/OrderServiceImplOwnershipTest.java`, `.../security/TestTokens.java`

**Interfaces:**
- `Caller(Long customerId, boolean admin)` with `static Caller from(Authentication)` (uses `CurrentUser`).
- `OrderService`: `OrderPlacementResult createOrder(OrderRequest request, long customerId)`; `OrderResponse getOrder(String orderNumber, Caller caller)`; `CursorPage<OrderResponse> getOrders(String cursor, int limit, Caller caller)`; `void deleteOrder(String orderNumber)` (unchanged).
- `OrderRepository` adds: `Optional<Order> findByOrderNumberAndCustomerId(String orderNumber, Long customerId)`, `List<Order> findByCustomerIdAndIdGreaterThanOrderByIdAsc(Long customerId, Long afterId, Limit limit)`.
- Controller: `createOrder` takes `@AuthenticationPrincipal Jwt jwt`; `CurrentUser.customerId(jwt)` empty → 403. `getOrder`/`getOrders` build `Caller` — admin → unfiltered, otherwise filter by customerId; non-owner → `OrderNotFoundException` (404).
- Rules: `POST /api/orders` → `hasRole("USER")`; `GET /api/orders/**` → `hasAnyRole("USER","ADMIN")`; `DELETE /api/orders/**` → `hasRole("ADMIN")`.
- `OrderRequest` JSON:
  ```json
  {"shippingAddress":{"line1":"1 Main St","city":"Austin","state":"TX","postalCode":"73301","country":"US"},
   "billingAddress":{"line1":"1 Main St","city":"Austin","state":"TX","postalCode":"73301","country":"US"},
   "lineItems":[{"skuCode":"...","quantity":1}]}
  ```

- [ ] **Step 1: Write failing `OrderControllerSecurityTest`**: create as `user(42)` → 201 and `verify(orderService).createOrder(any(), eq(42L))`; create as admin → 403; create as USER without `customerId` → 403 (service not called); create without addresses → 400; get/list as user → service called with `Caller(42,false)`; as admin → `Caller(null,true)`; delete as admin 204, as user 403; no token 401.
- [ ] **Step 2: Write failing `OrderServiceImplOwnershipTest`** (Mockito repo + clients): `getOrder("ORD-1", new Caller(42L,false))` uses `findByOrderNumberAndCustomerId("ORD-1",42L)`, empty → `OrderNotFoundException`; admin uses `findByOrderNumber`; `getOrders` as user uses the customer-filtered repository method; `createOrder(req, 42L)` saves an `Order` with `customerId==42`, both addresses copied, and the response contains them.
- [ ] **Step 3: Run** `cd order && ./mvnw -q -B test -Dtest='OrderControllerSecurityTest,OrderServiceImplOwnershipTest,ServiceTokenProviderTest,ServiceTokenInterceptorTest'` (copy the two client tests from Task 6, adjusting the package) → FAIL.
- [ ] **Step 4: Implement.** The `Caller` filter must also apply to the cursor query: the page lookup for a user is `findByCustomerIdAndIdGreaterThanOrderByIdAsc`, followed by the existing `findAllWithLineItemsByIdIn` batch fetch.
- [ ] **Step 5: Run** → PASS. Compose, `.env.example` reset note, Hoppscotch updated; `docker compose config -q` → 0; JSON parses.

---

### Task 8: Generate-master-data — admin token, protected endpoints, 300 customers

**Files:**
- Modify: `generate-master-data/pom.xml` (resource-server starter; security-test), `application.properties` (JWT properties; `services.auth.base-url=${AUTH_SERVICE_URL:http://localhost:8086}`, `services.customer.base-url=${CUSTOMER_SERVICE_URL:http://localhost:8085}`, `masterdata.admin.email=${ADMIN_EMAIL:admin@oletrex.local}`, `masterdata.admin.password=${ADMIN_PASSWORD:Admin123!}`, `masterdata.customer-password=${MASTERDATA_CUSTOMER_PASSWORD:Customer123!}`)
- Create: `.../masterdata/security/*` (shared package)
- Create: `.../masterdata/client/AdminTokenProvider.java` (same caching rules as `ServiceTokenProvider`, but `POST /api/auth/login` with admin email/password), `.../masterdata/client/BearerInterceptor.java`
- Modify: `.../masterdata/client/ServicesClient.java` (product + inventory `RestClient`s use the admin token; new `authApi`, `customerApi`; `checkServicesReachable()` also pings auth `/.well-known/jwks.json` and customer `/actuator/health`; new `Outcome saveCustomer(CustomerRow row, String password)`), `Payloads.java` (+ `Register`, `Address`, `CustomerUpdate`, `Login`)
- Create: `.../masterdata/csv/CustomerRow.java` (`email, name, phone, line1, line2, city, state, postalCode, country`)
- Modify: `.../masterdata/csv/MasterData.java` (+ `List<CustomerRow> customers`), `MasterDataCsvReader.java` (+ `customers.csv`, `toCustomer`)
- Modify: `.../masterdata/dto/MasterDataSummary.java` (+ `int customers`), `PopulateReport.java` (+ `StepReport customers`), `.../masterdata/service/MasterDataService.java` (customers step last, key = email)
- Create: `generate-master-data/src/main/resources/master-data/customers.csv` (300 rows)
- Modify: `.../masterdata/config/OpenApiConfig.java`; `docker-compose.yml` (master-data env `AUTH_SERVICE_URL`, `CUSTOMER_SERVICE_URL`, `AUTH_JWKS_URL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `MASTERDATA_CUSTOMER_PASSWORD`; `depends_on` auth + customer healthy); `.env.example`
- Modify: `hoppscotch/oletrex-collection.json` (Master Data folder uses `<<adminToken>>`; add "Customer login (seeded)" with `customer001@example.com` / `Customer123!` setting `accessToken`)
- Test: `.../masterdata/csv/CustomerCsvTest.java`, `.../masterdata/client/SaveCustomerTest.java`, `.../masterdata/controller/MasterDataSecurityTest.java`

**Interfaces:**
- `saveCustomer` algorithm:
  ```
  POST /api/auth/register {email, password, name, phone, deliveryAddress}   -> 201 => CREATED
  on 409: POST /api/auth/login {email, password} -> token   (401 => ApiCallException "seed password no longer works")
          PUT /api/customers/me (Bearer token) {name, phone, deliveryAddress} -> UPDATED
  ```
  Register/login/PUT do **not** send the admin token.
- Rules: `/api/master-data/**` → `hasRole("ADMIN")`.
- `customers.csv` header exactly `email,name,phone,line1,line2,city,state,postalCode,country`. Generated once by a throwaway script in the scratchpad (not committed): `random.seed(300)`, emails `customer001@example.com` … `customer300@example.com`, fictional first/last name pools, phone `+1 555-01xx-style` (`+1 555 0` + 3 digits, 555 range is fictional), US cities with matching state codes and 5-digit ZIPs, `country=US`, ~30 % with a `line2` (`Apt N`).

- [ ] **Step 1: Generate `customers.csv`** with the script; check `wc -l` → 301 and `cut -d, -f1 | sort | uniq -d` → empty.
- [ ] **Step 2: Write failing tests.**
  - `CustomerCsvTest`: reader loads 300 customers from the bundled CSVs; every row has a non-blank email ending `@example.com`, `country=="US"`, `postalCode` matches `\d{5}`.
  - `SaveCustomerTest` (MockRestServiceServer on auth + customer APIs): 201 → `CREATED`; 409 then login 200 then PUT 200 → `UPDATED` and the PUT carried `Bearer <login token>`; 409 then login 401 → `ApiCallException`; register request has no `Authorization` header.
  - `MasterDataSecurityTest`: summary/populate as admin → 200/202-or-200 (whatever the controller returns today), as user → 403, no token → 401.
- [ ] **Step 3: Run** `cd generate-master-data && ./mvnw -q -B test -Dtest='CustomerCsvTest,SaveCustomerTest,MasterDataSecurityTest'` → FAIL.
- [ ] **Step 4: Implement.** Populate order: locations → products → inventory → customers. `success` in `PopulateReport` is false if any customer failed.
- [ ] **Step 5: Run** → PASS. Compose, `.env.example`, Hoppscotch updated; `docker compose config -q` → 0; JSON parses.

---

### Task 9: End-to-end verification on a throwaway stack

**Files:**
- Create (scratchpad only, not committed): `e2e-override.yml` (project `-p authtest`, container names `authtest-*`, host ports postgres 15432, product 28082, inventory 28083, order 28081, master-data 28084, customer 28085, auth 28086), `e2e_check.py`

- [ ] **Step 1: Build and start** `docker compose -p authtest -f docker-compose.yml -f <scratchpad>/e2e-override.yml --profile seed up -d --build`. Expected: all services `healthy`; master-data log shows populate finished with customers `created=300 failed=0`.
- [ ] **Step 2: Run `e2e_check.py`** (stdlib `urllib`, against the 2808x ports). It asserts, printing PASS/FAIL per line:
  - no token → 401 on product list, order list, inventory list, customers/me.
  - `customer001@example.com` / `Customer123!` login 200; JWKS has one RSA key without `d`.
  - user: product list 200; inventory list 403; product POST 403; `/api/customers/me` 200 with email `customer001@example.com`; place order with shipping + billing → 201 `CONFIRMED` with `customerId` and both addresses; deductions direct call 403.
  - customer002 cannot GET customer001's order (404) and its order list doesn't contain it.
  - admin login 200; admin sees both customers' orders; admin POST order → 403; admin deletes an order 204; user delete → 403.
  - register new user with `" New.User@Example.com "` → 201; login `new.user@example.com` → 200; register again → 409.
  - stop customer container → register → 503; `GET /api/auth/users` (admin) shows no account for that email; start customer again.
  - admin disables a user → that user's login 401; enable → 200; admin self-demote → 409.
  - token with tampered payload → 401.
  - master-data populate re-run → customers `updated=300 failed=0`.
- [ ] **Step 3: Tear down** `docker compose -p authtest down -v` (only `authtest_*` volumes). Confirm `docker ps` still shows the user's `oletrex-postgres` and `docker exec oletrex-postgres psql -U oletrex -d product_db -tc "select count(*) from products"` → unchanged (2008).
- [ ] **Step 4: Report** results to the user with any failures verbatim; list the one-off steps their existing local DB needs (create `customer_db`/`auth_db`, drop old order tables). Do not commit.
