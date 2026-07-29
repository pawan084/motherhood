"use client";

// Admin accounts — GET/POST /admin/admins, both owner-only.
//
// Both endpoints existed with no page behind them, so the only way to create an
// admin was the ADMIN_BOOTSTRAP_EMAIL/PASSWORD env pair at first boot. A team
// could not add a second person without redeploying.
//
// One thing the UI has to be honest about: `create_user` on the backend is an
// UPSERT. Submitting an email that already exists overwrites that admin's
// password and role and bumps their token version, signing them out everywhere.
// That is a reasonable "reset a colleague's access" primitive, but a form
// labelled only "Add admin" would let an owner do it by accident, so the form
// detects the collision and relabels itself before you submit.

import { useCallback, useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { api, roleAtLeast } from "@/lib/api";
import { useSession } from "@/lib/session";

type AdminRow = { email: string; role: string; created: number | null };

const ROLES = [
  { value: "viewer", detail: "Read-only. Safety flags arrive with the health text withheld." },
  { value: "support", detail: "Can review flags and resolve feedback, and can read flagged text." },
  { value: "owner", detail: "Everything, including managing these accounts." },
];

// Matches nothing on the backend — it has no strength rule — so this is a
// client-side floor, stated rather than silently enforced.
const MIN_PASSWORD = 12;

function fmt(ts: number | null): string {
  return ts ? new Date(ts * 1000).toLocaleDateString() : "—";
}

export default function AdminsPage() {
  const { session, loading: sessionLoading } = useSession();
  const isOwner = roleAtLeast("owner", session?.role ?? null);

  const [rows, setRows] = useState<AdminRow[] | null>(null);
  const [error, setError] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("viewer");

  const load = useCallback(() => {
    api<{ items: AdminRow[] }>("/admins")
      .then((r) => setRows(r.items))
      .catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    if (sessionLoading || !isOwner) return;
    load();
  }, [sessionLoading, isOwner, load]);

  const existing = rows?.find((r) => r.email.toLowerCase() === email.trim().toLowerCase());
  const tooShort = password.length > 0 && password.length < MIN_PASSWORD;
  const canSubmit = !busy && email.trim() !== "" && password.length >= MIN_PASSWORD;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    setError("");
    setNote("");
    try {
      await api("/admins", {
        method: "POST",
        body: JSON.stringify({ email: email.trim().toLowerCase(), password, role }),
      });
      setNote(
        existing
          ? `Reset access for ${email.trim().toLowerCase()} — they've been signed out everywhere.`
          : `Added ${email.trim().toLowerCase()} as ${role}.`,
      );
      setEmail("");
      setPassword("");
      setRole("viewer");
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't save that admin.");
    } finally {
      setBusy(false);
    }
  }

  if (sessionLoading) {
    return <Shell title="Admins"><p className="text-ink-muted">Checking your role…</p></Shell>;
  }

  // The backend enforces this too (require_admin("owner")); this just explains
  // the 403 instead of rendering a form that can only fail.
  if (!isOwner) {
    return (
      <Shell title="Admins">
        <section className="card max-w-xl">
          <p className="text-sm text-ink">Managing admin accounts requires the owner role.</p>
          <p className="mt-2 text-sm text-ink-muted">
            You&apos;re signed in as <strong>{session?.email}</strong> ({session?.role}).
          </p>
        </section>
      </Shell>
    );
  }

  return (
    <Shell title="Admins">
      {error && <p className="mb-4 text-urgent">{error}</p>}
      {note && <p className="mb-4 text-sage-deep">{note}</p>}

      <section className="card mb-8 overflow-x-auto p-0">
        <table className="w-full">
          <thead>
            <tr className="border-b border-line">
              <th className="th">Email</th>
              <th className="th">Role</th>
              <th className="th">Added</th>
            </tr>
          </thead>
          <tbody>
            {(rows ?? []).map((a) => (
              <tr key={a.email} className="border-b border-line/60">
                <td className="td">
                  {a.email}
                  {a.email === session?.email && (
                    <span className="ml-2 text-xs text-ink-muted">(you)</span>
                  )}
                </td>
                <td className="td capitalize text-ink-muted">{a.role}</td>
                <td className="td whitespace-nowrap text-ink-muted">{fmt(a.created)}</td>
              </tr>
            ))}
            {rows?.length === 0 && (
              <tr><td className="td text-ink-muted" colSpan={3}>No admins.</td></tr>
            )}
            {!rows && (
              <tr><td className="td text-ink-muted" colSpan={3}>Loading…</td></tr>
            )}
          </tbody>
        </table>
      </section>

      <section className="card max-w-xl">
        <h2 className="mb-1 font-semibold text-aubergine">
          {existing ? "Reset an existing admin" : "Add an admin"}
        </h2>
        <p className="mb-4 text-sm text-ink-muted">
          {existing
            ? `${existing.email} already exists as ${existing.role}. Submitting will replace their password and role, and sign them out of every session.`
            : "They'll sign in with this email and password. Roles can be changed later by submitting the same email again."}
        </p>

        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          <label className="flex flex-col gap-1">
            <span className="text-sm text-ink">Email</span>
            <input
              className="input"
              type="email"
              autoComplete="off"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@yourteam.com"
              required
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-sm text-ink">
              {existing ? "New password" : "Password"}
            </span>
            <input
              className="input"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <span className={`text-xs ${tooShort ? "text-urgent" : "text-ink-muted"}`}>
              At least {MIN_PASSWORD} characters. Share it with them over something
              other than email, and have them change it by submitting this form
              again once they&apos;re in.
            </span>
          </label>

          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm text-ink">Role</legend>
            {ROLES.map((r) => (
              <label key={r.value} className="flex items-start gap-2">
                <input
                  type="radio"
                  name="role"
                  className="mt-1"
                  value={r.value}
                  checked={role === r.value}
                  onChange={() => setRole(r.value)}
                />
                <span>
                  <span className="text-sm capitalize text-ink">{r.value}</span>
                  <span className="block text-xs text-ink-muted">{r.detail}</span>
                </span>
              </label>
            ))}
          </fieldset>

          <button type="submit" className="btn self-start" disabled={!canSubmit}>
            {busy ? "Saving…" : existing ? "Reset this admin" : "Add admin"}
          </button>
        </form>
      </section>
    </Shell>
  );
}
