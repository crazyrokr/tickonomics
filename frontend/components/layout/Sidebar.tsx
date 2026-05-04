"use client";

import { useState } from "react";

interface NavItem {
  label: string;
  href: string;
  icon: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", href: "/", icon: "📊" },
  { label: "Charts", href: "/charts", icon: "📈" },
  { label: "KPIs", href: "/kpis", icon: "🎯" },
  { label: "Monitoring", href: "/monitoring", icon: "🔍" },
  { label: "Config", href: "/config", icon: "⚙️" },
];

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside
      className={`bg-sidebar text-sidebar-text flex flex-col transition-all duration-200 ${
        collapsed ? "w-16" : "w-56"
      }`}
    >
      <div className="h-14 flex items-center justify-between px-4 border-b border-zinc-800">
        {!collapsed && (
          <span className="text-sm font-semibold text-white">
            Tickonomics
          </span>
        )}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="text-sidebar-text hover:text-white p-1"
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {collapsed ? "→" : "←"}
        </button>
      </div>
      <nav className="flex-1 py-2">
        {NAV_ITEMS.map((item) => (
          <a
            key={item.href}
            href={item.href}
            className="flex items-center gap-3 px-4 py-2 hover:bg-sidebar-hover transition-colors text-sm"
          >
            <span aria-hidden="true">{item.icon}</span>
            {!collapsed && <span>{item.label}</span>}
          </a>
        ))}
      </nav>
    </aside>
  );
}
