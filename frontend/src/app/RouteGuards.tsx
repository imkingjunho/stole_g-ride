import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/shared/stores/authStore';
export function RequireAuth() {
  const token = useAuthStore((s) => s.accessToken);
  const location = useLocation();
  return token ? <Outlet /> : <Navigate to="/login" state={{ from: location.pathname }} replace />;
}
export function GuestOnly() {
  return useAuthStore((s) => s.accessToken) ? <Navigate to="/request" replace /> : <Outlet />;
}
