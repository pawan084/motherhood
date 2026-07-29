import type { Metadata } from "next";
import LegalPage from "../legal/legal";

export const metadata: Metadata = {
  title: "Privacy — Aira",
  description:
    "What Aira stores, what it never does with your health data, and how to export or delete everything.",
};

// The claims below are the ones the product actually implements today — each
// maps to a real endpoint or a real gate, so this page can't drift into
// describing a product we don't ship:
//   "never used for advertising"  -> consent.py, data_for_ads is locked off
//   "you control what's remembered" -> /v1/memory (approve / forget)
//   "export or delete"            -> /v1/account/export, /v1/account/delete
//   "screened before every reply" -> chat.py safety gate
// Anything a lawyer still needs to add goes around these, not over them.
export default function Privacy() {
  return (
    <LegalPage title="Privacy" updated="29 July 2026">
      <p>
        Aira is built around one idea: your health context belongs to you. This
        page describes what the product does today, not what it might do later.
      </p>

      <h2>Your health data is never an advertising product</h2>
      <p>
        This is not a preference you have to find and switch off — it is locked
        off in the consent ledger and cannot be granted, by you or by us. There
        is no code path that shares your care context with an advertiser.
      </p>

      <h2>You control what Aira remembers</h2>
      <p>
        Aira keeps a small amount of care context so it doesn&apos;t ask you the
        same thing every day — your journey, goals, appointments and medicines.
        Every item is listed under <strong>You → What Aira remembers</strong>,
        where you can stop it being used in answers or delete it outright. If
        you turn AI personalisation off, nothing remembered reaches a reply at
        all.
      </p>

      <h2>Messages are screened before Aira answers</h2>
      <p>
        Every message passes a safety gate first. Ordinary conversations are not
        retained. Messages that raise an urgent or concerning signal are kept
        with their safety classification so a human can review the system&apos;s
        behaviour — that record is what makes the safety gate auditable rather
        than a claim.
      </p>

      <h2>Documents you upload</h2>
      <p>
        Prescriptions, reports and scans stay in your private Care Vault. A
        document is never read into an answer unless you approve it. In the
        current build Aira stores a document&apos;s details — name, type and
        size — and does not extract text from it.
      </p>

      <h2>Export or delete, whenever you want</h2>
      <p>
        <strong>You → Your data</strong> gives you both. Export downloads
        everything Aira holds for you as a single JSON file. Deletion is
        immediate and irreversible: it removes your profile, conversations, care
        items, memory, consent history and safety records, and the deletion has
        to be confirmed before it runs.
      </p>

      <h2>Partner access</h2>
      <p>
        If you invite a partner, you choose what they see. Appointments and
        reminders are separate from health details, which stay off unless you
        turn them on — and even then a partner sees counts, never the text of a
        symptom log or a private check-in note. An invite works once, expires
        after seven days, and you can revoke it at any time, including after it
        has been accepted. A partner can never change your care data.
      </p>

      <h2>Getting in touch</h2>
      <p>
        <strong>You → Help &amp; feedback</strong> sends a report to the same
        review queue as the safety flags, so a person reads it.
      </p>
    </LegalPage>
  );
}
