"use client";

// Creating an account, and signing in.
//
// Two paths: email/password, and Google. The email path is the one that always
// works — Google needs a client id this deployment may not have, and until this
// existed, a deployment without one had a "Sign in" button that could not sign
// anyone in. The backend has had /account/signup and /account/login all along.
//
// Signing in is optional by design: Aira works anonymously from first launch,
// and an account exists so a user can keep their care context across devices.
// So this is a modal you can close, not a gate in front of the product.
//
// The two paths differ in a way the user has to know BEFORE choosing:
// signing UP carries this browser's anonymous care data onto the new account;
// signing IN switches to the account's own data and leaves what's here behind.
// Merging two people's care records is not something to do silently, so the
// dialog says so instead, and only when there is actually something to lose.

import { useEffect, useRef, useState } from "react";
import { AlertTriangle, X } from "lucide-react";
import {
  GOOGLE_CLIENT_ID, MIN_PASSWORD_LENGTH, localCareItemCount, signIn, signInWithGoogle,
  signUp, type User,
} from "../aira-api";

const GSI_SRC = "https://accounts.google.com/gsi/client";

type GoogleCredentialResponse = { credential?: string };

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (o: {
            client_id: string;
            callback: (r: GoogleCredentialResponse) => void;
          }) => void;
          renderButton: (el: HTMLElement, o: Record<string, unknown>) => void;
        };
      };
    };
  }
}

/** Load the GSI script once, shared across mounts. */
let gsiPromise: Promise<void> | null = null;
function loadGsi(): Promise<void> {
  if (typeof document === "undefined") return Promise.resolve();
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!gsiPromise) {
    gsiPromise = new Promise<void>((resolve, reject) => {
      const existing = document.querySelector<HTMLScriptElement>(`script[src="${GSI_SRC}"]`);
      if (existing) {
        existing.addEventListener("load", () => resolve());
        existing.addEventListener("error", () => reject(new Error("gsi failed")));
        return;
      }
      const s = document.createElement("script");
      s.src = GSI_SRC;
      s.async = true;
      s.defer = true;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error("Couldn't reach Google Sign-In."));
      document.head.appendChild(s);
    }).catch((e) => { gsiPromise = null; throw e; });
  }
  return gsiPromise;
}

export default function SignIn({
  close, onSignedIn,
}: {
  close: () => void;
  onSignedIn: (user: User) => void;
}) {
  const buttonRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState<"signup" | "signin">("signup");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  // Only fetched for the sign-in path, and only used to warn. Starts at null so
  // "we haven't looked yet" is distinct from "there is nothing here".
  const [localItems, setLocalItems] = useState<number | null>(null);

  const isSignUp = mode === "signup";
  const tooShort = isSignUp && password.length > 0 && password.length < MIN_PASSWORD_LENGTH;
  const canSubmit = email.trim() !== "" && password !== "" && !busy && !tooShort;

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
      onSignedIn(await (isSignUp ? signUp(email.trim(), password) : signIn(email.trim(), password)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") close(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

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
              onSignedIn(await signInWithGoogle(res.credential));
            } catch (e) {
              setError(e instanceof Error ? e.message : "Sign-in failed.");
            } finally {
              setBusy(false);
            }
          },
        });
        window.google.accounts.id.renderButton(buttonRef.current, {
          theme: "outline", size: "large", text: "signin_with", width: 280,
        });
      })
      .catch((e) => alive && setError(e instanceof Error ? e.message : "Sign-in unavailable."));
    return () => { alive = false; };
  }, [onSignedIn]);

  // Focus moves into the dialog, and back to the trigger on close — the same
  // rule as the tool sheets and the urgent handoff. aria-modal tells assistive
  // tech the page behind is inert, so leaving focus out there contradicts what
  // the dialog has just announced about itself.
  const dialogRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const first = dialogRef.current?.querySelector<HTMLElement>(
      'input, button:not([aria-label^="Close"]), a[href]',
    );
    (first ?? dialogRef.current)?.focus();
    return () => opener?.focus?.();
  }, []);

  return (
    <div className="modal-backdrop" onClick={close} role="dialog" aria-modal="true" aria-label="Sign in">
      <div className="modal" ref={dialogRef} tabIndex={-1} style={{ maxWidth: 420 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p>Optional</p>
            <h2>{isSignUp ? "Create an account" : "Sign in"}</h2>
          </div>
          <button onClick={close} aria-label="Close sign in"><X size={17} /></button>
        </div>
        <div className="modal-body">
          <p style={{ margin: "0 0 16px", color: "var(--muted)", fontSize: 14, lineHeight: 1.7 }}>
            {isSignUp
              ? "Aira works without an account. Creating one keeps your care context if you change device — everything you've already added comes with you."
              : "Enter the email and password you signed up with."}
          </p>

          {/* Signing IN abandons what's in this browser. Say so BEFORE it
              happens — discovering it afterwards is not recoverable. Shown only
              when there is actually something to lose. */}
          {!isSignUp && localItems !== null && localItems > 0 && (
            <div className="banner" style={{ marginBottom: 14, alignItems: "flex-start" }}>
              <AlertTriangle size={15} />
              <span>
                This browser has {localItems} care {localItems === 1 ? "item" : "items"}{" "}
                {localItems === 1 ? "that isn't" : "that aren't"} part of an account. Signing in
                switches to your account&apos;s data and leaves {localItems === 1 ? "it" : "them"}{" "}
                behind. To keep {localItems === 1 ? "it" : "them"} instead, create an account.
              </span>
            </div>
          )}

          {error && <div className="banner error" role="alert" style={{ marginBottom: 14 }}>{error}</div>}

          <form onSubmit={submit}>
            <label className="field">
              <span>Email</span>
              <input
                type="email" value={email} autoComplete="email" required
                onChange={(ev) => setEmail(ev.target.value)}
                placeholder="you@example.com"
              />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                type="password" value={password} required
                autoComplete={isSignUp ? "new-password" : "current-password"}
                onChange={(ev) => setPassword(ev.target.value)}
              />
            </label>
            {isSignUp && (
              <p style={{
                margin: "-6px 0 14px", fontSize: 13, lineHeight: 1.6,
                color: tooShort ? "var(--red)" : "var(--muted)",
              }}>
                {/* Stated up front rather than as an error after submitting. */}
                At least {MIN_PASSWORD_LENGTH} characters. Length matters more than symbols —
                a short phrase you&apos;ll remember beats P@ssw0rd.
              </p>
            )}
            <button className="btn-primary" type="submit" disabled={!canSubmit} style={{ width: "100%" }}>
              {busy
                ? (isSignUp ? "Creating your account…" : "Signing in…")
                : (isSignUp ? "Create account" : "Sign in")}
            </button>
          </form>

          <button
            className="btn-ghost"
            style={{ width: "100%", marginTop: 10, border: 0 }}
            onClick={() => { setMode(isSignUp ? "signin" : "signup"); setError(""); }}
          >
            {isSignUp ? "Already have an account? Sign in" : "New to Aira? Create an account"}
          </button>

          {!isSignUp && (
            <p style={{ margin: "6px 0 0", color: "var(--muted)", fontSize: 13, lineHeight: 1.6 }}>
              {/* Honest about a gap rather than a link that goes nowhere:
                  resetting a password needs to send email, and this build has
                  no provider configured. */}
              Password resets aren&apos;t available in this build yet. If you can&apos;t sign in,
              you can keep using Aira without an account.
            </p>
          )}

          {GOOGLE_CLIENT_ID ? (
            <>
              <p className="or-rule"><span>or</span></p>
              <div ref={buttonRef} style={{ display: "grid", placeItems: "center", minHeight: 44 }} />
            </>
          ) : (
            <p style={{ margin: "16px 0 0", color: "var(--muted)", fontSize: 13, lineHeight: 1.6 }}>
              Google Sign-In isn&apos;t configured for this deployment, so email and password
              is the only way in right now. Your data is safe either way — it&apos;s stored
              against this browser and you can export it at any time.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
