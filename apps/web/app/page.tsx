"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Bell,
  BookOpen,
  Brain,
  BriefcaseMedical,
  CalendarDays,
  Camera,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleUserRound,
  ClipboardCheck,
  Clock,
  Download,
  EyeOff,
  FileText,
  Heart,
  House,
  Image as ImageIcon,
  Languages,
  LifeBuoy,
  LockKeyhole,
  MapPin,
  MessageCircle,
  Mic,
  MoreHorizontal,
  Phone,
  Pill,
  Play,
  Plus,
  ScanLine,
  Send,
  ShieldCheck,
  Siren,
  SlidersHorizontal,
  Sparkles,
  Upload,
  UserRound,
  Users,
  Volume2,
  Wind,
  X,
  type LucideIcon,
} from "lucide-react";
import {
  AiraAPI,
  telHref,
  type UrgentHelp as UrgentHelpPayload,
  type TodayData,
  type JourneyData,
} from "./aira-api";

// Words that trigger an instant urgent route on the client as a FALLBACK only —
// the real gate runs server-side in AiraAPI.chatTurn. This local list keeps the
// prototype safe even when the backend is unreachable.
const RED_WORDS = ["bleeding", "chest pain", "cannot breathe", "can't breathe",
  "severe pain", "fainted", "not moving", "kill myself", "emergency"];

// Shown when the backend is offline and we fall back to the local keyword gate
// (no real care-team number available in that case).
const OFFLINE_URGENT: UrgentHelpPayload = {
  headline: "Please contact your care team now.",
  message: "Do not wait for an AI response if you feel seriously unwell or are worried about your baby.",
  care_team: { name: null, phone: null },
  emergency_contact: { name: null, phone: null },
  show_emergency_services: true,
};

// A live-chat entry, carrying the trust label + action card the server returns.
type ChatEntry = {
  from: "user" | "aira";
  text: string;
  trust?: "wellness" | "watchful" | null;
  card?: { tool: string; title: string; detail: string } | null;
  disclaimer?: boolean;
};

const JOURNEY_TO_API: Record<string, "trying" | "pregnant" | "postpartum" | "exploring"> = {
  "Trying to conceive": "trying",
  Pregnant: "pregnant",
  Postpartum: "postpartum",
  Exploring: "exploring",
};

// Reverse: the API journey key → a human label for headers/rings.
const JOURNEY_LABEL: Record<string, string> = {
  trying: "Trying to conceive",
  pregnant: "Pregnant",
  postpartum: "Postpartum",
  exploring: "Exploring",
};

type Screen = "Today" | "Aira" | "Journey" | "Care" | "You";
type Journey = "Trying to conceive" | "Pregnant" | "Postpartum" | "Exploring";
type Message = { from: "aira" | "user"; text: string };
type ToolName =
  | "notifications"
  | "checkin"
  | "reminder"
  | "medicine"
  | "appointment"
  | "upload"
  | "wellness"
  | "symptom"
  | "partner"
  | "privacy"
  | "memory"
  | "voice"
  | "companion"
  | "careplan"
  | "support"
  | "emergency";

const navigation: { name: Screen; icon: LucideIcon }[] = [
  { name: "Today", icon: House },
  { name: "Aira", icon: Sparkles },
  { name: "Journey", icon: BookOpen },
  { name: "Care", icon: BriefcaseMedical },
  { name: "You", icon: UserRound },
];

const toolMeta: Record<ToolName, { title: string; eyebrow: string; icon: LucideIcon }> = {
  notifications: { title: "Updates", eyebrow: "Notification centre", icon: Bell },
  checkin: { title: "How are you?", eyebrow: "Daily check-in", icon: Heart },
  reminder: { title: "Create a reminder", eyebrow: "Aira tool", icon: Clock },
  medicine: { title: "Medicines", eyebrow: "Care routine", icon: Pill },
  appointment: { title: "Visit copilot", eyebrow: "Tomorrow · 10:30 AM", icon: CalendarDays },
  upload: { title: "Add to Care Vault", eyebrow: "Private document upload", icon: Upload },
  wellness: { title: "A two-minute reset", eyebrow: "Guided wellness", icon: Wind },
  symptom: { title: "Log a symptom", eyebrow: "Track, don’t diagnose", icon: Activity },
  partner: { title: "Partner actions", eyebrow: "Practical support", icon: Users },
  privacy: { title: "Privacy centre", eyebrow: "Your data, your control", icon: LockKeyhole },
  memory: { title: "What Aira remembers", eyebrow: "Care context", icon: Brain },
  voice: { title: "Voice & language", eyebrow: "Conversation settings", icon: Languages },
  companion: { title: "Companion mode", eyebrow: "Avatar & connection", icon: CircleUserRound },
  careplan: { title: "Your care plan", eyebrow: "Week 24 priorities", icon: ClipboardCheck },
  support: { title: "Human support", eyebrow: "Help centre", icon: LifeBuoy },
  emergency: { title: "Emergency profile", eyebrow: "Available offline", icon: Siren },
};

const chatTools: { name: ToolName; label: string; detail: string }[] = [
  { name: "checkin", label: "Check in", detail: "Mood, energy & sleep" },
  { name: "reminder", label: "Reminder", detail: "Medicine or care task" },
  { name: "upload", label: "Care Vault", detail: "Prescription or report" },
  { name: "wellness", label: "Reset", detail: "Two calm minutes" },
  { name: "symptom", label: "Track", detail: "Log a change" },
  { name: "companion", label: "Companion", detail: "Voice or avatar mode" },
];

function BrandOrb({ compact = false }: { compact?: boolean }) {
  return (
    <span className={compact ? "brand-orb compact" : "brand-orb"} aria-hidden="true">
      <i />
      <b />
    </span>
  );
}

function Landing({ enterApp }: { enterApp: () => void }) {
  return (
    <div className="landing">
      <header className="site-header">
        <a className="site-logo" href="#top" aria-label="Aira home">
          <BrandOrb compact />
          <span>Aira</span>
        </a>
        <nav aria-label="Website">
          <a href="#how">How Aira helps</a>
          <a href="#safety">Safety</a>
          <a href="#privacy">Privacy</a>
        </nav>
        <div className="site-actions">
          <button className="text-button" onClick={enterApp}>Sign in</button>
          <button className="site-cta" onClick={enterApp}>Talk to Aira <ArrowRight size={16} /></button>
        </div>
      </header>

      <main id="top">
        <section className="landing-hero">
          <div className="hero-copy">
            <span className="premium-label"><ShieldCheck size={14} /> Private maternal wellness companion</span>
            <h1>Your private AI companion for motherhood.</h1>
            <p>One calm conversation for pregnancy, postpartum, and everything between.</p>
            <div className="hero-actions">
              <button className="site-cta large" onClick={enterApp}>Start with Aira <ArrowRight size={18} /></button>
              <a className="outline-cta" href="#how">See how it works</a>
            </div>
            <div className="trust-row">
              <span><ShieldCheck size={17} /> Safety checked</span>
              <span><LockKeyhole size={17} /> Private by design</span>
              <span><EyeOff size={17} /> No ads</span>
            </div>
          </div>

          <div className="hero-visual" aria-label="Aira mobile app preview">
            <div className="hero-bloom" aria-hidden="true" />
            <div className="landing-phone">
              <div className="phone-island" />
              <div className="preview-top">
                <span>Aira</span>
                <button aria-label="More"><MoreHorizontal size={20} /></button>
              </div>
              <div className="preview-content">
                <div className="preview-trust"><ShieldCheck size={13} /> Safety checked</div>
                <div className="preview-today">
                  <span className="preview-eyebrow">Tuesday · Week 24</span>
                  <h3>Good evening, Maya</h3>
                  <p>Your plan for today</p>
                </div>
                <div className="preview-plan">
                  <span className="preview-icon"><Pill size={20} /></span>
                  <div className="preview-plan-text"><strong>Prenatal vitamin</strong><small>8 PM · with food</small></div>
                  <button onClick={enterApp} className="preview-done"><Check size={12} /> Mark as done</button>
                </div>
                <button className="preview-chat" onClick={enterApp}>
                  <BrandOrb compact />
                  <span><strong>Chat with Aira</strong><small>Ask anything, by text or voice</small></span>
                  <ChevronRight size={16} />
                </button>
              </div>
              <div className="preview-nav" aria-hidden="true">
                <span className="active"><House size={15} /><i>Today</i></span>
                <span><Sparkles size={15} /><i>Aira</i></span>
                <span><BookOpen size={15} /><i>Journey</i></span>
                <span><BriefcaseMedical size={15} /><i>Care</i></span>
                <span><UserRound size={15} /><i>You</i></span>
              </div>
              <div className="home-indicator" />
            </div>
          </div>
        </section>

        <section className="promise-strip" id="how">
          <p>Aira listens, organises and protects—without turning motherhood into another dashboard.</p>
          <div className="promise-grid">
            <article><span>01</span><MessageCircle /><h2>Begin with a conversation</h2><p>Onboarding, questions and care actions happen naturally inside Chat.</p></article>
            <article><span>02</span><Sparkles /><h2>One clear next step</h2><p>Aira reduces noise and surfaces only what deserves attention now.</p></article>
            <article><span>03</span><ShieldCheck /><h2>Safety before answers</h2><p>Every input passes through a safety gate with a clear human handoff.</p></article>
          </div>
        </section>

        <section className="safety-section" id="safety">
          <div>
            <span className="premium-label"><ShieldCheck size={14} /> Clinical trust, wellness tone</span>
            <h2>Calm when you need calm. Clear when you need care.</h2>
          </div>
          <div className="safety-scale">
            <article className="green"><i /><strong>Green</strong><span>Wellness guidance and everyday support</span></article>
            <article className="amber"><i /><strong>Amber</strong><span>Watchful guidance and care-team contact</span></article>
            <article className="red"><i /><strong>Red</strong><span>Immediate human or emergency handoff</span></article>
          </div>
        </section>

        <section className="privacy-section" id="privacy">
          <div className="privacy-orb"><LockKeyhole /></div>
          <div><span className="premium-label light">Privacy is a product feature</span><h2>Your care. Your context. Your control.</h2><p>Review what Aira remembers, choose what can shape future answers, and export or delete selected data at any time.</p></div>
          <button onClick={enterApp}>Explore privacy controls <ArrowRight size={17} /></button>
        </section>
      </main>

      <footer><span className="site-logo"><BrandOrb compact /> Aira</span><p>Wellness support, not diagnosis or emergency care.</p><button onClick={enterApp}>Open the interactive app</button></footer>
    </div>
  );
}

function AppNav({ active, onChange, locked = false }: { active: Screen; onChange: (screen: Screen) => void; locked?: boolean }) {
  return (
    <nav className="app-nav" aria-label="Aira navigation">
      {navigation.map(({ name, icon: Icon }) => (
        <button
          key={name}
          className={active === name ? "active" : ""}
          onClick={() => onChange(name)}
          disabled={locked && name !== "Aira"}
          aria-label={locked && name !== "Aira" ? `${name}, available after setup` : name}
        >
          <Icon size={19} strokeWidth={active === name ? 2.3 : 1.7} />
          <span>{name}</span>
        </button>
      ))}
    </nav>
  );
}

function AppHeader({ detail, urgent, notifications }: { detail?: string; urgent: () => void; notifications: () => void }) {
  return (
    <header className="app-header">
      <div className="app-brand">
        <BrandOrb compact />
        <div><strong>Aira</strong>{detail && <small>{detail}</small>}</div>
      </div>
      <div className="header-actions">
        <button className="icon-button notification" onClick={notifications} aria-label="Open notifications"><Bell size={18} /><i>3</i></button>
        <button className="icon-button urgent" onClick={urgent} aria-label="Open urgent help"><Siren size={18} /></button>
      </div>
    </header>
  );
}

function TodayScreen({ onChat, urgent, onNavigate, openTool }: {
  onChat: () => void;
  urgent: () => void;
  onNavigate: (screen: Screen) => void;
  openTool: (tool: ToolName) => void;
}) {
  const [prepared, setPrepared] = useState(false);
  const [today, setToday] = useState<TodayData | null>(null);

  useEffect(() => {
    AiraAPI.today().then(setToday).catch(() => undefined);
  }, []);

  // Journey-aware fields, falling back to the static demo when offline.
  const weeks = today?.weeks ?? null;
  const journeyLabel = today ? JOURNEY_LABEL[today.journey] ?? "Exploring" : "Pregnant";
  const name = today?.name || "Maya";
  const contextLine = today?.context_line || "Second trimester";
  const priorities = today?.priorities ?? [];
  const contextDetail = priorities.length ? priorities.join(" · ") : "Nothing new to note today";
  const action = today?.next_action ?? {
    tool: "appointment", title: "Prepare for tomorrow’s appointment",
    detail: "Review three questions based on your recent fatigue note.", minutes: 3,
  };
  const ActionIcon = toolMeta[action.tool as ToolName]?.icon ?? CalendarDays;

  return (
    <>
      <AppHeader detail={weeks != null ? `Week ${weeks}` : journeyLabel} urgent={urgent} notifications={() => openTool("notifications")} />
      <main className="screen-content today">
        <section className="today-intro">
          <span className="eyebrow">Good morning, {name}</span>
          <h1>You’re on track.</h1>
          <p>Nothing urgent needs your attention right now.</p>
        </section>
        <section className="context-card">
          <div className="context-ring">{weeks != null ? <><strong>{weeks}</strong><span>weeks</span></> : <><strong>{journeyLabel[0]}</strong><span>stage</span></>}</div>
          <div><span className="eyebrow">Your current context</span><h2>{contextLine}</h2><p>{contextDetail}</p></div>
          <button onClick={() => onNavigate("Journey")} aria-label="Open Journey"><ChevronRight size={18} /></button>
        </section>
        <section className="next-action">
          <div className="next-action-top"><span><Sparkles size={14} /> One meaningful next action</span><small>{action.minutes ? `About ${action.minutes} min` : "A few minutes"}</small></div>
          <div className="next-action-main">
            <span className="large-icon"><ActionIcon size={24} /></span>
            <div><h2>{action.title}</h2><p>{action.detail}</p></div>
          </div>
          <button className={prepared ? "primary-action complete" : "primary-action"} onClick={() => { setPrepared(true); openTool(action.tool as ToolName); }}>
            {prepared ? <><Check size={17} /> Opened</> : <>Start with Aira <ArrowRight size={17} /></>}
          </button>
        </section>
        <div className="all-clear"><CheckCircle2 size={17} /><span><strong>You’re caught up for today.</strong><small>Aira will surface something only when it matters.</small></span></div>
        <button className="quiet-chat" onClick={onChat}><BrandOrb compact /><span><strong>Continue with Aira</strong><small>Ask anything, by text or voice</small></span><ChevronRight size={18} /></button>
      </main>
      <AppNav active="Today" onChange={onNavigate} />
    </>
  );
}

function OnboardingCard({
  step, journey, setJourney, goals, setGoals, language, setLanguage, choose, complete,
}: {
  step: number;
  journey: Journey | "";
  setJourney: (value: Journey) => void;
  goals: string[];
  setGoals: (value: string[]) => void;
  language: string;
  setLanguage: (value: string) => void;
  choose: (value: string) => void;
  complete: () => void;
}) {
  const journeys: { value: Journey; detail: string; icon: LucideIcon }[] = [
    { value: "Trying to conceive", detail: "Planning and preconception support", icon: Heart },
    { value: "Pregnant", detail: "Week-by-week maternal guidance", icon: Sparkles },
    { value: "Postpartum", detail: "Recovery and newborn rhythm", icon: Users },
    { value: "Exploring", detail: "Look around before deciding", icon: BookOpen },
  ];
  const priorities = ["Better sleep", "Less overwhelm", "Nutrition", "Movement", "Medicine routine", "Visit preparation"];

  if (step === 0) return (
    <section className="inline-card onboarding-card">
      <span className="card-kicker">1 of 4 · Your journey</span>
      <h2>Where are you right now?</h2>
      <p>Choose a starting point. Nothing here is a diagnosis.</p>
      <div className="option-list">
        {journeys.map(({ value, detail, icon: Icon }) => (
          <button key={value} onClick={() => { setJourney(value); choose(value); }}>
            <span className="option-icon"><Icon size={18} /></span>
            <span><strong>{value}</strong><small>{detail}</small></span>
            <ChevronRight size={16} />
          </button>
        ))}
      </div>
    </section>
  );

  if (step === 1) return (
    <section className="inline-card onboarding-card">
      <span className="card-kicker">2 of 4 · The basics</span>
      <h2>A little context, at your pace</h2>
      <p>Skip anything you would rather add later.</p>
      <label className="field"><span>{journey === "Pregnant" ? "Due date or current week" : "Helpful date"} <em>Optional</em></span><input placeholder={journey === "Pregnant" ? "e.g. Week 24" : "Add later"} /></label>
      <label className="field"><span>Preferred language</span><select value={language} onChange={(event) => setLanguage(event.target.value)}><option>English</option><option>Hindi</option><option>Hinglish</option><option>Spanish</option></select></label>
      <button className="primary-action" onClick={() => choose(`Continue in ${language}`)}>Continue <ArrowRight size={16} /></button>
      <button className="card-link" onClick={() => choose("I’ll add this later")}>I’ll add this later</button>
    </section>
  );

  if (step === 2) {
    const toggle = (item: string) => setGoals(goals.includes(item) ? goals.filter((goal) => goal !== item) : goals.length < 3 ? [...goals, item] : goals);
    return (
      <section className="inline-card onboarding-card">
        <span className="card-kicker">3 of 4 · Your priorities</span>
        <h2>What would feel most helpful?</h2>
        <p>Choose up to three. Aira will keep Today focused.</p>
        <div className="priority-grid">
          {priorities.map((item) => <button key={item} className={goals.includes(item) ? "selected" : ""} onClick={() => toggle(item)}>{goals.includes(item) ? <Check size={14} /> : <Plus size={14} />}{item}</button>)}
        </div>
        <button className="primary-action" disabled={!goals.length} onClick={() => choose(goals.join(", "))}>Continue with {goals.length} selected <ArrowRight size={16} /></button>
      </section>
    );
  }

  if (step === 3) return (
    <section className="inline-card onboarding-card">
      <span className="card-kicker">4 of 4 · Conversation style</span>
      <h2>How should Aira respond?</h2>
      <p>Switch modes at any time inside Chat.</p>
      <div className="option-list compact-options">
        {[
          { name: "Text chat", detail: "Quiet, clear and easy to revisit", icon: MessageCircle },
          { name: "Voice conversation", detail: "Talk naturally when your hands are full", icon: Mic },
          { name: "Talking avatar", detail: "A warm visual companion · Preview", icon: CircleUserRound },
        ].map(({ name, detail, icon: Icon }) => <button key={name} onClick={() => choose(name)}><span className="option-icon"><Icon size={18} /></span><span><strong>{name}</strong><small>{detail}</small></span><ChevronRight size={16} /></button>)}
      </div>
    </section>
  );

  return (
    <section className="inline-card ready-card">
      <div className="ready-icon"><Check size={21} /></div>
      <span className="card-kicker">Private setup complete</span>
      <h2>Your Aira is ready.</h2>
      <p>We’ll begin with one clear next step—not a crowded dashboard.</p>
      <div className="ready-summary"><span><small>Journey</small><strong>{journey || "Exploring"}</strong></span><span><small>Focus</small><strong>{goals[0] || "Gentle support"}</strong></span></div>
      <button className="primary-action" onClick={complete}>Open my Today <ArrowRight size={16} /></button>
      <button className="card-link">Review what Aira remembers</button>
    </section>
  );
}

function ChatScreen({
  urgent, onUrgent, onNavigate, onboardingComplete, completeOnboarding, openTool,
}: {
  urgent: () => void;
  onUrgent: (payload: UrgentHelpPayload) => void;
  onNavigate: (screen: Screen) => void;
  onboardingComplete: boolean;
  completeOnboarding: () => void;
  openTool: (tool: ToolName) => void;
}) {
  const [step, setStep] = useState(onboardingComplete ? 5 : 0);
  const [journey, setJourney] = useState<Journey | "">("");
  const [goals, setGoals] = useState<string[]>([]);
  const [language, setLanguage] = useState("English");
  const [message, setMessage] = useState("");
  const [chatLog, setChatLog] = useState<ChatEntry[]>([]);
  const [sending, setSending] = useState(false);
  const [prepared, setPrepared] = useState(false);
  const [toolsOpen, setToolsOpen] = useState(false);
  const [history, setHistory] = useState<Message[]>(onboardingComplete ? [] : [{ from: "aira", text: "Hi, I’m Aira. We’ll set up your care here in chat—one small question at a time." }]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const onboarding = !onboardingComplete;

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [history, step, chatLog, sending]);

  // Post the collected onboarding to the backend (best-effort), then continue.
  const finishOnboarding = async () => {
    try {
      await AiraAPI.onboarding({
        journey: JOURNEY_TO_API[journey] || "exploring",
        language,
        priorities: goals,
      });
    } catch {
      // Offline: keep the local prototype flow so the demo still works.
    }
    completeOnboarding();
  };

  const prompts = [
    "First, where are you in your journey?",
    "Thank you. Share only what would make your guidance more useful.",
    "What would you most like support with right now?",
    "How would you like Aira to respond?",
    "Your private care context is ready. You can change any of this later.",
  ];

  const choose = (answer: string) => {
    const next = Math.min(step + 1, 4);
    setHistory((current) => [...current, { from: "user", text: answer }, { from: "aira", text: prompts[next] }]);
    setStep(next);
  };

  const hasRedWord = (value: string) =>
    RED_WORDS.some((word) => value.toLowerCase().includes(word));

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const value = message.trim();
    if (!value) return;

    if (onboarding) {
      // During onboarding, keep the instant local pre-check for urgent routing.
      if (hasRedWord(value)) { setMessage(""); urgent(); return; }
      if (step === 0) setJourney(value.toLowerCase().includes("preg") ? "Pregnant" : value.toLowerCase().includes("post") ? "Postpartum" : "Exploring");
      if (step === 2) setGoals([value]);
      choose(value);
      setMessage("");
      return;
    }

    // Live chat: the SERVER runs the safety gate. Render the user's message,
    // then call the API; a red result routes to urgent help with a real number.
    setMessage("");
    const priorHistory = chatLog.map((m) => ({
      role: m.from === "user" ? "user" : "assistant", content: m.text,
    }));
    setChatLog((c) => [...c, { from: "user", text: value }]);
    setSending(true);
    try {
      const res = await AiraAPI.chatTurn(value, priorHistory);
      if (res.urgent) { onUrgent(res.urgent_help ?? OFFLINE_URGENT); return; }
      setChatLog((c) => [...c, {
        from: "aira", text: res.reply ?? "",
        trust: res.trust_label, card: res.action_card, disclaimer: res.disclaimer_needed,
      }]);
    } catch {
      // Offline fallback: local keyword gate + a calm canned reply.
      if (hasRedWord(value)) { onUrgent(OFFLINE_URGENT); return; }
      setChatLog((c) => [...c, {
        from: "aira", trust: "wellness",
        text: "I’ve understood that. I can help organise the next step, or show you when contacting your care team would be safer.",
      }]);
    } finally {
      setSending(false);
    }
  };

  if (onboarding) return (
    <>
      <AppHeader detail={`Private setup · ${Math.min(step + 1, 4)} of 4`} urgent={urgent} notifications={() => openTool("notifications")} />
      <main className="screen-content chat onboarding-chat" ref={scrollRef}>
        <div className="safety-chip"><ShieldCheck size={13} /> Private by design · Safety checked</div>
        <BrandOrb />
        {history.map((item, index) => <div className={`message-row ${item.from}`} key={`${item.from}-${index}`}><div className={item.from === "aira" ? "ai-message" : "user-message"}>{item.text}</div></div>)}
        <OnboardingCard step={step} journey={journey} setJourney={setJourney} goals={goals} setGoals={setGoals} language={language} setLanguage={setLanguage} choose={choose} complete={finishOnboarding} />
      </main>
      <Composer message={message} setMessage={setMessage} submit={submit} openTools={() => setToolsOpen(true)} openVoice={() => openTool("voice")} simple />
      <AppNav active="Aira" onChange={onNavigate} locked />
    </>
  );

  return (
    <>
      <AppHeader detail="Your care companion" urgent={urgent} notifications={() => openTool("notifications")} />
      <main className="screen-content chat live-chat">
        <div className="safety-chip"><ShieldCheck size={13} /> Safety checked</div>
        <BrandOrb />
        <div className="ai-message">Good morning, Maya. Your appointment is tomorrow. Shall we prepare together?</div>
        <section className="dynamic-card">
          <div className="dynamic-head"><span><Sparkles size={14} /> Next best action</span><small>Based on your context</small></div>
          <div className="dynamic-main"><span className="large-icon"><CalendarDays size={22} /></span><div><h2>Appointment copilot</h2><p>Three questions based on Week 24 and your fatigue note.</p></div></div>
          <button className={prepared ? "primary-action complete" : "primary-action"} onClick={() => setPrepared(true)}>{prepared ? <><Check size={16} /> Questions prepared</> : <>Prepare questions <ArrowRight size={16} /></>}</button>
        </section>
        <div className="suggestion-row">
          <button onClick={() => openTool("checkin")}>I feel tired</button>
          <button onClick={() => openTool("reminder")}>Set a reminder</button>
          <button onClick={() => setChatLog((c) => [...c, { from: "aira", text: "Tell me what’s on your mind. I’ll help make it clearer.", trust: "wellness" }])}>Ask anything</button>
        </div>
        {chatLog.map((m, index) => m.from === "user" ? (
          <div className="message-row user" key={`c-${index}`}><div className="user-message">{m.text}</div></div>
        ) : (
          <div className="ai-message follow-up" key={`c-${index}`}>
            {m.trust && (
              <span className="trust-label" style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 8, fontWeight: 800, textTransform: "uppercase", letterSpacing: ".06em", color: m.trust === "watchful" ? "var(--amber)" : "var(--sage)", marginBottom: 4 }}>
                <ShieldCheck size={11} /> {m.trust === "watchful" ? "Watchful · consider your care team" : "Wellness guidance"}
              </span>
            )}
            <div>{m.text}</div>
            {m.card && (
              <button className="follow-up-card" onClick={() => openTool(m.card!.tool as ToolName)}
                style={{ display: "flex", alignItems: "center", gap: 8, width: "100%", marginTop: 8, padding: 9, border: "1px solid var(--line)", borderRadius: 12, background: "#fff", textAlign: "left" }}>
                <Sparkles size={15} />
                <span style={{ display: "grid" }}><strong style={{ fontSize: 10 }}>{m.card.title}</strong><small style={{ color: "var(--muted)", fontSize: 8 }}>{m.card.detail}</small></span>
                <ChevronRight size={15} style={{ marginLeft: "auto" }} />
              </button>
            )}
            {m.disclaimer && <small style={{ display: "block", marginTop: 6, color: "var(--muted)", fontSize: 8 }}>This isn’t medical advice — please check with your care team.</small>}
          </div>
        ))}
        {sending && <div className="ai-message follow-up" style={{ opacity: 0.6 }}>…</div>}
      </main>
      <Composer message={message} setMessage={setMessage} submit={submit} openTools={() => setToolsOpen(!toolsOpen)} openVoice={() => openTool("voice")} />
      {toolsOpen && <ToolTray close={() => setToolsOpen(false)} openTool={(tool) => { setToolsOpen(false); openTool(tool); }} />}
      <AppNav active="Aira" onChange={onNavigate} />
    </>
  );
}

function Composer({ message, setMessage, submit, openTools, openVoice, simple = false }: {
  message: string;
  setMessage: (value: string) => void;
  submit: (event: FormEvent) => void;
  openTools: () => void;
  openVoice: () => void;
  simple?: boolean;
}) {
  return (
    <form className="composer" onSubmit={submit}>
      {!simple && <button type="button" className="composer-secondary" onClick={openTools} aria-label="Open Aira tools"><Plus size={19} /></button>}
      <input aria-label="Message Aira" placeholder={simple ? "Answer in your own words…" : "Message Aira…"} value={message} onChange={(event) => setMessage(event.target.value)} />
      <button type="button" className="composer-secondary" onClick={openVoice} aria-label="Start voice conversation"><Mic size={18} /></button>
      <button className="composer-send" aria-label="Send message"><Send size={17} /></button>
    </form>
  );
}

function ToolTray({ close, openTool }: { close: () => void; openTool: (tool: ToolName) => void }) {
  return (
    <div className="tool-tray">
      <button className="tray-scrim" onClick={close} aria-label="Close tools" />
      <section>
        <div className="tray-head"><div><span className="card-kicker">Tools in this conversation</span><h2>What would help now?</h2></div><button onClick={close} aria-label="Close tools"><X size={18} /></button></div>
        <div className="tool-grid">
          {chatTools.map((tool) => {
            const Icon = toolMeta[tool.name].icon;
            return <button key={tool.name} onClick={() => openTool(tool.name)}><span><Icon size={18} /></span><strong>{tool.label}</strong><small>{tool.detail}</small></button>;
          })}
        </div>
      </section>
    </div>
  );
}

function JourneyScreen({ urgent, onNavigate, openTool }: { urgent: () => void; onNavigate: (screen: Screen) => void; openTool: (tool: ToolName) => void }) {
  const [journey, setJourney] = useState<JourneyData | null>(null);

  useEffect(() => {
    AiraAPI.journey().then(setJourney).catch(() => undefined);
  }, []);

  const weeks = journey?.weeks ?? null;
  const journeyLabel = journey ? JOURNEY_LABEL[journey.journey] ?? "Exploring" : "Pregnant";
  const title = journey?.title || "Your pregnancy";
  const thisWeek = journey?.this_week || "Movement may feel more regular";
  const body = journey?.body || "Notice your own pattern rather than comparing with someone else’s.";
  const sections = journey?.sections?.length ? journey.sections : [
    { title: "Your body", text: "Energy, sleep and changes worth knowing" },
    { title: "Your baby", text: "Growth explained without information overload" },
    { title: "Your next visit", text: "What to ask and what to bring" },
  ];
  const sectionIcons = [Activity, Heart, CalendarDays];

  return (
    <>
      <AppHeader detail={weeks != null ? `Week ${weeks}` : journeyLabel} urgent={urgent} notifications={() => openTool("notifications")} />
      <main className="screen-content journey">
        <span className="eyebrow">Your journey</span>
        <h1>{weeks != null ? `Week ${weeks}, gently explained.` : `${title}, gently.`}</h1>
        <p className="screen-lead">Only the changes most useful to understand now.</p>
        <section className="journey-hero">
          <div className="journey-orbit">{weeks != null ? <><span>{weeks}</span><small>weeks</small></> : <><span>{journeyLabel[0]}</span><small>stage</small></>}</div>
          <div><span className="card-kicker">This week</span><h2>{thisWeek}</h2><p>{body}</p></div>
        </section>
        <div className="editorial-list">
          {sections.map((s, i) => {
            const Icon = sectionIcons[i % sectionIcons.length];
            return <button key={`${s.title}-${i}`}><span><Icon size={19} /></span><div><strong>{s.title}</strong><small>{s.text}</small></div><ChevronRight size={17} /></button>;
          })}
        </div>
        <section className="journey-actions">
          <button onClick={() => openTool("wellness")}><Wind size={18} /><span><strong>Calm</strong><small>Two-minute reset</small></span></button>
          <button onClick={() => openTool("symptom")}><Activity size={18} /><span><strong>Track</strong><small>Log a change</small></span></button>
        </section>
      </main>
      <AppNav active="Journey" onChange={onNavigate} />
    </>
  );
}

function CareScreen({ urgent, onNavigate, openTool }: { urgent: () => void; onNavigate: (screen: Screen) => void; openTool: (tool: ToolName) => void }) {
  return (
    <>
      <AppHeader detail="Organised with Aira" urgent={urgent} notifications={() => openTool("notifications")} />
      <main className="screen-content care">
        <span className="eyebrow">Care</span>
        <h1>Everything ready when you need it.</h1>
        <section className="care-appointment">
          <div className="care-date"><strong>29</strong><small>JUL</small></div>
          <div><span className="card-kicker">Tomorrow · 10:30 AM</span><h2>Dr. Ananya Mehta</h2><p>City Women’s Clinic</p></div>
          <button onClick={() => openTool("appointment")}><ArrowRight size={18} /></button>
        </section>
        <div className="care-metrics"><button onClick={() => openTool("medicine")}><strong>1</strong><span>Medicine due</span></button><button onClick={() => openTool("upload")}><strong>4</strong><span>Documents</span></button><button onClick={() => openTool("appointment")}><strong>3</strong><span>Questions ready</span></button></div>
        <div className="editorial-list care-list">
          {[
            { tool: "medicine" as ToolName, icon: Pill, title: "Medicines", text: "Next: prenatal vitamin · 8 PM" },
            { tool: "upload" as ToolName, icon: FileText, title: "Care Vault", text: "Prescriptions, reports and scans" },
            { tool: "careplan" as ToolName, icon: ClipboardCheck, title: "Care plan", text: "Three priorities for this week" },
            { tool: "partner" as ToolName, icon: Users, title: "Partner actions", text: "Practical support, with consent" },
            { tool: "support" as ToolName, icon: LifeBuoy, title: "Care team", text: "Ask for non-urgent human support" },
          ].map(({ tool, icon: Icon, title, text }) => <button key={title} onClick={() => openTool(tool)}><span><Icon size={19} /></span><div><strong>{title}</strong><small>{text}</small></div><ChevronRight size={17} /></button>)}
        </div>
        <button className="emergency-link" onClick={() => openTool("emergency")}><Siren size={17} /> Emergency profile <ChevronRight size={16} /></button>
      </main>
      <AppNav active="Care" onChange={onNavigate} />
    </>
  );
}

function YouScreen({ urgent, onNavigate, openTool }: { urgent: () => void; onNavigate: (screen: Screen) => void; openTool: (tool: ToolName) => void }) {
  const [personalisation, setPersonalisation] = useState(true);
  return (
    <>
      <AppHeader detail="Your care, your control" urgent={urgent} notifications={() => openTool("notifications")} />
      <main className="screen-content you">
        <section className="profile-card"><div className="profile-avatar">M</div><div><h1>Maya</h1><p>Week 24 · English & Hindi</p></div><button aria-label="Edit profile"><ChevronRight size={18} /></button></section>
        <div className="editorial-list settings-list">
          {[
            { tool: "privacy" as ToolName, icon: LockKeyhole, title: "Privacy & consent", text: "Data, permissions and export" },
            { tool: "memory" as ToolName, icon: Brain, title: "What Aira remembers", text: "Review or forget care context" },
            { tool: "voice" as ToolName, icon: Languages, title: "Voice & language", text: "English · Aira warm voice" },
            { tool: "companion" as ToolName, icon: CircleUserRound, title: "Companion mode", text: "Text, voice or talking avatar" },
            { tool: "partner" as ToolName, icon: Users, title: "Partner access", text: "Practical tasks only" },
            { tool: "support" as ToolName, icon: LifeBuoy, title: "Help & human support", text: "Care navigator, FAQs and feedback" },
          ].map(({ tool, icon: Icon, title, text }) => <button key={title} onClick={() => openTool(tool)}><span><Icon size={19} /></span><div><strong>{title}</strong><small>{text}</small></div><ChevronRight size={17} /></button>)}
        </div>
        <div className="control-card"><span><strong>AI personalisation</strong><small>Use only approved context in answers</small></span><button className={personalisation ? "switch on" : "switch"} onClick={() => setPersonalisation(!personalisation)} aria-label="Toggle AI personalisation"><i /></button></div>
        <div className="privacy-note"><ShieldCheck size={17} /><span><strong>Never used for advertising</strong><small>Your sensitive health data is not an ad product.</small></span></div>
      </main>
      <AppNav active="You" onChange={onNavigate} />
    </>
  );
}

function Toggle({ enabled, setEnabled }: { enabled: boolean; setEnabled: (value: boolean) => void }) {
  return <button className={enabled ? "switch on" : "switch"} onClick={() => setEnabled(!enabled)} aria-label="Toggle setting"><i /></button>;
}

function ToolSheet({ tool, close }: { tool: ToolName; close: () => void }) {
  const [saved, setSaved] = useState(false);
  const [selected, setSelected] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [playing, setPlaying] = useState(false);
  const [uploaded, setUploaded] = useState(false);
  const [secondPhoto, setSecondPhoto] = useState(false);
  const [emergencyPhone, setEmergencyPhone] = useState<string | null>(null);
  const info = toolMeta[tool];
  const Icon = info.icon;
  const save = () => { setSaved(true); window.setTimeout(close, 900); };

  // The emergency sheet's "Call care team" needs a real number — pull it from
  // the user's emergency profile so the button is never inert.
  useEffect(() => {
    if (tool !== "emergency") return;
    AiraAPI.emergencyProfile()
      .then((ep) => setEmergencyPhone(ep.care_team_phone ?? null))
      .catch(() => undefined);
  }, [tool]);
  const choices = (items: string[]) => <div className="choice-row">{items.map((item) => <button key={item} className={selected === item ? "selected" : ""} onClick={() => setSelected(item)}>{item}</button>)}</div>;

  let content;
  if (tool === "notifications") content = <>
    <div className="notification-list">
      <button><span><Pill size={18} /></span><div><strong>Prenatal vitamin</strong><small>Due today at 8:00 PM</small></div><i /></button>
      <button><span><CalendarDays size={18} /></span><div><strong>Appointment tomorrow</strong><small>Your visit brief is ready</small></div><i /></button>
      <button><span><Heart size={18} /></span><div><strong>Gentle check-in</strong><small>How did you sleep last night?</small></div></button>
    </div>
    <button className="secondary-action" onClick={() => setSaved(true)}>Mark all as read</button>
    <button className="sheet-link">Notification settings & quiet hours</button>
  </>;
  else if (tool === "checkin") content = <>
    <h3>How are you feeling right now?</h3>
    <div className="feeling-row">{["Good", "Okay", "Tired", "Low", "Unwell"].map((item) => <button key={item} className={selected === item ? "selected" : ""} onClick={() => setSelected(item)}><span>{item === "Good" ? "◡" : item === "Okay" ? "—" : item === "Tired" ? "⌁" : item === "Low" ? "◠" : "!"}</span>{item}</button>)}</div>
    <label className="sheet-field"><span>Last night’s sleep</span><div className="range-field"><input type="range" min="1" max="10" defaultValue="6" /><b>6h</b></div></label>
    <label className="sheet-field"><span>Anything else? <em>Optional</em></span><textarea placeholder="A private note for your timeline" /></label>
    <button className="primary-action" onClick={save}>Save check-in <ArrowRight size={16} /></button>
  </>;
  else if (tool === "reminder") content = <>
    <label className="sheet-field"><span>Remind me to</span><input defaultValue="Take prenatal vitamin" /></label>
    <div className="two-fields"><label className="sheet-field"><span>Time</span><input type="time" defaultValue="20:00" /></label><label className="sheet-field"><span>Repeat</span><select defaultValue="Daily"><option>Daily</option><option>Weekdays</option><option>Once</option></select></label></div>
    <div className="toggle-line"><span><strong>Private lock-screen text</strong><small>Show “Aira reminder” only</small></span><Toggle enabled={enabled} setEnabled={setEnabled} /></div>
    <button className="primary-action" onClick={save}>Set reminder <ArrowRight size={16} /></button>
  </>;
  else if (tool === "medicine") content = <>
    <div className="medicine-card"><span><Pill size={22} /></span><div><strong>Prenatal vitamin</strong><small>1 tablet · Daily · With food</small></div><b>8:00 PM</b></div>
    <div className="week-dots">{["M", "T", "W", "T", "F", "S", "S"].map((day, index) => <span key={`${day}-${index}`}><i className={index < 5 ? "taken" : ""}>{index < 5 ? <Check size={12} /> : "·"}</i><small>{day}</small></span>)}</div>
    <button className="primary-action" onClick={save}>Mark today as taken <Check size={16} /></button>
    <button className="sheet-link">Edit schedule or reminder</button>
    <div className="trust-note"><ShieldCheck size={15} /> Aira can organise reminders but cannot start, stop or change medication.</div>
  </>;
  else if (tool === "appointment") content = <>
    <div className="visit-card"><span><CalendarDays size={22} /></span><div><strong>Dr. Ananya Mehta</strong><small>City Women’s Clinic · 10:30 AM</small></div><button><MapPin size={16} /></button></div>
    <h3>Questions prepared with Aira</h3>
    <ol className="question-list"><li>Do I need any tests this week?</li><li>What changes should I expect next?</li><li>How can I manage fatigue better?</li></ol>
    <div className="context-pills"><span>Concern <strong>Fatigue</strong></span><span>Medicines <strong>1 active</strong></span><span>Files <strong>4 ready</strong></span></div>
    <button className="primary-action" onClick={save}>Create visit brief <ArrowRight size={16} /></button>
  </>;
  else if (tool === "upload") content = <>
    <div className="document-types">{["Prescription", "Lab report", "Scan", "Other"].map((item) => <button key={item} className={selected === item ? "selected" : ""} onClick={() => setSelected(item)}>{item}</button>)}</div>
    <button className={uploaded ? "upload-zone uploaded" : "upload-zone"} onClick={() => setUploaded(true)}>
      {uploaded ? <CheckCircle2 size={25} /> : <><ScanLine size={25} /></>}
      <strong>{uploaded ? "prescription-july.pdf" : "Choose or scan a document"}</strong>
      <small>{uploaded ? "Ready to save · 1.8 MB" : "PDF, JPG or PNG · Max 20 MB"}</small>
    </button>
    {uploaded && <div className="classification"><span>Aira classified this as</span><strong>{selected || "Prescription"}</strong><button>Change</button></div>}
    <div className="toggle-line"><span><strong>Use in future answers</strong><small>Only after you approve extracted details</small></span><Toggle enabled={enabled} setEnabled={setEnabled} /></div>
    <button className="primary-action" disabled={!uploaded} onClick={save}>Save to Care Vault <ArrowRight size={16} /></button>
  </>;
  else if (tool === "wellness") content = <>
    <div className={playing ? "wellness-player playing" : "wellness-player"}><BrandOrb /><strong>{playing ? "Breathe in… and soften" : "Two quiet minutes"}</strong><button onClick={() => setPlaying(!playing)}>{playing ? <><Volume2 size={16} /> Pause</> : <><Play size={16} /> Begin</>}</button></div>
    <div className="session-meta"><span><Clock size={14} /> 2 minutes</span><span><Volume2 size={14} /> Voice optional</span><span>CC Captions</span></div>
    <button className="secondary-action" onClick={save}>Save for later</button>
  </>;
  else if (tool === "symptom") content = <>
    <label className="sheet-field"><span>What changed?</span><input placeholder="e.g. headache, swelling, nausea" /></label>
    <h3>How noticeable is it?</h3>{choices(["Mild", "Moderate", "Severe"])}
    <div className="two-fields"><label className="sheet-field"><span>Started</span><select><option>Today</option><option>Yesterday</option><option>This week</option></select></label><label className="sheet-field"><span>Pattern</span><select><option>Comes and goes</option><option>Constant</option><option>Not sure</option></select></label></div>
    <div className="warning-note"><AlertTriangle size={15} /> If it feels severe, sudden or worrying, contact your care team rather than waiting for Aira.</div>
    <button className="primary-action" onClick={save}>Add to timeline <ArrowRight size={16} /></button>
  </>;
  else if (tool === "partner") content = <>
    <div className="partner-card"><div>R</div><span><strong>Rahul</strong><small>Practical tasks only</small></span><b>Connected</b></div>
    <label className="sheet-field"><span>New task</span><input defaultValue="Pick up iron supplements" /></label>
    <div className="two-fields"><label className="sheet-field"><span>When</span><select><option>Today</option><option>Tomorrow</option><option>This week</option></select></label><label className="sheet-field"><span>Share</span><select><option>Task only</option><option>Task + note</option></select></label></div>
    <div className="trust-note"><LockKeyhole size={15} /> Conversations and reports stay private unless you explicitly share them.</div>
    <button className="primary-action" onClick={save}>Assign task <ArrowRight size={16} /></button>
  </>;
  else if (tool === "privacy") content = <>
    <div className="settings-panel">
      <div><span><strong>AI personalisation</strong><small>Approved context shapes answers</small></span><Toggle enabled={enabled} setEnabled={setEnabled} /></div>
      <div><span><strong>Partner access</strong><small>Tasks only</small></span><Toggle enabled={true} setEnabled={() => undefined} /></div>
      <div><span><strong>Store raw voice audio</strong><small>Off · transcript only</small></span><Toggle enabled={false} setEnabled={() => undefined} /></div>
      <div><span><strong>Health data for ads</strong><small>Never</small></span><CheckCircle2 size={18} /></div>
    </div>
    <div className="privacy-actions"><button><Download size={16} /> Download data</button><button><FileText size={16} /> Consent history</button><button><SlidersHorizontal size={16} /> Active sessions</button><button className="danger-text"><X size={16} /> Delete selected data</button></div>
  </>;
  else if (tool === "memory") content = <>
    <p className="sheet-copy">Aira remembers only what helps organise your care. Remove any item at any time.</p>
    <div className="memory-list"><button><span>Journey stage</span><strong>Pregnant · Week 24</strong><ChevronRight size={16} /></button><button><span>Care priorities</span><strong>Sleep · Fatigue · Visits</strong><ChevronRight size={16} /></button><button><span>Communication</span><strong>English · Gentle detail</strong><ChevronRight size={16} /></button><button><span>Recent concern</span><strong>Fatigue</strong><X size={16} /></button></div>
    <button className="secondary-action" onClick={save}>Forget recent conversation</button>
  </>;
  else if (tool === "voice") content = <>
    <div className={playing ? "voice-card listening" : "voice-card"}><BrandOrb /><h3>{playing ? "I’m listening…" : "Talk naturally with Aira"}</h3><p>A transcript appears before anything is saved.</p><button onClick={() => setPlaying(!playing)}><Mic size={17} /> {playing ? "Stop listening" : "Start voice"}</button></div>
    <div className="two-fields"><label className="sheet-field"><span>Language</span><select><option>English</option><option>Hindi</option><option>Hinglish</option></select></label><label className="sheet-field"><span>Voice</span><select><option>Aira · Warm</option><option>Aira · Calm</option></select></label></div>
    <div className="toggle-line"><span><strong>Store raw audio</strong><small>Off by default; transcript only</small></span><Toggle enabled={false} setEnabled={() => undefined} /></div>
  </>;
  else if (tool === "companion") content = <>
    <div className="companion-tabs">{choices(["Aira avatar", "Future-baby story"])}</div>
    <div className="companion-preview"><div className="companion-face"><BrandOrb /></div><span><strong>{selected === "Future-baby story" ? "A gentle imagined character" : "Aira · warm avatar"}</strong><small>{selected === "Future-baby story" ? "A private connection experience" : "Lip-synced voice conversation"}</small></span></div>
    {selected === "Future-baby story" && <><div className="photo-pair"><button onClick={() => setUploaded(true)} className={uploaded ? "added" : ""}>{uploaded ? <ImageIcon size={21} /> : <Camera size={21} />}<strong>{uploaded ? "Your photo added" : "Add your photo"}</strong></button><button onClick={() => setSecondPhoto(true)} className={secondPhoto ? "added" : ""}>{secondPhoto ? <ImageIcon size={21} /> : <Camera size={21} />}<strong>{secondPhoto ? "Partner photo added" : "Add partner photo"}</strong></button></div><div className="warning-note"><ShieldCheck size={15} /> Illustrative only—not a prediction of appearance, health, personality or genetics. Both people must consent.</div></>}
    <button className="primary-action" onClick={save}>{selected === "Future-baby story" ? "Create private preview" : "Use talking avatar"} <ArrowRight size={16} /></button>
  </>;
  else if (tool === "careplan") content = <>
    <div className="plan-progress"><span><i /></span><strong>Three of five priorities on track</strong></div>
    <div className="plan-list"><button><Check /><span><strong>Take prenatal vitamin</strong><small>Daily · 8 PM</small></span></button><button><Check /><span><strong>Prepare visit questions</strong><small>Ready for tomorrow</small></span></button><button><b>3</b><span><strong>Log fatigue for three days</strong><small>Day two of three</small></span></button><button><b>4</b><span><strong>Try one calm session</strong><small>Two minutes</small></span></button></div>
    <button className="primary-action" onClick={save}>Continue plan with Aira <ArrowRight size={16} /></button>
  </>;
  else if (tool === "support") content = <>
    <div className="support-list"><button><span><Users size={18} /></span><div><strong>Ask a care navigator</strong><small>Non-urgent human support · Within four hours</small></div><ChevronRight size={16} /></button><button><span><LifeBuoy size={18} /></span><div><strong>Help centre</strong><small>Using Aira, privacy and troubleshooting</small></div><ChevronRight size={16} /></button><button><span><AlertTriangle size={18} /></span><div><strong>Report an AI answer</strong><small>Clinical, safety or technical concern</small></div><ChevronRight size={16} /></button></div>
    <div className="warning-note"><Siren size={15} /> For emergencies or urgent symptoms, use Urgent Help or contact local emergency services.</div>
  </>;
  else content = <>
    <div className="emergency-profile"><div><span>Stage</span><strong>Pregnant · Week 24</strong></div><div><span>Doctor</span><strong>Dr. Ananya Mehta</strong></div><div><span>Hospital</span><strong>City Women’s Clinic</strong></div><div><span>Medicine</span><strong>Prenatal vitamin</strong></div><div><span>Trusted contact</span><strong>Rahul · Partner</strong></div></div>
    {telHref(emergencyPhone) ? (
      <a className="danger-action" href={telHref(emergencyPhone)!} style={{ textDecoration: "none" }}><Phone size={17} /> Call care team</a>
    ) : (
      <button className="danger-action" disabled title="Add a care-team number in your emergency profile"><Phone size={17} /> Call care team</button>
    )}
    <button className="danger-outline"><FileText size={17} /> Share emergency profile</button>
    <button className="sheet-link">Edit offline emergency details</button>
  </>;

  return (
    <div className="sheet-layer" role="dialog" aria-modal="true" aria-label={info.title}>
      <button className="sheet-scrim" onClick={close} aria-label="Close panel" />
      <section className="tool-sheet">
        <div className="sheet-grabber" />
        <header><span className="sheet-icon"><Icon size={19} /></span><div><p>{info.eyebrow}</p><h2>{info.title}</h2></div><button onClick={close} aria-label={`Close ${info.title}`}><X size={18} /></button></header>
        <div className="sheet-content">{content}</div>
        {saved && <div className="saved-toast"><Check size={15} /> Saved with Aira</div>}
      </section>
    </div>
  );
}

function UrgentHelp({ close, emergencyProfile, payload }: {
  close: () => void;
  emergencyProfile: () => void;
  payload?: UrgentHelpPayload | null;
}) {
  const [careTeamPhone, setCareTeamPhone] = useState<string | null>(payload?.care_team.phone ?? null);
  const [contactPhone, setContactPhone] = useState<string | null>(payload?.emergency_contact?.phone ?? null);

  useEffect(() => {
    if (payload) {
      setCareTeamPhone(payload.care_team.phone ?? null);
      setContactPhone(payload.emergency_contact?.phone ?? null);
      return;
    }
    // Manual open (not from a red turn): pull the number from the profile.
    AiraAPI.emergencyProfile()
      .then((ep) => { setCareTeamPhone(ep.care_team_phone ?? null); setContactPhone(ep.emergency_contact_phone ?? null); })
      .catch(() => undefined);
  }, [payload]);

  const careHref = telHref(careTeamPhone);
  const contactHref = telHref(contactPhone);
  return (
    <div className="urgent-screen">
      <button className="urgent-close" onClick={close} aria-label="Close urgent help"><X size={20} /></button>
      <span className="urgent-symbol"><Siren size={30} /></span>
      <span className="card-kicker danger-text">Urgent help</span>
      <h1>{payload?.headline ?? "Please contact your care team now."}</h1>
      <p>{payload?.message ?? "Do not wait for an AI response if you feel seriously unwell or are worried about your baby."}</p>
      {careHref ? (
        <a className="danger-action" href={careHref} style={{ textDecoration: "none" }}><Phone size={17} /> Call care team</a>
      ) : (
        <button className="danger-action" onClick={emergencyProfile}><Phone size={17} /> Add a care-team number</button>
      )}
      {contactHref && (
        <a className="danger-outline" href={contactHref} style={{ textDecoration: "none" }}><Phone size={17} /> Call emergency contact</a>
      )}
      <button className="danger-outline" onClick={emergencyProfile}><FileText size={17} /> Open emergency profile</button>
      <button className="card-link" onClick={close}>I’m safe for now</button>
    </div>
  );
}

function AppExperience({ backToSite }: { backToSite: () => void }) {
  const [screen, setScreen] = useState<Screen>("Aira");
  const [onboardingComplete, setOnboardingComplete] = useState(false);
  const [activeTool, setActiveTool] = useState<ToolName | null>(null);
  const [urgent, setUrgent] = useState(false);
  const [urgentPayload, setUrgentPayload] = useState<UrgentHelpPayload | null>(null);
  const go = (next: Screen) => {
    if (!onboardingComplete && next !== "Aira") return;
    setActiveTool(null);
    setScreen(next);
  };
  const openUrgent = (payload: UrgentHelpPayload | null) => {
    setActiveTool(null);
    setUrgentPayload(payload);
    setUrgent(true);
  };
  const common = {
    urgent: () => openUrgent(null),
    onNavigate: go,
    openTool: (tool: ToolName) => setActiveTool(tool),
  };
  return (
    <main className="app-stage">
      <aside className="app-stage-copy">
        <button className="back-site" onClick={backToSite}><ChevronLeft size={16} /> Back to Aira</button>
        <span className="premium-label"><ShieldCheck size={14} /> Interactive product experience</span>
        <h1>Care begins with a conversation.</h1>
        <p>Complete the four chat-based questions, then explore every care tool inside the same calm system.</p>
        <div className="stage-points"><span><Check size={15} /> No separate onboarding forms</span><span><Check size={15} /> Dynamic tools inside Chat</span><span><Check size={15} /> Safety gate on every input</span></div>
      </aside>
      <section className="phone-wrap">
        <div className="app-phone">
          <div className="phone-island" />
          {screen === "Today" && <TodayScreen onChat={() => go("Aira")} {...common} />}
          {screen === "Aira" && <ChatScreen {...common} onUrgent={openUrgent} onboardingComplete={onboardingComplete} completeOnboarding={() => { setOnboardingComplete(true); setScreen("Today"); }} />}
          {screen === "Journey" && <JourneyScreen {...common} />}
          {screen === "Care" && <CareScreen {...common} />}
          {screen === "You" && <YouScreen {...common} />}
          {activeTool && <ToolSheet tool={activeTool} close={() => setActiveTool(null)} />}
          {urgent && <UrgentHelp close={() => setUrgent(false)} payload={urgentPayload} emergencyProfile={() => { setUrgent(false); setActiveTool("emergency"); }} />}
        </div>
      </section>
      <aside className="app-stage-map">
        <span className="card-kicker">Five quiet destinations</span>
        {navigation.map(({ name, icon: Icon }) => <button key={name} className={screen === name ? "active" : ""} onClick={() => go(name)} disabled={!onboardingComplete && name !== "Aira"}><Icon size={17} /><span>{name}</span></button>)}
        <button className="stage-urgent" onClick={() => openUrgent(null)}><Siren size={17} /><span>Urgent help</span></button>
      </aside>
    </main>
  );
}

export default function Home() {
  const [insideApp, setInsideApp] = useState(false);
  return insideApp ? <AppExperience backToSite={() => setInsideApp(false)} /> : <Landing enterApp={() => setInsideApp(true)} />;
}
