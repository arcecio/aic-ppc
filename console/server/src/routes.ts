import { Router } from "express";
import { SERVICES, findService } from "./services.ts";
import { getMode, setMode } from "./state.ts";
import { probe } from "./health.ts";
import {
  dockerContainerStatus,
  dockerLogs,
  dockerStart,
  dockerStop,
} from "./docker.ts";
import {
  nativeLogs,
  nativeStart,
  nativeStop,
  processAlive,
  readPid,
} from "./native.ts";
import type { Mode, ServiceState } from "./types.ts";

export const router = Router();

async function buildState(id: string): Promise<ServiceState | null> {
  const def = findService(id);
  if (!def) return null;
  const mode = getMode(def.id);
  const status = await probe(def.health, mode);

  let pid: number | null = null;
  let containerStatus: string | null = null;

  if (mode === "native") {
    const p = readPid(def.id);
    pid = p && processAlive(p) ? p : null;
  } else if (def.docker) {
    containerStatus = await dockerContainerStatus(def.docker);
  }

  return {
    id: def.id,
    label: def.label,
    description: def.description,
    mode,
    availableModes: def.modes,
    status,
    pid,
    containerStatus,
  };
}

router.get("/services", async (_req, res) => {
  const states = await Promise.all(SERVICES.map((s) => buildState(s.id)));
  res.json(states.filter(Boolean));
});

router.get("/services/:id", async (req, res) => {
  const state = await buildState(req.params.id);
  if (!state) return res.status(404).json({ error: "unknown service" });
  res.json(state);
});

router.post("/services/:id/mode", async (req, res) => {
  const def = findService(req.params.id);
  if (!def) return res.status(404).json({ error: "unknown service" });
  const mode = (req.body?.mode ?? "") as Mode;
  if (!def.modes.includes(mode)) {
    return res.status(400).json({ error: `mode '${mode}' not available for ${def.id}` });
  }
  setMode(def.id, mode);
  res.json(await buildState(def.id));
});

router.post("/services/:id/start", async (req, res) => {
  const def = findService(req.params.id);
  if (!def) return res.status(404).json({ error: "unknown service" });
  const mode = getMode(def.id);
  try {
    if (mode === "docker") {
      if (!def.docker) throw new Error(`${def.id} has no docker spec`);
      await dockerStart(def.docker);
    } else {
      if (!def.native) throw new Error(`${def.id} has no native spec`);
      nativeStart(def.id, def.native);
    }
    res.json(await buildState(def.id));
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    res.status(500).json({ error: msg });
  }
});

router.post("/services/:id/stop", async (req, res) => {
  const def = findService(req.params.id);
  if (!def) return res.status(404).json({ error: "unknown service" });
  const mode = getMode(def.id);
  try {
    if (mode === "docker") {
      if (!def.docker) throw new Error(`${def.id} has no docker spec`);
      await dockerStop(def.docker);
    } else {
      nativeStop(def.id);
    }
    res.json(await buildState(def.id));
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    res.status(500).json({ error: msg });
  }
});

router.get("/services/:id/logs", async (req, res) => {
  const def = findService(req.params.id);
  if (!def) return res.status(404).json({ error: "unknown service" });
  const tail = Math.min(2000, Number(req.query.tail) || 200);
  const mode = getMode(def.id);
  const logs =
    mode === "docker" && def.docker
      ? await dockerLogs(def.docker, tail)
      : nativeLogs(def.id, tail);
  res.type("text/plain").send(logs);
});
