# Phase 9 — Broadcasting / Campaigns Database Mapping

## Existing Database Tables & Schema

### 1. `campaigns` Table
Primary table storing campaign metadata, status, schedule, and aggregated metrics.

```sql
CREATE TABLE `campaigns` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `uuid` char(36) NOT NULL,
  `workspace_id` bigint unsigned NOT NULL,
  `name` varchar(128) NOT NULL,
  `channel` varchar(32) NOT NULL, -- 'whatsapp', 'sms', 'email'
  `whatsapp_phone_number_id` varchar(64) DEFAULT NULL,
  `audience_type` varchar(32) NOT NULL, -- 'segment', 'contact_list', 'tag', 'csv'
  `audience_ref` varchar(255) DEFAULT NULL,
  `template_ref` json DEFAULT NULL,
  `payload_json` json DEFAULT NULL,
  `schedule_at` timestamp NULL DEFAULT NULL,
  `timezone` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'draft', -- 'draft', 'queued', 'sending', 'paused', 'completed', 'failed', 'cancelled'
  `totals_json` json DEFAULT NULL,
  `created_by` bigint unsigned DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `campaigns_uuid_unique` (`uuid`),
  KEY `campaigns_workspace_id_status_index` (`workspace_id`,`status`),
  CONSTRAINT `campaigns_workspace_id_foreign` FOREIGN KEY (`workspace_id`) REFERENCES `workspaces` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2. `campaign_recipients` Table
Recipient records for tracking delivery and engagement status per contact.

```sql
CREATE TABLE `campaign_recipients` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `campaign_id` bigint unsigned NOT NULL,
  `contact_id` bigint unsigned NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'queued', -- 'queued', 'sent', 'delivered', 'read', 'failed'
  `provider_message_id` varchar(128) DEFAULT NULL,
  `tracking_token` varchar(64) DEFAULT NULL,
  `unsubscribe_token` varchar(64) DEFAULT NULL,
  `sent_at` timestamp NULL DEFAULT NULL,
  `delivered_at` timestamp NULL DEFAULT NULL,
  `read_at` timestamp NULL DEFAULT NULL,
  `clicked_at` timestamp NULL DEFAULT NULL,
  `opted_out_at` timestamp NULL DEFAULT NULL,
  `failed_reason` text DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `campaign_recipients_campaign_contact_unique` (`campaign_id`,`contact_id`),
  KEY `campaign_recipients_campaign_id_status_index` (`campaign_id`,`status`),
  CONSTRAINT `campaign_recipients_campaign_id_foreign` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`id`) ON DELETE CASCADE,
  CONSTRAINT `campaign_recipients_contact_id_foreign` FOREIGN KEY (`contact_id`) REFERENCES `contacts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## JPA Entity Mapping Strategy

1. **`Campaign.java`**:
   - `@Table(name = "campaigns")`
   - Attributes: `id`, `uuid`, `workspaceId`, `name`, `channel`, `whatsappPhoneNumberId`, `audienceType`, `audienceRef`, `templateRef` (JSON converted as Map/String), `payloadJson` (JSON converted), `scheduleAt` (LocalDateTime), `timezone`, `status`, `totalsJson` (JSON converted), `createdBy`, `createdAt`, `updatedAt`.
   - Utility method: `updateTotals(List<CampaignRecipient> recipients)`.

2. **`CampaignRecipient.java`**:
   - `@Table(name = "campaign_recipients")`
   - Attributes: `id`, `campaignId`, `contactId`, `status`, `providerMessageId`, `trackingToken`, `unsubscribeToken`, `sentAt`, `deliveredAt`, `readAt`, `clickedAt`, `optedOutAt`, `failedReason`, `createdAt`, `updatedAt`.
   - Relations: `@ManyToOne` to `Contact` (lazy).
