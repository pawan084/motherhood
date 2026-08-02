/** Today: the calm landing tab. Renders ONLY from the real care context —
 * greeting, stage, computed week. The "one meaningful next action" concept
 * arrives with the P4-P7 backends; until then the single honest action is
 * continuing with Aira. */
import { ChevronRight, MessageCircle } from "lucide-react";

import { useApp } from "../state";

const STAGE_TITLES: Record<string, string> = {
  trying_to_conceive: "Trying to conceive",
  pregnant: "Pregnant",
  postpartum: "Postpartum",
};

function trimester(week: number): string {
  if (week <= 13) return "First trimester";
  if (week <= 27) return "Second trimester";
  return "Third trimester";
}

export default function TodayScreen({ onChat }: { onChat: () => void }) {
  const { context } = useApp();
  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  const name = context?.display_name ? `, ${context.display_name}` : "";

  return (
    <main className="screen-content today">
      <section className="today-intro">
        <span className="eyebrow">
          {greeting}
          {name}
        </span>
        <h1>One calm place for your care.</h1>
      </section>

      {context && (
        <section className="context-card">
          {context.week !== null && (
            <div className="context-ring">
              <strong>{context.week}</strong>
              <span>{context.stage === "postpartum" ? "weeks in" : "weeks"}</span>
            </div>
          )}
          <div>
            <span className="eyebrow">Your current context</span>
            <h2>
              {context.stage === "pregnant" && context.week !== null
                ? trimester(context.week)
                : STAGE_TITLES[context.stage] ?? context.stage}
            </h2>
            <p>
              {context.stage === "pregnant" && context.due_date && `Due ${context.due_date}`}
              {context.stage === "postpartum" && context.birth_date && `Born ${context.birth_date}`}
              {context.stage === "trying_to_conceive" && "Aira is here through the planning too."}
            </p>
          </div>
        </section>
      )}

      <button className="quiet-chat" onClick={onChat}>
        <MessageCircle size={18} />
        <span>
          <strong>Continue with Aira</strong>
          <small>Ask anything — every message is safety-checked</small>
        </span>
        <ChevronRight size={18} />
      </button>
    </main>
  );
}
