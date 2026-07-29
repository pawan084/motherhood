"use client";

// Google Sign-In.
//
// The backend has had a complete implementation all along — POST /account/google
// verifies the ID token against Google's certs and promotes an anonymous device
// user in place, keeping their care data — and no client ever called it. The
// landing page's "Sign in" button simply entered the app as an anonymous user,
// which is the one CTA in the product that was mislabelled rather than inert.
//
// Signing in is optional by design: Aira works anonymously from first launch,
// and an account exists so a user can keep their care context across devices.
// So this is a modal you can close, not a gate in front of the product.
//
// When VITE_GOOGLE_CLIENT_ID is unset this says so plainly instead of rendering
// a button that can only fail — the server 503s without a client ID configured,
// and "Sign in" leading to a 503 would be a worse lie than the one being fixed.

import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import { GOOGLE_CLIENT_ID, signInWithGoogle, type User } from "../aira-api";

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

  return (
    <div className="modal-backdrop" onClick={close} role="dialog" aria-modal="true" aria-label="Sign in">
      <div className="modal" style={{ maxWidth: 420 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p>Optional</p>
            <h2>Sign in</h2>
          </div>
          <button onClick={close} aria-label="Close sign in"><X size={17} /></button>
        </div>
        <div className="modal-body">
          <p style={{ margin: "0 0 16px", color: "var(--muted)", fontSize: 14, lineHeight: 1.7 }}>
            Aira works without an account. Signing in keeps your care context if
            you change device — everything you&apos;ve already added comes with you.
          </p>

          {error && <div className="banner error" style={{ marginBottom: 14 }}>{error}</div>}

          {GOOGLE_CLIENT_ID ? (
            <>
              <div ref={buttonRef} style={{ display: "grid", placeItems: "center", minHeight: 44 }} />
              {busy && (
                <p style={{ margin: "12px 0 0", color: "var(--muted)", fontSize: 13, textAlign: "center" }}>
                  Signing you in…
                </p>
              )}
            </>
          ) : (
            <div className="note-line">
              {/* One <span>, deliberately. `.note-line` is a flex row built for
                  "icon + text", so every inline child becomes its own flex
                  column — the <code> tokens split this paragraph into five
                  columns and shredded it. Wrapping keeps it a single item. */}
              <span>
                Google Sign-In isn&apos;t configured for this deployment, so
                there is nothing to sign in with yet. Set{" "}
                <code>VITE_GOOGLE_CLIENT_ID</code> here and{" "}
                <code>GOOGLE_CLIENT_ID</code> on the backend to enable it. Your
                data is safe either way — it&apos;s stored against this browser
                and you can export it at any time.
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
