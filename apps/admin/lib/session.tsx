"use client";

// The signed-in admin, resolved from the server.
//
// The console previously never called GET /admin/me. It read `role` out of
// localStorage and trusted it, which meant two things: a stale value survived a
// role change until something happened to 401, and the middleware's cookie check
// only proved a cookie was *present*, not that the session behind it was valid.
// So the UI could render a full owner console for an expired session and only
// fall over on the first request.
//
// This resolves the session once per mount and treats the server's answer as
// authoritative. localStorage is still written, but only as a cache for the
// first paint — never as the source of truth.
//
// This is UX correctness, not a security boundary: every admin route is
// re-authorised server-side by `require_admin`, so hiding a nav item has never
// been what stops a viewer from acting like an owner.

import { createContext, useContext, useEffect, useState } from "react";
import { api, getRole, setRole } from "./api";

export type AdminSession = { email: string; role: string };

type SessionState = {
  session: AdminSession | null;
  /** True until /admin/me answers; render skeletons rather than a wrong role. */
  loading: boolean;
};

const SessionContext = createContext<SessionState>({ session: null, loading: true });

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AdminSession | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    api<AdminSession>("/me")
      .then((s) => {
        if (!alive) return;
        setSession(s);
        setRole(s.role);          // refresh the first-paint cache
      })
      // A 401 is already handled inside api(): it clears the role and sends the
      // browser to /login, so there is nothing useful to do here.
      .catch(() => undefined)
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, []);

  return (
    <SessionContext.Provider value={{ session, loading }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession(): SessionState {
  return useContext(SessionContext);
}

/**
 * The admin's role once confirmed, falling back to the cached value only while
 * the check is still in flight — so the first paint isn't empty, but anything
 * rendered after the answer arrives reflects the server.
 */
export function useRole(): string | null {
  const { session, loading } = useSession();
  return session?.role ?? (loading ? getRole() : null);
}
