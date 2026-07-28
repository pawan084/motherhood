"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import Shell from "@/components/Shell";
import { api, type Overview } from "@/lib/api";

function Stat({ label, value, hint, tone }: { label: string; value: string | number; hint?: string; tone?: string }) {
  return (
    <div className="card">
      <div className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{label}</div>
      <div className={`mt-1 text-3xl font-semibold ${tone || "text-aubergine"}`}>{value}</div>
      {hint && <div className="mt-1 text-xs text-ink-muted">{hint}</div>}
    </div>
  );
}

export default function Dashboard() {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Overview>("/overview?days=30").then(setData).catch((e) => setError(e.message));
  }, []);

  return (
    <Shell title="Dashboard">
      {error && <p className="text-urgent">{error}</p>}
      {!data ? (
        <p className="text-ink-muted">Loading…</p>
      ) : (
        <div className="space-y-6">
          <section className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <Stat label="Total users" value={data.metrics.total_users} hint={`${data.metrics.accounts} accounts`} />
            <Stat label="Active (7d)" value={data.metrics.wau} hint={`${data.metrics.dau} today`} />
            <Stat label="Chat turns" value={data.metrics.chat_turns} />
            <Stat label="Open feedback" value={data.feedback.open} hint={`${data.feedback.reports} reports`} />
          </section>

          <section className="card border-urgent/30">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-semibold text-aubergine">Safety</h2>
              <Link href="/safety" className="text-sm font-medium text-aubergine underline">Review flags →</Link>
            </div>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
              <Stat label="Red" value={data.safety.red} tone="text-urgent" />
              <Stat label="Amber" value={data.safety.amber} tone="text-amber" />
              <Stat label="Unreviewed" value={data.safety.unreviewed} />
              <Stat label="Degraded" value={data.safety.degraded} hint="keyword-only" />
              <Stat label="Total" value={data.safety.total} />
            </div>
            {data.safety.degraded > 0 && (
              <p className="mt-3 text-sm text-amber">
                ⚠ {data.safety.degraded} screens ran keyword-only — the LLM classifier was
                unavailable. Check the System page.
              </p>
            )}
          </section>
        </div>
      )}
    </Shell>
  );
}
