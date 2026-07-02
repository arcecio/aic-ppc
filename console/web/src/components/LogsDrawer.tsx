import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { fetchLogs } from "../lib/api";

type Props = { serviceId: string; serviceLabel: string; onClose: () => void };

export function LogsDrawer({ serviceId, serviceLabel, onClose }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ["logs", serviceId],
    queryFn: () => fetchLogs(serviceId, 500),
    refetchInterval: 2000,
  });

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 sm:items-center">
      <div className="flex h-[80vh] w-full max-w-4xl flex-col rounded-t-lg bg-slate-900 shadow-2xl sm:rounded-lg">
        <div className="flex items-center justify-between border-b border-slate-800 px-4 py-3">
          <h2 className="font-semibold">{serviceLabel} — logs</h2>
          <button
            onClick={onClose}
            className="rounded p-1 text-slate-400 hover:bg-slate-800 hover:text-slate-100"
            aria-label="Close logs"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <pre className="flex-1 overflow-auto bg-slate-950 px-4 py-3 font-mono text-xs leading-relaxed text-slate-300">
          {isLoading ? "Loading…" : data || "(no logs yet)"}
        </pre>
      </div>
    </div>
  );
}
