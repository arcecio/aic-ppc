import path from "node:path";
import { fileURLToPath } from "node:url";
import type { ServiceDef } from "./types.ts";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
// console/server/src → ../../.. = app/ (where docker-compose.yml lives)
export const REPO_ROOT = path.resolve(__dirname, "..", "..", "..");

export const SERVICES: ServiceDef[] = [
  {
    id: "postgres",
    label: "Postgres",
    description: "PG16 + pgvector (host :5434). Docker only — no native install.",
    defaultMode: "docker",
    modes: ["docker"],
    health: { kind: "tcp", host: "127.0.0.1", port: 5434 },
    docker: {
      composeService: "postgres",
      containerName: "aip-ppc-postgres",
    },
  },
  {
    id: "tei",
    label: "TEI Embeddings",
    description:
      "intfloat/e5-large-v2 via text-embeddings-inference on :8086 (vector arm of the knowledgebase retriever). Native uses the brewed binary; docker runs the amd64 image under Rosetta. Optional — the app degrades to lexical-only without it.",
    defaultMode: "native",
    modes: ["docker", "native"],
    health: {
      kind: "http",
      urlByMode: {
        docker: "http://127.0.0.1:8086/health",
        native: "http://127.0.0.1:8086/health",
      },
    },
    docker: {
      composeService: "tei",
      composeProfile: "docker-tei",
      containerName: "aip-ppc-tei",
    },
    native: {
      cwd: REPO_ROOT,
      command: "text-embeddings-router",
      args: [
        "--model-id",
        "intfloat/e5-large-v2",
        "--port",
        "8086",
        "--hostname",
        "127.0.0.1",
      ],
    },
  },
  {
    id: "backend",
    label: "Backend (Spring Boot)",
    description:
      "Java 21 + Spring Boot 3.3. Docker runs the built jar on :8082; native runs ./gradlew bootRun on :8080 for hot reload.",
    defaultMode: "docker",
    modes: ["docker", "native"],
    health: {
      kind: "http",
      urlByMode: {
        docker: "http://127.0.0.1:8082/actuator/health",
        native: "http://127.0.0.1:8080/actuator/health",
      },
    },
    docker: {
      composeService: "backend",
      containerName: "aip-ppc-backend",
    },
    native: {
      cwd: path.join(REPO_ROOT, "backend"),
      command: "./gradlew",
      args: ["bootRun"],
      env: {
        DB_URL: "jdbc:postgresql://127.0.0.1:5434/aipppc",
        DB_USER: "aipppc",
        DB_PASS: "aipppc",
        EMBEDDING_URL: "http://127.0.0.1:8086",
        APP_BOOTSTRAP_ADMIN_EMAIL: "admin@lacity.gov",
      },
    },
  },
  {
    id: "frontend",
    label: "Frontend (Vite)",
    description:
      "React 18 + Vite. Docker serves the prod build behind nginx on :8095; native runs `npm run dev` on :5173.",
    defaultMode: "docker",
    modes: ["docker", "native"],
    health: {
      kind: "http",
      urlByMode: {
        docker: "http://127.0.0.1:8095",
        native: "http://127.0.0.1:5173",
      },
    },
    docker: {
      composeService: "frontend",
      containerName: "aip-ppc-frontend",
    },
    native: {
      cwd: path.join(REPO_ROOT, "frontend"),
      command: "npm",
      args: ["run", "dev"],
    },
  },
];

export function findService(id: string): ServiceDef | undefined {
  return SERVICES.find((s) => s.id === id);
}
