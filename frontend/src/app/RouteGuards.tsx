import { useAdminAccess } from '@/features/admin/useAdminAccess';
import { QueryState } from '@/shared/ui/QueryState';
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/shared/stores/authStore';
export function RequireAuth() {
  const token = useAuthStore((s) => s.accessToken);
  const location = useLocation();
  return token ? <Outlet /> : <Navigate to="/login" state={{ from: location.pathname }} replace />;
}
export function GuestOnly() {
  return useAuthStore((s) => s.accessToken) ? <LoginDestination /> : <Outlet />;
}

function LoginDestination() {
  const admin = useAdminAccess();
  const location = useLocation();
  if (admin.isError || admin.isPending)
    return (
      <section className="page">
        <QueryState
          loading={admin.isPending}
          error={admin.error}
          retry={() => void admin.refetch()}
        />
        {admin.isError && (
          <Link className="link" to="/request">
            동승 요청으로 이동
          </Link>
        )}
      </section>
    );
  return (
    <Navigate
      to={admin.data || location.state?.from === '/admin' ? '/admin' : '/request'}
      replace
    />
  );
}
