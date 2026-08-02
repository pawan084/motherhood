/** App shell: header, five tabs, the urgent overlay.
 *
 * Tabs other than Aira stay locked until a care context exists (onboarding
 * runs inside chat, per ref/diagram.png). The urgent overlay opens from the
 * header on every screen AND from a chat turn's gate decision.
 */
import { useState } from "react";
import { Bell, BookOpen, BriefcaseMedical, House, Siren, Sparkles, UserRound } from "lucide-react";

import { UrgentHelpPayload } from "./api";
import CareScreen from "./screens/CareScreen";
import ChatScreen from "./screens/ChatScreen";
import JourneyScreen from "./screens/JourneyScreen";
import TodayScreen from "./screens/TodayScreen";
import UrgentHelp from "./screens/UrgentHelp";
import YouScreen from "./screens/YouScreen";
import { useApp } from "./state";

type Screen = "Today" | "Aira" | "Journey" | "Care" | "You";

const NAV: { name: Screen; icon: typeof House }[] = [
  { name: "Today", icon: House },
  { name: "Aira", icon: Sparkles },
  { name: "Journey", icon: BookOpen },
  { name: "Care", icon: BriefcaseMedical },
  { name: "You", icon: UserRound },
];

export default function App() {
  const { ready, offline, context, refresh } = useApp();
  const [screen, setScreen] = useState<Screen>("Aira");
  const [urgent, setUrgent] = useState<{ open: boolean; payload?: UrgentHelpPayload }>({ open: false });
  const locked = !context;

  if (!ready)
    return (
      <div className="boot">
        <span className="brand-orb" aria-hidden="true" />
        <p>Aira</p>
      </div>
    );

  if (offline)
    return (
      <div className="boot">
        <span className="brand-orb" aria-hidden="true" />
        <p>Aira can't reach its backend right now.</p>
        <button className="primary-action" onClick={() => void refresh()}>
          Try again
        </button>
        <small className="boot-note">
          If you feel seriously unwell, contact your care team — don't wait for the app.
        </small>
      </div>
    );

  const go = (next: Screen) => {
    if (locked && next !== "Aira") return;
    setScreen(next);
  };

  return (
    <div className="app-frame">
      <header className="app-header">
        <div className="app-brand">
          <span className="brand-orb compact" aria-hidden="true" />
          <div>
            <strong>Aira</strong>
            <small>{locked ? "Private setup" : "Your care companion"}</small>
          </div>
        </div>
        <div className="header-actions">
          <button className="icon-button" aria-label="Notifications (coming soon)" disabled>
            <Bell size={18} />
          </button>
          <button
            className="icon-button urgent"
            onClick={() => setUrgent({ open: true })}
            aria-label="Open urgent help"
          >
            <Siren size={18} />
          </button>
        </div>
      </header>

      {screen === "Today" && <TodayScreen onChat={() => setScreen("Aira")} />}
      {screen === "Aira" && <ChatScreen onUrgent={(payload) => setUrgent({ open: true, payload })} />}
      {screen === "Journey" && <JourneyScreen />}
      {screen === "Care" && <CareScreen />}
      {screen === "You" && <YouScreen onRedoSetup={() => setScreen("Aira")} />}

      <nav className="app-nav" aria-label="Aira navigation">
        {NAV.map(({ name, icon: Icon }) => (
          <button
            key={name}
            className={screen === name ? "active" : ""}
            onClick={() => go(name)}
            disabled={locked && name !== "Aira"}
            aria-label={locked && name !== "Aira" ? `${name}, available after setup` : name}
          >
            <Icon size={19} strokeWidth={screen === name ? 2.3 : 1.7} />
            <span>{name}</span>
          </button>
        ))}
      </nav>

      {urgent.open && <UrgentHelp payload={urgent.payload} close={() => setUrgent({ open: false })} />}
    </div>
  );
}
