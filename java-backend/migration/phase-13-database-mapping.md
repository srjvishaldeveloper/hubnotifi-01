# Phase 13 — Database Mapping

## DB Schema Preservation Notice
No database schema changes, table creations, or column modifications are allowed. Schema validation mode in Hibernate is used.

---

## 1. `plans` Table

| Column Name | Type | Nullable | Key | Default | Java Entity Field | Type |
|---|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | auto_increment | `id` | `Long` |
| `name` | varchar(255) | NO | | | `name` | `String` |
| `slug` | varchar(255) | NO | UNI | | `slug` | `String` |
| `description` | text | YES | | NULL | `description` | `String` |
| `price_cents` | int | NO | | 0 | `priceCents` | `Integer` |
| `currency_code` | varchar(10) | NO | FK | USD | `currencyCode` | `String` |
| `interval` | varchar(20) | NO | | month | `interval` | `String` |
| `sort_order` | int | NO | | 0 | `sortOrder` | `Integer` |
| `enabled` | tinyint(1) | NO | | 1 | `enabled` | `Boolean` |
| `monthly_price_cents` | int | YES | | NULL | `monthlyPriceCents` | `Integer` |
| `yearly_price_cents` | int | YES | | NULL | `yearlyPriceCents` | `Integer` |
| `trial_days` | int | YES | | 0 | `trialDays` | `Integer` |
| `stripe_monthly_id` | varchar(255) | YES | | NULL | `stripeMonthlyId` | `String` |
| `stripe_yearly_id` | varchar(255) | YES | | NULL | `stripeYearlyId` | `String` |
| `paddle_monthly_id` | varchar(255) | YES | | NULL | `paddleMonthlyId` | `String` |
| `paddle_yearly_id` | varchar(255) | YES | | NULL | `paddleYearlyId` | `String` |
| `features` | json | YES | | NULL | `features` | `String` / `@Convert` |
| `limits` | json | YES | | NULL | `limits` | `String` / `@Convert` |
| `featured` | tinyint(1) | NO | | 0 | `featured` | `Boolean` |
| `popular` | tinyint(1) | NO | | 0 | `popular` | `Boolean` |
| `white_label_enabled` | tinyint(1) | NO | | 0 | `whiteLabelEnabled` | `Boolean` |
| `created_at` | timestamp | YES | | NULL | `createdAt` | `LocalDateTime` |
| `updated_at` | timestamp | YES | | NULL | `updatedAt` | `LocalDateTime` |

---

## 2. `subscriptions` Table

| Column Name | Type | Nullable | Key | Default | Java Entity Field | Type |
|---|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | auto_increment | `id` | `Long` |
| `user_id` | bigint | NO | FK | | `userId` | `Long` |
| `plan_id` | bigint | NO | FK | | `planId` | `Long` |
| `status` | varchar(50) | NO | | active | `status` | `String` |
| `billing_cycle` | varchar(20) | NO | | month | `billingCycle` | `String` |
| `starts_at` | timestamp | YES | | NULL | `startsAt` | `LocalDateTime` |
| `ends_at` | timestamp | YES | | NULL | `endsAt` | `LocalDateTime` |
| `gateway` | varchar(50) | YES | | NULL | `gateway` | `String` |
| `gateway_subscription_id` | varchar(255) | YES | | NULL | `gatewaySubscriptionId` | `String` |
| `gateway_metadata` | json | YES | | NULL | `gatewayMetadata` | `String` |
| `renews_at` | timestamp | YES | | NULL | `renewsAt` | `LocalDateTime` |
| `trial_ends_at` | timestamp | YES | | NULL | `trialEndsAt` | `LocalDateTime` |
| `trial_reminder_sent_at` | timestamp | YES | | NULL | `trialReminderSentAt` | `LocalDateTime` |
| `created_at` | timestamp | YES | | NULL | `createdAt` | `LocalDateTime` |
| `updated_at` | timestamp | YES | | NULL | `updatedAt` | `LocalDateTime` |

---

## 3. `client_subscriptions` Table

| Column Name | Type | Nullable | Key | Default | Java Entity Field | Type |
|---|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | auto_increment | `id` | `Long` |
| `client_id` | bigint | NO | FK | | `clientId` | `Long` |
| `plan_id` | bigint | NO | FK | | `planId` | `Long` |
| `billing_cycle` | varchar(20) | NO | | monthly | `billingCycle` | `String` |
| `starts_at` | timestamp | YES | | NULL | `startsAt` | `LocalDateTime` |
| `ends_at` | timestamp | YES | | NULL | `endsAt` | `LocalDateTime` |
| `status` | varchar(50) | NO | | active | `status` | `String` |
| `assigned_by_admin_id` | bigint | YES | FK | NULL | `assignedByAdminId` | `Long` |
| `created_at` | timestamp | YES | | NULL | `createdAt` | `LocalDateTime` |
| `updated_at` | timestamp | YES | | NULL | `updatedAt` | `LocalDateTime` |

---

## 4. `payment_transactions` Table

| Column Name | Type | Nullable | Key | Default | Java Entity Field | Type |
|---|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | auto_increment | `id` | `Long` |
| `subscription_id` | bigint | YES | FK | NULL | `subscriptionId` | `Long` |
| `user_id` | bigint | NO | FK | | `userId` | `Long` |
| `gateway` | varchar(50) | NO | | | `gateway` | `String` |
| `gateway_transaction_id` | varchar(255) | YES | | NULL | `gatewayTransactionId` | `String` |
| `amount_cents` | int | NO | | 0 | `amountCents` | `Integer` |
| `currency_code` | varchar(10) | NO | | USD | `currencyCode` | `String` |
| `currency` | varchar(10) | YES | | NULL | `currency` | `String` |
| `status` | varchar(50) | NO | | pending | `status` | `String` |
| `payload` | json | YES | | NULL | `payload` | `String` |
| `coupon_id` | bigint | YES | FK | NULL | `couponId` | `Long` |
| `refunded_at` | timestamp | YES | | NULL | `refundedAt` | `LocalDateTime` |
| `refund_reason` | varchar(255) | YES | | NULL | `refundReason` | `String` |
| `refunded_cents` | int | YES | | NULL | `refundedCents` | `Integer` |
| `invoice_path` | varchar(255) | YES | | NULL | `invoicePath` | `String` |
| `tax_amount_cents` | int | YES | | 0 | `taxAmountCents` | `Integer` |
| `created_at` | timestamp | YES | | NULL | `createdAt` | `LocalDateTime` |
| `updated_at` | timestamp | YES | | NULL | `updatedAt` | `LocalDateTime` |

---

## 5. `payment_gateway_configs` Table

| Column Name | Type | Nullable | Key | Default | Java Entity Field | Type |
|---|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | auto_increment | `id` | `Long` |
| `gateway` | varchar(50) | NO | UNI | | `gateway` | `String` |
| `test_mode` | tinyint(1) | NO | | 1 | `testMode` | `Boolean` |
| `enabled` | tinyint(1) | NO | | 0 | `enabled` | `Boolean` |
| `credentials` | json / text | YES | | NULL | `credentials` | `String` |
| `created_at` | timestamp | YES | | NULL | `createdAt` | `LocalDateTime` |
| `updated_at` | timestamp | YES | | NULL | `updatedAt` | `LocalDateTime` |

---

## 6. `coupons` Table

| Column Name | Type | Nullable | Key | Default | Java Entity Field | Type |
|---|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | auto_increment | `id` | `Long` |
| `code` | varchar(64) | NO | UNI | | `code` | `String` |
| `kind` | varchar(20) | NO | | percent | `kind` | `String` |
| `amount` | decimal(10,2) | NO | | 0.00 | `amount` | `BigDecimal` |
| `duration` | varchar(20) | NO | | once | `duration` | `String` |
| `duration_in_months` | int | YES | | NULL | `durationInMonths` | `Integer` |
| `applies_to_plan_ids` | json | YES | | NULL | `appliesToPlanIds` | `String` |
| `max_redemptions` | int | YES | | NULL | `maxRedemptions` | `Integer` |
| `times_redeemed` | int | NO | | 0 | `timesRedeemed` | `Integer` |
| `enabled` | tinyint(1) | NO | | 1 | `enabled` | `Boolean` |
| `expires_at` | timestamp | YES | | NULL | `expiresAt` | `LocalDateTime` |
| `stripe_coupon_id` | varchar(255) | YES | | NULL | `stripeCouponId` | `String` |
| `created_at` | timestamp | YES | | NULL | `createdAt` | `LocalDateTime` |
| `updated_at` | timestamp | YES | | NULL | `updatedAt` | `LocalDateTime` |

---

## 7. `tax_rates` Table

| Column Name | Type | Nullable | Key | Default | Java Entity Field | Type |
|---|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | auto_increment | `id` | `Long` |
| `name` | varchar(100) | NO | | | `name` | `String` |
| `country` | varchar(2) | NO | | | `country` | `String` |
| `region` | varchar(100) | YES | | NULL | `region` | `String` |
| `percentage` | decimal(5,2) | NO | | 0.00 | `percentage` | `BigDecimal` |
| `inclusive` | tinyint(1) | NO | | 0 | `inclusive` | `Boolean` |
| `enabled` | tinyint(1) | NO | | 1 | `enabled` | `Boolean` |
| `stripe_tax_rate_id` | varchar(255) | YES | | NULL | `stripeTaxRateId` | `String` |
| `created_at` | timestamp | YES | | NULL | `createdAt` | `LocalDateTime` |
| `updated_at` | timestamp | YES | | NULL | `updatedAt` | `LocalDateTime` |

---

## 8. `billing_events` Table

| Column Name | Type | Nullable | Key | Default | Java Entity Field | Type |
|---|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | auto_increment | `id` | `Long` |
| `gateway` | varchar(50) | NO | | | `gateway` | `String` |
| `event_id` | varchar(255) | NO | UNI | | `eventId` | `String` |
| `event_type` | varchar(255) | NO | | | `eventType` | `String` |
| `payload` | json | YES | | NULL | `payload` | `String` |
| `processed_at` | timestamp | YES | | NULL | `processedAt` | `LocalDateTime` |
| `error` | text | YES | | NULL | `error` | `String` |
| `attempts` | int | NO | | 0 | `attempts` | `Integer` |
| `created_at` | timestamp | YES | | NULL | `createdAt` | `LocalDateTime` |
| `updated_at` | timestamp | YES | | NULL | `updatedAt` | `LocalDateTime` |
