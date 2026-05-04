import { getLoginUrl, isAuthDisabled } from "@/lib/auth";
import { redirect } from "next/navigation";

export default async function LoginPage() {
  if (isAuthDisabled()) {
    redirect("/");
  }

  const loginUrl = getLoginUrl();

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="w-full max-w-sm p-8 bg-surface rounded-lg border border-border">
        <h1 className="text-2xl font-semibold text-center mb-6">
          Tickonomics Dashboard
        </h1>
        <a
          href={loginUrl}
          className="block w-full text-center px-4 py-2 bg-ili-blue text-white rounded-md hover:bg-blue-700 transition-colors"
        >
          Sign in with SSO
        </a>
      </div>
    </div>
  );
}
