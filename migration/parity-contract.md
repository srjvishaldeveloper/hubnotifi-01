# PHP ↔ Java Behavioral Parity Contract

This contract defines the mandatory 1:1 equivalency requirements between the existing Laravel implementation and the Java Spring Boot backend.

---

## 1. Core Endpoints Parity Matrix

### Endpoint: `GET /app/dashboard`
- **PHP Request**: GET `/app/dashboard` (Header: `X-Inertia: true`, Cookie: `JSESSIONID` / `laravel_session`)
- **Java Request**: GET `/app/dashboard` (Header: `X-Inertia: true`, Cookie: `JSESSIONID`)
- **PHP Response**: Status `200 OK`, `Content-Type: application/json`, `X-Inertia: true`
- **Java Response**: Status `200 OK`, `Content-Type: application/json`, `X-Inertia: true`
- **Inertia Component**: `Client/Dashboard`
- **Inertia Props**: `{ "title": "Workspace Dashboard", "unreadCount": 5, "auth": { "user": { "id": 1, "name": "...", "workspace_id": 1 } }, "branding": { ... }, "flash": {} }`
- **Authorization**: `auth:web` / `hasRole('CLIENT')`

---

### Endpoint: `PUT /profile`
- **PHP Request**: PUT `/profile` (Payload: `{ "name": "New Name", "email": "user@example.com" }`)
- **Java Request**: PUT `/profile` (Payload: `{ "name": "New Name", "email": "user@example.com" }`)
- **PHP Status**: `303 See Other` (Location: `/profile`)
- **Java Status**: `303 See Other` (Location: `/profile`)
- **Database Effect**: Updates `name` and `email` columns in `users` table for authenticated `user.id`.
- **Flash Data**: `{ "success": "Profile updated successfully." }`

---

### Endpoint: `POST /workspaces`
- **PHP Request**: POST `/workspaces` (Payload: `{ "name": "Acme Marketing" }`)
- **Java Request**: POST `/workspaces` (Payload: `{ "name": "Acme Marketing" }`)
- **PHP Status**: `303 See Other` (Location: `/app/dashboard`)
- **Java Status**: `303 See Other` (Location: `/app/dashboard`)
- **Database Effect**: Inserts `workspaces` row (`name`, `owner_id`, `client_id`) and inserts `workspace_user` pivot row (`workspace_id`, `user_id`, `role='owner'`).
- **Flash Data**: `{ "success": "Workspace created successfully." }`

---

### Endpoint: `POST /workspaces/{id}/switch`
- **PHP Request**: POST `/workspaces/1/switch`
- **Java Request**: POST `/workspaces/1/switch`
- **PHP Status**: `303 See Other` (Location: `/app/dashboard`)
- **Java Status**: `303 See Other` (Location: `/app/dashboard`)
- **Authorization**: Validates `user_id` belongs to `workspace_id` in `workspace_user`. If invalid, returns `403 Forbidden`.
- **Database Effect**: Updates `users.workspace_id`.
- **Session Effect**: Sets active workspace context in session.

---

### Endpoint: `GET /admin/clients`
- **PHP Request**: GET `/admin/clients` (Header: `X-Inertia: true`)
- **Java Request**: GET `/admin/clients` (Header: `X-Inertia: true`)
- **PHP Status**: `200 OK`
- **Java Status**: `200 OK`
- **Inertia Component**: `Admin/Clients/Index`
- **Authorization**: Requires `ROLE_ADMIN` and `permission:view_clients`.

---

## 2. Validation Parity Specification
- **Email Uniqueness**: Case-insensitive lookup against `users.email` and `admin_users.email`.
- **Validation Failure Format**: HTTP `422 Unprocessable Entity` returning:
```json
{
  "message": "The given data was invalid.",
  "errors": {
    "field_name": [ "Validation error message." ]
  }
}
```
- **Inertia Validation Errors**: Passed via shared Inertia props `errors` key on 303 redirect back.
