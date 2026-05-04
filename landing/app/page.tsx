import type { Metadata } from "next";
import Hero from "@/components/hero/Hero";
import ProblemSection from "@/components/problem/ProblemSection";
import HowItWorks from "@/components/how-it-works/HowItWorks";
import LiveDemo from "@/components/live-demo/LiveDemo";
import SignalShowcase from "@/components/signal-showcase/SignalShowcase";
import PortfolioTeaser from "@/components/portfolio-teaser/PortfolioTeaser";
import PricingSection from "@/components/pricing/PricingSection";
import Footer from "@/components/footer/Footer";

export const metadata: Metadata = {
  title: "Tickonomics — Real-Time Funding Market Intelligence",
  description:
    "Detect liquidity stress and equity price divergence before the market moves.",
  alternates: { canonical: "https://tickonomics.io" },
};

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "Tickonomics",
  url: "https://tickonomics.io",
  applicationCategory: "FinanceApplication",
  operatingSystem: "Web",
  description:
    "Real-time funding market intelligence. Detect liquidity stress and equity price divergence before the market moves.",
  offers: {
    "@type": "Offer",
    price: "0",
    priceCurrency: "USD",
  },
};

export default function Home() {
  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <Hero />
      <ProblemSection />
      <HowItWorks />
      <LiveDemo />
      <SignalShowcase />
      <PortfolioTeaser />
      <PricingSection />
      <Footer />
    </>
  );
}
