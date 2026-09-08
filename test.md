# Hub Notification — Manual QA / Deployment Test Plan

Target: `java-backend` (Spring Boot port), served at `http://127.0.0.1:8080` in dev.
Follow phases in order — later phases assume earlier ones passed (e.g. Channel Setup needs a workspace, which needs an account).

## 0. Before you start

1. **MySQL running** and reachable with the credentials in `java-backend/.env` (`DB_HOST`, `DB_PASSWORD`, DB name `whatsmine`).
2. **Start the backend**: `cd java-backend && .\gradlew.bat bootRun` (or however you normally run it). Confirm it's healthy:
   ```
   curl http://127.0.0.1:8080/actuator/health
   ```
   should return `{"status":"UP", ...}`.
3. Open `http://127.0.0.1:8080` in a browser.

### Seeded test accounts (created automatically on first boot by `DataSeeder.java`)
| Role | Email | Password | Notes |
|---|---|---|---|
| **Super Admin** | `admin@example.com` | `password` | Log in at `/admin/login`, not `/login` |
| Demo client user | `client@example.com` | `password` | Already has a workspace + 1 demo contact, email pre-verified |

---

## Phase 1 — New account signup (client side)

1. Go to `http://127.0.0.1:8080/register`.
2. Fill in name / email / password, submit.
3. **Expected**: account + a `Client`, a default `Workspace` ("`<name>`'s Workspace"), and a `WorkspaceUser` (role `owner`) are all created in one shot, and you're logged straight in and redirected to `/app/dashboard`.
4. Check the Dashboard: "No active workspace" banner should **not** appear (you own a workspace from step 3). Workspaces count should show `1`, Team members `1`, Plan `Free`.
5. Log out (top-right menu), log back in at `/login` with the same credentials — confirm session works.

**⚠️ Known gap — email verification does not exist.** There is no verification email, no `/verify-email` route, and `register()` never sets `email_verified_at` — it stays `NULL` forever for every self-registered account (`AuthService.java:95-137`). The Dashboard's onboarding checklist will show "Verify your email address" as permanently incomplete for any account created via `/register`. This is not something you can test end-to-end right now — treat it as a known backlog item, not a bug in your test run. (The two *seeded* accounts above are pre-verified in the DB, so they don't hit this.)

---

## Phase 2 — Super Admin panel

1. Log in at `http://127.0.0.1:8080/admin/login` with `admin@example.com` / `password`.
2. **Admin Dashboard** (`/admin/dashboard`) — should show real platform-wide stats (this one *is* fully real, unlike the client dashboard was before today's fix).
3. **Clients** (`/admin/clients`) — you should see every client created in Phase 1, plus the seeded `SpaGreen Wellness`. Try: view a client, edit, impersonate ("Impersonate" button should log you in as that client and show an impersonation banner on the client side; "Stop impersonating" should return you to admin).
4. **Plans** (`/admin/plans`) — create/edit a plan, check it shows up on the public pricing page.
5. **Integrations** (`/admin/integrations`) — this is where you plug in real third-party API keys. Confirmed-real, with actual input forms (not just a catalog entry) for:
   - `meta_app` — WhatsApp / Instagram / Messenger / Facebook (App ID, App Secret, System User Token, Webhook Verify Token, Embedded Signup Config IDs)
   - `sms_twilio` — Account SID, Auth Token, From Number
   - `oauth_linkedin`, `oauth_twitter`, `oauth_youtube`, `oauth_tiktok` — Client ID/Secret each
   - `oauth_shopify`, `oauth_bigcommerce` — Client ID/Secret each
   - `google_places` — API Key

   Everything else in that catalog (AI providers, Google Workspace, Qdrant, S3/DO/Wasabi storage) is listed but has no working save form yet — don't spend test time on those.

---

## Phase 3 — Channel Setup (this is the part that needs real credentials to fully test)

All of this lives under the client sidebar **Channel Setup** (`/app/inbox/setup`), except SMS/Email which are under **Broadcasting**.

### 3a. WhatsApp (real, needs `meta_app` configured in Phase 2 step 5)
1. As a client user, go to Channel Setup → Connect WhatsApp (Embedded Signup).
2. Without `meta_app` credentials configured: expect a graceful "not configured, contact your administrator" message — **that itself is a valid pass**, it means the failure path works correctly.
3. With real Meta App ID/Secret + a real WhatsApp Business embedded-signup config ID: complete the Facebook popup flow, confirm a `ChannelAccount` (channel=`whatsapp`) appears, phone number syncs, status shows active.
4. Test "Sync phone numbers", "Refresh status", "Change display name", "Re-register webhook" buttons — all hit real Graph API calls.

### 3b. Messenger / Instagram (real, same `meta_app` credentials)
Same Channel Setup page, "Connect Instagram" / "Connect Messenger" via embedded signup. Same expectations as 3a.

### 3c. SMS Gateway (real, needs `sms_twilio` configured)
1. Go to **SMS Gateways** (`/app/broadcasts/sms-gateways`).
2. Enter Twilio Account SID / Auth Token / From Number — this is the *same* credential store as Admin → Integrations `sms_twilio`; whichever you fill in last wins, so just configure it once.
3. Send a real test SMS to a phone you control, confirm delivery and that a `Message` row is recorded.

### 3d. Email Server (real, per-workspace SMTP — `WorkspaceSmtpConfig`)
1. Go to **Email Server** (`/app/broadcasts/email-server`).
2. Enter your SMTP host/port + credentials + from-address, save, use the "test" action to send yourself a real email, confirm delivery.

### 3e. Inbox itself
1. After connecting at least one real channel and receiving/sending a real message, go to **Inbox** (`/app/inbox`).
2. Confirm the conversation appears, reply from the UI, confirm the reply actually sends (check the contact's real phone/WhatsApp/etc. received it) and shows status `sent`, not `failed`.
3. Test: assign to a team member, mark resolved/snoozed, typing indicator (open the same conversation in two browser tabs as two different users if you want to test real-time typing broadcast).

---

## Phase 4 — Contacts

1. `/contacts` — Add a contact manually, edit it, delete it.
2. **Bulk import** / **Import CSV** — upload a small CSV, confirm contacts appear.
3. **Export CSV** — download, confirm the file has your contacts.
4. **Segments** (`/segments`) — create a segment with a filter condition, confirm it only matches the right contacts.
5. Opt-in toggles (WA/SMS/Email badges) — toggle one, confirm it persists on reload.

---

## Phase 5 — Automations

This module got heavy real-bug fixes today — worth testing thoroughly.

1. **Create** an automation (`/app/automations` → New). Confirm the builder opens with just a Trigger node, no crash.
2. **Build a flow**: Trigger (Message Received) → Send WhatsApp/SMS node → Save. Reload the page — confirm the node and its configured text (body, etc.) both survived the reload (this used to silently fail/corrupt before today's fix).
3. **Activate** the automation (Draft → Active toggle) — confirm it flips and persists.
4. **Test button** — dry-run, confirm it shows a step-by-step trace with no real send.
5. **Real trigger test**: with a real channel connected (Phase 3), send yourself a real WhatsApp/SMS message that matches the trigger, and confirm the automation actually fires and actually sends the configured reply — check the automation's **Runs** page (`/app/automations/{uuid}/runs`) for a `completed` run with real logs, not `failed: No trigger node.`
6. **Wait/Delay node**: build Trigger → Wait 1 minute → Send SMS, fire it, confirm the run sits at `waiting` and then actually resumes and completes ~1 minute later (check the `jobs` table or just wait and refresh the Runs page) — this used to hang forever before today's fix.
7. **AI Reply / Run Chatbot node**: confirm the generated reply is actually delivered to the contact, not just logged.
8. Known gap, don't test as if it should work: **Google Calendar/Sheets/Docs/Forms nodes, WooCommerce/Shopify product nodes, Add to Campaign, WhatsApp Form/Catalog/Sequence nodes** are stubbed — they'll report "not available"/simulated results, that's expected for now.

---

## Phase 6 — Dashboard

1. `/app/dashboard` as a client with a real workspace — confirm real numbers: Workspaces count, Team members, Active automations, Contacts, and the KPI tiles/charts reflect real DB state (cross-check a couple against Contacts/Automations pages directly).
2. Switch the date range filter (7/30/90 days) — numbers/charts should change accordingly.
3. Onboarding nudge banner — complete a real step (e.g. connect a channel) and reload; the "next step" should advance. (Skip "Verify your email" — see Phase 1 note.)

---

## Phase 7 — Social Media

1. **Social Accounts** (`/app/social/accounts`) — connect Facebook/Instagram (uses `meta_app` creds) or LinkedIn/Twitter/YouTube/TikTok (each needs its own OAuth app configured in Admin → Integrations, Phase 2). Confirm a connected account shows up with name + picture.
2. **Post Composer** (`/app/social/composer`) — write a post, pick target account(s), schedule for a future time or "publish now."
3. **Posts** (`/app/social/posts`) — confirm it lists the post with correct status. If you have >20 posts, confirm pagination controls render without crashing (fixed today).
4. **Calendar** (`/app/social/calendar`) — confirm scheduled posts appear on the right day; test the Status/Account/Network filter dropdowns actually narrow the list (fixed today — used to silently do nothing).
5. Wait for a scheduled post's time to pass, confirm it actually publishes (check the real platform) for Facebook/Instagram/Twitter/LinkedIn.
6. **Known gap**: TikTok and YouTube publishing always fails right now (`TikTokDriver`/`YoutubeDriver` throw "not supported") — confirm it fails *cleanly* (post shows `failed` status with a message), not silently or with a server crash.
7. Sidebar highlighting — while on any Social Media page, confirm the correct sidebar item is highlighted green (fixed today — wildcard route matching was broken for every section after Calendar).

---

## Phase 8 — Broadcasting / Campaigns

1. `/app/broadcasts/campaigns` — create a campaign targeting a segment or all contacts, send a test send to yourself, then launch for real to a small test list.
2. Confirm delivery stats update as messages go out.

---

## Known limitations to keep in mind while testing (not bugs — don't file these)

- **Email verification**: doesn't exist yet (Phase 1).
- **TikTok / YouTube post publishing**: always fails (fails cleanly, not silently) (Phase 7).
- **Automation nodes**: Google Calendar/Sheets/Docs/Forms, e-commerce product cards, Add to Campaign, WhatsApp Form/Catalog/Sequence are stubbed (Phase 5).
- **Automation triggers** `campaign.sent`, `contact.tag_added`, `form.submitted` have never been wired up (pre-existing gap, not a regression) — don't build a test around these firing.
- Admin → Integrations: AI providers, Google Workspace, Qdrant, and cloud storage providers are listed but have no working save form yet.

---

## ⚠️ Before you actually deploy — one DB schema fix must travel with you

Earlier this session we found `automations.nodes`, `automations.edges`, `automations.trigger_config`, `automation_runs.context`, and `automation_run_logs.output` were wrongly created as `VARCHAR(255)` instead of the intended `JSON` type — causing hard save failures for any non-trivial automation. **This was fixed by hand, directly against the dev database** — there is no migration file for it. Before deploying to any other environment (staging/production), run this against that database too:

```sql
ALTER TABLE automations MODIFY nodes JSON NULL;
ALTER TABLE automations MODIFY edges JSON NULL;
ALTER TABLE automations MODIFY trigger_config JSON NULL;
ALTER TABLE automation_runs MODIFY context JSON NULL;
ALTER TABLE automation_run_logs MODIFY output JSON NULL;
```

If you have a schema-migration tool/process for this Java backend, turn this into a proper versioned migration rather than running it ad hoc again.
