import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { MapPin } from 'lucide-react';
import { getPermitTypes, searchParcels } from '../api/reference';
import { createProject } from '../api/projects';
import { Button, Card, ErrorText, Field, inputClass, Spinner } from '../components/ui';
import { apiError } from '../api/client';
import type { Parcel, PermitType } from '../types';

export default function NewProject() {
  const navigate = useNavigate();
  const { data: permitTypes, isLoading } = useQuery({ queryKey: ['permitTypes'], queryFn: getPermitTypes });

  const [permitCode, setPermitCode] = useState('');
  const [title, setTitle] = useState('');
  const [address, setAddress] = useState('');
  const [projectScope, setProjectScope] = useState('');
  const [intendedUse, setIntendedUse] = useState('');
  const [description, setDescription] = useState('');
  const [formData, setFormData] = useState<Record<string, unknown>>({});
  const [parcel, setParcel] = useState<Parcel | null>(null);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const permit: PermitType | undefined = useMemo(
    () => permitTypes?.find((p) => p.code === permitCode), [permitTypes, permitCode]);

  async function lookup() {
    if (!address.trim()) return;
    setLookupBusy(true);
    try {
      const results = await searchParcels(address);
      setParcel(results[0] ?? null);
    } finally {
      setLookupBusy(false);
    }
  }

  function setField(id: string, value: unknown) {
    setFormData((f) => ({ ...f, [id]: value }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const project = await createProject({
        title, permitTypeCode: permitCode, projectScope, intendedUse, description,
        address, apn: parcel?.apn, formData,
      });
      navigate(`/projects/${project.id}`);
    } catch (err) {
      setError(apiError(err));
    } finally {
      setBusy(false);
    }
  }

  if (isLoading) return <Spinner />;

  return (
    <div className="max-w-3xl">
      <h1 className="mb-4 text-2xl font-bold text-slate-900">Start a Pre-Plan Check</h1>
      <form onSubmit={submit}>
        <ErrorText>{error}</ErrorText>

        <Card className="mb-4">
          <Field label="Permit / project type" htmlFor="permit" required>
            <select id="permit" required value={permitCode} onChange={(e) => { setPermitCode(e.target.value); setFormData({}); }} className={inputClass}>
              <option value="">Select a permit type…</option>
              {permitTypes?.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
            </select>
          </Field>
          {permit?.description && <p className="text-sm text-slate-600">{permit.description}</p>}
        </Card>

        <Card className="mb-4">
          <Field label="Project title" htmlFor="title" required>
            <input id="title" required value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} />
          </Field>
          <Field label="Project address" htmlFor="address" hint="We validate against City GIS (ZIMAS/NavigateLA).">
            <div className="flex gap-2">
              <input id="address" value={address} onChange={(e) => setAddress(e.target.value)} className={inputClass}
                placeholder="e.g. 8080 Mulholland Dr, Los Angeles, CA 90046" />
              <Button type="button" variant="secondary" onClick={lookup} disabled={lookupBusy}>
                <MapPin className="h-4 w-4" aria-hidden="true" /> {lookupBusy ? '…' : 'Look up'}
              </Button>
            </div>
          </Field>
          {parcel && (
            <div className="rounded-md bg-brand-50 p-3 text-sm text-brand-700" role="status">
              <p className="font-semibold">Parcel resolved · APN {parcel.apn}</p>
              <p>Zone: {parcel.zone ?? '—'} · Council District {parcel.councilDistrict ?? '—'}</p>
              {parcel.overlays.length > 0 && <p>Overlays: {parcel.overlays.join(', ')}</p>}
              {parcel.hazardZones.length > 0 && <p>Hazards: {parcel.hazardZones.join(', ')}</p>}
            </div>
          )}
        </Card>

        <Card className="mb-4">
          <Field label="Project scope" htmlFor="scope" hint="Describe the work. Used by the screening engine.">
            <textarea id="scope" rows={3} value={projectScope} onChange={(e) => setProjectScope(e.target.value)} className={inputClass} />
          </Field>
          <Field label="Intended use" htmlFor="use">
            <input id="use" value={intendedUse} onChange={(e) => setIntendedUse(e.target.value)} className={inputClass} />
          </Field>
          <Field label="Additional description" htmlFor="desc">
            <textarea id="desc" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} className={inputClass} />
          </Field>
        </Card>

        {permit && permit.formSchema.length > 0 && (
          <Card className="mb-4">
            <h2 className="mb-2 text-lg font-semibold">{permit.name} details</h2>
            {permit.formSchema.map((field) => (
              <Field key={field.id} label={field.label} htmlFor={field.id} required={field.required}>
                {field.type === 'boolean' ? (
                  <input id={field.id} type="checkbox" checked={Boolean(formData[field.id])}
                    onChange={(e) => setField(field.id, e.target.checked)} className="h-4 w-4" />
                ) : field.type === 'select' ? (
                  <select id={field.id} className={inputClass} value={String(formData[field.id] ?? '')}
                    onChange={(e) => setField(field.id, e.target.value)}>
                    <option value="">Select…</option>
                    {field.options?.map((o) => <option key={o} value={o}>{o}</option>)}
                  </select>
                ) : (
                  <input id={field.id} type={field.type === 'number' ? 'number' : 'text'} className={inputClass}
                    value={String(formData[field.id] ?? '')}
                    onChange={(e) => setField(field.id, field.type === 'number' ? Number(e.target.value) : e.target.value)} />
                )}
              </Field>
            ))}
          </Card>
        )}

        <Button type="submit" disabled={busy || !permitCode || !title}>
          {busy ? 'Creating…' : 'Create project'}
        </Button>
      </form>
    </div>
  );
}
