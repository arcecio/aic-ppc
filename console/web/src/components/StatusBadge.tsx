import clsx from "clsx";
import type { Status } from "../lib/api";

const styles: Record<Status, { dot: string; text: string; label: string }> = {
  up: { dot: "bg-emerald-400", text: "text-emerald-300", label: "Up" },
  down: { dot: "bg-rose-500", text: "text-rose-300", label: "Down" },
  starting: { dot: "bg-amber-400 animate-pulse", text: "text-amber-300", label: "Starting" },
  unknown: { dot: "bg-slate-500", text: "text-slate-400", label: "Unknown" },
};

export function StatusBadge({ status }: { status: Status }) {
  const s = styles[status];
  return (
    <span className={clsx("inline-flex items-center gap-2 text-sm font-medium", s.text)}>
      <span className={clsx("h-2 w-2 rounded-full", s.dot)} />
      {s.label}
    </span>
  );
}
