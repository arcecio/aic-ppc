import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Play, Square, FileText } from "lucide-react";
import clsx from "clsx";
import {
  type Mode,
  type ServiceState,
  setServiceMode,
  startService,
  stopService,
} from "../lib/api";
import { ModeToggle } from "./ModeToggle";
import { StatusBadge } from "./StatusBadge";
import { LogsDrawer } from "./LogsDrawer";

export function ServiceCard({ service }: { service: ServiceState }) {
  const qc = useQueryClient();
  const [logsOpen, setLogsOpen] = useState(false);

  const invalidate = () => qc.invalidateQueries({ queryKey: ["services"] });

  const startM = useMutation({ mutationFn: () => startService(service.id), onSettled: invalidate });
  const stopM = useMutation({ mutationFn: () => stopService(service.id), onSettled: invalidate });
  const modeM = useMutation({
    mutationFn: (mode: Mode) => setServiceMode(service.id, mode),
    onSettled: invalidate,
  });

  const isBusy = startM.isPending || stopM.isPending || modeM.isPending;
  const error = startM.error || stopM.error || modeM.error;

  const subtitle =
    service.mode === "native"
      ? service.pid
        ? `pid ${service.pid}`
        : "no managed process"
      : service.containerStatus
        ? `container ${service.containerStatus}`
        : "no container";

  return (
    <>
      <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-5 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <h3 className="text-lg font-semibold text-slate-100">{service.label}</h3>
              <StatusBadge status={service.status} />
            </div>
            <p className="mt-1 text-xs text-slate-500">{subtitle}</p>
          </div>
          <ModeToggle
            value={service.mode}
            available={service.availableModes}
            disabled={isBusy}
            onChange={(m) => modeM.mutate(m)}
          />
        </div>

        <p className="mt-3 text-sm leading-snug text-slate-400">{service.description}</p>

        <div className="mt-4 flex items-center gap-2">
          <button
            onClick={() => startM.mutate()}
            disabled={isBusy || service.status === "up"}
            className={clsx(
              "inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
              "bg-emerald-600 text-white hover:bg-emerald-500",
              "disabled:cursor-not-allowed disabled:bg-slate-800 disabled:text-slate-500",
            )}
          >
            <Play className="h-3.5 w-3.5" /> Start
          </button>
          <button
            onClick={() => stopM.mutate()}
            disabled={isBusy || service.status === "down"}
            className={clsx(
              "inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
              "bg-rose-600 text-white hover:bg-rose-500",
              "disabled:cursor-not-allowed disabled:bg-slate-800 disabled:text-slate-500",
            )}
          >
            <Square className="h-3.5 w-3.5" /> Stop
          </button>
          <button
            onClick={() => setLogsOpen(true)}
            className="ml-auto inline-flex items-center gap-1.5 rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800"
          >
            <FileText className="h-3.5 w-3.5" /> Logs
          </button>
        </div>

        {error && (
          <p className="mt-3 rounded bg-rose-950/50 px-3 py-2 text-xs text-rose-300">
            {(error as Error).message}
          </p>
        )}
      </div>

      {logsOpen && (
        <LogsDrawer
          serviceId={service.id}
          serviceLabel={service.label}
          onClose={() => setLogsOpen(false)}
        />
      )}
    </>
  );
}
