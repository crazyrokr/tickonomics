"use client";

import { useState, useCallback } from "react";
import type { ConfigEntry } from "@/types/api";

interface ConfigEditorProps {
  initialConfig: ConfigEntry[];
  onSubmit: (entries: ConfigEntry[]) => Promise<void>;
  isAdmin: boolean;
}

function toJson(entries: ConfigEntry[]): string {
  const obj: Record<string, unknown> = {};
  for (const entry of entries) {
    obj[entry.key] = entry.value;
  }
  return JSON.stringify(obj, null, 2);
}

function parseConfig(text: string): ConfigEntry[] | null {
  try {
    const parsed = JSON.parse(text);
    if (typeof parsed !== "object" || Array.isArray(parsed)) return null;
    return Object.entries(parsed).map(([key, value]) => ({ key, value }));
  } catch {
    return null;
  }
}

function computeDiff(original: string, current: string): string[] {
  const origLines = original.split("\n");
  const currLines = current.split("\n");
  const maxLen = Math.max(origLines.length, currLines.length);
  const diff: string[] = [];

  for (let i = 0; i < maxLen; i++) {
    const o = origLines[i] ?? "";
    const c = currLines[i] ?? "";
    if (o !== c) {
      if (o) diff.push(`- ${o}`);
      if (c) diff.push(`+ ${c}`);
    }
  }
  return diff;
}

export function ConfigEditor({ initialConfig, onSubmit, isAdmin }: ConfigEditorProps) {
  const [text, setText] = useState(() => toJson(initialConfig));
  const [original] = useState(() => toJson(initialConfig));
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showDiff, setShowDiff] = useState(false);

  const parsed = parseConfig(text);
  const diff = computeDiff(original, text);
  const hasChanges = diff.length > 0;

  const handleSubmit = useCallback(async () => {
    if (!parsed) {
      setError("Invalid JSON format");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(parsed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Submit failed");
    } finally {
      setSubmitting(false);
    }
  }, [parsed, onSubmit]);

  if (!isAdmin) {
    return (
      <div className="rounded-lg border border-border p-4 bg-surface">
        <h3 className="text-sm font-medium text-muted mb-3">Configuration</h3>
        <pre className="text-sm bg-surface-dark text-foreground p-3 rounded overflow-auto max-h-96" data-testid="config-readonly">
          {text}
        </pre>
        <p className="text-xs text-muted mt-2">Read-only view. Admin access required to edit.</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border p-4 bg-surface">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-muted">Configuration Editor</h3>
        <button
          onClick={() => setShowDiff(!showDiff)}
          className="text-xs text-ili-blue hover:underline"
          disabled={!hasChanges}
          data-testid="toggle-diff"
        >
          {showDiff ? "Hide Diff" : "Show Diff"}
        </button>
      </div>

      <textarea
        className="w-full h-96 p-3 font-mono text-sm bg-surface-dark text-foreground border border-border rounded resize-y"
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          setError(null);
        }}
        data-testid="config-textarea"
      />

      {error && (
        <p className="text-sm text-ili-red mt-2" data-testid="config-error">{error}</p>
      )}

      {showDiff && hasChanges && (
        <div className="mt-3 p-3 bg-surface-dark rounded text-sm font-mono" data-testid="config-diff">
          {diff.map((line, i) => (
            <div
              key={i}
              className={
                line.startsWith("- ")
                  ? "text-ili-red"
                  : line.startsWith("+ ")
                    ? "text-ili-green"
                    : ""
              }
            >
              {line}
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center gap-3 mt-3">
        <button
          onClick={handleSubmit}
          disabled={!hasChanges || !parsed || submitting}
          className="px-4 py-2 text-sm bg-ili-blue text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          data-testid="config-submit"
        >
          {submitting ? "Submitting..." : "Submit"}
        </button>
        {!parsed && (
          <span className="text-xs text-ili-red">Invalid JSON</span>
        )}
      </div>
    </div>
  );
}
