import { useAuth } from '../hooks/useAuth';
import { Card } from '../components/ui';

export default function Profile() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <div className="max-w-lg">
      <h1 className="mb-4 text-2xl font-bold text-slate-900">Account</h1>
      <Card>
        <dl className="space-y-2 text-sm">
          <div className="flex justify-between"><dt className="text-slate-500">Name</dt><dd className="font-medium">{user.name}</dd></div>
          <div className="flex justify-between"><dt className="text-slate-500">Email</dt><dd className="font-medium">{user.email}</dd></div>
          <div className="flex justify-between"><dt className="text-slate-500">Role</dt><dd className="font-medium">{user.role}</dd></div>
          {user.organization && <div className="flex justify-between"><dt className="text-slate-500">Organization</dt><dd className="font-medium">{user.organization}</dd></div>}
        </dl>
      </Card>
      <p className="mt-4 text-xs text-slate-500">
        You are interacting with an AI-assisted system. Results are advisory; final determinations are made by City of
        Los Angeles staff. To speak with staff directly, contact LADBS Development Services.
      </p>
    </div>
  );
}
