"use client";
import { useCallback, useEffect, useState } from "react";
import Shell, { Pill } from "@/components/Shell";
import { api, type SafetyFlag } from "@/lib/api";

type SafetyResp = {
  items: SafetyFlag[];
  stats: { total: number; red: number; amber: number; unreviewed: number; degraded: number };
};

function fmt(ts: number | null): string {
  return ts ? new Date(ts * 1000).toLocaleString() : "—";
}

export default function SafetyPage() {
  const [data, setData] = useState<SafetyResp | null>(null);
  const [error, setError] = useState("");
  const [level, setLevel] = useState("all");
  const [unreviewed, setUnreviewed] = useState(false);
  const [notes, setNotes] = useState<Record<number, string>>({});

  const load = useCallback(() => {
    const params = new URLSearchParams();
    if (level !== "all") params.set("level", level);
    if (unreviewed) params.set("unreviewed", "1");
    params.set("limit", "100");
    api<SafetyResp>(`/safety/flags?${params.toString()}`).then(setData).catch((e) => setError(e.message));
  }, [level, unreviewed]);

  useEffect(() => {
    load();
  }, [load]);

  async function markReviewed(id: number) {
    setError("");
    try {
      await api(`/safety/flags/${id}/review`, {
        method: "POST",
        body: JSON.stringify({ note: notes[id] || "" }),
      });
      load();
    } catch (e: any) {
      setError(e.message);
    }
  }

  return (
    <Shell title="Safety flags">
      {error && <p className="mb-4 text-urgent">{error}</p>}
      {!data ? (
        <p className="text-ink-muted">Loading…</p>
      ) : (
        <div className="space-y-6">
          <section className="flex flex-wrap gap-6 text-sm">
            <span className="text-ink-muted">Total <b className="text-aubergine">{data.stats.total}</b></span>
            <span className="text-ink-muted">Red <b className="text-urgent">{data.stats.red}</b></span>
            <span className="text-ink-muted">Amber <b className="text-amber">{data.stats.amber}</b></span>
            <span className="text-ink-muted">Unreviewed <b className="text-aubergine">{data.stats.unreviewed}</b></span>
            <span className="text-ink-muted">Degraded <b className="text-aubergine">{data.stats.degraded}</b></span>
          </section>

          <section className="flex flex-wrap items-center gap-4">
            <select className="input max-w-[10rem]" value={level} onChange={(e) => setLevel(e.target.value)}>
              <option value="all">All levels</option>
              <option value="red">Red</option>
              <option value="amber">Amber</option>
              <option value="green">Green</option>
            </select>
            <label className="flex items-center gap-2 text-sm text-ink-muted">
              <input type="checkbox" checked={unreviewed} onChange={(e) => setUnreviewed(e.target.checked)} />
              Unreviewed only
            </label>
          </section>

          <section className="card overflow-x-auto p-0">
            <table className="w-full">
              <thead>
                <tr className="border-b border-line">
                  <th className="th">Time</th>
                  <th className="th">Level</th>
                  <th className="th">Categories</th>
                  <th className="th">Message</th>
                  <th className="th">User</th>
                  <th className="th">Status</th>
                  <th className="th"></th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((f) => (
                  <tr key={f.id} className="border-b border-line/60">
                    <td className="td whitespace-nowrap text-ink-muted">{fmt(f.ts)}</td>
                    <td className="td">
                      <Pill level={f.level} />
                      {f.degraded && (
                        <span className="pill ml-1 bg-amber/15 text-amber">degraded</span>
                      )}
                    </td>
                    <td className="td">{f.categories.join(", ")}</td>
                    <td className="td max-w-md">{f.message}</td>
                    <td className="td font-mono text-xs text-ink-muted">{f.user_id.slice(0, 8)}…</td>
                    <td className="td">
                      {f.reviewed ? (
                        <span className="text-sage-deep">reviewed{f.reviewed_by ? ` · ${f.reviewed_by}` : ""}</span>
                      ) : (
                        <span className="text-ink-muted">unreviewed</span>
                      )}
                    </td>
                    <td className="td">
                      {!f.reviewed && (
                        <div className="flex flex-col gap-1">
                          <input
                            className="input min-w-[8rem]"
                            placeholder="note (optional)"
                            value={notes[f.id] || ""}
                            onChange={(e) => setNotes((n) => ({ ...n, [f.id]: e.target.value }))}
                          />
                          <button className="btn" onClick={() => markReviewed(f.id)}>Mark reviewed</button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
                {data.items.length === 0 && (
                  <tr>
                    <td className="td text-ink-muted" colSpan={7}>No flags.</td>
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
