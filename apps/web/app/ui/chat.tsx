"use client";

// Aira — the conversation. A full-height chat panel plus a tool drawer on
// desktop; the drawer collapses under 780px and the same tools stay reachable
// from the composer's "+" button.
//
// The safety gate runs on the SERVER (`POST /v1/chat/turn`). A red result comes
// back with `reply: null` and an `urgent_help` payload carrying a real care-team
// number, and we route straight to the urgent screen without rendering an AI
// answer. RED_WORDS below is NOT a safety gate — it is the offline fallback used
// only when the request itself fails, and it errs toward showing urgent help.

import { useEffect, useRef, useState, type FormEvent } from "react";
import { AlertTriangle, CalendarDays, ChevronRight, ClipboardCheck, Heart, Mic, Pill, Plus, Send, ShieldCheck, Sparkles, Upload, Wind } from "lucide-react";
import { AiraAPI, type TurnResponse, type UrgentHelp } from "../aira-api";
import { isToolName, type ToolName } from "./types";

const RED_WORDS = ["bleeding", "chest pain", "cannot breathe", "can't breathe",
  "severe pain", "fainted", "not moving", "kill myself", "emergency"];

const OFFLINE_URGENT: UrgentHelp = {
  headline: "Please contact your care team now.",
  message: "Do not wait for an AI response if you feel seriously unwell or are worried about your baby.",
  care_team: { name: null, phone: null },
  emergency_contact: { name: null, phone: null },
  show_emergency_services: true,
};

export const CHAT_TOOLS: { name: ToolName; label: string; detail: string; icon: typeof Heart }[] = [
  { name: "checkin", label: "Check in", detail: "Mood, energy and sleep", icon: Heart },
  { name: "reminder", label: "Reminder", detail: "A medicine or care task", icon: ClipboardCheck },
  { name: "medicine", label: "Medicines", detail: "Track a routine you were given", icon: Pill },
  { name: "appointment", label: "Appointment", detail: "Prepare for your next visit", icon: CalendarDays },
  { name: "upload", label: "Care Vault", detail: "A prescription, report or scan", icon: Upload },
  { name: "symptom", label: "Track a change", detail: "Log it, don't diagnose it", icon: AlertTriangle },
  { name: "wellness", label: "Two-minute reset", detail: "A calm pause", icon: Wind },
];

type Entry = {
  from: "user" | "aira";
  text: string;
  trust?: "wellness" | "watchful" | null;
  card?: { tool: string; title: string; detail: string } | null;
  disclaimer?: boolean;
};

export default function Chat({
  openTool, onUrgent, onDegraded, onAfterTurn,
}: {
  openTool: (t: ToolName) => void;
  onUrgent: (payload: UrgentHelp) => void;
  onDegraded: (degraded: boolean) => void;
  onAfterTurn: () => void;
}) {
  const [log, setLog] = useState<Entry[]>([]);
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [toolsOpen, setToolsOpen] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Rehydrate the conversation from the server so it survives a reload.
  useEffect(() => {
    AiraAPI.chatHistory(50)
      .then((r) => setLog(r.items.map((i) => ({
        from: i.role === "user" ? "user" : "aira",
        text: i.text,
        trust: i.safety_level === "amber" ? "watchful" : i.safety_level === "green" ? "wellness" : null,
      }))))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [log, sending]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const value = message.trim();
    if (!value || sending) return;
    setMessage("");
    setError("");

    const priorHistory = log.map((m) => ({
      role: m.from === "user" ? "user" : "assistant", content: m.text,
    }));
    setLog((c) => [...c, { from: "user", text: value }]);
    setSending(true);
    try {
      const res: TurnResponse = await AiraAPI.chatTurn(value, priorHistory);
      onDegraded(res.safety.degraded);
      if (res.urgent) {
        onUrgent(res.urgent_help ?? OFFLINE_URGENT);
        return;
      }
      setLog((c) => [...c, {
        from: "aira", text: res.reply ?? "",
        trust: res.trust_label, card: res.action_card, disclaimer: res.disclaimer_needed,
      }]);
      onAfterTurn();
    } catch (err) {
      // The server gate never ran. Fail toward the urgent handoff on anything
      // that looks serious, and say plainly that we're offline otherwise.
      if (RED_WORDS.some((w) => value.toLowerCase().includes(w))) {
        onUrgent(OFFLINE_URGENT);
        return;
      }
      setError(err instanceof Error ? err.message : "Couldn't reach Aira.");
      setLog((c) => [...c, {
        from: "aira", trust: null,
        text: "I can't reach my safety checks right now, so I won't answer this one. If anything feels urgent, contact your care team.",
      }]);
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="chat-layout">
      <section className="panel chat-panel">
        <div className="chat-title">
          <span className="brand-orb compact" aria-hidden="true"><i /><b /></span>
          <div>
            <h2>Aira</h2>
            <p>Wellness support, not diagnosis or emergency care</p>
          </div>
          <span className="online">Screened before every reply</span>
        </div>

        <div className="messages" ref={scrollRef}>
          {log.length === 0 && !sending && (
            <div className="message aira">
              Hi — I&apos;m Aira. Tell me how you&apos;re doing, or ask me anything.
              I&apos;ll keep it to one clear next step.
            </div>
          )}
          {log.map((m, i) => (
            m.from === "user"
              ? <div className="message me" key={i}>{m.text}</div>
              : (
                <div className="message aira" key={i}>
                  {m.trust && (
                    <span className="trust-label" style={{
                      color: m.trust === "watchful" ? "var(--amber)" : "#346147",
                    }}>
                      <ShieldCheck size={12} />
                      {m.trust === "watchful" ? "Watchful · consider your care team" : "Wellness guidance"}
                    </span>
                  )}
                  <div>{m.text}</div>
                  {m.card && isToolName(m.card.tool) && (
                    <button className="message-card" onClick={() => openTool(m.card!.tool as ToolName)}>
                      <Sparkles size={16} />
                      <span style={{ flex: 1 }}>
                        <strong>{m.card.title}</strong>
                        <small>{m.card.detail}</small>
                      </span>
                      <ChevronRight size={15} />
                    </button>
                  )}
                  {m.disclaimer && (
                    <small className="disclaimer">
                      This isn&apos;t medical advice — please check with your care team.
                    </small>
                  )}
                </div>
              )
          ))}
          {sending && <div className="message aira" style={{ opacity: .6 }}>…</div>}
        </div>

        {error && <div className="banner error" style={{ margin: "0 14px" }}><AlertTriangle size={15} /> {error}</div>}

        <form className="chat-composer" onSubmit={submit}>
          <button type="button" onClick={() => setToolsOpen(true)} aria-label="Open tools">
            <Plus size={18} />
          </button>
          <input
            aria-label="Message Aira"
            placeholder="Message Aira…"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
          />
          <button type="button" aria-label="Voice input (coming soon)" disabled title="Voice input is not wired up yet">
            <Mic size={17} />
          </button>
          <button className="send" aria-label="Send message" disabled={sending || !message.trim()}>
            <Send size={16} />
          </button>
        </form>
      </section>

      <aside className="panel tool-drawer">
        <p className="eyebrow">Tools in this conversation</p>
        <h3>What would help right now?</h3>
        <div className="tool-list">
          {CHAT_TOOLS.map(({ name, label, detail, icon: Icon }) => (
            <button key={name} onClick={() => openTool(name)}>
              <span className="tool-icon"><Icon size={17} /></span>
              <span>
                <strong>{label}</strong>
                <small>{detail}</small>
              </span>
              <ChevronRight size={15} />
            </button>
          ))}
        </div>
      </aside>

      {/* Small screens: the drawer is hidden, so the composer's "+" opens the
          same list as a sheet rather than losing access to the tools. */}
      {toolsOpen && (
        <div className="modal-backdrop" onClick={() => setToolsOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <div>
                <p>Tools</p>
                <h2>What would help right now?</h2>
              </div>
              <button onClick={() => setToolsOpen(false)} aria-label="Close tools">✕</button>
            </div>
            <div className="modal-body">
              <div className="tool-list">
                {CHAT_TOOLS.map(({ name, label, detail, icon: Icon }) => (
                  <button key={name} onClick={() => { setToolsOpen(false); openTool(name); }}>
                    <span className="tool-icon"><Icon size={17} /></span>
                    <span>
                      <strong>{label}</strong>
                      <small>{detail}</small>
                    </span>
                    <ChevronRight size={15} />
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
