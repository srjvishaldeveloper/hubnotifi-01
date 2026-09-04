# WhatsApp Integration Inventory — Phase 7 Migration

## 1. Overview & Provider Identification
- **Provider**: Meta WhatsApp Cloud API (Graph API `v18.0` / `v19.0`) & Meta Embedded Signup.
- **Base Endpoint**: `https://graph.facebook.com/v18.0/`

---

## 2. Complete Laravel Inventory

### A. Routes & Controller Endpoints
| HTTP Method | Route URL | Laravel Controller Method | Middleware | Description |
|---|---|---|---|---|
| GET | `/webhooks/whatsapp/global` | `WhatsappWebhookController@verifyGlobal` | None (Public) | Global Meta webhook challenge verification |
| POST | `/webhooks/whatsapp/global` | `WhatsappWebhookController@receiveGlobal` | None (Public) | Global Meta webhook inbound message/status callback |
| GET | `/webhooks/whatsapp/{token}` | `WhatsappWebhookController@verify` | None (Public) | Per-WABA token webhook challenge verification |
| POST | `/webhooks/whatsapp/{token}` | `WhatsappWebhookController@receive` | None (Public) | Per-WABA token webhook inbound message/status callback |
| GET | `/app/whatsapp/templates` | `WhatsappTemplateController@index` | `auth:web`, `client-app` | Render `Client/WhatsApp/Templates` Inertia page |
| POST | `/app/whatsapp/templates` | `WhatsappTemplateController@store` | `auth:web`, `client-app` | Create WhatsApp template via Meta Graph API |
| POST | `/app/whatsapp/templates/sync` | `WhatsappTemplateController@sync` | `auth:web`, `client-app` | Sync templates from Meta Graph API into database |
| DELETE | `/app/whatsapp/templates/{id}` | `WhatsappTemplateController@destroy` | `auth:web`, `client-app` | Delete template from Meta & database |
| POST | `/app/whatsapp/setup/embedded-signup` | `WhatsappEmbeddedSignupController@store` | `auth:web`, `client-app` | Complete Embedded Signup WABA connection |
| DELETE | `/app/whatsapp/setup/{waba}` | `WhatsappSetupController@destroy` | `auth:web`, `client-app` | Disconnect WhatsApp account |
| POST | `/app/whatsapp/setup/{waba}/sync-phone-numbers` | `WhatsappSetupController@syncPhoneNumbers` | `auth:web`, `client-app` | Sync phone numbers from Meta |
| GET | `/widgets/whatsapp/{key}.js` | `WhatsappWidgetController@embed` | None (Public) | JS Widget Embed code generator |

---

### B. Database Tables
1. `whatsapp_business_accounts` (WABA credentials, `waba_id`, `webhook_verify_token`, status)
2. `whatsapp_phone_numbers` (phone number details, quality rating, display phone, `phone_number_id`)
3. `whatsapp_templates` (name, language, category, status, components JSON, `meta_template_id`)
4. `whatsapp_template_submissions` (submission log history)
5. `whatsapp_auto_replies` (auto-reply keywords & payload JSON)
6. `whatsapp_widgets` (floating website widget configuration)

---

### C. Jobs & Events
- **Job**: `ProcessInboundMessageJob` (asynchronously processes inbound messages, updates message statuses `sent` → `delivered` → `read` → `failed`, creates/links contacts and conversations).
- **Driver Service**: `App\Modules\Whatsapp\Services\WhatsappDriver` (implements outbound message dispatch via Meta Cloud API).

---

## 3. Java Target Class Architecture
- **Entities**:
  - [`WhatsappBusinessAccount.java`](file:///d:/hubnotification/java-backend/src/main/java/com/whatsmine/model/WhatsappBusinessAccount.java)
  - [`WhatsappPhoneNumber.java`](file:///d:/hubnotification/java-backend/src/main/java/com/whatsmine/model/WhatsappPhoneNumber.java)
  - [`WhatsappTemplate.java`](file:///d:/hubnotification/java-backend/src/main/java/com/whatsmine/model/WhatsappTemplate.java)
  - [`WhatsappAutoReply.java`](file:///d:/hubnotification/java-backend/src/main/java/com/whatsmine/model/WhatsappAutoReply.java)
  - [`WhatsappWidget.java`](file:///d:/hubnotification/java-backend/src/main/java/com/whatsmine/model/WhatsappWidget.java)
- **Repositories**:
  - `WhatsappBusinessAccountRepository.java`
  - `WhatsappPhoneNumberRepository.java`
  - `WhatsappTemplateRepository.java`
- **API Client & Services**:
  - `WhatsAppApiClient.java` (Meta Cloud API HTTP Client)
  - `WhatsAppService.java` (Business logic for messaging, templates, and setup)
- **Controllers**:
  - `WhatsAppWebhookController.java` (`/webhooks/whatsapp/**`)
  - `WhatsAppTemplateController.java` (`/app/whatsapp/templates/**`)
  - `WhatsAppSetupController.java` (`/app/whatsapp/setup/**`)
