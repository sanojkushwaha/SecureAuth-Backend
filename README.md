# SecureAuth Backend

A secure, RESTful authentication API built with Spring Boot. The service provides account registration, email verification, JWT authentication with refresh tokens, password recovery, account-lockout protection, role-based access control, and OpenAPI documentation.

## Features

- Register users with server-side validation and BCrypt password hashing
- Verify accounts with a 24-hour email-verification token
- Authenticate users with short-lived access tokens and refresh tokens
- Invalidate server-side refresh tokens on logout and password reset
- Lock accounts for 15 minutes after five failed login attempts
- Protect authenticated user and admin routes with Spring Security
- Support role-based authorization with `USER` and `ADMIN` roles
- Send password-reset emails with one-hour reset tokens
- Prevent account enumeration during password-reset requests
- Return centralized, structured API error responses
- Explore and test APIs using Swagger UI

## Tech stack

| Area | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.3 |
| Web | Spring MVC |
| Security | Spring Security, JJWT, BCrypt |
| Persistence | Spring Data JPA, Hibernate, MySQL |
| Email | Spring Mail / SMTP |
| API documentation | Springdoc OpenAPI / Swagger UI |
| Build tool | Maven |

## Prerequisites

- JDK 17 or later
- Maven 3.9 or later
- MySQL 8 or later
- An SMTP account for email verification and password reset messages

## Quick start

### 1. Create the database

```sql
CREATE DATABASE secureauth_db;
```

### 2. Configure environment variables

Copy `.env.example` and replace its placeholder values. Do not commit actual credentials or secrets.

```env
DB_URL=jdbc:mysql://localhost:3306/secureauth_db
DB_USERNAME=root
DB_PASSWORD=your_mysql_password

JWT_SECRET=replace-with-a-long-random-secret-key-in-production
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=no-reply@secureauth.com

FRONTEND_URL=http://localhost:5180
PORT=8080
```

For Gmail, use an app password rather than your account password. `FRONTEND_URL` must point to the frontend that handles `/verify-email` and `/reset-password` links.

### 3. Run the service

```bash
mvn spring-boot:run
```

The API starts at `http://localhost:8080`.

## Swagger UI

After starting the service, open the interactive API documentation:

```text
http://localhost:8080/swagger-ui.html
```

The raw OpenAPI document is available at:

```text
http://localhost:8080/v3/api-docs
```

Use Swagger's **Authorize** button to supply an access token as `Bearer <access-token>` for protected endpoints.

## API reference

| Method | Endpoint | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Public | Register an account and create a verification token. |
| `GET` | `/api/auth/verify-email?token=...` | Public | Verify the registered email address. |
| `POST` | `/api/auth/login` | Public | Authenticate and receive access and refresh tokens. |
| `POST` | `/api/auth/refresh` | Authenticated | Exchange a valid refresh token for a new access token. |
| `POST` | `/api/auth/logout` | Authenticated | Revoke the user's stored refresh token. |
| `POST` | `/api/auth/forgot-password` | Public | Request a password-reset email. |
| `POST` | `/api/auth/reset-password` | Public | Reset a password with a valid reset token. |
| `GET` | `/api/user/me` | Authenticated | Retrieve the current user's profile. |
| `GET` | `/api/admin/users` | Admin | Retrieve all user profiles. |

### Register

```http
POST /api/auth/register
Content-Type: application/json

{
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "password": "SecurePass@123"
}
```

Passwords must contain at least eight characters, including an uppercase letter, lowercase letter, number, and special character.

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "jane@example.com",
  "password": "SecurePass@123"
}
```

The response includes an access token, refresh token, user ID, name, email, and role.

### Authenticated request

```http
GET /api/user/me
Authorization: Bearer <access-token>
```

## Authentication lifecycle

1. The user registers; the backend creates an email-verification token valid for 24 hours.
2. The user verifies their email before signing in.
3. Successful sign-in returns a 15-minute access token and a seven-day refresh token by default.
4. The client sends the access token in the `Authorization` header.
5. When the access token expires, the client submits the refresh token to `/api/auth/refresh`.
6. Logout and password reset clear the stored refresh token, ending active sessions.

## Project structure

```text
src/main/java/com/secureauth/
├── config/       # Spring Security and OpenAPI configuration
├── controller/   # Authentication, user, and admin endpoints
├── dto/          # Validated request and response models
├── entity/       # User and role persistence models
├── exception/    # Global error handling
├── repository/   # JPA repositories
├── security/     # JWT utilities, filter, and user-details service
└── service/      # Authentication, email, and user business logic
```

## Build and test

Build the application:

```bash
mvn clean package
```

Run tests:

```bash
mvn test
```

The packaged JAR is generated in `target/`.

## Production checklist

- Supply all secrets through environment variables or a secret manager.
- Use a cryptographically strong `JWT_SECRET` of at least 256 bits.
- Set `FRONTEND_URL` to the deployed frontend origin.
- Restrict the CORS origin allowlist in `SecurityConfig` to trusted deployments.
- Use a managed mail provider and production database credentials.
- Replace `spring.jpa.hibernate.ddl-auto=update` with database migrations before production deployment.
- Deploy behind HTTPS and set appropriate logging levels.
