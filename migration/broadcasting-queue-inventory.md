# Phase 9 — Broadcasting / Campaigns Queue & Processing Inventory

## Queue Architecture & Jobs

The Laravel Broadcasting module processes campaigns asynchronously using Laravel Queues on the `broadcast` queue:

### 1. `LaunchCampaignJob`
- **Trigger**: Called when user launches a campaign (`POST /app/broadcasts/campaigns/{campaign}/launch`) or by `LaunchScheduledCampaignsJob`.
- **Logic**:
  1. Checks if status is `queued`.
  2. Updates status to `sending`.
  3. Resolves target contacts based on `audience_type` (`segment`, `tag`, `contact_list`, `csv`).
  4. Filters contacts by channel opt-in (`opt_in_whatsapp`, `opt_in_sms`, `opt_in_email`).
  5. If audience is empty: sets status to `failed` with reason `'No matching contacts for audience.'`.
  6. Inserts `CampaignRecipient` records in chunks of 1000 using `insertOrIgnore` for idempotency.
  7. Spawns `DispatchCampaignChunkJob` per chunk.
  8. Spawns `FinalizeCampaignJob` with appropriate delay.

### 2. `SendCampaignMessageJob` / Direct Service Execution
- **Channel Execution**:
  - **WhatsApp**: Calls Meta Cloud API (`WhatsAppApiClient.sendTemplate(...)`). Personalizes template components using `CampaignPersonalizer`.
  - **SMS**: Calls configured workspace SMS gateway driver. Personalizes text payload using `CampaignPersonalizer`.
  - **Email**: Renders HTML payload and sends email. Personalizes subject and body.

### 3. `FinalizeCampaignJob`
- **Logic**:
  - Re-calculates campaign totals (`totals_json`).
  - Sets campaign status to `completed` if all recipients are processed.
