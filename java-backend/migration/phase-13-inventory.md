# Phase 13 — Billing & Subscriptions Inventory

## Overview
Phase 13 covers the complete 1:1 migration of Laravel's Billing, Subscriptions, Plans, Coupons, Tax Rates, Payment Gateways, Checkout, and Webhook system to Java 21 + Spring Boot.

---

## 1. Routes Inventory

### Client Routes (`routes/client.php`)
- `GET /subscription` -> `ClientSubscriptionController@show` -> Inertia `client/Subscription/Show`
- `POST /subscription/change-plan` -> `ClientSubscriptionController@changePlan` -> Redirect
- `GET /subscription/invoice/{transaction}` -> `ClientSubscriptionController@invoiceDownload` -> PDF response
- `DELETE /subscription` -> `ClientSubscriptionController@destroy` -> Redirect
- `POST /coupon/check` -> `ClientSubscriptionController@couponCheck` -> JSON Response
- `GET /billing` -> `BillingController@index` -> Inertia `client/Billing/Index`
- `GET /pricing` -> `PricingController@index` -> Inertia `client/Pricing`
- `POST /checkout` -> `CheckoutController@store` -> Inertia location redirect / Interstitial `client/Checkout/Sdk`

### Admin Routes (`routes/admin.php`)
- `GET /plans` -> `Admin\PlanController@index` -> Inertia `Admin/Plans/Index`
- `POST /plans` -> `Admin\PlanController@store` -> Redirect
- `GET /plans/{plan}/edit` -> `Admin\PlanController@edit` -> Inertia `Admin/Plans/Edit`
- `PUT /plans/{plan}` -> `Admin\PlanController@update` -> Redirect
- `DELETE /plans/{plan}` -> `Admin\PlanController@destroy` -> Redirect
- `POST /plans/{plan}/duplicate` -> `Admin\PlanController@duplicate` -> Redirect
- `POST /plans/reorder` -> `Admin\PlanController@reorder` -> Redirect
- `GET /subscriptions` -> `Admin\SubscriptionController@index` -> Inertia `Admin/Subscriptions/Index`
- `GET /subscriptions/export` -> `Admin\SubscriptionController@export` -> CSV Stream
- `GET /subscriptions/user-search` -> `Admin\SubscriptionController@userSearch` -> JSON Response
- `POST /subscriptions` -> `Admin\SubscriptionController@store` -> Redirect
- `GET /payments` -> `Admin\PaymentController@index` -> Inertia `Admin/Payments/Index`
- `POST /payments/{transaction}/refund` -> `Admin\TransactionController@refund` -> Redirect
- `GET /payment-gateways` -> `Admin\PaymentGatewayConfigController@index` -> Inertia `Admin/PaymentGateways/Index`
- `GET /payment-gateways/{gateway}` -> `Admin\PaymentGatewayConfigController@show` -> JSON Response
- `PUT /payment-gateways/{gateway}` -> `Admin\PaymentGatewayConfigController@update` -> Redirect
- `GET /coupons` -> `Admin\CouponController@index` -> Inertia `Admin/Coupons/Index`
- `POST /coupons` -> `Admin\CouponController@store` -> Redirect
- `PUT /coupons/{coupon}` -> `Admin\CouponController@update` -> Redirect
- `DELETE /coupons/{coupon}` -> `Admin\CouponController@destroy` -> Redirect
- `GET /tax-rates` -> `Admin\TaxRateController@index` -> Inertia `Admin/TaxRates/Index`
- `POST /tax-rates` -> `Admin\TaxRateController@store` -> Redirect
- `PUT /tax-rates/{taxRate}` -> `Admin\TaxRateController@update` -> Redirect
- `DELETE /tax-rates/{taxRate}` -> `Admin\TaxRateController@destroy` -> Redirect

### Webhook Routes (`routes/web.php`)
- `POST /webhooks/stripe` -> `WebhookController@stripe`
- `POST /webhooks/paypal` -> `WebhookController@paypal`
- `POST /webhooks/paddle` -> `WebhookController@paddle`
- `POST /webhooks/razorpay` -> `WebhookController@razorpay`
- `POST /webhooks/cashfree` -> `WebhookController@cashfree`
- `POST /webhooks/tap` -> `WebhookController@tap`
- `POST /webhooks/paystack` -> `WebhookController@paystack`
- `POST /webhooks/xendit` -> `WebhookController@xendit`
- `POST /webhooks/paymob` -> `WebhookController@paymob`
- `POST /webhooks/myfatoorah` -> `WebhookController@myfatoorah`
- `POST /webhooks/mollie` -> `WebhookController@mollie`
- `POST /webhooks/square` -> `WebhookController@square`
- `POST /webhooks/mercadopago` -> `WebhookController@mercadopago`

---

## 2. Models Inventory
1. `Plan`: `plans` table (name, slug, description, price_cents, currency_code, interval, sort_order, enabled, monthly_price_cents, yearly_price_cents, trial_days, stripe_monthly_id, stripe_yearly_id, paddle_monthly_id, paddle_yearly_id, features JSON, limits JSON, featured, popular, white_label_enabled)
2. `Subscription`: `subscriptions` table (user_id, plan_id, status, billing_cycle, starts_at, ends_at, gateway, gateway_subscription_id, gateway_metadata JSON, renews_at, trial_ends_at, trial_reminder_sent_at)
3. `ClientSubscription`: `client_subscriptions` table (client_id, plan_id, billing_cycle, starts_at, ends_at, status, assigned_by_admin_id)
4. `PaymentTransaction`: `payment_transactions` table (subscription_id, user_id, gateway, gateway_transaction_id, amount_cents, currency_code, currency, status, payload JSON, coupon_id, refunded_at, refund_reason, refunded_cents, invoice_path, tax_amount_cents)
5. `PaymentGatewayConfig`: `payment_gateway_configs` table (gateway, test_mode, enabled, credentials JSON encrypted)
6. `Coupon`: `coupons` table (code, kind, amount, duration, duration_in_months, applies_to_plan_ids JSON, max_redemptions, times_redeemed, enabled, expires_at, stripe_coupon_id)
7. `TaxRate`: `tax_rates` table (name, country, region, percentage, inclusive, enabled, stripe_tax_rate_id)
8. `BillingEvent`: `billing_events` table (gateway, event_id, event_type, payload JSON, processed_at, error, attempts)

---

## 3. Services & Gateways Inventory
- `BillingGatewayRegistry`: Central service holding gateway implementations (`stripe`, `paypal`, `paddle`, `razorpay`, `cashfree`, `tap`, `paystack`, `xendit`, `paymob`, `myfatoorah`, `mollie`, `square`, `mercadopago`).
- `InvoiceService`: Generates PDF invoice documents for payment transactions.
- `WebhookIdempotencyService`: Guards against duplicate event execution using `billing_events` table.

---

## 4. Events Inventory
- `SubscriptionStarted`
- `PlanChanged`
- `SubscriptionCancelled`
- `SubscriptionExpired`
- `SubscriptionRenewed`
- `TrialEnding`

---

## 5. Inertia Components & Props
- `client/Subscription/Show`: `subscription`, `canCancel`, `canUpgrade`, `plans`, `transactions`
- `client/Billing/Index`: `transactions` (paginated)
- `client/Pricing`: `plans`, `gateways`, `is_authenticated`, `register_url`, `checkout_url`, `flash`
- `client/Checkout/Sdk`: `checkout`, `plan_name`, `pricing_url`
- `Admin/Plans/Index`: `plans`, `currencies`, `defaultCurrency`
- `Admin/Plans/Edit`: `plan`, `currencies`, `defaultCurrency`
- `Admin/Subscriptions/Index`: `subscriptions`, `filters`, `plans`
- `Admin/Payments/Index`: `payments`, `filters`
- `Admin/PaymentGateways/Index`: `gateways`
- `Admin/Coupons/Index`: `coupons`
- `Admin/TaxRates/Index`: `taxRates`
- `Admin/Transactions/Index`: `transactions`
