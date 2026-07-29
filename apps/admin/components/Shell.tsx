"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { logout, roleAtLeast } from "@/lib/api";
import { SessionProvider, useSession } from "@/lib/session";

// `minRole` mirrors the backend's `require_admin(...)` on each route group, so
// the nav doesn't offer a page that can only 403. The backend remains the
// authority — this is about not showing dead ends, not about access control.
const NAV: { href: string; label: string; minRole?: string }[] = [
  { href: "/", label: "Dashboard" },
  { href: "/safety", label: "Safety flags" },
  { href: "/feedback", label: "Feedback" },
  { href: "/users", label: "Users" },
  { href: "/content", label: "Content" },
  { href: "/prompts", label: "Prompts" },
  { href: "/system", label: "System" },
  { href: "/admins", label: "Admins", minRole: "owner" },
];

export default function Shell({ children, title }: { children: React.ReactNode; title: string }) {
  return (
    <SessionProvider>
      <ShellChrome title={title}>{children}</ShellChrome>
    </SessionProvider>
  );
}

function ShellChrome({ children, title }: { children: React.ReactNode; title: string }) {
  const pathname = usePathname();
  const router = useRouter();
  const { session, loading } = useSession();

  async function onLogout() {
    await logout();
    router.push("/login");
  }

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-56 shrink-0 flex-col border-r border-line bg-paper p-4">
        <div className="mb-6 flex items-center gap-2 px-2">
          <span className="grid h-7 w-7 place-items-center rounded-full bg-aubergine text-sm font-bold text-white">A</span>
          <span className="font-semibold text-aubergine">Aira Admin</span>
        </div>
        <nav className="flex flex-col gap-1">
          {NAV.filter((n) => !n.minRole || roleAtLeast(n.minRole, session?.role ?? null)).map((n) => {
            const active = n.href === "/" ? pathname === "/" : pathname.startsWith(n.href);
            return (
              <Link
                key={n.href}
                href={n.href}
                className={`rounded-lg px-3 py-2 text-sm font-medium ${
                  active ? "bg-lilac-mist text-aubergine" : "text-ink-muted hover:bg-lilac-mist"
                }`}
              >
                {n.label}
              </Link>
            );
          })}
        </nav>

        {/* Who you actually are, per the server — the console never showed this,
            so an admin had no way to tell which account or role they were using. */}
        <div className="mt-auto pt-6">
          {loading ? (
            <p className="px-2 text-xs text-ink-muted">Checking session…</p>
          ) : session ? (
            <div className="px-2">
              <p className="truncate text-xs font-medium text-ink" title={session.email}>
                {session.email}
              </p>
              <p className="text-xs capitalize text-ink-muted">{session.role}</p>
            </div>
          ) : null}
          <button onClick={onLogout} className="btn-ghost mt-3 w-full">Sign out</button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <h1 className="mb-6 text-2xl font-semibold text-aubergine">{title}</h1>
        {children}
      </main>
    </div>
  );
}

export function Pill({ level }: { level: string }) {
  const map: Record<string, string> = {
    red: "bg-urgent/15 text-urgent",
    amber: "bg-amber/15 text-amber",
    green: "bg-sage/20 text-sage-deep",
  };
  return <span className={`pill ${map[level] || "bg-lilac-mist text-aubergine"}`}>{level}</span>;
}
