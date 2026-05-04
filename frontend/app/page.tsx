import { Header } from "@/components/layout/Header";
import { Sidebar } from "@/components/layout/Sidebar";

export default function DashboardPage() {
  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <Header />
        <main className="flex-1 p-6">
          <h1 className="text-2xl font-semibold mb-6">Dashboard</h1>
          <p className="text-muted">
            Milestone 1 scaffold. Charts, KPI cards, and panels will be added in
            subsequent milestones.
          </p>
        </main>
      </div>
    </div>
  );
}
