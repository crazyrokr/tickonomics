import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
});

export const metadata: Metadata = {
  title: "Tickonomics — Real-Time Funding Market Intelligence",
  description:
    "Detect liquidity stress and equity price divergence before the market moves. Ingests Fed rates and live tick data to compute a composite Liquidity Index with adaptive signal generation.",
  keywords: [
    "liquidity",
    "federal reserve",
    "funding markets",
    "quantitative finance",
    "real-time signals",
  ],
  openGraph: {
    title: "Tickonomics — Real-Time Funding Market Intelligence",
    description:
      "Detect liquidity stress and equity price divergence before the market moves.",
    url: "https://tickonomics.io",
    siteName: "Tickonomics",
    images: [
      {
        url: "/images/og-image.png",
        width: 1200,
        height: 630,
        alt: "Tickonomics — Real-Time Funding Market Intelligence",
      },
    ],
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "Tickonomics — Real-Time Funding Market Intelligence",
    description:
      "Detect liquidity stress and equity price divergence before the market moves.",
    images: ["/images/og-image.png"],
  },
  metadataBase: new URL("https://tickonomics.io"),
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} antialiased`}>
      <body className="min-h-screen flex flex-col bg-white text-zinc-900">
        {children}
      </body>
    </html>
  );
}
