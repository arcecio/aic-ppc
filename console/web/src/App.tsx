import { useQuery } from "@tanstack/react-query";
import { Activity } from "lucide-react";
import { listServices } from "./lib/api";
import { ServiceCard } from "./components/ServiceCard";

export default function App() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["services"],
    queryFn: listServices,
  });

  return (
    <div className="mx-auto max-w-4xl px-6 py-8">
      <header className="mb-8 flex items-center gap-3">
        <Activity className="h-6 w-6 text-sky-400" />
        <div>
          <h1 className="text-2xl font-bold text-slate-100">AIP PPC Console</h1>
          <p className="text-sm text-slate-500">Start, stop, and check health on the local stack.</p>
        </div>
      </header>

      {isLoading && <p className="text-slate-400">Loading services…</p>}
      {error && (
        <p className="rounded bg-rose-950/50 px-3 py-2 text-sm text-rose-300">
          Failed to reach console server: {(error as Error).message}
        </p>
      )}

      <div className="space-y-4">
        {data?.map((s) => <ServiceCard key={s.id} service={s} />)}
      </div>
    </div>
  );
}
