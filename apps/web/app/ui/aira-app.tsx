"use client";

// The Aira web application root: owns the shared data, the active screen, and
// the two global overlays (a tool sheet and the urgent handoff).
//
// Onboarding is gated on the SERVER's `user.onboarded` rather than local state,
// so refreshing the page doesn't drop a returning user back into setup.

import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronLeft } from "lucide-react";
import {
  AiraAPI, health, type CareData, type CareItem, type ConsentFeature, type EmergencyProfile,
  type JourneyData, type TodayData, type UrgentHelp, type User,
} from "../aira-api";
import { Header, MobileNav, Sidebar } from "./shell";
import Today from "./today";
import Chat from "./chat";
import JourneyScreen from "./journey";
import Care from "./care";
import Updates, { buildUpdates } from "./updates";
import You from "./you";
import ToolSheet from "./tools";
import Urgent from "./urgent";
import Onboarding from "./onboarding";
import { useScreen } from "./use-hash";
import type { Screen, ToolName } from "./types";

const BREADCRUMB: Record<Screen, string> = {
  Today: "Your day",
  Aira: "Conversation",
  Journey: "Where you are",
  Care: "Appointments, medicines and documents",
  Updates: "What needs attention",
  You: "Profile, privacy and data",
};

export default function AiraApp({ onExit }: { onExit: () => void }) {
  // The active screen lives in the URL, so refresh and Back both behave.
  const [screen, setScreen] = useScreen();
  const [user, setUser] = useState<User | null>(null);
  const [today, setToday] = useState<TodayData | null>(null);
  const [journey, setJourney] = useState<JourneyData | null>(null);
  const [care, setCare] = useState<CareData | null>(null);
  const [timeline, setTimeline] = useState<CareItem[]>([]);
  const [emergency, setEmergency] = useState<EmergencyProfile | null>(null);
  const [consent, setConsent] = useState<ConsentFeature[]>([]);
  const [loading, setLoading] = useState(true);
  const [bootError, setBootError] = useState("");
  const [degraded, setDegraded] = useState(false);
  const [tool, setTool] = useState<ToolName | null>(null);
  const [urgent, setUrgent] = useState<{ open: boolean; payload: UrgentHelp | null }>(
    { open: false, payload: null });

  const refresh = useCallback(async () => {
    const [t, c, e, tl] = await Promise.allSettled([
      AiraAPI.today(), AiraAPI.care(), AiraAPI.emergencyProfile(), AiraAPI.timeline(),
    ]);
    if (t.status === "fulfilled") setToday(t.value);
    if (c.status === "fulfilled") setCare(c.value);
    if (e.status === "fulfilled") setEmergency(e.value);
    if (tl.status === "fulfilled") setTimeline(tl.value.items);
  }, []);

  // First load. `me` decides whether onboarding runs, so it gates the rest.
  useEffect(() => {
    let alive = true;
    (async () => {
      // Seed the trust pill before any turn: with no classifier configured,
      // screening is keyword-only from the very first message, and the header
      // should say so rather than defaulting to a reassuring green.
      health()
        .then((h) => alive && setDegraded(!h.llm_configured))
        .catch(() => alive && setDegraded(true));
      try {
        const { user: u } = await AiraAPI.me();
        if (!alive) return;
        setUser(u);
        if (u.onboarded) {
          await refresh();
          AiraAPI.journey().then((j) => alive && setJourney(j)).catch(() => undefined);
          AiraAPI.consent().then((r) => alive && setConsent(r.features)).catch(() => undefined);
        }
      } catch (err) {
        if (alive) {
          setBootError(err instanceof Error ? err.message
            : "Couldn't reach the Aira backend.");
        }
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; };
  }, [refresh]);

  // Journey content only changes when the profile does, so it's refetched on
  // demand rather than on every screen change.
  const reloadJourney = useCallback(() => {
    AiraAPI.journey().then(setJourney).catch(() => undefined);
  }, []);

  const onboarded = !!user?.onboarded;

  const markTaken = useCallback(async (id: string) => {
    try {
      await AiraAPI.markMedicineTaken(id);
      await refresh();
    } catch { /* the row simply stays due */ }
  }, [refresh]);

  const setReminderDone = useCallback(async (id: string, done: boolean) => {
    try {
      await AiraAPI.setReminderDone(id, done);
      await refresh();
    } catch { /* the row keeps its previous state */ }
  }, [refresh]);

  const renameItem = useCallback(async (id: string, field: string, value: string) => {
    try {
      await AiraAPI.updateCareItem(id, { [field]: value });
      await refresh();
    } catch { /* the row keeps its previous label */ }
  }, [refresh]);

  const deleteItem = useCallback(async (id: string) => {
    try {
      await AiraAPI.deleteCareItem(id);
      await refresh();
    } catch { /* the row stays */ }
  }, [refresh]);

  const openUrgent = (payload: UrgentHelp | null) => {
    setTool(null);
    setUrgent({ open: true, payload });
  };

  const updates = useMemo(
    () => buildUpdates(care, emergency, degraded, setTool, setScreen, markTaken),
    [care, emergency, degraded, markTaken, setScreen],
  );

  if (loading) {
    return (
      <div className="aira-app">
        <div className="workspace" style={{ marginLeft: 0, display: "grid", placeItems: "center", minHeight: "100vh" }}>
          <p style={{ color: "var(--muted)" }}>Opening Aira…</p>
        </div>
      </div>
    );
  }

  if (bootError) {
    return (
      <div className="aira-app">
        <div className="workspace" style={{ marginLeft: 0 }}>
          <div className="page-wrap" style={{ maxWidth: 620, paddingTop: 80 }}>
            <button className="btn-ghost" onClick={onExit} style={{ marginBottom: 22 }}>
              <ChevronLeft size={15} /> Back to the site
            </button>
            <h1 style={{ fontFamily: "var(--serif)", fontSize: 34, fontWeight: 500 }}>
              Aira can&apos;t reach its backend.
            </h1>
            <p style={{ color: "var(--muted)", lineHeight: 1.7 }}>
              The app needs the API for its safety gate, so it won&apos;t run on
              placeholder content. Start the backend with{" "}
              <code>uvicorn app:app --reload</code> in <code>backend/</code>, and make
              sure <code>VITE_API_URL</code> points at it.
            </p>
            <div className="banner error" style={{ marginTop: 18 }}>{bootError}</div>
          </div>
        </div>
      </div>
    );
  }

  // Onboarding takes the whole workspace — no sidebar to wander off into.
  if (!onboarded) {
    return (
      <div className="aira-app">
        <div className="workspace" style={{ marginLeft: 0 }}>
          <header className="web-header">
            <div>
              <p className="breadcrumb">Private setup</p>
              <h1>Welcome to Aira</h1>
            </div>
            <button className="btn-ghost" onClick={onExit}>
              <ChevronLeft size={15} /> Back to the site
            </button>
          </header>
          <Onboarding onDone={async () => {
            const { user: u } = await AiraAPI.me();
            setUser(u);
            await refresh();
            reloadJourney();
            AiraAPI.consent().then((r) => setConsent(r.features)).catch(() => undefined);
            setScreen("Today");
          }} />
        </div>
      </div>
    );
  }

  return (
    <div className="aira-app">
      <Sidebar
        active={screen} onNavigate={setScreen} locked={false} user={user} today={today}
        badge={updates.length} onUrgent={() => openUrgent(null)}
      />

      <div className="workspace">
        <Header
          title={screen === "Aira" ? "Aira" : screen}
          breadcrumb={BREADCRUMB[screen]}
          trust={{ degraded }}
          onUrgent={() => openUrgent(null)}
          onNavigate={setScreen}
          badge={updates.length}
        />

        <div className="page-wrap">
          {screen === "Today" && (
            <Today
              today={today} care={care} loading={care === null}
              openTool={setTool}
              onNavigate={(s) => setScreen(s)}
              onMarkTaken={markTaken}
              onReminderDone={setReminderDone}
            />
          )}
          {screen === "Aira" && (
            <Chat
              openTool={setTool}
              onUrgent={(p) => openUrgent(p)}
              onDegraded={setDegraded}
              onAfterTurn={refresh}
            />
          )}
          {screen === "Journey" && <JourneyScreen journey={journey} loading={!journey} />}
          {screen === "Care" && (
            <Care care={care} emergency={emergency} timeline={timeline}
                  loading={care === null}
                  openTool={setTool} onMarkTaken={markTaken}
                  onReminderDone={setReminderDone}
                  onRename={renameItem} onDelete={deleteItem} />
          )}
          {screen === "Updates" && <Updates updates={updates} loading={care === null} />}
          {screen === "You" && (
            <You
              user={user} consent={consent} openTool={setTool}
              onProfileSaved={(u) => { setUser(u); refresh(); reloadJourney(); }}
              onConsentChanged={setConsent}
              onDeleted={onExit}
            />
          )}
        </div>
      </div>

      <MobileNav active={screen} onNavigate={setScreen} locked={false} badge={updates.length} />

      {tool && (
        <ToolSheet
          tool={tool}
          close={() => setTool(null)}
          onSaved={() => { refresh(); AiraAPI.consent().then((r) => setConsent(r.features)).catch(() => undefined); }}
        />
      )}
      {urgent.open && (
        <Urgent
          payload={urgent.payload}
          close={() => setUrgent({ open: false, payload: null })}
          openEmergencyProfile={() => { setUrgent({ open: false, payload: null }); setTool("emergency"); }}
        />
      )}
    </div>
  );
}
