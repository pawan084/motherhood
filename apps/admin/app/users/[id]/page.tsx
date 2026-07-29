"use client";

// A single user's record, from GET /admin/users/{id}.
//
// The endpoint existed with no page behind it, so the console could list users
// but never open one — support could see that somebody was stuck at onboarding
// and had no way to look at their actual record.
//
// Deliberately read-only, and deliberately thin: it shows the account row and
// nothing from the user's care data. An admin has no business reading someone's
// symptom logs or conversations from here — the only health text the console
// ever surfaces is a safety flag, which is gated behind `support` and exists so
// the gate itself can be audited.

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import Shell from "@/components/Shell";
import { api } from "@/lib/api";

type UserDetail = {
  id: string; kind: string; email: string | null; google_sub: string | null;
  name: string; journey: string; language: string; onboarded: boolean;
  token_version: number; created: number | null; last_seen: number | null;
};

function fmt(ts: number | null): string {
  return ts ? new Date(ts * 1000).toLocaleString() : "—";
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start gap-4 border-b border-line/60 py-3 last:border-0">
      <span className="w-40 shrink-0 text-sm text-ink-muted">{label}</span>
      <span className="text-sm text-ink">{children}</span>
    </div>
  );
}

export default function UserDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id;
  const [user, setUser] = useState<UserDetail | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!id) return;
    api<{ user: UserDetail }>(`/users/${encodeURIComponent(id)}`)
      .then((r) => setUser(r.user))
      .catch((e) => setError(e.message));
  }, [id]);

  return (
    <Shell title="User">
      <Link href="/users" className="mb-6 inline-block text-sm text-ink-muted hover:text-aubergine">
        ← Back to users
      </Link>

      {error && <p className="mb-4 text-urgent">{error}</p>}

      {!user && !error && <p className="text-ink-muted">Loading…</p>}

      {user && (
        <>
          <section className="card max-w-2xl">
            <Row label="Name">{user.name || <span className="text-ink-muted">—</span>}</Row>
            <Row label="Email">
              {user.email || <span className="text-ink-muted">anonymous device user</span>}
            </Row>
            <Row label="Account type">{user.kind}</Row>
            <Row label="Journey">{user.journey || <span className="text-ink-muted">not set</span>}</Row>
            <Row label="Language">{user.language}</Row>
            <Row label="Onboarded">{user.onboarded ? "Yes" : "No"}</Row>
            <Row label="Created">{fmt(user.created)}</Row>
            <Row label="Last seen">{fmt(user.last_seen)}</Row>
            <Row label="User ID"><code className="text-xs">{user.id}</code></Row>
            <Row label="Google linked">{user.google_sub ? "Yes" : "No"}</Row>
          </section>

          <p className="mt-4 max-w-2xl text-xs text-ink-muted">
            Care data, conversations and memory are not shown here. The only
            user health text the console surfaces is a safety flag, which
            requires the <code>support</code> role and exists so the safety gate
            can be reviewed.
          </p>
        </>
      )}
    </Shell>
  );
}
