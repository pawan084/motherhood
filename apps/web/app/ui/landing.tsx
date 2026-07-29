"use client";

// The marketing site, rebuilt from ref/Aira-HTML-Website.
//
// Two things are deliberately different from that reference:
//
//  1. The hero shows a floating *product window*, not a phone. The reference is
//     a mobile prototype; this app is a responsive web product, and a hero that
//     advertises a phone would misrepresent what a visitor is about to get.
//  2. The journey cards actually start that journey. "Begin this journey" hands
//     the chosen journey to onboarding rather than dropping everyone on a
//     generic first step.

import Link from "next/link";
import {
  ArrowRight, Bell, BookOpen, CalendarDays, Check, ChevronRight, ClipboardCheck,
  FileText, Heart, LockKeyhole, MessageCircle, Mic, Phone, Pill, Send,
  ShieldCheck, Sparkles, Users, Wind,
} from "lucide-react";
import type { Journey } from "../aira-api";

function BrandOrb({ className = "brand-orb compact" }: { className?: string }) {
  return <span className={className} aria-hidden="true"><i /><b /></span>;
}

// Handed to onboarding so a journey chosen on the landing page is preselected.
// sessionStorage rather than a query param: the app is hash-routed, and this is
// a one-shot handoff that shouldn't linger in a shareable URL.
export const PRESELECT_KEY = "aira_preselect_journey";

export default function Landing({ enterApp }: { enterApp: () => void }) {
  const startJourney = (j: Journey) => {
    try { sessionStorage.setItem(PRESELECT_KEY, j); } catch { /* private mode */ }
    enterApp();
  };

  return (
    <div className="landing">
      <header className="landing-nav">
        <a className="landing-brand" href="#top" aria-label="Aira home">
          <BrandOrb /> <span>Aira</span>
        </a>
        <nav aria-label="Website">
          <a href="#how">How it works</a>
          <a href="#journeys">Journeys</a>
          <a href="#care">Care tools</a>
          <a href="#safety">Safety</a>
        </nav>
        <div>
          <button className="nav-signin" onClick={enterApp}>Sign in</button>
          <button className="nav-cta" onClick={enterApp}>Meet Aira <ArrowRight size={15} /></button>
        </div>
      </header>

      <main id="top">
        <section className="landing-hero">
          <div className="hero-glow one" aria-hidden="true" />
          <div className="hero-glow two" aria-hidden="true" />

          <div className="landing-hero-copy">
            <p className="landing-eyebrow"><span /> AI-first maternal wellness</p>
            <h1>The care<br />between care.</h1>
            <p>
              A private companion that turns everyday questions, appointments,
              reminders and wellbeing into one calm conversation.
            </p>
            <div className="hero-actions">
              <button className="landing-primary" onClick={enterApp}>
                Start your journey <ArrowRight size={16} />
              </button>
              <button className="landing-secondary" onClick={enterApp}>
                <Sparkles size={15} /> Explore the product
              </button>
            </div>
            <div className="hero-proof">
              <span><LockKeyhole size={15} /> Privacy by design</span>
              <span><Heart size={15} /> Personalised gently</span>
              <span><Users size={15} /> Built for every journey</span>
            </div>
          </div>

          <div className="product-stage" aria-label="Aira product preview">
            <div className="stage-aura a" aria-hidden="true" />
            <div className="stage-aura b" aria-hidden="true" />
            <article className="product-window">
              <header>
                <div>
                  <BrandOrb />
                  <strong>Aira</strong>
                  <small>Your care companion</small>
                </div>
                <span><ShieldCheck size={13} /> Safety checked</span>
              </header>
              <div className="product-chat">
                <p className="aira-bubble">
                  Your appointment is tomorrow. Shall we prepare together?
                </p>
                <div className="product-tool">
                  <div>
                    <span><CalendarDays size={18} /></span>
                    <p>
                      <small>APPOINTMENT COPILOT</small>
                      <strong>Three useful questions, ready.</strong>
                    </p>
                  </div>
                  <ol>
                    <li>Do I need any tests this week?</li>
                    <li>What changes should I expect next?</li>
                    <li>How can I manage fatigue better?</li>
                  </ol>
                </div>
                <div className="product-chips">
                  <span><Heart size={13} /> Check in</span>
                  <span><ClipboardCheck size={13} /> Reminder</span>
                  <span><FileText size={13} /> Add report</span>
                </div>
              </div>
              <footer>
                <span>Message Aira…</span>
                <button onClick={enterApp} aria-label="Start with Aira"><Send size={15} /></button>
              </footer>
            </article>

            <div className="floating-card reset-card">
              <span className="breath-mini" aria-hidden="true" />
              <div>
                <small>JUST FOR YOU</small>
                <strong>A 2-minute reset</strong>
              </div>
              <Wind size={16} />
            </div>
            <div className="floating-card privacy-card">
              <ShieldCheck size={18} />
              <div>
                <strong>Private care context</strong>
                <small>You stay in control</small>
              </div>
            </div>
          </div>
        </section>

        <section className="trust-ribbon">
          <p>Designed for the moments that happen outside the appointment.</p>
          <div>
            <span>Trying to conceive</span><i /><span>Pregnancy</span><i />
            <span>Postpartum</span><i /><span>Partner support</span>
          </div>
        </section>

        <section className="landing-section" id="how">
          <div className="section-intro">
            <p className="landing-eyebrow"><span /> One calm loop</p>
            <h2>Ask. Understand.<br />Take the next step.</h2>
            <p>Aira keeps care useful without turning motherhood into another dashboard to manage.</p>
          </div>
          <div className="how-grid">
            <article>
              <b>01</b>
              <span className="feature-icon lilac"><MessageCircle size={22} /></span>
              <h3>Start with conversation</h3>
              <p>Onboarding, check-ins and questions happen naturally through chat — at your pace and in your language.</p>
            </article>
            <article>
              <b>02</b>
              <span className="feature-icon sage"><LockKeyhole size={22} /></span>
              <h3>Build private context</h3>
              <p>Aira remembers only what helps: your journey, goals, appointments, medicines and preferences — and only what you approve.</p>
            </article>
            <article>
              <b>03</b>
              <span className="feature-icon peach"><Sparkles size={22} /></span>
              <h3>Move care forward</h3>
              <p>Turn conversation into reminders, visit questions, simple logs and gentle wellness moments.</p>
            </article>
          </div>
        </section>

        <section className="conversation-section" id="care">
          <div className="conversation-copy">
            <p className="landing-eyebrow light"><span /> Chat first, always</p>
            <h2>One conversation.<br />Every care tool.</h2>
            <p>
              No maze of forms. No overwhelming feed. Aira surfaces the right tool
              inside the conversation, exactly when it becomes useful.
            </p>
            <div className="tool-cloud">
              <span><Pill size={13} /> Medicine reminders</span>
              <span><CalendarDays size={13} /> Appointment copilot</span>
              <span><FileText size={13} /> Care Vault</span>
              <span><Heart size={13} /> Daily check-in</span>
              <span><Wind size={13} /> Two-minute reset</span>
            </div>
            <button onClick={enterApp}>Open the conversation <ArrowRight size={15} /></button>
          </div>
          <div className="conversation-visual">
            <div className="visual-header">
              <BrandOrb />
              <div><strong>Aira</strong><small>Here with you</small></div>
              <b>Screened</b>
            </div>
            <div className="visual-messages">
              <p>How has your energy felt today?</p>
              <p className="me">Lower than usual after lunch.</p>
              <p>Thank you for sharing. Would you like to log the pattern, or prepare a question for tomorrow?</p>
              <div>
                <button onClick={enterApp}><ClipboardCheck size={13} /> Log the pattern</button>
                <button onClick={enterApp}><CalendarDays size={13} /> Prepare a question</button>
              </div>
            </div>
            <div className="visual-input">
              <span>Message Aira…</span>
              <Mic size={15} />
            </div>
          </div>
        </section>

        <section className="landing-section" id="journeys">
          <div className="section-intro centered">
            <p className="landing-eyebrow"><span /> Care that evolves</p>
            <h2>With you, through every chapter.</h2>
            <p>Your needs change. Aira&apos;s language, tools and daily rhythm change with them.</p>
          </div>
          <div className="landing-journeys">
            <article className="ttc">
              <p className="card-number">01</p>
              <span><Heart size={22} /></span>
              <h3>Trying to conceive</h3>
              <p>Cycle-aware wellbeing, preparation support and a calm place for questions.</p>
              <button onClick={() => startJourney("trying")}>
                Begin this journey <ChevronRight size={15} />
              </button>
            </article>
            <article className="pregnancy">
              <p className="card-number">02</p>
              <span><Sparkles size={22} /></span>
              <h3>Pregnancy</h3>
              <p>Week-by-week context, care planning, appointment preparation and reminders.</p>
              <button onClick={() => startJourney("pregnant")}>
                Begin this journey <ChevronRight size={15} />
              </button>
            </article>
            <article className="postpartum">
              <p className="card-number">03</p>
              <span><Users size={22} /></span>
              <h3>Postpartum</h3>
              <p>Recovery, emotional check-ins and gentle support while everything settles.</p>
              <button onClick={() => startJourney("postpartum")}>
                Begin this journey <ChevronRight size={15} />
              </button>
            </article>
          </div>
        </section>

        <section className="care-showcase">
          <div className="care-showcase-copy">
            <p className="landing-eyebrow"><span /> Care, organised quietly</p>
            <h2>Everything important.<br />Nothing overwhelming.</h2>
            <p>
              Aira brings together the practical pieces of your care while preserving
              the calm of a wellness product.
            </p>
            <ul>
              <li><Check size={16} /> Medicines and gentle reminders</li>
              <li><Check size={16} /> Appointments and question preparation</li>
              <li><Check size={16} /> Prescriptions and reports, kept private</li>
              <li><Check size={16} /> Export or delete your data whenever you want</li>
            </ul>
            <button className="landing-primary" onClick={enterApp}>
              Open the Care space <ChevronRight size={15} />
            </button>
          </div>
          <div className="care-stack">
            <article className="care-main-card">
              <div>
                <span className="feature-icon lilac" style={{ margin: 0 }}><CalendarDays size={20} /></span>
                <div>
                  <strong>Your next appointment</strong>
                  <small>Questions prepared with Aira</small>
                </div>
              </div>
              <span className="feature-icon sage"><ClipboardCheck size={20} /></span>
              <button onClick={enterApp}>Prepare together</button>
            </article>
            <article>
              <span><Pill size={17} /></span>
              <div><strong>Medicines</strong><small>Reminders you set, never prescriptions</small></div>
              <ChevronRight size={15} style={{ color: "var(--muted)" }} />
            </article>
            <article>
              <span><FileText size={17} /></span>
              <div><strong>Care Vault</strong><small>Prescriptions, reports and scans</small></div>
              <ChevronRight size={15} style={{ color: "var(--muted)" }} />
            </article>
            <article>
              <span><Bell size={17} /></span>
              <div><strong>Updates</strong><small>Only what genuinely needs you</small></div>
              <ChevronRight size={15} style={{ color: "var(--muted)" }} />
            </article>
          </div>
        </section>

        <section className="landing-safety" id="safety">
          <div className="safety-symbol">
            <ShieldCheck size={42} />
            <i /><i />
          </div>
          <div>
            <p className="landing-eyebrow"><span /> Safety before intelligence</p>
            <h2>Aira knows when<br />not to be the answer.</h2>
          </div>
          <div>
            <p>
              Every message passes through a safety gate before Aira replies. Urgent
              signals lead straight to your care team and emergency profile — not to
              another AI response.
            </p>
            <div className="safety-points">
              <span><ShieldCheck size={16} /> Clear trust labels on every reply</span>
              <span><Phone size={16} /> Human escalation with your real number</span>
              <span><LockKeyhole size={16} /> Private by design, never used for ads</span>
              <span><BookOpen size={16} /> Wellness support, not diagnosis</span>
            </div>
          </div>
        </section>

        <section className="final-cta">
          <p className="landing-eyebrow light"><span /> Meet Aira</p>
          <h2>A calmer way to care,<br />from the very first question.</h2>
          <p>Begin with a private conversation. Build only the support that feels useful to you.</p>
          <div>
            <button onClick={enterApp}>Start your journey <ArrowRight size={15} /></button>
            <button onClick={enterApp}>Explore the product</button>
          </div>
        </section>
      </main>

      <footer className="landing-footer">
        <div>
          <a className="landing-brand" href="#top"><BrandOrb /> <span>Aira</span></a>
          <p>AI-first maternal wellness, designed around the mother.</p>
        </div>
        <div>
          <strong>Product</strong>
          <a href="#how">How it works</a>
          <a href="#journeys">Journeys</a>
          <a href="#care">Care tools</a>
        </div>
        <div>
          <strong>Trust</strong>
          <a href="#safety">Safety</a>
          <Link href="/privacy">Privacy</Link>
          <Link href="/terms">Terms</Link>
        </div>
        <div>
          <strong>Important</strong>
          <p>
            Aira supports wellbeing and care preparation. It does not diagnose,
            prescribe, or replace your care team. In an emergency, contact your local
            emergency services.
          </p>
        </div>
        <small>© 2026 Aira. Made with care for every maternal journey.</small>
      </footer>
    </div>
  );
}
