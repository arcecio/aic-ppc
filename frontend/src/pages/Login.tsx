import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Building2 } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { Button, ErrorText, Field, inputClass } from '../components/ui';
import { apiError } from '../api/client';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await login(email, password);
      navigate('/dashboard');
    } catch (err) {
      setError(apiError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="mb-6 flex items-center gap-2">
          <Building2 className="h-8 w-8 text-brand-500" aria-hidden="true" />
          <div>
            <h1 className="text-xl font-bold text-brand-700">AIP Pre-Plan Check Assistant</h1>
            <p className="text-sm text-slate-500">City of Los Angeles · LADBS</p>
          </div>
        </div>
        <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">Sign in</h2>
          <p className="mb-4 text-xs text-slate-500">
            In production, applicants sign in via the Angeleno Account and staff via City SSO (Okta).
          </p>
          <ErrorText>{error}</ErrorText>
          <Field label="Email" htmlFor="email" required>
            <input id="email" type="email" autoComplete="email" required value={email}
              onChange={(e) => setEmail(e.target.value)} className={inputClass} />
          </Field>
          <Field label="Password" htmlFor="password" required>
            <input id="password" type="password" autoComplete="current-password" required value={password}
              onChange={(e) => setPassword(e.target.value)} className={inputClass} />
          </Field>
          <Button type="submit" disabled={busy} className="w-full justify-center">
            {busy ? 'Signing in…' : 'Sign in'}
          </Button>
          <p className="mt-4 text-center text-sm text-slate-600">
            No account? <Link to="/register" className="text-brand-600 underline">Create one</Link>
          </p>
        </form>
      </div>
    </main>
  );
}
