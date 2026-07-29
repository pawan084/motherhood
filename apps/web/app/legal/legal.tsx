// Shared chrome for the public legal pages.
//
// These exist because the landing footer linked to /privacy and /terms while
// the app had exactly one route, so both 404'd. The backend does serve legal
// copy (backend/legal.py), but on the API origin — a visitor clicking "Privacy"
// in the footer never reaches it. A maternal-health product with a dead privacy
// link is the worst possible one to leave broken, so the pages are served by
// the web app itself.
//
// Static and server-rendered on purpose: no client JS, no API call, no auth.
// They must load for someone with no account, and for an app-store reviewer.

import Link from "next/link";

export default function LegalPage({
  title, updated, children,
}: {
  title: string;
  updated: string;
  children: React.ReactNode;
}) {
  return (
    <div className="legal-page">
      <header>
        <Link className="landing-brand" href="/" aria-label="Aira home">
          <span className="brand-orb compact" aria-hidden="true"><i /><b /></span>
          <span>Aira</span>
        </Link>
        <nav aria-label="Legal">
          <Link href="/privacy">Privacy</Link>
          <Link href="/terms">Terms</Link>
        </nav>
      </header>

      <main>
        <p className="eyebrow">Legal</p>
        <h1>{title}</h1>
        <p className="legal-updated">Last updated {updated}</p>
        {children}
        <p className="legal-note">
          Aira supports wellbeing and care preparation. It does not diagnose,
          prescribe, or replace your care team. In an emergency, contact your
          local emergency services.
        </p>
      </main>

      <footer>
        <Link href="/">← Back to Aira</Link>
      </footer>
    </div>
  );
}
