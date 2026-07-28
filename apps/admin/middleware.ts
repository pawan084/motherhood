import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

// Gate the console behind the httpOnly `admin_session` cookie the backend sets
// on login. This is a UX redirect only — the backend is the real authority and
// re-checks every request; a forged cookie still fails there.
const PUBLIC = ["/login"];

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  if (PUBLIC.some((p) => pathname.startsWith(p))) return NextResponse.next();
  const hasSession = req.cookies.has("admin_session");
  if (!hasSession) {
    const url = req.nextUrl.clone();
    url.pathname = "/login";
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  // Everything except Next internals and static assets.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|icon.svg).*)"],
};
