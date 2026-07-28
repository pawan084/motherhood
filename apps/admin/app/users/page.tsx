"use client";
import { useCallback, useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { api, type UserRow } from "@/lib/api";

type UsersResp = { items: UserRow[] };

function fmt(ts: number | null): string {
  return ts ? new Date(ts * 1000).toLocaleString() : "—";
}

export default function UsersPage() {
  const [data, setData] = useState<UsersResp | null>(null);
  const [error, setError] = useState("");
  const [q, setQ] = useState("");

  const load = useCallback((query: string) => {
    const params = new URLSearchParams();
    if (query.trim()) params.set("q", query.trim());
    params.set("limit", "100");
    api<UsersResp>(`/users?${params.toString()}`).then(setData).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    load(q);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load]);

  function onSearch(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    load(q);
  }

  return (
    <Shell title="Users">
      {error && <p className="mb-4 text-urgent">{error}</p>}
      <form onSubmit={onSearch} className="mb-6 flex items-center gap-3">
        <input
          className="input max-w-md"
          placeholder="Search by name or email…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <button type="submit" className="btn">Search</button>
      </form>

      {!data ? (
        <p className="text-ink-muted">Loading…</p>
      ) : (
        <section className="card overflow-x-auto p-0">
          <table className="w-full">
            <thead>
              <tr className="border-b border-line">
                <th className="th">Name</th>
                <th className="th">Email</th>
                <th className="th">Kind</th>
                <th className="th">Journey</th>
                <th className="th">Language</th>
                <th className="th">Onboarded</th>
                <th className="th">Last seen</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((u) => (
                <tr key={u.id} className="border-b border-line/60">
                  <td className="td">{u.name || "—"}</td>
                  <td className="td">{u.email || <span className="text-ink-muted">anon device</span>}</td>
                  <td className="td text-ink-muted">{u.kind}</td>
                  <td className="td text-ink-muted">{u.journey}</td>
                  <td className="td text-ink-muted">{u.language}</td>
                  <td className="td">{u.onboarded ? "✓" : "—"}</td>
                  <td className="td whitespace-nowrap text-ink-muted">{fmt(u.last_seen)}</td>
                </tr>
              ))}
              {data.items.length === 0 && (
                <tr>
                  <td className="td text-ink-muted" colSpan={7}>No users.</td>
                </tr>
              )}
            </tbody>
          </table>
        </section>
      )}
    </Shell>
  );
}
