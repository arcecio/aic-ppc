import fs from "node:fs";
import path from "node:path";
import type { Mode, ServiceId } from "./types.ts";
import { SERVICES } from "./services.ts";

export const RUNTIME_DIR = "/tmp/aip-console";
const STATE_FILE = path.join(RUNTIME_DIR, "state.json");

fs.mkdirSync(RUNTIME_DIR, { recursive: true });

type Persisted = { mode: Record<ServiceId, Mode> };

function load(): Persisted {
  try {
    const raw = fs.readFileSync(STATE_FILE, "utf8");
    return JSON.parse(raw);
  } catch {
    const initial: Persisted = { mode: {} as Record<ServiceId, Mode> };
    for (const s of SERVICES) initial.mode[s.id] = s.defaultMode;
    return initial;
  }
}

let state = load();

function save() {
  fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

export function getMode(id: ServiceId): Mode {
  const stored = state.mode[id];
  if (stored) return stored;
  const def = SERVICES.find((s) => s.id === id);
  return def?.defaultMode ?? "docker";
}

export function setMode(id: ServiceId, mode: Mode) {
  state.mode[id] = mode;
  save();
}

export function pidPath(id: ServiceId): string {
  return path.join(RUNTIME_DIR, `${id}.pid`);
}

export function logPath(id: ServiceId): string {
  return path.join(RUNTIME_DIR, `${id}.log`);
}
