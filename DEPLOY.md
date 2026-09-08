# Deployment runbook — GitHub → Jenkins → Server

This is the hands-on setup that has to happen once, by you, before the pipeline (`Jenkinsfile`) can run automatically. Everything here needs a real cloud account / real server, so it can't be done from an assistant session — follow it in order.

Pipeline files already in the repo, in case you're jumping in partway:
- `Jenkinsfile` — the pipeline itself (has two `TODO`s you must fill in: `REGISTRY`/`PROD_HOST`)
- `deploy/docker-compose.yml` + `deploy/.env.example` — the stack that runs on the server
- `deploy/nginx/hubnotification.conf` — reverse proxy + TLS
- `java-backend/src/main/resources/db/migration/` — Flyway migrations (already verified working locally this session)

---

## 1. Provision the server

Any VPS works; a single **Ubuntu 22.04 LTS, 2 vCPU / 4 GB RAM, 40+ GB disk** box is enough to run the app + MySQL + nginx together for a small-to-medium workload. DigitalOcean, Hetzner, or AWS Lightsail are the least fiddly for a single-box setup — pick whichever you already have billing with.

Once you have SSH access as root:

```bash
# Create a non-root deploy user (Jenkins and you will SSH in as this user, not root)
adduser deploy
usermod -aG sudo deploy

# Firewall: only SSH, HTTP, HTTPS
apt update && apt install -y ufw
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable

# Docker + Compose plugin
curl -fsSL https://get.docker.com | sh
usermod -aG docker deploy
```

Log out and back in as `deploy` from here on.

## 2. Point your domain at the server

Create an A record for your domain (or subdomain) pointing at the server's public IP. Confirm with `dig yourdomain.com` before moving on — certbot in step 5 will fail without this.

## 3. First-time app bootstrap (by hand, before Jenkins exists)

```bash
mkdir -p /opt/hubnotifi
# From your own machine, copy just the deploy/ folder:
scp -r deploy/* deploy/.env.example deploy@YOUR_SERVER:/opt/hubnotifi/
```

On the server:

```bash
cd /opt/hubnotifi
cp .env.example .env
nano .env   # fill in real DB_PASSWORD, MYSQL_ROOT_PASSWORD, APP_URL

# First boot: mysql + nginx (app image doesn't exist in the registry yet, that's Jenkins' job later)
docker compose up -d mysql
docker compose logs -f mysql   # wait for "ready for connections", then Ctrl-C
```

You don't have an app image to run yet — that gets pushed by the *first Jenkins build* in step 6. Leave `mysql` running and move on.

## 4. Set up TLS

```bash
docker compose up -d nginx   # nginx.conf as shipped only serves the ACME challenge over :80 so far
docker compose run --rm certbot certonly --webroot -w /var/www/certbot -d yourdomain.com
```

Then edit `deploy/nginx/hubnotification.conf` on the server: uncomment the redirect in the `:80` block and the whole `:443` block (instructions are inline in the file), and:

```bash
docker compose restart nginx
```

Add a renewal cron (certs expire every 90 days):

```bash
echo "0 3 * * * cd /opt/hubnotifi && docker compose run --rm certbot renew && docker compose restart nginx" | sudo tee /etc/cron.d/certbot-renew
```

## 5. Install Jenkins

Simplest: Jenkins in Docker, on the **same server** to start (move it to a second small box later if you want build failures fully isolated from the running app — not necessary for a first setup).

```bash
docker volume create jenkins_home
docker run -d --name jenkins --restart unless-stopped \
  -p 8081:8080 -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v $(which docker):/usr/bin/docker \
  jenkins/jenkins:lts
```

(Port `8081` on the host, not `8080` — the app container already owns `127.0.0.1:8080`.)

```bash
docker logs jenkins   # copy the initial admin password it prints
```

Visit `http://YOUR_SERVER_IP:8081` (or tunnel it — don't leave Jenkins's UI open to the whole internet long-term; either firewall port 8081 to your own IP only, or put it behind the same nginx + a basic-auth block once things are stable), unlock with that password, install the **suggested plugins** plus these specifically:
- GitHub Integration
- Docker Pipeline
- SSH Agent
- Pipeline (usually already in "suggested")

Create your Jenkins admin account when prompted.

## 6. Jenkins credentials

Manage Jenkins → Credentials → System → Global credentials → Add Credentials, three times:

| Kind | ID (must match exactly) | Value |
|---|---|---|
| Username with password | `docker-registry-creds` | Your Docker Hub username + an access token (Docker Hub → Account Settings → Security → New Access Token — don't use your real password) |
| SSH Username with private key | `prod-server-ssh` | Username `deploy`, private key = the key that can SSH into the server as `deploy` |
| Secret text | `prod-db-password` | The same `DB_PASSWORD` you put in `/opt/hubnotifi/.env` |

## 7. Create the Jenkins job

New Item → **Pipeline** (or "Multibranch Pipeline" if you want PRs/branches building too, but this Jenkinsfile is written for a single `main`-branch deploy job):
- Pipeline → Definition: "Pipeline script from SCM"
- SCM: Git, Repository URL: `https://github.com/srjvishaldeveloper/hubnotifi-01.git`
- Branch: `*/main`
- Script Path: `Jenkinsfile`
- Build Triggers: check **"GitHub hook trigger for GITScm polling"**

Before the first real build, open `Jenkinsfile` in the repo and fill in the two `TODO`s (`REGISTRY`, `PROD_HOST`), commit, push.

## 8. GitHub webhook

Repo → Settings → Webhooks → Add webhook:
- Payload URL: `http://YOUR_SERVER_IP:8081/github-webhook/` (note the trailing slash)
- Content type: `application/json`
- Events: "Just the push event"

Push anything to `main` and confirm Jenkins picks it up (Jenkins job page → should show a new build starting within seconds).

## 9. Watch the first real deploy

The first build's **Deploy** stage will `docker compose pull app` an image that doesn't exist on the server yet the very first time only if the earlier **Docker build & push** stage failed — if that stage succeeded, the image is already in your registry and `pull` will work. Watch the Jenkins console output stage-by-stage; if the **Health check** stage fails, the `post { failure { ... } }` block attempts to pull+restart whatever was previously `:latest` — on a true first deploy there is no "previous" yet, so a first-build failure just needs manual investigation via `docker compose logs app` on the server, not a rollback.

Once green, confirm from your own machine:
```bash
curl -I https://yourdomain.com/actuator/health
```

## 10. Prove the safety net works

Push a commit that breaks a test on purpose, confirm the Jenkins build fails at the **Build + test backend** stage (not blocking on the pre-existing test debt — see the comment in that stage — so use a compile error or an assertion in a *new* test if you want a clean failure signal) and never reaches Deploy. Then revert and push again to confirm recovery.

---

## Notes / things to revisit once this is running for real

- The `Migrate database` stage's exact one-shot Flyway invocation is a starting point, not proven against a real server yet (see the comment in the Jenkinsfile) — the app's own Flyway-on-startup (already verified working locally this session) is a safety net either way, so a rough first version of this stage is fine to ship and refine.
- Consider moving Jenkins to its own small VPS once budget allows, so a runaway build can't compete with the running app for CPU/RAM on the same box.
- The frontend build output under `java-backend/src/main/resources/static/build/` is still tracked in git from before this pipeline existed — harmless to leave as-is (CI rebuilds it fresh every time now), but you could add it to `.gitignore` later to stop it showing up in diffs.
