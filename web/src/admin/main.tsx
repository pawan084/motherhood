/** Aira admin console — deliberately small: login, overview, the safety
 * review queue, and the Journey CMS. The token lives in sessionStorage (an
 * admin session should not survive the browser closing).
 */
import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";

import "../styles.css";
import "./admin.css";

const API = (import.meta.env.VITE_API_URL as string | undefined) ?? "http://127.0.0.1:8001";

function token(): string | null {
  return sessionStorage.getItem("aira.admin_token");
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(`${API}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
      ...init?.headers,
    },
  });
  if (r.status === 401) {
    sessionStorage.removeItem("aira.admin_token");
    location.reload();
  }
  if (!r.ok) throw new Error((await r.json().catch(() => null))?.detail ?? `${r.status}`);
  return r.json();
}

function Login({ onDone }: { onDone: (role: string) => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const submit = async (e: { preventDefault(): void }) => {
    e.preventDefault();
    setError("");
    try {
      const r = await call<{ token: string; role: string }>("/admin/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      sessionStorage.setItem("aira.admin_token", r.token);
      onDone(r.role);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    }
  };
  return (
    <form className="admin-login" onSubmit={(e) => void submit(e)}>
      <h1>Aira admin</h1>
      <input placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <input
        placeholder="Password"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      {error && <p className="field-error">{error}</p>}
      <button className="primary-action">Sign in</button>
    </form>
  );
}

type AuditRow = {
  id: string;
  ts: number;
  input: string;
  stage: string;
  week: number | null;
  decision: string;
  source: string;
  matched: string[];
  reason: string;
  latency_ms: number;
  review: { note: string; ts: number } | null;
};

function SafetyQueue() {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [total, setTotal] = useState(0);
  const [decision, setDecision] = useState("urgent");
  const [unreviewedOnly, setUnreviewedOnly] = useState(true);
  const load = () => {
    const q = new URLSearchParams();
    if (decision) q.set("decision", decision);
    if (unreviewedOnly) q.set("unreviewed", "true");
    void call<{ total: number; rows: AuditRow[] }>(`/admin/safety-audit?${q}`).then((r) => {
      setRows(r.rows);
      setTotal(r.total);
    });
  };
  useEffect(load, [decision, unreviewedOnly]);

  const review = async (id: string) => {
    const note = prompt("Review note (optional):") ?? "";
    await call(`/admin/safety-audit/${id}/review`, { method: "POST", body: JSON.stringify({ note }) });
    load();
  };

  return (
    <section className="admin-panel">
      <header>
        <h2>Safety review queue</h2>
        <div className="admin-filters">
          <select value={decision} onChange={(e) => setDecision(e.target.value)}>
            <option value="">All decisions</option>
            {["urgent", "caution", "ok", "error"].map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
          <label>
            <input
              type="checkbox"
              checked={unreviewedOnly}
              onChange={(e) => setUnreviewedOnly(e.target.checked)}
            />
            unreviewed only
          </label>
          <span className="admin-count">{total} rows</span>
        </div>
      </header>
      {rows.map((r) => (
        <article className={`audit-row d-${r.decision}`} key={r.id}>
          <div className="audit-meta">
            <strong>{r.decision}</strong>
            <span>{r.source}</span>
            <span>
              {r.stage || "no stage"}
              {r.week != null && ` · wk ${r.week}`}
            </span>
            <span>{new Date(r.ts * 1000).toLocaleString()}</span>
            <span>{r.latency_ms} ms</span>
          </div>
          <p className="audit-input">{r.input}</p>
          {r.matched.length > 0 && <p className="audit-matched">rules: {r.matched.join(", ")}</p>}
          {r.reason && <p className="audit-reason">{r.reason}</p>}
          {r.review ? (
            <p className="audit-reviewed">✓ reviewed{r.review.note && ` — ${r.review.note}`}</p>
          ) : (
            <button className="secondary-action" onClick={() => void review(r.id)}>
              Mark reviewed
            </button>
          )}
        </article>
      ))}
      {rows.length === 0 && <p className="care-empty">Nothing in this view.</p>}
    </section>
  );
}

type Band = {
  id: string;
  stage: string;
  week_start: number;
  week_end: number;
  title: string;
  yourself: string;
  baby: string;
  prepare: string;
};

function JourneyCms() {
  const [bands, setBands] = useState<Band[]>([]);
  const [open, setOpen] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const load = () => void call<{ bands: Band[] }>("/admin/journey").then((r) => setBands(r.bands));
  useEffect(load, []);

  const save = async (band: Band) => {
    await call(`/admin/journey/${band.id}`, {
      method: "PUT",
      body: JSON.stringify({
        title: band.title,
        yourself: band.yourself,
        baby: band.baby,
        prepare: band.prepare,
      }),
    });
    setSaved(band.id);
    setTimeout(() => setSaved(null), 1500);
  };

  const edit = (id: string, field: keyof Band, value: string) =>
    setBands((cur) => cur.map((b) => (b.id === id ? { ...b, [field]: value } : b)));

  return (
    <section className="admin-panel">
      <header>
        <h2>Journey content</h2>
      </header>
      {bands.map((b) => (
        <article className="band-row" key={b.id}>
          <button className="band-head" onClick={() => setOpen(open === b.id ? null : b.id)}>
            <strong>{b.title}</strong>
            <span>
              {b.stage}
              {b.week_end > 0 && ` · wk ${b.week_start}–${b.week_end}`}
            </span>
          </button>
          {open === b.id && (
            <div className="band-edit">
              <input value={b.title} onChange={(e) => edit(b.id, "title", e.target.value)} />
              {(["yourself", "baby", "prepare"] as const).map((field) => (
                <label key={field}>
                  <span>{field}</span>
                  <textarea
                    rows={3}
                    value={b[field]}
                    onChange={(e) => edit(b.id, field, e.target.value)}
                  />
                </label>
              ))}
              <button className="primary-action" onClick={() => void save(b)}>
                {saved === b.id ? "Saved" : "Save band"}
              </button>
            </div>
          )}
        </article>
      ))}
    </section>
  );
}

function Overview() {
  const [stats, setStats] = useState<Record<string, number> | null>(null);
  useEffect(() => {
    void call<Record<string, number>>("/admin/overview").then(setStats);
  }, []);
  if (!stats) return null;
  const items: [string, string][] = [
    ["learners_with_context", "Learners"],
    ["accounts", "Accounts"],
    ["turns_screened", "Turns screened"],
    ["urgent_turns", "Urgent"],
    ["unreviewed_urgent", "Unreviewed urgent"],
  ];
  return (
    <section className="admin-stats">
      {items.map(([key, label]) => (
        <div key={key} className={key === "unreviewed_urgent" && stats[key] > 0 ? "hot" : ""}>
          <strong>{stats[key]}</strong>
          <span>{label}</span>
        </div>
      ))}
    </section>
  );
}

function AdminApp() {
  const [role, setRole] = useState<string | null>(null);
  useEffect(() => {
    if (token())
      void call<{ role: string }>("/admin/me")
        .then((r) => setRole(r.role))
        .catch(() => sessionStorage.removeItem("aira.admin_token"));
  }, []);

  if (!token() || !role) return <Login onDone={setRole} />;
  return (
    <div className="admin-frame">
      <header className="admin-top">
        <h1>Aira admin</h1>
        <span>{role}</span>
        <button
          className="card-link"
          onClick={() => {
            sessionStorage.removeItem("aira.admin_token");
            location.reload();
          }}
        >
          Sign out
        </button>
      </header>
      <Overview />
      {(role === "support" || role === "owner") && <SafetyQueue />}
      <JourneyCms />
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AdminApp />
  </StrictMode>,
);
