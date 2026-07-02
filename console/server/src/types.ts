export type Mode = "docker" | "native";

export type ServiceId = "postgres" | "backend" | "frontend" | "tei";

export type HealthProbe =
  | { kind: "http"; urlByMode: Partial<Record<Mode, string>> }
  | { kind: "tcp"; host: string; port: number };

export type NativeSpec = {
  cwd: string;
  command: string;
  args: string[];
  env?: Record<string, string>;
};

export type DockerSpec = {
  composeService: string;
  composeProfile?: string;
  containerName: string;
};

export type ServiceDef = {
  id: ServiceId;
  label: string;
  description: string;
  defaultMode: Mode;
  modes: Mode[];
  health: HealthProbe;
  docker?: DockerSpec;
  native?: NativeSpec;
};

export type Status = "up" | "down" | "starting" | "unknown";

export type ServiceState = {
  id: ServiceId;
  label: string;
  description: string;
  mode: Mode;
  availableModes: Mode[];
  status: Status;
  pid: number | null;
  containerStatus: string | null;
};
