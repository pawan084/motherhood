"use client";
import { useCallback, useEffect, useState } from "react";
import Shell, { Pill } from "@/components/Shell";
import { api, type FeedbackRow } from "@/lib/api";

type FeedbackResp = {
  items: FeedbackRow[];
  stats: { total: number; open: number; reports: number };
};

function fmt(ts: number | null): string {
  return ts ? new Date(ts * 1000).toLocaleString() : "—";
}

export default function FeedbackPage() {
  const [data, setData] = useState<FeedbackResp | null>(null);
  const [error, setError] = useState("");
  const [kind, setKind] = useState("all");
  const [status, setStatus] = useState("all");

  const load = useCallback(() => {
    const params = new URLSearchParams();
    if (kind !== "all") params.set("kind", kind);
    if (status !== "all") params.set("status", status);
    params.set("limit", "200");
    api<FeedbackResp>(`/feedback?${params.toString()}`).then(setData).catch((e) => setError(e.message));
  }, [kind, status]);

  useEffect(() => {
    load();
  }, [load]);

  async function resolve(id: string) {
    setError("");
    try {
      await api(`/feedback/${id}/resolve`, { method: "POST" });
      load();
    } catch (e: any) {
      setError(e.message);
    }
  }

  return (
    <Shell title="Feedback">
      {error && <p className="mb-4 text-urgent">{error}</p>}
      {!data ? (
        <p className="text-ink-muted">Loading…</p>
      ) : (
        <div className="space-y-6">
          <section className="flex flex-wrap gap-6 text-sm">
            <span className="text-ink-muted">Total <b className="text-aubergine">{data.stats.total}</b></span>
            <span className="text-ink-muted">Open <b className="text-aubergine">{data.stats.open}</b></span>
            <span className="text-ink-muted">Reports <b className="text-urgent">{data.stats.reports}</b></span>
          </section>

          <section className="flex flex-wrap items-center gap-4">
            <select className="input max-w-[10rem]" value={kind} onChange={(e) => setKind(e.target.value)}>
              <option value="all">All kinds</option>
              <option value="general">General</option>
              <option value="bug">Bug</option>
              <option value="clinical">Clinical</option>
              <option value="safety">Safety</option>
              <option value="technical">Technical</option>
              <option value="praise">Praise</option>
            </select>
            <select className="input max-w-[10rem]" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="all">All statuses</option>
              <option value="open">Open</option>
              <option value="resolved">Resolved</option>
            </select>
          </section>

          <section className="card overflow-x-auto p-0">
            <table className="w-full">
              <thead>
                <tr className="border-b border-line">
                  <th className="th">Time</th>
                  <th className="th">Kind</th>
                  <th className="th">Message</th>
                  <th className="th">Ref</th>
                  <th className="th">Status</th>
                  <th className="th"></th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((f) => (
                  <tr key={f.id} className="border-b border-line/60">
                    <td className="td whitespace-nowrap text-ink-muted">{fmt(f.ts)}</td>
                    <td className="td"><Pill level={f.kind} /></td>
                    <td className="td max-w-md">{f.message}</td>
                    <td className="td text-ink-muted">{f.ref || "—"}</td>
                    <td className="td">
                      {f.status === "resolved" ? (
                        <span className="text-sage-deep">resolved{f.handled_by ? ` · ${f.handled_by}` : ""}</span>
                      ) : (
                        <span className="text-ink-muted">{f.status}</span>
                      )}
                    </td>
                    <td className="td">
                      {f.status === "open" && (
                        <button className="btn" onClick={() => resolve(f.id)}>Resolve</button>
                      )}
                    </td>
                  </tr>
                ))}
                {data.items.length === 0 && (
                  <tr>
                    <td className="td text-ink-muted" colSpan={6}>No feedback.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </section>
        </div>
      )}
    </Shell>
  );
}
