# Business Parity Inventory — Core Application Modules

## 1. Core Module Summary & Status Mapping

| Module Name | PHP Route Pattern | PHP Controller | Inertia Component | Priority | Target Phase | Status |
|---|---|---|---|---|---|---|
| **Dashboard (Client)** | `GET /app/dashboard` | `Client\DashboardController` | `Client/Dashboard` | P0 | Phase 6 | In Progress |
| **Dashboard (Admin)** | `GET /admin/dashboard` | `Admin\DashboardController` | `Admin/Dashboard` | P0 | Phase 6 | In Progress |
| **Profile / Account** | `GET /profile`, `PUT /profile`, `PUT /password` | `ProfileController` | `Profile/Edit` | P0 | Phase 6 | In Progress |
| **Workspace Core** | `GET /workspaces`, `POST /workspaces`, `POST /workspaces/{id}/switch` | `WorkspaceController` | `Workspaces/Index` | P0 | Phase 6 | In Progress |
| **Workspace Members** | `GET /workspaces/team`, `POST /workspaces/team/invite` | `Client\TeamController` | `Team/Index` | P0 | Phase 6 | In Progress |
| **Client Management** | `GET /admin/clients`, `POST /admin/clients`, `PUT /admin/clients/{id}` | `Admin\ClientController` | `Admin/Clients/Index` | P0 | Phase 6 | In Progress |
| **Admin Users** | `GET /admin/admins`, `POST /admin/admins`, `PUT /admin/admins/{id}` | `Admin\AdminUserController` | `Admin/Admins/Index` | P0 | Phase 6 | In Progress |
| **Roles & Permissions**| `GET /admin/roles-permissions`, `POST /admin/roles` | `Admin\RolesPermissionsController` | `Admin/RolesPermissions/Index` | P0 | Phase 6 | In Progress |
| **System Settings** | `GET /admin/settings`, `PUT /admin/settings` | `Admin\SystemSettingsController` | `Admin/Settings/Index` | P0 | Phase 6 | In Progress |

---

## 2. Detailed Endpoint Inventory

### A. Dashboard Module
- **PHP**: `Client\DashboardController@index`
  - Route: `GET /app/dashboard`
  - Middleware: `auth:web`
  - Tables: `workspaces`, `users`, `clients`, `client_subscriptions`
  - Inertia Component: `Client/Dashboard`
  - Java Target: `ClientDashboardController.java` (`com.whatsmine.controller.client`)

- **PHP**: `Admin\DashboardController@index`
  - Route: `GET /admin/dashboard`
  - Middleware: `auth:admin`
  - Tables: `admin_users`, `clients`, `users`, `client_subscriptions`
  - Inertia Component: `Admin/Dashboard`
  - Java Target: `AdminDashboardController.java` (`com.whatsmine.controller.admin`)

### B. Profile Module
- **PHP**: `ProfileController@edit`
  - Route: `GET /profile`
  - Middleware: `auth:web`
  - Inertia Component: `Profile/Edit`
  - Java Target: `ProfileController.java` (`com.whatsmine.controller.client`)

- **PHP**: `ProfileController@update`
  - Route: `PUT /profile`
  - Validation: `name` (required, string, max:255), `email` (required, email, unique:users,email,{id})
  - Database Effect: Updates `users` record
  - Flash: `success -> Profile updated successfully.`

- **PHP**: `PasswordController@update`
  - Route: `PUT /password`
  - Validation: `current_password` (required, current_password), `password` (required, min:8, confirmed)
  - Database Effect: Replaces `password` hash in `users` record
  - Flash: `success -> Password updated successfully.`

### C. Workspace Module
- **PHP**: `WorkspaceController@index`
  - Route: `GET /workspaces`
  - Middleware: `auth:web`
  - Inertia Component: `Workspaces/Index`
  - Java Target: `WorkspaceController.java` (`com.whatsmine.controller.client`)

- **PHP**: `WorkspaceController@store`
  - Route: `POST /workspaces`
  - Validation: `name` (required, string, max:255)
  - Database Effect: Inserts `workspaces` and `workspace_user`
  - Flash: `success -> Workspace created successfully.`

- **PHP**: `WorkspaceController@switch`
  - Route: `POST /workspaces/{workspace}/switch`
  - Authorization: Validates user membership in `workspace_user`
  - Session Effect: Updates `workspace_id` in session and `users.workspace_id`
  - Flash: `success -> Switched workspace.`

### D. Team & Workspace Members
- **PHP**: `Client\TeamController@index`
  - Route: `GET /app/team`
  - Middleware: `auth:web`
  - Tables: `workspace_user`, `users`
  - Inertia Component: `Team/Index`
  - Java Target: `TeamController.java` (`com.whatsmine.controller.client`)

- **PHP**: `Client\TeamController@store`
  - Route: `POST /app/team/members`
  - Validation: `email` (required, email), `role` (required, in:owner,admin,member)
  - Database Effect: Inserts `workspace_user`
  - Flash: `success -> Team member added.`

### E. Client Management (Admin)
- **PHP**: `Admin\ClientController@index`
  - Route: `GET /admin/clients`
  - Middleware: `auth:admin`, `permission:view_clients`
  - Tables: `clients`, `client_subscriptions`, `plans`
  - Inertia Component: `Admin/Clients/Index`
  - Java Target: `AdminClientController.java` (`com.whatsmine.controller.admin`)

- **PHP**: `Admin\ClientController@store`
  - Route: `POST /admin/clients`
  - Validation: `name` (required, string, max:255), `email` (nullable, email)
  - Database Effect: Inserts `clients`
  - Flash: `success -> Client created.`

### F. Roles & Permissions (Admin)
- **PHP**: `Admin\RolesPermissionsController@index`
  - Route: `GET /admin/roles-permissions`
  - Middleware: `auth:admin`, `permission:view_admin_roles`
  - Tables: `roles`, `permissions`
  - Inertia Component: `Admin/RolesPermissions/Index`
  - Java Target: `RolesPermissionsController.java` (`com.whatsmine.controller.admin`)

- **PHP**: `Admin\RoleController@store`
  - Route: `POST /admin/roles`
  - Validation: `name` (required), `key` (required, unique:roles,key)
  - Database Effect: Inserts `roles`
