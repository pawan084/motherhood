# Aira — AI Maternal Wellness Companion

A polished, responsive ReactJS web prototype for a chat-first maternal
wellness experience. Aira supports people who are trying to conceive,
pregnant, or postpartum while keeping urgent-care escalation and consent
controls visible.

## Included experience

- Premium public landing page
- Conversational onboarding inside Aira Chat
- Personalized Today, Journey, Care, and You destinations
- Intent-aware action cards inside the conversation
- Reminders, appointment copilot, medicine and document tools
- Mood, sleep, symptom, and wellness check-ins
- Privacy, memory, voice, language, partner, and notification controls
- Urgent-help safety flow
- Optional companion avatar and future-baby story concept with explicit
  consent language
- Responsive desktop, tablet, and mobile layouts

This is a front-end product prototype. Health guidance, authentication,
notifications, storage, photo generation, and clinical integrations require
production back-end services and appropriate medical, privacy, and regulatory
review.

## Run locally

Requirements: Node.js 22.13 or newer.

```bash
npm install
npm run dev
```

Open the local URL printed by Vite.

## Verify a production build

```bash
npm run lint
npm test
npm run validate:artifact
```

## Stack

- React 19
- TypeScript
- Vite with the Vinext React adapter
- Lucide icons
- Responsive CSS

## Key source files

- `app/page.tsx` — complete interactive product experience
- `app/globals.css` — responsive visual system and component styling
- `public/assets/` — local visual assets
