export type Mode = "docker" | "native";
export type Status = "up" | "down" | "starting" | "unknown";

export type ServiceState = {
  id: string;
  label: string;
  description: string;
  mode: Mode;
  availableModes: Mode[];
  status: Status;
  pid: number | null;
  containerStatus: string | null;
};

async function jsonOrThrow<T>(r: Response): Promise<T> {
  if (!r.ok) {
    const t = await r.text();
    throw new Error(`${r.status}: ${t}`);
  }
  return r.json() as Promise<T>;
}

export async function listServices(): Promise<ServiceState[]> {
  return jsonOrThrow(await fetch("/api/services"));
}

export async function startService(id: string): Promise<ServiceState> {
  return jsonOrThrow(await fetch(`/api/services/${id}/start`, { method: "POST" }));
}

export async function stopService(id: string): Promise<ServiceState> {
  return jsonOrThrow(await fetch(`/api/services/${id}/stop`, { method: "POST" }));
}

export async function setServiceMode(id: string, mode: Mode): Promise<ServiceState> {
  return jsonOrThrow(
    await fetch(`/api/services/${id}/mode`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode }),
    }),
  );
}

export async function fetchLogs(id: string, tail = 200): Promise<string> {
  const r = await fetch(`/api/services/${id}/logs?tail=${tail}`);
  if (!r.ok) throw new Error(`${r.status}`);
  return r.text();
}
