# PHP Functionality Inventory

## 1. Controller & Endpoint Catalog

### A. Authentication Controllers (`app/Http/Controllers/Auth/`)
1. **`AuthenticatedSessionController.php`**: Handles client user login, session generation, and logout.
2. **`RegisteredUserController.php`**: Self-service client account registration and default workspace initialization.
3. **`PasswordResetLinkController.php` & `NewPasswordController.php`**: Email password reset token handling.
4. **`TwoFactorController.php`**: Google2FA TOTP verification, QR code generation, backup codes.
5. **`SocialiteController.php`**: OAuth2 login via Google & Microsoft.
6. **`MagicLinkController.php`**: Passwordless magic link authentication via email.

### B. Admin Panel Controllers (`app/Http/Controllers/Admin/`)
1. **`DashboardController.php`**: System-wide analytics, ARR/MRR metrics, active clients, message volume metrics.
2. **`ClientController.php`**: Full CRUD for client accounts, workspace inspection, quota adjustments, suspension.
3. **`AdminUserController.php`**: Administrator management, password changes, account status.
4. **`RolesPermissionsController.php` & `PermissionController.php`**: RBAC management for internal admin roles.
5. **`PlanController.php`**: Subscription plan builder (limits, pricing, billing cycles, features).
6. **`SubscriptionController.php`**: Global client subscriptions overview and manual plan assignments.
7. **`TransactionController.php`**: Billing payment transaction log and invoice downloading.
8. **`CouponController.php`**: Discount coupons creation and redemption rules.
9. **`TaxRateController.php`**: Regional tax configuration (VAT, GST, sales tax).
10. **`PaymentGatewayConfigController.php`**: Gateways setup (Stripe, PayPal, Razorpay, Cashfree, Tap).
11. **`SystemSettingsController.php`**: System-wide configuration (general settings, app branding, storage disks).
12. **`EmailSystemController.php`**: Global SMTP configuration, mail templates, test email dispatcher.
13. **`PusherSettingsController.php`**: WebSocket / Reverb credentials & cluster configuration.
14. **`QueueController.php`**: Real-time background job queues monitoring, failed jobs inspection, retry triggers.
15. **`AuditLogController.php`**: Admin action audit logs recording IP, user agent, changes.
16. **`ImpersonationController.php`**: One-click client impersonation start/stop.
17. **`LandingPageController.php` & `CmsPageController.php`**: Public landing page editor, custom CMS pages (Terms, Privacy).
18. **`LocaleController.php` & `TranslationController.php`**: Multi-language locales management, translation dictionary editor.
19. **`CronSetupController.php`**: Cron status check and instructions.

### C. Client Portal Controllers (`app/Http/Controllers/Client/`)
1. **`DashboardController.php`**: Workspace activity summary, message delivery metrics, active campaigns overview.
2. **`SettingsController.php`**: Workspace profile, default timezone, default currency, SMS gateway secrets.
3. **`TeamController.php`**: Team member invitations, workspace access delegation, role assignment.
4. **`InvitationController.php`**: Email invitation acceptance and account binding.
5. **`ApiTokenController.php`**: Sanctum API access tokens creation and revocation.
6. **`WebhookEndpointController.php`**: Outgoing webhook endpoints registration and signature secret generation.
7. **`BillingController.php` & `SubscriptionController.php`**: Client subscription checkout, plan upgrades, payment methods.
8. **`MediaController.php`**: Media asset manager (image/video/audio upload and deletion).
9. **`NotificationController.php`**: In-app notifications and read status.
10. **`WebPushController.php`**: VAPID & OneSignal push subscriptions registration.
11. **`SupportTicketController.php`**: Support ticket submission, agent replies, attachments.
12. **`OnboardingController.php`**: Multi-step onboarding guide status update.
13. **`SearchController.php`**: Global workspace search across contacts, messages, campaigns.

### D. Feature Module Controllers (`app/Modules/`)
1. **`Whatsapp` Module**:
   - `WhatsappAccountController.php`: Connect WhatsApp Business Account (WABA), QR code scan, phone number registration.
   - `WhatsappTemplateController.php`: Sync, submit, and manage Meta WhatsApp message templates.
   - `WhatsappMessageController.php`: Outbound template/text/media sending.
2. **`Inbox` Module**:
   - `InboxController.php`: Live multi-agent conversation inbox.
   - `ConversationController.php`: Assign conversations, resolve, tag, star, filter by status.
   - `MessageController.php`: Send text, media, voice notes, canned responses.
   - `InternalNoteController.php`: Internal team collaboration notes on conversations.
3. **`Broadcasting` Module**:
   - `CampaignController.php`: Create bulk messaging campaigns, select target contact lists, schedule send times.
   - `BroadcastingController.php`: Campaign progress monitoring, delivery stats, cancel campaign.
4. **`Automation` Module**:
   - `AutomationController.php`: Trigger-action workflow builder (keyword triggers, auto-replies, sequence delays).
   - `FlowController.php`: Visual chatbot node builder data handler.
5. **`AI` Module**:
   - `AiAssistantController.php`: AI Chatbot agent configuration, prompt templates, fallback behaviors.
   - `AiKnowledgeBaseController.php`: Document upload (PDF, CSV, TXT), vector indexing, Qdrant integration.
   - `AiGenerateController.php`: Direct AI response generation for agent suggestions.
6. **`Ecommerce` Module**:
   - `ProductController.php`: Product catalog sync and management.
   - `OrderController.php`: E-commerce orders list, automated WhatsApp order status updates.
7. **`Leads` Module**:
   - `LeadController.php`: Lead capture forms, lead scoring rules, stage pipelines.
8. **`Social` Module**:
   - `SocialAccountController.php`: Facebook Page & Instagram Business account connection.

### E. Webhook Handlers (`app/Http/Controllers/Webhooks/` & `routes/webhooks.php`)
1. **`WhatsAppWebhookController.php`**: Inbound Meta webhook verification (`hub.verify_token`) and event receiver (messages, delivery status DLRs, read receipts, template approvals).
2. **`StripeWebhookController.php`**: Stripe events (`customer.subscription.created/updated/deleted`, `invoice.payment_succeeded`).
3. **`PayPalWebhookController.php`**, **`RazorpayWebhookController.php`**, **`CashfreeWebhookController.php`**, **`TapWebhookController.php`**: Gateway payment status callbacks.
4. **`SmsWebhookController.php`**: Fast2SMS DLR callbacks.

---

## 2. Domain Services & Business Logic Catalog

1. **`InstallerService.php`**: First-run setup check (`APP_INSTALLED`), database connection test, key generation, admin creation.
2. **`StorageManager.php`**: Multi-disk abstraction (local `public` vs `AWS S3`), URL generation, file validation, storage cleanup.
3. **`OnboardingService.php`**: Workspace initialization progress tracker.
4. **`I18nFileService.php`**: JSON translation files reader, flat key-value dictionary compiler per locale.
5. **`CredentialResolver.php`**: Multi-tenant API key resolver (resolves system default vs workspace-specific Meta/Stripe credentials).
6. **`UsageMeter.php`**: Plan limits quota meter (tracks campaigns sent, WhatsApp message count, AI credits consumed per billing cycle).
7. **`PaymentGatewayManager.php`**: Unified billing gateway interface wrapping Stripe, PayPal, Razorpay, Cashfree, and Tap.

---

## 3. Background Jobs, Queues & Console Commands

1. **Scheduled Commands (`app/Console/Commands/` & `routes/console.php`)**:
   - `billing:charge-recurring`: Runs daily to process recurring subscription renewals for hosted gateway cards.
   - `broadcasting:process-scheduled`: Checks scheduled campaigns due for dispatch.
   - `whatsapp:sync-templates`: Periodically syncs WhatsApp template statuses from Meta Graph API.
   - `queue:prune-batches` & `model:prune`: Database maintenance and log cleanup.

2. **Queued Jobs (`app/Jobs/` & `app/Modules/*/Jobs/`)**:
   - `SendWhatsAppMessageJob`: Dispatches individual WhatsApp messages with rate-limiting and retry backoff.
   - `ProcessCampaignJob`: Chunks campaign target list and queues message dispatches.
   - `ExecuteAutomationFlowJob`: Evaluates automation node conditions and triggers actions asynchronously.
   - `ProcessInboundWebhookJob`: Parses incoming WhatsApp/Social webhooks asynchronously to prevent HTTP timeouts.
   - `GenerateAiResponseJob`: Invokes OpenAI/Claude API asynchronously and broadcasts result via Echo.
   - `SendWebhookDeliveryJob`: Dispatches outgoing webhook events to client-registered HTTP endpoints.
   - `SendEmailJob`: Queues outbound SMTP email dispatch.

---

## 4. Real-time Events & WebSockets

1. **`MessageSent` Event**: Broadcasts new inbound or outbound message to private workspace channel `private-workspace.{workspace_id}`.
2. **`ConversationUpdated` Event**: Broadcasts status changes (assigned agent, unread count, closed).
3. **`CampaignProgressUpdated` Event**: Pushes campaign delivery percentage updates to client dashboard.
4. **`Channel Authorization` (`routes/channels.php`)**: Verifies user workspace membership before granting WebSocket channel access.
