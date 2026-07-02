import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';
import { Layout } from './components/Layout';
import { Spinner } from './components/ui';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import NewProject from './pages/NewProject';
import ProjectDetail from './pages/ProjectDetail';
import StaffDashboard from './pages/StaffDashboard';
import StaffReview from './pages/StaffReview';
import AdminRules from './pages/AdminRules';
import Profile from './pages/Profile';
import type { JSX } from 'react';

function Protected({ children, staff, admin }: { children: JSX.Element; staff?: boolean; admin?: boolean }) {
  const { user, loading, isStaff, isAdmin } = useAuth();
  if (loading) return <div className="p-8"><Spinner /></div>;
  if (!user) return <Navigate to="/login" replace />;
  if (staff && !isStaff) return <Navigate to="/dashboard" replace />;
  if (admin && !isAdmin) return <Navigate to="/dashboard" replace />;
  return <Layout>{children}</Layout>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/dashboard" element={<Protected><Dashboard /></Protected>} />
      <Route path="/projects/new" element={<Protected><NewProject /></Protected>} />
      <Route path="/projects/:id" element={<Protected><ProjectDetail /></Protected>} />
      <Route path="/staff" element={<Protected staff><StaffDashboard /></Protected>} />
      <Route path="/staff/runs/:runId" element={<Protected staff><StaffReview /></Protected>} />
      <Route path="/admin/rules" element={<Protected admin><AdminRules /></Protected>} />
      <Route path="/profile" element={<Protected><Profile /></Protected>} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
