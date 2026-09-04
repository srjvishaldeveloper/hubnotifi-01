# Phase 13 — Billing & Subscriptions Parity Contract

## Overview
Phase 13 establishes 1:1 behavioral, security, routing, database schema, and Inertia prop contract parity between the original Laravel application and the Java 21 Spring Boot backend for all **Billing & Subscriptions** functionality.

---

## Route & Controller Parity Matrix

| HTTP Method | Route | Controller Method | Inertia Component / Output | Props / Payload | Authorization / Security |
|---|---|---|---|---|---|
| `GET` | `/billing` | `ClientBillingController.index` | `Billing/Index` | `subscription`, `plans`, `transactions`, `gateways`, `client` | Client Authenticated |
| `GET` | `/subscription` | `ClientSubscriptionController.show` | `Subscription/Show` | `subscription`, `plans`, `gateways` | Client Authenticated |
| `POST` | `/subscription/change-plan` | `ClientSubscriptionController.changePlan` | Redirect `/subscription` | `plan_id`, `billing_cycle` | Client Authenticated |
| `DELETE` | `/subscription` | `ClientSubscriptionController.cancel` | Redirect `/subscription` | None | Client Authenticated |
| `GET` | `/subscription/invoice/{id}` | `ClientSubscriptionController.downloadInvoice` | PDF Download | `Content-Type: application/pdf` | Client Authenticated |
| `GET` | `/pricing` | `PricingController.index` | `Pricing/Index` | `plans`, `currencies`, `defaultCurrency` | Public / Optional Auth |
| `POST` | `/checkout` | `CheckoutController.store` | Redirect/Inertia Location | `plan_id`, `billing_cycle`, `gateway` | Client Authenticated |
| `POST` | `/coupon/check` | `CheckoutController.checkCoupon` | JSON | `valid`, `discount`, `code` | Public / Client Authenticated |
| `POST` | `/webhooks/{gateway}` | `WebhookController.handleWebhook` | JSON | `status: "received"` / `status: "ignored"` | Webhook Idempotency Header |
| `GET` | `/admin/plans` | `AdminPlanController.index` | `Admin/Plans/Index` | `plans`, `currencies`, `defaultCurrency` | Admin Authenticated |
| `POST` | `/admin/plans` | `AdminPlanController.store` | Redirect `/admin/plans` | Plan creation payload | Admin Authenticated |
| `GET` | `/admin/plans/{id}/edit` | `AdminPlanController.edit` | `Admin/Plans/Edit` | `plan`, `currencies`, `defaultCurrency` | Admin Authenticated |
| `PUT` | `/admin/plans/{id}` | `AdminPlanController.update` | Redirect `/admin/plans` | Plan update payload | Admin Authenticated |
| `DELETE` | `/admin/plans/{id}` | `AdminPlanController.destroy` | Redirect `/admin/plans` | None | Admin Authenticated |
| `POST` | `/admin/plans/{id}/duplicate` | `AdminPlanController.duplicate` | Redirect `/admin/plans` | None | Admin Authenticated |
| `POST` | `/admin/plans/reorder` | `AdminPlanController.reorder` | Redirect `/admin/plans` | `order` array | Admin Authenticated |
| `GET` | `/admin/subscriptions` | `AdminSubscriptionController.index` | `Admin/Subscriptions/Index` | `subscriptions`, `stats`, `plans` | Admin Authenticated |
| `GET` | `/admin/subscriptions/user-search` | `AdminSubscriptionController.searchUsers` | JSON | `users` list | Admin Authenticated |
| `POST` | `/admin/subscriptions/assign` | `AdminSubscriptionController.assign` | Redirect `/admin/subscriptions` | `user_id`, `plan_id`, `billing_cycle` | Admin Authenticated |
| `DELETE` | `/admin/subscriptions/{id}` | `AdminSubscriptionController.cancel` | Redirect `/admin/subscriptions` | None | Admin Authenticated |
| `GET` | `/admin/subscriptions/export` | `AdminSubscriptionController.exportCsv` | CSV File | `Content-Type: text/csv` | Admin Authenticated |
| `GET` | `/admin/payments` | `AdminPaymentController.index` | `Admin/Payments/Index` | `payments`, `gateways` | Admin Authenticated |
| `GET` | `/admin/payment-gateways` | `AdminPaymentGatewayConfigController.index` | `Admin/PaymentGateways/Index` | `gateways` | Admin Authenticated |
| `GET` | `/admin/payment-gateways/{gateway}` | `AdminPaymentGatewayConfigController.show` | JSON | Gateway config | Admin Authenticated |
| `PUT` | `/admin/payment-gateways/{gateway}` | `AdminPaymentGatewayConfigController.update` | Redirect `/admin/payment-gateways` | Gateway settings | Admin Authenticated |
| `GET` | `/admin/coupons` | `AdminCouponController.index` | `Admin/Coupons/Index` | `coupons` | Admin Authenticated |
| `POST` | `/admin/coupons` | `AdminCouponController.store` | Redirect `/admin/coupons` | Coupon payload | Admin Authenticated |
| `PUT` | `/admin/coupons/{id}` | `AdminCouponController.update` | Redirect `/admin/coupons` | Coupon payload | Admin Authenticated |
| `DELETE` | `/admin/coupons/{id}` | `AdminCouponController.destroy` | Redirect `/admin/coupons` | None | Admin Authenticated |
| `GET` | `/admin/tax-rates` | `AdminTaxRateController.index` | `Admin/TaxRates/Index` | `tax_rates` | Admin Authenticated |
| `POST` | `/admin/tax-rates` | `AdminTaxRateController.store` | Redirect `/admin/tax-rates` | Tax rate payload | Admin Authenticated |
| `PUT` | `/admin/tax-rates/{id}` | `AdminTaxRateController.update` | Redirect `/admin/tax-rates` | Tax rate payload | Admin Authenticated |
| `DELETE` | `/admin/tax-rates/{id}` | `AdminTaxRateController.destroy` | Redirect `/admin/tax-rates` | None | Admin Authenticated |

---

## Database Schema Compatibility

| Entity Class | Table Name | Key Fields & Types | Indexes & Constraints |
|---|---|---|---|
| `Plan` | `plans` | `id`, `name`, `slug`, `price_cents`, `currency_code`, `monthly_price_cents`, `yearly_price_cents`, `trial_days`, `features` (JSON), `limits` (JSON) | Unique(`slug`) |
| `Subscription` | `subscriptions` | `id`, `user_id`, `plan_id`, `status`, `billing_cycle`, `starts_at`, `ends_at`, `gateway`, `stripe_id` | Foreign Key to User/Plan |
| `ClientSubscription` | `client_subscriptions` | `id`, `client_id`, `plan_id`, `status`, `billing_cycle`, `starts_at`, `ends_at`, `assigned_by_admin_id` | Foreign Key to Client/Plan |
| `PaymentTransaction` | `payment_transactions` | `id`, `user_id`, `gateway`, `transaction_id`, `amount_cents`, `currency_code`, `status`, `payload` (JSON) | Foreign Key to User |
| `PaymentGatewayConfig` | `payment_gateway_configs` | `id`, `gateway`, `enabled`, `environment`, `api_key`, `api_secret`, `webhook_secret`, `settings` (JSON) | Unique(`gateway`) |
| `Coupon` | `coupons` | `id`, `code`, `kind`, `amount`, `duration`, `enabled`, `expires_at`, `max_redemptions`, `times_redeemed` | Unique(`code`) |
| `TaxRate` | `tax_rates` | `id`, `name`, `country`, `state`, `percentage`, `inclusive`, `enabled` | Standard |
| `BillingEvent` | `billing_events` | `id`, `event_id`, `gateway`, `event_type`, `payload` (JSON), `processed_at` | Unique(`event_id`) |

---

## Verification Results
- **Automated Tests:** 121 / 121 tests passing (0 failures).
- **Phase 13 Integration Test:** `Phase13BillingParityIntegrationTest` (13/13 passing).
- **Build Status:** `BUILD SUCCESSFUL`.
