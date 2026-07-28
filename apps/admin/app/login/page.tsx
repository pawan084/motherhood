"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { login } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState(process.env.NEXT_PUBLIC_DEV_ADMIN_EMAIL || "");
  const [password, setPassword] = useState(process.env.NEXT_PUBLIC_DEV_ADMIN_PASSWORD || "");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await login(email.trim(), password);
      router.push("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign in failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-screen place-items-center p-6">
      <form onSubmit={onSubmit} className="card w-full max-w-sm">
        <div className="mb-5 flex items-center gap-2">
          <span className="grid h-8 w-8 place-items-center rounded-full bg-aubergine font-bold text-white">A</span>
          <div>
            <div className="font-semibold text-aubergine">Aira Admin</div>
            <div className="text-xs text-ink-muted">Safety, care content & users</div>
          </div>
        </div>
        <label className="mb-1 block text-xs font-semibold text-ink-muted">Email</label>
        <input className="input mb-3" value={email} onChange={(e) => setEmail(e.target.value)}
               type="email" autoComplete="username" required />
        <label className="mb-1 block text-xs font-semibold text-ink-muted">Password</label>
        <input className="input mb-4" value={password} onChange={(e) => setPassword(e.target.value)}
               type="password" autoComplete="current-password" required />
        {error && <p className="mb-3 text-sm text-urgent">{error}</p>}
        <button className="btn w-full" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
      </form>
    </div>
  );
}
