import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Building2 } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { Button, ErrorText, Field, inputClass } from '../components/ui';
import { apiError } from '../api/client';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', organization: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  function set<K extends keyof typeof form>(k: K, v: string) {
    setForm((f) => ({ ...f, [k]: v }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await register(form);
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
          <h1 className="text-xl font-bold text-brand-700">Create your account</h1>
        </div>
        <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <ErrorText>{error}</ErrorText>
          <Field label="Full name" htmlFor="name" required>
            <input id="name" required value={form.name} onChange={(e) => set('name', e.target.value)} className={inputClass} />
          </Field>
          <Field label="Email" htmlFor="email" required>
            <input id="email" type="email" required value={form.email} onChange={(e) => set('email', e.target.value)} className={inputClass} />
          </Field>
          <Field label="Organization (optional)" htmlFor="org">
            <input id="org" value={form.organization} onChange={(e) => set('organization', e.target.value)} className={inputClass} />
          </Field>
          <Field label="Password" htmlFor="password" hint="At least 8 characters." required>
            <input id="password" type="password" required minLength={8} value={form.password}
              onChange={(e) => set('password', e.target.value)} className={inputClass} />
          </Field>
          <Button type="submit" disabled={busy} className="w-full justify-center">
            {busy ? 'Creating…' : 'Create account'}
          </Button>
          <p className="mt-4 text-center text-sm text-slate-600">
            Already have an account? <Link to="/login" className="text-brand-600 underline">Sign in</Link>
          </p>
        </form>
      </div>
    </main>
  );
}
