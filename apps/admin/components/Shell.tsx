"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { logout } from "@/lib/api";

const NAV = [
  { href: "/", label: "Dashboard" },
  { href: "/safety", label: "Safety flags" },
  { href: "/feedback", label: "Feedback" },
  { href: "/users", label: "Users" },
  { href: "/content", label: "Content" },
  { href: "/prompts", label: "Prompts" },
  { href: "/system", label: "System" },
];

export default function Shell({ children, title }: { children: React.ReactNode; title: string }) {
  const pathname = usePathname();
  const router = useRouter();
  async function onLogout() {
    await logout();
    router.push("/login");
  }
  return (
    <div className="flex min-h-screen">
      <aside className="w-56 shrink-0 border-r border-line bg-paper p-4">
        <div className="mb-6 flex items-center gap-2 px-2">
          <span className="grid h-7 w-7 place-items-center rounded-full bg-aubergine text-sm font-bold text-white">A</span>
          <span className="font-semibold text-aubergine">Aira Admin</span>
        </div>
        <nav className="flex flex-col gap-1">
          {NAV.map((n) => {
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
        <button onClick={onLogout} className="btn-ghost mt-6 w-full">Sign out</button>
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
