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

# Text Embeddings Inference (TEI) Setup Guide

This guide provides instructions for deploying and troubleshooting Hugging Face's Text Embeddings Inference (TEI) server for large models (e.g., `intfloat/e5-large-v2`). It covers optimized setups for NVIDIA GPUs and Apple Silicon, as well as common memory exhaustion issues.

## Table of Contents

* [Running on NVIDIA GPUs (Docker)](https://www.google.com/search?q=%23running-on-nvidia-gpus-docker)
* [Running on Apple Silicon (macOS Native)](https://www.google.com/search?q=%23running-on-apple-silicon-macos-native)
* [Troubleshooting: Server Hangs on "Warming up model"](https://www.google.com/search?q=%23troubleshooting-server-hangs-on-warming-up-model)
* [Verifying Hardware Acceleration](https://www.google.com/search?q=%23verifying-hardware-acceleration)

---

## Running on NVIDIA GPUs (Docker)

To utilize NVIDIA GPUs, you must run TEI via Docker using the correct architecture-specific image.

**Prerequisites:**

* Docker installed.
* [NVIDIA Container Toolkit](https://www.google.com/search?q=https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html) installed on the host system to enable GPU passthrough.

**Launch Command:**
For NVIDIA Blackwell architecture (e.g., RTX 5000 series), use the `120` image tag.

```bash
docker run --gpus all \
  -p 8086:80 \
  -v $PWD/data:/data \
  --pull always \
  ghcr.io/huggingface/text-embeddings-inference:120-1.9 \
  --model-id intfloat/e5-large-v2 \
  --max-client-batch-size 8 \
  --max-batch-tokens 4096 \
  --max-concurrent-requests 64

```

> **Note:** If switching from a previous CPU-bound container, ensure you stop and remove the old container (`docker stop <id>` and `docker rm <id>`) before launching the GPU container to prevent port conflicts.

---

## Running on Apple Silicon (macOS Native)

**Do not use Docker on macOS.** Docker on macOS runs inside a Linux virtual machine, which cannot access the physical Apple Silicon GPU (M-series). If run via Docker, TEI will fall back to the CPU and perform poorly.

To utilize Apple's Metal Performance Shaders (MPS), you must install and run the server natively.

**1. Install via Homebrew:**
Hugging Face provides a pre-compiled, Metal-optimized binary for macOS.

```bash
brew install text-embeddings-inference

```

**2. Launch the Native Router:**
Weights are automatically stored in your native `~/.cache/huggingface/hub` directory.

```bash
text-embeddings-router \
  --model-id intfloat/e5-large-v2 \
  --max-client-batch-size 8 \
  --max-batch-tokens 4096 \
  --max-concurrent-requests 64 \
  --port 8086

```

---

## Troubleshooting: Server Hangs on "Warming up model"

If the server logs reach `Warming up model` and hang indefinitely (or the process crashes silently), the host system has likely run out of memory (RAM/VRAM). During the warmup phase, TEI runs a forward pass using your maximum configured batch limits to pre-allocate memory.

**Resolution:**
Lower the batch and concurrency limits in your launch command. Heavy models like `e5-large-v2` require massive memory to initialize at default settings.

Add or adjust the following flags to establish a safe baseline:

```bash
--max-client-batch-size 8 \
--max-batch-tokens 4096 \
--max-concurrent-requests 64

```

*Once the server successfully boots with these baseline settings, you can incrementally increase `--max-batch-tokens` to maximize your specific hardware's throughput.*

---

## Verifying Hardware Acceleration

Ensure your server is actually utilizing the GPU rather than falling back to the CPU.

### For NVIDIA (Linux/Windows)

1. Send a test embedding request to the server.
2. In a separate terminal, run `watch -n 1 nvidia-smi`.
3. You should see the `text-embeddings-router` process consuming VRAM, with GPU utilization spiking when the request is processed.

### For Apple Silicon (macOS)

1. Send a test request to your local server:
```bash
curl 127.0.0.1:8086/embed \
  -X POST \
  -d '{"inputs":"Testing the Metal backend"}' \
  -H 'Content-Type: application/json'

```


2. **GUI Check:** Open `Activity Monitor`, press `Cmd + 4` (GPU History), and watch for a spike when the request hits.
3. **Terminal Check:** Run `sudo powermetrics --samplers gpu_power -n 10` and monitor the `GPU active residency` percentage. It should jump significantly from 0% when processing the payload.
