import { spawn } from "node:child_process";
import fs from "node:fs";
import type { NativeSpec, ServiceId } from "./types.ts";
import { logPath, pidPath } from "./state.ts";

export function nativeStart(id: ServiceId, spec: NativeSpec): number {
  const existing = readPid(id);
  if (existing && processAlive(existing)) {
    return existing;
  }

  const out = fs.openSync(logPath(id), "a");
  const err = fs.openSync(logPath(id), "a");

  const env = { ...process.env, ...(spec.env ?? {}) };

  const child = spawn(spec.command, spec.args, {
    cwd: spec.cwd,
    env,
    detached: true,
    stdio: ["ignore", out, err],
  });

  if (!child.pid) {
    fs.closeSync(out);
    fs.closeSync(err);
    throw new Error(`failed to spawn ${spec.command}`);
  }

  fs.writeFileSync(pidPath(id), String(child.pid));
  child.unref();
  return child.pid;
}

export function nativeStop(id: ServiceId): boolean {
  const pid = readPid(id);
  if (!pid) return false;
  try {
    // Negative pid → kill the process group (detached spawn creates one).
    process.kill(-pid, "SIGTERM");
  } catch {
    try {
      process.kill(pid, "SIGTERM");
    } catch {
      /* already dead */
    }
  }
  fs.rmSync(pidPath(id), { force: true });
  return true;
}

export function readPid(id: ServiceId): number | null {
  try {
    const raw = fs.readFileSync(pidPath(id), "utf8").trim();
    const pid = Number(raw);
    return Number.isFinite(pid) && pid > 0 ? pid : null;
  } catch {
    return null;
  }
}

export function processAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

export function nativeLogs(id: ServiceId, tail = 200): string {
  try {
    const content = fs.readFileSync(logPath(id), "utf8");
    const lines = content.split("\n");
    return lines.slice(-tail).join("\n");
  } catch {
    return "";
  }
}
