# Automated Parity Testing Framework

This framework validates that Java Spring Boot responses match Laravel PHP responses for identical requests.

---

## 1. Parity Test Execution Protocol

For each test case:
1. **Request Execution**: Sends an HTTP request with identical URL, headers (`X-Inertia`, `X-Workspace-Id`, `Authorization`), cookies, and JSON/Form payload.
2. **Response Capture**:
   - HTTP Status Code
   - Content-Type Header
   - `X-Inertia` Header
   - `Location` Header (on 303 Redirects)
   - `X-Inertia-Location` Header (on 409 Conflicts)
   - Inertia Component Name
   - Inertia Props Structure
3. **Database Mutation Assertion**: Verifies that database state changes (rows created, updated, or soft-deleted) match expected outcomes.

---

## 2. Normalization Rules
Dynamic values normalized during assertion comparison:
- `timestamp` / `created_at` / `updated_at`
- Auto-incremented `id` fields
- Randomly generated CSRF tokens
- Password hashes (`$2y$12$...`)

---

## 3. Automated Parity Test Suite Coverage

- [`ParityIntegrationTest.java`](file:///d:/hubnotification/java-backend/src/test/java/com/whatsmine/parity/ParityIntegrationTest.java):
  1. Client Dashboard (`GET /app/dashboard`) component & props contract
  2. Admin Dashboard (`GET /admin/dashboard`) component & props contract
  3. Profile edit (`GET /profile`) component contract
  4. Profile update (`PUT /profile`) mutation & flash message parity
  5. Password update (`PUT /password`) BCrypt hash verification parity
  6. Workspace list (`GET /workspaces`) component contract
  7. Workspace creation (`POST /workspaces`) database insertion & redirect parity
  8. Workspace switching (`POST /workspaces/{id}/switch`) session & security update parity
  9. Team member list (`GET /app/team`) component & props parity
  10. Admin client list (`GET /admin/clients`) component & props parity
  11. Admin client creation (`POST /admin/clients`) client database insertion parity
  12. Admin role list (`GET /admin/roles-permissions`) component contract
  13. Admin role creation (`POST /admin/roles`) database insertion parity
  14. Admin settings (`GET /admin/settings`) component & system settings parity
