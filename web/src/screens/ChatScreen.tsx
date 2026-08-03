/** The Aira tab: onboarding-in-chat until a care context exists, then the
 * live streaming conversation.
 *
 * Gate handling mirrors the backend contract exactly:
 *  - `urgent`  -> the full-screen Urgent Help takeover (no reply exists)
 *  - `error`   -> an honest inline notice; the trust chip says
 *                 "Safety check unavailable", never "Safety checked"
 *  - `caution`/`ok` -> the reply streams in under its trust chip
 */
import { FormEvent, useEffect, useRef, useState } from "react";
import { ArrowRight, Check, Mic, ShieldCheck, Sparkles, Square } from "lucide-react";

import {
  Card,
  CareContext,
  putCareContext,
  respondStream,
  respondVoice,
  speak,
  UrgentHelpPayload,
} from "../api";
import { useApp } from "../state";

type Message =
  | { kind: "user"; text: string }
  | { kind: "aira"; text: string; label?: string; decision?: string; cards?: Card[] }
  | { kind: "notice"; text: string };

const STAGES = [
  { value: "trying_to_conceive", title: "Trying to conceive", detail: "Planning and preconception support" },
  { value: "pregnant", title: "Pregnant", detail: "Week-by-week support" },
  { value: "postpartum", title: "Postpartum", detail: "Recovery and newborn rhythm" },
] as const;

const LANGUAGES = [
  { value: "en", label: "English" },
  { value: "hi", label: "हिन्दी" },
  { value: "hi-Latn", label: "Hinglish" },
];

function OnboardingFlow({ onDone }: { onDone: (ctx: CareContext) => void }) {
  const [step, setStep] = useState(0);
  const [stage, setStage] = useState<string>("");
  const [date, setDate] = useState("");
  const [name, setName] = useState("");
  const [language, setLanguage] = useState("en");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const needsDate = stage === "pregnant" || stage === "postpartum";

  const submit = async () => {
    setSaving(true);
    setError("");
    try {
      const ctx = await putCareContext({
        stage,
        ...(stage === "pregnant" ? { due_date: date } : {}),
        ...(stage === "postpartum" ? { birth_date: date } : {}),
        display_name: name.trim() || undefined,
        language,
      });
      onDone(ctx);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save — please try again.");
      setSaving(false);
    }
  };

  if (step === 0)
    return (
      <section className="inline-card">
        <span className="card-kicker">1 of 3 · Your journey</span>
        <h2>Where are you right now?</h2>
        <p>Choose a starting point. Nothing here is a diagnosis.</p>
        <div className="option-list">
          {STAGES.map((s) => (
            <button
              key={s.value}
              onClick={() => {
                setStage(s.value);
                setStep(1);
              }}
            >
              <span>
                <strong>{s.title}</strong>
                <small>{s.detail}</small>
              </span>
              <ArrowRight size={16} />
            </button>
          ))}
        </div>
      </section>
    );

  if (step === 1)
    return (
      <section className="inline-card">
        <span className="card-kicker">2 of 3 · The basics</span>
        <h2>{stage === "pregnant" ? "When is your baby due?" : stage === "postpartum" ? "When was your baby born?" : "A little context"}</h2>
        {needsDate ? (
          <label className="field">
            <span>{stage === "pregnant" ? "Estimated due date" : "Baby's birth date"}</span>
            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
        ) : (
          <p>No dates needed for now.</p>
        )}
        <button className="primary-action" disabled={needsDate && !date} onClick={() => setStep(2)}>
          Continue <ArrowRight size={16} />
        </button>
        <button className="card-link" onClick={() => setStep(0)}>
          Back
        </button>
      </section>
    );

  return (
    <section className="inline-card">
      <span className="card-kicker">3 of 3 · You</span>
      <h2>What should Aira call you?</h2>
      <label className="field">
        <span>
          Name <em>Optional</em>
        </span>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" />
      </label>
      <label className="field">
        <span>Preferred language</span>
        <select value={language} onChange={(e) => setLanguage(e.target.value)}>
          {LANGUAGES.map((l) => (
            <option key={l.value} value={l.value}>
              {l.label}
            </option>
          ))}
        </select>
      </label>
      {error && <p className="field-error">{error}</p>}
      <button className="primary-action" disabled={saving} onClick={() => void submit()}>
        {saving ? "Saving…" : "Finish setup"} <Check size={16} />
      </button>
      <button className="card-link" onClick={() => setStep(1)}>
        Back
      </button>
    </section>
  );
}

export default function ChatScreen({ onUrgent }: { onUrgent: (payload?: UrgentHelpPayload) => void }) {
  const { context, setContext, config } = useApp();
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [micError, setMicError] = useState("");
  const recorderRef = useRef<MediaRecorder | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  const historyOf = (msgs: Message[]) =>
    msgs
      .filter((m): m is Extract<Message, { kind: "user" | "aira" }> => m.kind === "user" || m.kind === "aira")
      .slice(-20)
      .map((m) => ({ role: m.kind === "user" ? ("user" as const) : ("assistant" as const), content: m.text }));

  const playReply = async (text: string) => {
    const blob = await speak(text);
    if (!blob) return; // voice is an enhancement; the text already rendered
    const url = URL.createObjectURL(blob);
    audioRef.current?.pause();
    audioRef.current = new Audio(url);
    audioRef.current.onended = () => URL.revokeObjectURL(url);
    void audioRef.current.play().catch(() => URL.revokeObjectURL(url));
  };

  const sendVoice = async (blob: Blob) => {
    setBusy(true);
    try {
      const turn = await respondVoice(blob, historyOf(messages));
      if (turn.decision === "empty") {
        setMessages((cur) => [...cur, { kind: "notice", text: turn.message ?? "I couldn't hear that." }]);
        return;
      }
      if (turn.transcript) setMessages((cur) => [...cur, { kind: "user", text: turn.transcript! }]);
      if (turn.decision === "urgent") {
        onUrgent(turn.urgent_help);
        setMessages((cur) => [
          ...cur,
          { kind: "notice", text: turn.urgent_help?.headline ?? "Please contact your care team now." },
        ]);
        return;
      }
      if (turn.decision === "error" || !turn.reply) {
        setMessages((cur) => [
          ...cur,
          { kind: "aira", text: turn.message ?? "Aira can't safely respond right now.",
            label: turn.safety?.label, decision: "error" },
        ]);
        return;
      }
      setMessages((cur) => [
        ...cur,
        { kind: "aira", text: turn.reply!, label: turn.safety?.label, decision: turn.decision, cards: turn.cards },
      ]);
      void playReply(turn.reply);
    } catch {
      setMessages((cur) => [...cur, { kind: "notice", text: "Voice turn failed — please try again." }]);
    } finally {
      setBusy(false);
    }
  };

  const toggleRecording = async () => {
    setMicError("");
    if (recording) {
      recorderRef.current?.stop();
      setRecording(false);
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      const chunks: BlobPart[] = [];
      recorder.ondataavailable = (e) => e.data.size && chunks.push(e.data);
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(chunks, { type: recorder.mimeType || "audio/webm" });
        if (blob.size > 0) void sendVoice(blob);
      };
      recorderRef.current = recorder;
      recorder.start();
      setRecording(true);
    } catch {
      setMicError("Microphone unavailable — check the browser permission.");
    }
  };

  const send = async (event: FormEvent) => {
    event.preventDefault();
    const text = input.trim();
    if (!text || busy) return;
    setInput("");
    setBusy(true);

    // History from rendered messages (user/aira only, bounded server-side too).
    const history = historyOf(messages);

    setMessages((cur) => [...cur, { kind: "user", text }]);
    let replyIndex = -1;

    await respondStream(text, history, {
      onGate: (decision, label, urgentHelp, message) => {
        if (decision === "urgent") {
          onUrgent(urgentHelp);
          setMessages((cur) => [
            ...cur,
            { kind: "notice", text: urgentHelp?.headline ?? "Please contact your care team now." },
          ]);
          return;
        }
        if (decision === "error") {
          setMessages((cur) => [
            ...cur,
            { kind: "aira", text: message ?? "Aira can't safely respond right now.", label, decision },
          ]);
          return;
        }
        setMessages((cur) => {
          replyIndex = cur.length;
          return [...cur, { kind: "aira", text: "", label, decision }];
        });
      },
      onDelta: (chunk) => {
        setMessages((cur) =>
          cur.map((m, i) => (i === replyIndex && m.kind === "aira" ? { ...m, text: m.text + chunk } : m)),
        );
      },
      onCards: (cards) => {
        setMessages((cur) => cur.map((m, i) => (i === replyIndex && m.kind === "aira" ? { ...m, cards } : m)));
      },
      onError: (message) => {
        setMessages((cur) => [...cur, { kind: "notice", text: message }]);
      },
      onDone: () => setBusy(false),
    });
  };

  if (!context)
    return (
      <main className="screen-content chat" ref={scrollRef}>
        <div className="safety-chip">
          <ShieldCheck size={13} /> Private by design
        </div>
        <div className="ai-message">
          Hi, I'm Aira. We'll set up your care here in chat — one small question at a time.
        </div>
        <OnboardingFlow onDone={setContext} />
      </main>
    );

  return (
    <>
      <main className="screen-content chat" ref={scrollRef}>
        {messages.length === 0 && (
          <>
            <div className="safety-chip">
              <ShieldCheck size={13} /> Every message is safety-checked
            </div>
            <div className="ai-message">
              {context.display_name ? `Hi ${context.display_name}. ` : "Hi. "}
              How are you feeling today?
            </div>
            {config?.ai_disclaimer && <p className="disclaimer">{config.ai_disclaimer}</p>}
          </>
        )}
        {messages.map((m, i) => {
          if (m.kind === "user")
            return (
              <div className="message-row user" key={i}>
                <div className="user-message">{m.text}</div>
              </div>
            );
          if (m.kind === "notice")
            return (
              <div className="notice-message" key={i}>
                {m.text}
              </div>
            );
          return (
            <div className="message-row aira" key={i}>
              <div className={m.decision === "error" ? "ai-message error" : "ai-message"}>
                {m.label && (
                  <span className={m.decision === "error" ? "trust-chip unavailable" : "trust-chip"}>
                    <ShieldCheck size={11} /> {m.label}
                  </span>
                )}
                {m.text || (busy && i === messages.length - 1 ? "…" : m.text)}
                {m.cards && m.cards.length > 0 && (
                  <div className="card-row">
                    {m.cards.map((card, j) => (
                      <button
                        key={j}
                        className="action-card"
                        onClick={() =>
                          setMessages((cur) => [
                            ...cur,
                            { kind: "notice", text: `"${card.title}" is on the way — this tool isn't built yet.` },
                          ])
                        }
                      >
                        <Sparkles size={13} />
                        <span>
                          <strong>{card.title}</strong>
                          {card.subtitle && <small>{card.subtitle}</small>}
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </main>
      {micError && <p className="mic-error">{micError}</p>}
      <form className="composer" onSubmit={(e) => void send(e)}>
        <input
          aria-label="Message Aira"
          placeholder={recording ? "Listening…" : "Message Aira…"}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={busy || recording}
        />
        <button
          type="button"
          className={recording ? "composer-mic recording" : "composer-mic"}
          aria-label={recording ? "Stop recording" : "Speak to Aira"}
          disabled={busy}
          onClick={() => void toggleRecording()}
        >
          {recording ? <Square size={15} /> : <Mic size={17} />}
        </button>
        <button className="composer-send" aria-label="Send message" disabled={busy || recording || !input.trim()}>
          <ArrowRight size={17} />
        </button>
      </form>
    </>
  );
}
