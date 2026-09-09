import type { Metadata } from "next";
import { Fraunces, Nunito } from "next/font/google";
import "./globals.css";

const fraunces = Fraunces({
  subsets: ["latin"],
  variable: "--font-display-loaded",
  display: "swap",
});

const nunito = Nunito({
  subsets: ["latin"],
  variable: "--font-body-loaded",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Kidora — Child English Tutor",
  description: "Kidora 儿童英语口语陪练",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className={`${fraunces.variable} ${nunito.variable}`}>
        <style>{`
          :root {
            --font-display: var(--font-display-loaded), "Palatino Linotype", serif;
            --font-body: var(--font-body-loaded), "Segoe UI", sans-serif;
          }
        `}</style>
        {children}
      </body>
    </html>
  );
}
