import { useQuery } from '@tanstack/react-query';
import { api, unwrap } from '@/shared/api/client';
export function useStats() {
  return useQuery({
    queryKey: ['stats'],
    queryFn: async () => {
      const r = await api.GET('/api/stats/me');
      return unwrap(r.data, r.error);
    },
    retry: false,
  });
}
export function useHistory(page: number, yearMonth: string) {
  return useQuery({
    queryKey: ['history', page, yearMonth],
    queryFn: async () => {
      const r = await api.GET('/api/history', {
        params: { query: { page, size: 20, ...(yearMonth ? { yearMonth } : {}) } },
      });
      return unwrap(r.data, r.error);
    },
  });
}
