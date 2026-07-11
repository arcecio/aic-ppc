# Deploying AIP PPC to the web

The simplest production deployment is **one small cloud VM running the
existing Docker Compose stack** plus a production override that adds
HTTPS. Everything the app needs ships in this repo already:

- `docker-compose.yml` — the full stack: Postgres 16 + pgvector, Spring Boot
  backend, React frontend behind nginx (which proxies `/api` to the backend),
  and an optional TEI embeddings sidecar.
- `docker-compose.prod.yml` — production override: adds a
  [Caddy](https://caddyserver.com) reverse proxy for automatic Let's Encrypt
  HTTPS, un-publishes the internal dev ports, and makes `DB_PASS` /
  `JWT_SECRET` mandatory.
- `deploy/Caddyfile` — the (tiny) Caddy config.

Static hosts (Vercel, Netlify, GitHub Pages) are **not** an option on their
own: the frontend is just the visible half of a stateful system that needs a
JVM backend, Postgres **with the pgvector extension**, a persistent disk for
uploaded plan files, an always-on process for the monthly knowledgebase
refresh, and ~110 MB request bodies.

## 1. Provision a VM

Any Linux VM with Docker works. 2 vCPU / 4 GB RAM is comfortable for the
JVM + Postgres; add ~20 GB of disk headroom for uploaded plans. Concrete
options in the $10–25/month range:

- Hetzner Cloud CX22
- DigitalOcean 4 GB droplet
- AWS Lightsail 4 GB instance

Install Docker Engine with the Compose plugin (Compose **v2.24.4+** is
required by the override file; any current Docker install qualifies):

```bash
curl -fsSL https://get.docker.com | sh
```

Open ports 22, 80 and 443 in the provider firewall / security group. Nothing
else needs to be exposed — Postgres and the backend stay on the internal
Docker network in production.

## 2. Point DNS at the VM

Create an A record (e.g. `ppc.example.com`) pointing at the VM's public IP.
Caddy uses this hostname to obtain a Let's Encrypt certificate automatically
on first boot — no certbot, no manual certificates.

## 3. Configure

```bash
git clone <this-repo> aic-ppc && cd aic-ppc
cp .env.example .env
```

Edit `.env`. Required for production:

| Variable | Value |
|---|---|
| `DOMAIN` | The DNS name from step 2, e.g. `ppc.example.com` |
| `DB_PASS` | A strong generated password (e.g. `openssl rand -base64 24`) |
| `JWT_SECRET` | ≥ 256-bit random secret (e.g. `openssl rand -base64 48`) |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | The email that gets auto-promoted to STAFF + ADMIN on boot — register this account first |

Optional:

- `ANTHROPIC_API_KEY` — enables AI-assisted analysis. When blank the engine
  runs rules-only with a deterministic heuristic fallback, so the app works
  without it.
- Embeddings — the vector retrieval arm degrades gracefully to lexical-only
  when no embedding service is reachable. On a linux/amd64 VM you can run the
  bundled TEI sidecar: add `--profile docker-tei` to the compose commands
  below and set `EMBEDDING_URL=http://tei:80` in `.env` (first boot downloads
  ~1.3 GB of model weights).

> Note: `DB_URL`, `STORAGE_PATH`, `EMBEDDING_*` defaults in `.env.example`
> target local (non-Docker) development. Inside the compose stack the backend
> already receives the correct container-network values from
> `docker-compose.yml`, so you don't need to change them.

## 4. Launch

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

First build takes a few minutes (Gradle + npm). Then visit
`https://$DOMAIN`, register the bootstrap admin account, and upload a test
plan.

## 5. Operate

**Logs**

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend
```

**Update to a new version**

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Database schema migrations (Flyway) run automatically on backend startup.

**Backups** — two things hold state, both in named Docker volumes:

```bash
# Database
docker exec aip-ppc-postgres pg_dump -U aipppc aipppc | gzip > backup-$(date +%F).sql.gz

# Uploaded plan files (the storage_data volume mounted at /app/storage in the backend;
# --volumes-from reads it by the fixed container name, independent of the compose project prefix)
docker run --rm --volumes-from aip-ppc-backend -v "$PWD":/backup alpine \
  tar czf /backup/storage-$(date +%F).tar.gz /app/storage
```

`docker compose down` (without `-v`) keeps all volumes; data survives
restarts and rebuilds.

## Local smoke test of the production stack

On any machine with Docker, set `DOMAIN=localhost` (plus `DB_PASS` and
`JWT_SECRET`) in `.env` and run the same `up` command. Caddy serves
`https://localhost` with a self-signed certificate — use `curl -k` or accept
the browser warning.

## Alternatives considered

- **Railway / Render / Fly.io** — all can run this stack from the existing
  Dockerfiles and cost about the same as a VM. You'd deploy the backend and
  frontend as separate services, which means extra per-platform config: a
  managed Postgres that supports **pgvector**, a persistent volume for
  `/app/storage`, an always-on (not scale-to-zero) backend instance for the
  monthly scheduler, and routing that keeps the frontend and `/api` on one
  origin. Reasonable if you want managed infra, but more moving parts than
  `docker compose up` on one box.
- **Vercel / Netlify / GitHub Pages** — frontend-only hosts; not viable, as
  explained above.
- **Kubernetes / ECS** — overkill for a single-tenant assistant at this
  stage.
