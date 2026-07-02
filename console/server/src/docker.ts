import { execFile } from "node:child_process";
import { promisify } from "node:util";
import type { DockerSpec } from "./types.ts";
import { REPO_ROOT } from "./services.ts";

const exec = promisify(execFile);

function composeArgs(spec: DockerSpec): string[] {
  const args = ["compose"];
  if (spec.composeProfile) args.push("--profile", spec.composeProfile);
  return args;
}

// Build output from `npm install` etc. easily exceeds Node's default 1MB
// execFile buffer, which would otherwise kill the compose process mid-build.
const COMPOSE_BUFFER = 1024 * 1024 * 64;

export async function dockerStart(spec: DockerSpec): Promise<void> {
  const args = [...composeArgs(spec), "up", "-d", spec.composeService];
  await exec("docker", args, { cwd: REPO_ROOT, maxBuffer: COMPOSE_BUFFER });
}

export async function dockerStop(spec: DockerSpec): Promise<void> {
  const args = [...composeArgs(spec), "stop", spec.composeService];
  await exec("docker", args, { cwd: REPO_ROOT, maxBuffer: COMPOSE_BUFFER });
}

export async function dockerLogs(
  spec: DockerSpec,
  tail = 200,
): Promise<string> {
  const status = await dockerContainerStatus(spec);
  if (status === null) {
    return `(no container named ${spec.containerName} yet — start the service to create it)`;
  }
  try {
    const { stdout, stderr } = await exec(
      "docker",
      ["logs", "--tail", String(tail), spec.containerName],
      { cwd: REPO_ROOT, maxBuffer: 1024 * 1024 * 10 },
    );
    return (stderr || "") + stdout;
  } catch (err) {
    const e = err as { stderr?: string; message?: string };
    return e.stderr ?? e.message ?? "docker logs failed";
  }
}

export async function dockerContainerStatus(
  spec: DockerSpec,
): Promise<string | null> {
  try {
    const { stdout } = await exec(
      "docker",
      ["inspect", "-f", "{{.State.Status}}", spec.containerName],
      { cwd: REPO_ROOT },
    );
    return stdout.trim();
  } catch {
    return null;
  }
}
