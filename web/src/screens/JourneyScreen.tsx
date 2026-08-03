/** Journey: real week-banded content from GET /journey — the mockup's three
 * sections (your body / your baby / prepare for your visit) plus week paging
 * within the learner's own stage. Browsing never modifies the stored context.
 */
import { useEffect, useState } from "react";
import { Activity, CalendarDays, ChevronLeft, ChevronRight, Heart } from "lucide-react";

import { getJourney, JourneyResponse } from "../api";
import { useApp } from "../state";

const MAX_WEEK: Record<string, number> = { pregnant: 42, postpartum: 52 };

export default function JourneyScreen() {
  const { context } = useApp();
  const [data, setData] = useState<JourneyResponse | null>(null);
  const [failed, setFailed] = useState(false);

  const load = (week?: number) => {
    setFailed(false);
    getJourney(week)
      .then(setData)
      .catch(() => setFailed(true));
  };
  useEffect(() => load(), []);

  if (!context) return null;
  if (failed)
    return (
      <main className="screen-content journey">
        <span className="eyebrow">Your journey</span>
        <h1>Couldn't load this right now.</h1>
        <button className="secondary-action" onClick={() => load()}>
          Try again
        </button>
      </main>
    );
  if (!data || !data.content) return <main className="screen-content journey" />;

  const week = data.shown_week;
  const maxWeek = MAX_WEEK[data.stage] ?? 0;
  const isCurrent = week !== null && week === data.current_week;

  const sections = [
    { icon: Activity, title: data.stage === "postpartum" ? "You" : "Your body", text: data.content.yourself },
    { icon: Heart, title: "Your baby", text: data.content.baby },
    { icon: CalendarDays, title: "Prepare for your visit", text: data.content.prepare },
  ].filter((s) => s.text);

  return (
    <main className="screen-content journey">
      <span className="eyebrow">Your journey</span>
      {week !== null ? (
        <div className="week-pager">
          <button aria-label="Previous week" disabled={week <= 1} onClick={() => load(week - 1)}>
            <ChevronLeft size={18} />
          </button>
          <div className="journey-orbit">
            <span>{week}</span>
            <small>{data.stage === "postpartum" ? "weeks in" : "weeks"}</small>
          </div>
          <button aria-label="Next week" disabled={week >= maxWeek} onClick={() => load(week + 1)}>
            <ChevronRight size={18} />
          </button>
        </div>
      ) : (
        <h1>{data.content.title}</h1>
      )}
      {week !== null && (
        <div className="week-title">
          <h1>{data.content.title}</h1>
          {!isCurrent && data.current_week !== null && (
            <button className="card-link" onClick={() => load()}>
              Back to your week ({data.current_week})
            </button>
          )}
        </div>
      )}

      <div className="journey-sections">
        {sections.map(({ icon: Icon, title, text }) => (
          <section className="journey-block" key={title}>
            <span className="journey-block-head">
              <Icon size={16} /> {title}
            </span>
            <p>{text}</p>
          </section>
        ))}
      </div>

      <p className="disclaimer">{data.disclaimer}</p>
    </main>
  );
}
