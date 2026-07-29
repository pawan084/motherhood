"use client";

// The application shell: a fixed sidebar + workspace on desktop, collapsing to a
// bottom tab bar under 780px. This is what replaces the old 390x812 phone bezel
// the app used to render inside.

import { Bell, BookOpen, BriefcaseMedical, House, Settings, ShieldCheck, Siren, Sparkles, UserRound, type LucideIcon } from "lucide-react";
import type { Screen } from "./types";
import { JOURNEY_LABEL } from "./types";
import type { TodayData, User } from "../aira-api";

export const NAV: { name: Screen; icon: LucideIcon; blurb: string }[] = [
  { name: "Today", icon: House, blurb: "Your one next step" },
  // Not "by text or voice": spoken conversation isn't wired up in this build,
  // and the composer's mic is disabled to match.
  { name: "Aira", icon: Sparkles, blurb: "Chat with Aira" },
  { name: "Journey", icon: BookOpen, blurb: "Where you are now" },
  { name: "Care", icon: BriefcaseMedical, blurb: "Appointments and reminders" },
  { name: "Updates", icon: Bell, blurb: "What needs attention" },
  { name: "You", icon: UserRound, blurb: "Privacy and preferences" },
];

function BrandOrb({ className = "brand-orb compact" }: { className?: string }) {
  return <span className={className} aria-hidden="true"><i /><b /></span>;
}

/** Trust state shown in the header. `degraded` means the LLM classifier was
 *  unavailable and only the deterministic keyword floor ran — the user is still
 *  protected, but we say so rather than implying full coverage. */
export type TrustState = { degraded: boolean };

export function Sidebar({
  active, onNavigate, locked, user, today, badge, onUrgent,
}: {
  active: Screen;
  onNavigate: (s: Screen) => void;
  locked: boolean;
  user: User | null;
  today: TodayData | null;
  badge: number;
  onUrgent: () => void;
}) {
  const journeyLabel = today?.journey ? JOURNEY_LABEL[today.journey] ?? "Exploring" : "Exploring";
  const context = today?.weeks != null ? `Week ${today.weeks} · ${journeyLabel}` : journeyLabel;
  const name = user?.name || today?.name || "You";
  return (
    <aside className="sidebar">
      <button className="sidebar-brand" onClick={() => onNavigate("Today")} disabled={locked}>
        <BrandOrb />
        <span>Aira</span>
      </button>

      <p className="side-label">Your care</p>
      <nav className="side-nav" aria-label="Sections">
        {NAV.map(({ name: n, icon: Icon, blurb }) => (
          <button
            key={n}
            className={active === n ? "active" : ""}
            onClick={() => onNavigate(n)}
            disabled={locked && n !== "Aira"}
            aria-current={active === n ? "page" : undefined}
            title={blurb}
          >
            <Icon size={17} />
            <span>{n}</span>
            {n === "Updates" && badge > 0 && <b>{badge}</b>}
          </button>
        ))}
      </nav>

      <div className="care-context">
        <BrandOrb className="brand-orb compact" />
        <div>
          <strong>{context}</strong>
          <small>{today?.context_line || "Private care context"}</small>
        </div>
      </div>

      <button className="urgent-link" onClick={onUrgent}>
        <Siren size={15} /> Urgent help
      </button>

      <div className="side-profile">
        <span>{(name[0] || "Y").toUpperCase()}</span>
        <div>
          <strong>{name}</strong>
          <small>{user?.email || "Private account"}</small>
        </div>
        <button onClick={() => onNavigate("You")} aria-label="Open settings" disabled={locked}>
          <Settings size={16} />
        </button>
      </div>
    </aside>
  );
}

export function MobileNav({
  active, onNavigate, locked, badge,
}: { active: Screen; onNavigate: (s: Screen) => void; locked: boolean; badge: number }) {
  return (
    <nav className="mobile-nav" aria-label="Sections">
      {NAV.map(({ name: n, icon: Icon }) => (
        <button
          key={n}
          className={active === n ? "active" : ""}
          onClick={() => onNavigate(n)}
          disabled={locked && n !== "Aira"}
          aria-current={active === n ? "page" : undefined}
        >
          <span style={{ position: "relative", display: "grid", placeItems: "center" }}>
            <Icon size={19} />
            {n === "Updates" && badge > 0 && (
              <i style={{
                position: "absolute", top: -3, right: -6, minWidth: 14, height: 14,
                display: "grid", placeItems: "center", padding: "0 3px",
                borderRadius: 99, background: "var(--red)", color: "#fff",
                fontSize: 9, fontStyle: "normal", fontWeight: 800,
              }}>{badge}</i>
            )}
          </span>
          <span>{n}</span>
        </button>
      ))}
    </nav>
  );
}

export function Header({
  title, breadcrumb, trust, onUrgent, onNavigate, badge,
}: {
  title: string;
  breadcrumb: string;
  trust: TrustState;
  onUrgent: () => void;
  onNavigate: (s: Screen) => void;
  badge: number;
}) {
  return (
    <header className="web-header">
      <div>
        <p className="breadcrumb">{breadcrumb}</p>
        <h1>{title}</h1>
      </div>
      <div className="header-actions">
        <span
          className={trust.degraded ? "trust-pill degraded" : "trust-pill"}
          title={trust.degraded
            ? "The AI classifier is unavailable, so messages are screened by the deterministic keyword gate only. You are still protected; nuance may be missed."
            : "Every message is screened before Aira replies."}
        >
          <ShieldCheck size={14} />
          {trust.degraded ? "Keyword-only screening" : "Safety checked"}
        </span>
        <button className="icon-button" onClick={() => onNavigate("Updates")} aria-label="Open updates">
          <Bell size={16} />
          {badge > 0 && <i />}
        </button>
        <button className="icon-button urgent" onClick={onUrgent} aria-label="Open urgent help">
          <Siren size={16} />
        </button>
      </div>
    </header>
  );
}
