# Phase 9 — Broadcasting / Campaigns Complete Inventory

## Overview
This document contains the complete inventory of all Broadcasting, Campaigns, Recipient Management, Personalization, Audience Filtering, and Channel Dispatching components in the existing Laravel codebase (`App\Modules\Broadcasting`).

---

## 1. Routes & URL Structure
All routes use prefix `/app/broadcasts` and name prefix `client.`:

| Method | Endpoint | Name | Controller Action | Inertia Component / Output |
|---|---|---|---|---|
| `GET` | `/app/broadcasts/campaigns` | `client.campaigns.index` | `CampaignController@index` | `Broadcasting/Campaigns/Index` |
| `GET` | `/app/broadcasts/campaigns/create` | `client.campaigns.create` | `CampaignController@create` | `Broadcasting/Campaigns/Wizard` |
| `POST` | `/app/broadcasts/campaigns` | `client.campaigns.store` | `CampaignController@store` | Redirect → `client.campaigns.show` |
| `POST` | `/app/broadcasts/campaigns/draft` | `client.campaigns.store-draft` | `CampaignController@storeDraft` | JSON `{ "uuid": "..." }` |
| `POST` | `/app/broadcasts/campaigns/audience-preview` | `client.campaigns.audience-preview` | `CampaignController@audiencePreview` | JSON `{ "matched": X, "deliverable": Y, "sample": [...] }` |
| `GET` | `/app/broadcasts/campaigns/{campaign}` | `client.campaigns.show` | `CampaignController@show` | `Broadcasting/Campaigns/Show` |
| `GET` | `/app/broadcasts/campaigns/{campaign}/edit` | `client.campaigns.edit` | `CampaignController@edit` | `Broadcasting/Campaigns/Edit` |
| `PATCH` | `/app/broadcasts/campaigns/{campaign}` | `client.campaigns.update` | `CampaignController@update` | Redirect → `client.campaigns.show` |
| `POST` | `/app/broadcasts/campaigns/{campaign}/test-send` | `client.campaigns.test-send` | `CampaignController@testSend` | JSON `{ "ok": true, "message_id": "..." }` |
| `POST` | `/app/broadcasts/campaigns/{campaign}/launch` | `client.campaigns.launch` | `CampaignController@launch` | Redirect back (`with('success')`) |
| `POST` | `/app/broadcasts/campaigns/{campaign}/pause` | `client.campaigns.pause` | `CampaignController@pause` | Redirect back (`with('success')`) |
| `DELETE` | `/app/broadcasts/campaigns/{campaign}` | `client.campaigns.destroy` | `CampaignController@destroy` | Redirect → `client.campaigns.index` |

---

## 2. Eloquent Models
1. **`Campaign.php`** (`campaigns` table):
   - Attributes: `id`, `uuid`, `workspace_id`, `name`, `channel`, `whatsapp_phone_number_id`, `audience_type`, `audience_ref`, `template_ref` (JSON), `payload_json` (JSON), `schedule_at` (dateTime), `timezone`, `status`, `totals_json` (JSON), `created_by`, timestamps.
   - Relations: `recipients()` (`HasMany` to `CampaignRecipient`).
   - Method: `updateTotals()` (counts `queued`, `sent`, `delivered`, `read`, `failed`, `clicked`, `unsubscribed`).
2. **`CampaignRecipient.php`** (`campaign_recipients` table):
   - Attributes: `id`, `campaign_id`, `contact_id`, `status`, `provider_message_id`, `tracking_token`, `unsubscribe_token`, `sent_at`, `delivered_at`, `read_at`, `clicked_at`, `opted_out_at`, `failed_reason`, timestamps.
   - Relations: `campaign()` (`BelongsTo`), `contact()` (`BelongsTo`).

---

## 3. Services & Helpers
1. **`CampaignPersonalizer.php`**:
   - `renderText($template, $contact, $context)`: Replaces tokens:
     - `{{contact.first_name}}`, `{{contact.last_name}}`, `{{contact.name}}`, `{{contact.email}}`, `{{contact.phone_e164}}`, `{{contact.country}}`, `{{contact.language}}`
     - `{{contact.custom.<key>}}`
     - `{{context.<key>}}`
   - `renderTemplateComponents($components, $contact, $context)`: Renders WhatsApp template components array.
   - `availableContactTokens()`: Token dictionary for React UI picker.

---

## 4. Campaign Jobs
1. **`LaunchCampaignJob`**: Resolves audience contact IDs, filters channel opt-in status, creates `CampaignRecipient` records idempotently using `insertOrIgnore`, and dispatches chunk jobs.
2. **`DispatchCampaignChunkJob`**: Sends messages in batches.
3. **`SendCampaignMessageJob`**: Executes delivery via WhatsApp Cloud API / SMS / Email driver.
4. **`FinalizeCampaignJob`**: Calculates final campaign statistics (`updateTotals`) and updates status to `completed` or `failed`.
5. **`LaunchScheduledCampaignsJob`**: Cron worker that finds `queued` campaigns whose `schedule_at <= now()` and dispatches them.
