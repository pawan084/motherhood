/** Journey: the week, honestly. The editorial week-by-week content is a P5
 * backend feature (CMS-seeded, admin-authored); until it exists this screen
 * shows the real computed week and says what's coming instead of faking
 * medical content client-side. */
import { useApp } from "../state";

export default function JourneyScreen() {
  const { context } = useApp();
  if (!context) return null;

  return (
    <main className="screen-content journey">
      <span className="eyebrow">Your journey</span>
      {context.week !== null ? (
        <>
          <h1>
            Week {context.week}
            {context.stage === "postpartum" ? " after birth" : ""}.
          </h1>
          <section className="journey-hero">
            <div className="journey-orbit">
              <span>{context.week}</span>
              <small>weeks</small>
            </div>
            <div>
              <span className="card-kicker">This week</span>
              <h2>{context.stage === "pregnant" ? "Counting toward your due date" : "Finding your rhythm"}</h2>
              <p>
                {context.stage === "pregnant" && context.due_date
                  ? `Week 40 lands on ${context.due_date}.`
                  : "Every week is its own pace — Aira follows yours."}
              </p>
            </div>
          </section>
        </>
      ) : (
        <h1>Your journey, at your pace.</h1>
      )}
      <div className="coming-note">
        Week-by-week guidance — your body, your baby, and preparing for visits — is being authored with
        clinical review and will appear here. Ask Aira anything in the meantime.
      </div>
    </main>
  );
}
