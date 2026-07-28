"use client";
import { useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { api, type System } from "@/lib/api";

function providerPill(status: string): string {
  if (status === "ok") return "bg-sage/20 text-sage-deep";
  return "bg-urgent/15 text-urgent";
}

export default function SystemPage() {
  const [data, setData] = useState<System | null>(null);
  const [error, setError] = useState("");

  function load() {
    api<System>("/system").then(setData).catch((e) => setError(e.message));
  }

  useEffect(() => {
    load();
  }, []);

  return (
    <Shell title="System">
      {error && <p className="mb-4 text-urgent">{error}</p>}
      <div className="mb-6">
        <button className="btn-ghost" onClick={() => { setError(""); load(); }}>Refresh</button>
      </div>
      {!data ? (
        <p className="text-ink-muted">Loading…</p>
      ) : (
        <div className="space-y-6">
          <section className="card">
            <h2 className="mb-3 font-semibold text-aubergine">Environment</h2>
            <div className="flex flex-wrap gap-6 text-sm">
              <span className="text-ink-muted">Env <b className="text-aubergine">{data.env}</b></span>
              <span className="text-ink-muted">DB engine <b className="text-aubergine">{data.db.engine}</b></span>
            </div>
          </section>

          <section className="card overflow-x-auto p-0">
            <table className="w-full">
              <thead>
                <tr className="border-b border-line">
                  <th className="th">Provider</th>
                  <th className="th">Status</th>
                  <th className="th">Latency</th>
                  <th className="th">Error</th>
                </tr>
              </thead>
              <tbody>
                {data.providers.map((p) => (
                  <tr key={p.name} className="border-b border-line/60">
                    <td className="td font-medium text-aubergine">{p.name}</td>
                    <td className="td"><span className={`pill ${providerPill(p.status)}`}>{p.status}</span></td>
                    <td className="td text-ink-muted">{p.ms != null ? `${p.ms} ms` : "—"}</td>
                    <td className="td text-urgent">{p.error || "—"}</td>
                  </tr>
                ))}
                {data.providers.length === 0 && (
                  <tr>
                    <td className="td text-ink-muted" colSpan={4}>No providers.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </section>

          <section className="card">
            <h2 className="mb-3 font-semibold text-aubergine">Auth</h2>
            <div className="space-y-1 text-sm">
              <div className="flex items-center gap-2">
                <span className={data.auth.app_token_required ? "text-sage-deep" : "text-urgent"}>
                  {data.auth.app_token_required ? "✓" : "✗"}
                </span>
                <span className="text-ink-muted">App token required</span>
              </div>
              <div className="flex items-center gap-2">
                <span className={data.auth.google_signin ? "text-sage-deep" : "text-urgent"}>
                  {data.auth.google_signin ? "✓" : "✗"}
                </span>
                <span className="text-ink-muted">Google sign-in</span>
              </div>
            </div>
          </section>
        </div>
      )}
    </Shell>
  );
}
