import { useQuery } from '@tanstack/react-query';
import { api, unwrap } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';

export function useAdminAccess() {
  const userId = useAuthStore((s) => s.user?.id);
  const sessionVersion = useAuthStore((s) => s.sessionVersion);
  return useQuery({
    queryKey: ['admin-access', userId, sessionVersion],
    queryFn: async ({ signal }) => {
      const result = await api.GET('/api/admin/access', { signal });
      return unwrap(result.data, result.error);
    },
    retry: false,
  });
}
