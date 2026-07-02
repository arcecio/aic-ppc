import net from "node:net";
import type { HealthProbe, Mode, Status } from "./types.ts";

export async function probe(
  health: HealthProbe,
  mode: Mode,
  timeoutMs = 1500,
): Promise<Status> {
  if (health.kind === "tcp") return tcpProbe(health.host, health.port, timeoutMs);
  const url = health.urlByMode[mode];
  if (!url) return "unknown";
  return httpProbe(url, timeoutMs);
}

async function httpProbe(url: string, timeoutMs: number): Promise<Status> {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: controller.signal });
    // Treat any HTTP response (even 4xx/5xx) as "process is listening".
    // Spring Boot actuator returns 200 with {status:UP}; refine if needed.
    if (res.ok) return "up";
    if (res.status >= 400 && res.status < 600) return "up";
    return "unknown";
  } catch {
    return "down";
  } finally {
    clearTimeout(t);
  }
}

function tcpProbe(host: string, port: number, timeoutMs: number): Promise<Status> {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    let resolved = false;
    const done = (s: Status) => {
      if (resolved) return;
      resolved = true;
      socket.destroy();
      resolve(s);
    };
    socket.setTimeout(timeoutMs);
    socket.once("connect", () => done("up"));
    socket.once("timeout", () => done("down"));
    socket.once("error", () => done("down"));
    socket.connect(port, host);
  });
}
