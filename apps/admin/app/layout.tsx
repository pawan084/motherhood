import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Aira Admin",
  description: "Operational console for Aira — safety, care content, and users.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
