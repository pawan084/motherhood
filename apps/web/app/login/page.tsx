"use client";

// The dedicated /login route — the full-page version of the sign-in modal.
//
// Same two paths as the modal (email/password + Google) and the same honest
// framing: an account is OPTIONAL — Aira works anonymously, and an account only
// exists so care context can follow you across devices. Creating one carries
// this browser's anonymous data onto the account; signing in switches to the
// account's own data and leaves what's here behind, so the page warns before
// that happens, and only when there is something to lose.
//
// Reuses the API functions and the shared Google loader from ui/sign-in, so
// there is one implementation of the auth logic, not two.

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { AlertTriangle, ArrowLeft, Sparkles } from "lucide-react";
import {
  GOOGLE_CLIENT_ID, MIN_PASSWORD_LENGTH, demoSignIn, localCareItemCount,
  signIn, signInWithGoogle, signUp,
} from "../aira-api";
import { loadGsi } from "../ui/sign-in";

export default function LoginPage() {
  const [mode, setMode] = useState<"signin" | "signup">("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [demoBusy, setDemoBusy] = useState(false);
  const [localItems, setLocalItems] = useState<number | null>(null);
  const buttonRef = useRef<HTMLDivElement>(null);

  const isSignUp = mode === "signup";
  const tooShort = isSignUp && password.length > 0 && password.length < MIN_PASSWORD_LENGTH;
  const canSubmit = email.trim() !== "" && password !== "" && !busy && !tooShort;

  // On success, enter the app. The app lives under the #/app hash on the root
  // route, so this both leaves /login and boots the signed-in experience;
  // AiraApp re-reads /account/me and skips onboarding if the account is set up.
  const enterApp = () => { window.location.href = "/#/app/today"; };

  // One tap into a pre-filled demo — a fresh, isolated user, no sign-up.
  const runDemo = async () => {
    setDemoBusy(true);
    setError("");
    try {
      await demoSignIn();
      enterApp();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't start the demo.");
      setDemoBusy(false);
    }
  };

  useEffect(() => {
    if (isSignUp) return;
    let alive = true;
    localCareItemCount().then((n) => alive && setLocalItems(n)).catch(() => undefined);
    return () => { alive = false; };
  }, [isSignUp]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    setError("");
    try {
      await (isSignUp ? signUp(email.trim(), password) : signIn(email.trim(), password));
      enterApp();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong.");
      setBusy(false);
    }
  };

  useEffect(() => {
    if (!GOOGLE_CLIENT_ID) return;
    let alive = true;
    loadGsi()
      .then(() => {
        if (!alive || !buttonRef.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: GOOGLE_CLIENT_ID,
          callback: async (res) => {
            if (!res.credential) { setError("Google didn't return a credential."); return; }
            setBusy(true);
            setError("");
            try {
              await signInWithGoogle(res.credential);
              enterApp();
            } catch (e) {
              setError(e instanceof Error ? e.message : "Sign-in failed.");
              setBusy(false);
            }
          },
        });
        window.google.accounts.id.renderButton(buttonRef.current, {
          theme: "outline", size: "large", text: "signin_with", width: 320,
        });
      })
      .catch((e) => alive && setError(e instanceof Error ? e.message : "Sign-in unavailable."));
    return () => { alive = false; };
  }, []);

  return (
    <div className="auth-page">
      <div className="auth-card">
        <Link className="auth-back" href="/"><ArrowLeft size={15} /> Back to home</Link>

        <div className="auth-brand">
          <span className="brand-orb compact" aria-hidden="true"><i /><b /></span>
          <strong>Aira</strong>
        </div>

        <h1 className="auth-title">{isSignUp ? "Create your account" : "Welcome back"}</h1>
        <p className="auth-sub">
          {isSignUp
            ? "Aira works without an account. Creating one keeps your care context if you change device — everything you've already added comes with you."
            : "Sign in with the email and password you used before."}
        </p>

        <button className="auth-demo" onClick={runDemo} disabled={demoBusy || busy}>
          <Sparkles size={15} /> {demoBusy ? "Loading the demo…" : "Explore with a demo account"}
        </button>
        <p className="auth-demo-note">A week-24 pregnancy example, pre-filled — no sign-up needed.</p>

        <div className="auth-tabs" role="tablist" aria-label="Sign in or create account">
          <button role="tab" aria-selected={!isSignUp} className={!isSignUp ? "active" : ""}
                  onClick={() => { setMode("signin"); setError(""); }}>Sign in</button>
          <button role="tab" aria-selected={isSignUp} className={isSignUp ? "active" : ""}
                  onClick={() => { setMode("signup"); setError(""); }}>Create account</button>
        </div>

        {!isSignUp && localItems !== null && localItems > 0 && (
          <div className="banner" style={{ marginBottom: 14, alignItems: "flex-start" }}>
            <AlertTriangle size={15} />
            <span>
              This browser has {localItems} care {localItems === 1 ? "item" : "items"} not tied to
              an account. Signing in switches to your account&apos;s data and leaves{" "}
              {localItems === 1 ? "it" : "them"} behind — create an account instead to keep{" "}
              {localItems === 1 ? "it" : "them"}.
            </span>
          </div>
        )}
        {error && <div className="banner error" role="alert" style={{ marginBottom: 14 }}>{error}</div>}

        <form onSubmit={submit}>
          <label className="field">
            <span>Email</span>
            <input type="email" value={email} autoComplete="email" required
                   placeholder="you@example.com" onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" value={password} required
                   autoComplete={isSignUp ? "new-password" : "current-password"}
                   onChange={(e) => setPassword(e.target.value)} />
          </label>
          {isSignUp && (
            <p className="auth-hint" style={{ color: tooShort ? "var(--red)" : "var(--muted)" }}>
              At least {MIN_PASSWORD_LENGTH} characters. Length matters more than symbols — a short
              phrase you&apos;ll remember beats P@ssw0rd.
            </p>
          )}
          <button className="btn-primary" type="submit" disabled={!canSubmit} style={{ width: "100%" }}>
            {busy
              ? (isSignUp ? "Creating your account…" : "Signing in…")
              : (isSignUp ? "Create account" : "Sign in")}
          </button>
        </form>

        {!isSignUp && (
          <p className="auth-note">
            Password resets aren&apos;t available in this build yet. If you can&apos;t sign in, you
            can keep using Aira without an account.
          </p>
        )}

        {GOOGLE_CLIENT_ID ? (
          <>
            <p className="or-rule"><span>or</span></p>
            <div ref={buttonRef} style={{ display: "grid", placeItems: "center", minHeight: 44 }} />
          </>
        ) : (
          <p className="auth-note">
            Google Sign-In isn&apos;t configured for this deployment, so email and password is the
            only way in right now. Your data is safe either way — it&apos;s stored against this
            browser and you can export it any time.
          </p>
        )}

        <div className="auth-links">
          <button className="auth-link" onClick={enterApp}>Continue without an account</button>
          <p className="auth-legal">
            By continuing you agree to our <Link href="/terms">Terms</Link> and{" "}
            <Link href="/privacy">Privacy</Link>.
          </p>
        </div>
      </div>
    </div>
  );
}
