# Running the Application

This guide walks through everything needed to build, configure, and run the **Hub Notification** Spring Boot backend on your machine, in Docker, or in CI.

---

## 1. Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | **21** (Temurin recommended) | Set by the Gradle toolchain in `build.gradle` — Gradle will fetch a matching JDK automatically if one isn't found. |
| Gradle | Not required | The project ships the **Gradle Wrapper** (`gradlew` / `gradlew.bat`), which downloads Gradle 8.10.2 on first run. |
| MySQL | 8.x | Only required for the `dev` and `prod` profiles. Skip this if you use the `local` profile (see below). |
| Docker | 24+ | Optional, only needed for container-based runs. |

Verify Java is installed and on your `PATH`:

```powershell
java -version
```

---

## 2. Project Location

All backend commands below are run from the `java-backend/` directory:

```powershell
cd java-backend
```

---

## 3. Choosing a Run Profile

The application ships four Spring profiles, selected via `SPRING_PROFILES_ACTIVE` (defaults to `dev`):

| Profile | Database | Schema handling | Best for |
|---|---|---|---|
| `local` | In-memory H2 (`MODE=MySQL`) | `ddl-auto: update` — schema is created automatically | **Fastest way to get running** — no external database needed |
| `dev` | MySQL on `localhost:3306` | `ddl-auto: validate` — schema must already exist | Local development against a real MySQL instance |
| `prod` | MySQL (SSL) | `ddl-auto: none` | Production deployments |
| `test` | Isolated test config | — | Running the automated test suite |

If you just want to see the app running immediately, use **`local`** — it needs zero configuration.

---

## 4. Quick Start (Windows PowerShell, `local` profile)

```powershell
cd java-backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

The app starts on **http://localhost:8080**. An in-memory H2 database is created and populated with the schema automatically; data is reset every time the app restarts.

### macOS / Linux equivalent

```bash
cd java-backend
chmod +x gradlew
./gradlew bootRun --args="--spring.profiles.active=local"
```

---

## 5. Running Against MySQL (`dev` profile)

1. Create a database:

   ```sql
   CREATE DATABASE whatsmine CHARACTER SET utf8mb4;
   ```

2. Export connection settings as environment variables (or pass them as `-D` system properties):

   ```powershell
   $env:DB_HOST="127.0.0.1"
   $env:DB_PORT="3306"
   $env:DB_DATABASE="whatsmine"
   $env:DB_USERNAME="root"
   $env:DB_PASSWORD="your_password"
   ```

3. Because `dev` uses `ddl-auto: validate`, the schema must already exist before startup — apply your schema/migration scripts to the `whatsmine` database first.

4. Run:

   ```powershell
   .\gradlew.bat bootRun --args="--spring.profiles.active=dev"
   ```

---

## 6. Environment Variables Reference

All configuration is externalized via environment variables (see `src/main/resources/application*.yml`). Every variable has a sensible default for local development except where noted.

### Core

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active profile: `local`, `dev`, `prod`, `test` |
| `PORT` | `8080` | HTTP port |
| `APP_NAME` | `Hub Notification` | Display name used in emails, UI, invoices |
| `APP_VERSION` | `1.0.0` | Reported on the health endpoint |
| `APP_URL` | `http://localhost:8080` | Base URL used for generated links (OAuth callbacks, invites, webhooks) |
| `APP_DEMO_MODE` | `false` | Enables read-only/demo restrictions |
| `APP_INSTALLED` | `true` | Marks the app as fully installed |

### Database

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `127.0.0.1` | MySQL host (`dev`/`prod`) |
| `DB_PORT` | `3306` | MySQL port |
| `DB_DATABASE` | `whatsmine` | Schema name |
| `DB_USERNAME` | `root` (dev) / required (prod) | MySQL username |
| `DB_PASSWORD` | empty (dev) / required (prod) | MySQL password |
| `DB_URL` | H2 in-memory URL | Only used by the `local` profile |

### WhatsApp / Meta Integration

| Variable | Default | Description |
|---|---|---|
| `WHATSAPP_API_URL` | `https://graph.facebook.com/v18.0` | Meta Graph API base URL |
| `WHATSAPP_API_VERSION` | `v18.0` | Graph API version |
| `WHATSAPP_SYSTEM_USER_TOKEN` | `demo_system_user_token` | System user access token |
| `META_APP_ID` | `demo_meta_app_id` | Meta App ID |
| `META_APP_SECRET` | `demo_meta_app_secret` | Meta App secret |
| `WHATSAPP_GLOBAL_VERIFY_TOKEN` | `wh_global_verify` | Webhook verification token |

### Licensing (self-hosted deployments only)

| Variable | Default | Description |
|---|---|---|
| `LICENSE_VERIFY` | `false` | Enable license verification against a license server |
| `LICENSE_SERVER_URL` | empty | License API base URL |
| `LICENSE_API_KEY` | empty | License API key |
| `LICENSE_PRODUCT_ID` | empty | Product identifier |
| `LICENSE_VERIFY_TYPE` | `non_envato` | Verification mode |
| `LICENSE_CACHE_HOURS` | `12` | How long a successful check is cached |

> Licensing is disabled by default. It only matters if you are redistributing/reselling this build; a normal self-hosted operator can leave it off.

Set variables for a session in PowerShell like this:

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="secret"
.\gradlew.bat bootRun
```

Or create a `.env`-style block and load it via your process manager / container orchestrator of choice — Spring Boot reads all of the above from the process environment automatically.

---

## 7. Building an Executable JAR

```powershell
.\gradlew.bat bootJar
```

The runnable artifact is produced at:

```
java-backend/build/libs/whats-mine-backend-1.0.0-SNAPSHOT.jar
```

Run it directly:

```powershell
java -jar build\libs\whats-mine-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## 8. Running with Docker

The project includes a multi-stage `Dockerfile` (compiles with the Gradle wrapper, ships only the JRE + jar).

```powershell
cd java-backend
docker build -t hub-notification-backend .
docker run -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=prod `
  -e DB_HOST=host.docker.internal `
  -e DB_DATABASE=whatsmine `
  -e DB_USERNAME=root `
  -e DB_PASSWORD=secret `
  hub-notification-backend
```

The image defaults `SPRING_PROFILES_ACTIVE=prod` and exposes port `8080`. It includes a built-in Docker `HEALTHCHECK` that polls `/actuator/health`.

---

## 9. Verifying the App Is Running

Once started, check the health endpoint:

```powershell
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{ "status": "UP" }
```

Other useful Actuator endpoints (exposed by default): `/actuator/info`, `/actuator/metrics`.

---

## 10. Running Tests

```powershell
.\gradlew.bat test
```

Test-specific configuration lives in `src/test/resources/application-test.yml` and runs under the `test` profile automatically via JUnit 5 (`useJUnitPlatform()`).

---

## 11. Common Issues

| Symptom | Likely Cause | Fix |
|---|---|---|
| `Communications link failure` on startup | MySQL not reachable / wrong `DB_HOST`/`DB_PORT` | Confirm MySQL is running and credentials are correct, or switch to the `local` (H2) profile |
| `Schema-validation: missing table` | Using `dev`/`prod` profile against an empty database | `dev`/`prod` never auto-create tables (`ddl-auto: validate`/`none`); apply your schema first, or use `local` |
| Port `8080` already in use | Another process is bound to the port | Set `PORT=8081` (or any free port) before starting |
| `gradlew: command not found` | Wrapper script isn't executable (macOS/Linux) | `chmod +x gradlew` |

---

## 12. Handy Commands Cheat-Sheet

```powershell
# Fastest local run (H2, zero config)
.\gradlew.bat bootRun --args="--spring.profiles.active=local"

# Clean build
.\gradlew.bat clean build

# Build without running tests
.\gradlew.bat build -x test

# Package a runnable jar
.\gradlew.bat bootJar

# Run the packaged jar
java -jar build\libs\whats-mine-backend-1.0.0-SNAPSHOT.jar

# Run tests only
.\gradlew.bat test

# Build the Docker image
docker build -t hub-notification-backend ./java-backend
```
