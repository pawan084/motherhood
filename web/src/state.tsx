/** App-level state: device identity, config catalogs, care context.
 *
 * The care context is THE routing signal: null context => onboarding runs
 * inside chat; a context => the five tabs unlock. Everything renders from
 * what the backend actually returns — nothing here fabricates data.
 */
import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from "react";

import { AppConfig, CareContext, ensureDeviceToken, getCareContext, getConfig } from "./api";

type AppState = {
  ready: boolean;
  offline: boolean;
  config: AppConfig | null;
  context: CareContext | null;
  setContext: (ctx: CareContext) => void;
  refresh: () => Promise<void>;
};

const Ctx = createContext<AppState | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [offline, setOffline] = useState(false);
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [context, setContext] = useState<CareContext | null>(null);

  const refresh = useCallback(async () => {
    try {
      await ensureDeviceToken();
      const [cfg, ctx] = await Promise.all([getConfig(), getCareContext()]);
      setConfig(cfg);
      setContext(ctx);
      setOffline(false);
    } catch {
      // The offline state renders honestly (a banner + retry) — the app never
      // pretends the backend answered.
      setOffline(true);
    } finally {
      setReady(true);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <Ctx.Provider value={{ ready, offline, config, context, setContext, refresh }}>
      {children}
    </Ctx.Provider>
  );
}

export function useApp(): AppState {
  const value = useContext(Ctx);
  if (!value) throw new Error("useApp outside AppProvider");
  return value;
}

/** The offline-first emergency profile (ref/mockup.png "Urgent help" +
 * "Emergency profile"). Stored in localStorage BY DESIGN: it must open with
 * no network and no backend. It never syncs anywhere. */
export type EmergencyProfile = {
  name: string;
  stageLine: string;
  doctor: string;
  hospital: string;
  phone: string;
  notes: string;
};

const EMERGENCY_KEY = "aira.emergency_profile";

export function loadEmergencyProfile(): EmergencyProfile {
  try {
    const raw = localStorage.getItem(EMERGENCY_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    /* corrupted -> fresh */
  }
  return { name: "", stageLine: "", doctor: "", hospital: "", phone: "", notes: "" };
}

export function saveEmergencyProfile(profile: EmergencyProfile): void {
  localStorage.setItem(EMERGENCY_KEY, JSON.stringify(profile));
}
