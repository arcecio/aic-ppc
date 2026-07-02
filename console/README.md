# AIP PPC Console

A local SPA + tiny Express server for starting / stopping / checking health on
the four moving pieces of the stack: **postgres**, **backend**, **frontend**,
**TEI**. Ported from the Blue reference's `console/`, adapted to this stack's
ports. Dev tooling only — not part of the delivered product.

Each service has a mode toggle:

- **Docker** — shells out to `docker compose up -d <svc>` / `stop <svc>` against
  the repo's `docker-compose.yml` (TEI uses the `docker-tei` profile). Postgres
  is docker-only (no native install).
- **Native** — spawns the local dev command (`./gradlew bootRun`, `npm run dev`,
  `text-embeddings-router …`) detached, writing logs to
  `/tmp/aip-console/<id>.log` and the PID to `/tmp/aip-console/<id>.pid`.

Health is probed every 3s:

- TCP connect for postgres (`:5434`).
- HTTP probe for the rest (any response = up; connection refused = down).
  Backend probes `:8082` in docker mode and `:8080` (bootRun) in native mode;
  frontend probes `:8095` (nginx) vs `:5173` (Vite); TEI probes `:8086` either way.

## Run it

```bash
cd console
npm run install:all   # one-time
npm run dev
```

That brings up both halves via `concurrently`:

- Server on `http://127.0.0.1:9091` (REST under `/api/*`)
- SPA on `http://127.0.0.1:5175` (proxies `/api` to the server)

Open <http://127.0.0.1:5175>.

(Ports are offset from Blue's console — 9090/5174 — so both consoles can run on
the same machine.)

## REST

| Method | Path | Description |
|---|---|---|
| GET | `/api/services` | State of all services (mode, status, pid/container) |
| GET | `/api/services/:id` | State of one service |
| POST | `/api/services/:id/mode` | Switch mode: `{"mode": "docker" \| "native"}` |
| POST | `/api/services/:id/start` | Start in the current mode |
| POST | `/api/services/:id/stop` | Stop (docker stop / SIGTERM to the process group) |
| GET | `/api/services/:id/logs?tail=200` | Tail logs (docker logs / native log file) |
