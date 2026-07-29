import type { Metadata } from "next";
import LegalPage from "../legal/legal";

export const metadata: Metadata = {
  title: "Terms — Aira",
  description:
    "What Aira is, what it is explicitly not, and the limits you should hold it to.",
};

export default function Terms() {
  return (
    <LegalPage title="Terms" updated="29 July 2026">
      <p>
        By using Aira you agree to the terms below. The most important one is
        the first, and it is not boilerplate.
      </p>

      <h2>Aira is wellness support, not medical care</h2>
      <p>
        Aira does not diagnose conditions, prescribe or change medication, or
        replace your doctor or midwife. It can help you prepare for a visit,
        keep track of a routine you were already given, and think something
        through — that is the whole of it. Nothing Aira says is medical advice.
      </p>

      <h2>In an emergency, do not use Aira</h2>
      <p>
        If you are seriously unwell or worried about your baby, contact your
        care team or your local emergency services. Do not wait for an AI
        response. When Aira detects an urgent signal it stops answering and
        hands you to your care team — but you should never rely on it to notice
        first.
      </p>

      <h2>Medication</h2>
      <p>
        Aira organises reminders for a routine your care team gave you. It never
        starts, stops or changes medication. If a reminder in Aira disagrees with
        what your care team told you, your care team is right.
      </p>

      <h2>What we ask of you</h2>
      <p>
        Use Aira for yourself, or as a partner someone has explicitly invited.
        Don&apos;t use it to give anyone else medical advice, and don&apos;t rely
        on it as the only record of your care — keep whatever your clinic gives
        you.
      </p>

      <h2>Availability and accuracy</h2>
      <p>
        Aira is offered as-is. AI answers can be wrong or incomplete, and the
        service can be unavailable. When Aira&apos;s safety screening is degraded
        or unreachable, the app says so rather than quietly answering anyway —
        please take that notice seriously when you see it.
      </p>

      <h2>Your data and ending your use</h2>
      <p>
        You can export or delete everything at any time from{" "}
        <strong>You → Your data</strong>. Deleting your data ends your use of
        Aira and is irreversible. See the <a href="/privacy">Privacy</a> page for
        what is stored and why.
      </p>
    </LegalPage>
  );
}
