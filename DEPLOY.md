# Deploying MyWorkTea

VPS + Docker + GitHub Actions. Push to `main` → GitHub builds a Docker image,
pushes it to GitHub Container Registry (GHCR), then SSHes into your VPS and
restarts the app container with the new image. Every run's logs live under
the repo's **Actions** tab — that's your visibility into what shipped, when,
and whether it succeeded.

Three containers run on the VPS:
- **app** — the Spring Boot jar, built from `Dockerfile`
- **db** — MySQL, with a persistent volume for data
- **caddy** — reverse proxy that terminates HTTPS (automatic Let's Encrypt
  certs) and forwards to `app`

## 1. One-time VPS setup

Any Ubuntu 22.04+ droplet/VPS (DigitalOcean, Hetzner, Linode, etc.) works.
2GB RAM is enough to start.

```bash
# On the VPS, as root (or with sudo):
apt update && apt install -y docker.io docker-compose-plugin
systemctl enable --now docker

# Create a dedicated deploy user instead of using root over SSH
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
```

Generate a dedicated SSH key pair **on your own machine** (not the VPS) for
GitHub Actions to use — don't reuse your personal key:

```bash
ssh-keygen -t ed25519 -f ./gha_deploy_key -C "github-actions-deploy" -N ""
```

Add the public key to the VPS's `deploy` user:

```bash
# still on your machine
ssh-copy-id -i gha_deploy_key.pub deploy@YOUR_VPS_IP
```

Point your domain's DNS `A` record at the VPS's IP now, so the certificate
step below can succeed.

## 2. One-time GitHub setup

In the repo: **Settings → Secrets and variables → Actions → New repository
secret**. Add:

| Secret | Value |
|---|---|
| `VPS_HOST` | your VPS's IP or hostname |
| `VPS_USER` | `deploy` |
| `VPS_SSH_KEY` | contents of `gha_deploy_key` (the **private** key) |
| `VPS_PORT` | `22` (only needed if you changed the SSH port) |

No GHCR credentials needed — the workflow uses the automatically-provided
`GITHUB_TOKEN`.

By default a package published by a workflow's `GITHUB_TOKEN` is private.
Either make it public once (**repo → Packages → package → Package settings →
Change visibility**), or grant your VPS's pull access — public is simpler for
a project like this with no proprietary code in the image.

## 3. First deploy (bootstrap the server)

The workflow only *updates* the `app` container — it assumes `docker-compose.yml`,
`Caddyfile`, and `.env` already exist on the server. Set those up once:

```bash
ssh deploy@YOUR_VPS_IP
mkdir -p ~/myworktea && cd ~/myworktea
```

Copy `docker-compose.yml`, `Caddyfile`, and `.env.example` from this repo to
that directory (`scp` from your machine, or `git clone` the repo there and
symlink/copy them out — either works, the server doesn't need the source, only
these three files).

Edit `Caddyfile`, replacing `your-domain.com` with your real domain.

Copy `.env.example` to `.env` and fill in real values:

```bash
cp .env.example .env
nano .env   # fill in DBPASSWORD, DB_ROOT_PASSWORD, MAILHOST, ADMINPASSWORD, etc.
```

Log in to GHCR so `docker compose pull` can fetch the image (skip this if you
made the package public):

```bash
echo YOUR_GITHUB_PAT | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

Bring everything up:

```bash
docker compose up -d
docker compose ps        # all three should show "healthy" / "running"
docker compose logs -f app   # watch it start; Ctrl-C to stop watching
```

Visit your domain — Caddy should have already provisioned a TLS certificate
automatically on first request.

## 4. Day-to-day: how deploys happen

Push (or merge a PR) to `main`. That's it. The workflow:

1. Builds the jar and runs the test suite (`./mvnw clean verify`) — a failing
   build or test **stops here**, nothing gets deployed.
2. Builds the Docker image and pushes `:latest` and `:<commit-sha>` to GHCR.
3. SSHes into the VPS and runs `docker compose pull app && docker compose up
   -d --no-deps app` — only the app container restarts; the database and
   Caddy are untouched.

Watch it happen live under the repo's **Actions** tab: each push gets its own
run with expandable build/test/deploy log output and a pass/fail status.

To deploy without pushing new code (e.g. re-run after fixing a secret), use
**Actions → Build and deploy → Run workflow** (the `workflow_dispatch`
trigger).

## 5. Managing the running app

All commands below run on the VPS, from `~/myworktea`.

**Status of everything:**
```bash
docker compose ps
```

**Live logs** (Ctrl-C to stop watching, doesn't stop the app):
```bash
docker compose logs -f app        # just the app
docker compose logs -f            # everything, interleaved
docker compose logs --since 1h app
```

**Restart** (e.g. after editing `.env`):
```bash
docker compose restart app
```

**Stop / start everything:**
```bash
docker compose down     # stops and removes containers (volumes/data persist)
docker compose up -d    # brings it back up
```

**Shell into the running app container** (debugging):
```bash
docker compose exec app sh
```

## 6. Rolling back

Every image is also tagged with its commit SHA, so you can pin to a known-good
build instead of `latest`:

```bash
docker compose pull app  # no-op if already have it; or:
docker pull ghcr.io/astevenmics/ratemyworkplacetemp:<good-sha>
docker tag ghcr.io/astevenmics/ratemyworkplacetemp:<good-sha> \
           ghcr.io/astevenmics/ratemyworkplacetemp:latest
docker compose up -d --no-deps app
```

Find `<good-sha>` from the Actions tab (the run for the last commit that
worked) or `git log --oneline`.

## 7. Backups

The database lives in the `db-data` Docker volume. Take a logical dump
regularly (cron this):

```bash
docker compose exec db sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" myworktea' > backup-$(date +%F).sql
```

(`$MYSQL_ROOT_PASSWORD` is read from inside the `db` container's own environment, set from `DB_ROOT_PASSWORD` in `.env` — no need to have `.env` sourced into your shell.)

Copy dumps off the VPS (e.g. `scp` to your machine or push to object storage)
— a backup that only lives on the box it's backing up isn't a backup.

Uploaded proof documents live in the `uploads` Docker volume
(`docker volume inspect myworktea_uploads` for its path); back that up too if
it matters to you.

## 8. Updating secrets / config

Edit `.env` on the VPS, then:
```bash
docker compose restart app
```
`.env` is never committed to git (see `.gitignore`) and never touches GitHub
Actions — it only exists on the VPS.

## 9. Troubleshooting

- **App container keeps restarting**: `docker compose logs app` — almost
  always a missing/wrong required env var (`DBURL`, `MAILHOST`, `ADMINUSERNAME`,
  etc. have no defaults and the app refuses to start without them).
- **Votes/rate-limits feel shared across all visitors**: check
  `APP_RATELIMIT_TRUST_FORWARDED_FOR=true` is set in `.env` — without it,
  every request looks like it's coming from Caddy's internal IP.
- **No TLS certificate / HTTPS not working**: confirm DNS actually points at
  the VPS (`dig your-domain.com`) before Caddy tries to provision — Let's
  Encrypt needs to reach port 80 on that domain.
- **GHCR pull fails on the VPS**: either the package is still private and the
  VPS isn't logged in (`docker login ghcr.io`), or the image tag doesn't
  exist yet (check the Actions run actually pushed successfully).
