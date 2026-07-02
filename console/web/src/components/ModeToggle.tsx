import clsx from "clsx";
import type { Mode } from "../lib/api";

type Props = {
  value: Mode;
  available: Mode[];
  onChange: (mode: Mode) => void;
  disabled?: boolean;
};

const labels: Record<Mode, string> = { docker: "Docker", native: "Native" };

export function ModeToggle({ value, available, onChange, disabled }: Props) {
  return (
    <div className="inline-flex rounded-md border border-slate-700 bg-slate-900 p-0.5 text-xs">
      {(["docker", "native"] as Mode[]).map((m) => {
        const enabled = available.includes(m);
        const active = value === m;
        return (
          <button
            key={m}
            disabled={disabled || !enabled}
            onClick={() => enabled && onChange(m)}
            className={clsx(
              "px-3 py-1 rounded transition-colors",
              active && "bg-slate-700 text-slate-100",
              !active && enabled && "text-slate-400 hover:text-slate-200",
              !enabled && "text-slate-700 cursor-not-allowed",
            )}
            title={enabled ? `${labels[m]} mode` : `${labels[m]} mode unavailable for this service`}
          >
            {labels[m]}
          </button>
        );
      })}
    </div>
  );
}
