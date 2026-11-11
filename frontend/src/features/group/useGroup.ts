import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { api, unwrap, isMockMode } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';
import { GroupResponseError, readGroupDetail } from './groupResponse';
export function useGroup() {
  const { groupId } = useParams();
  const id = Number(groupId);
  const validId = Number.isSafeInteger(id) && id > 0;
  const userId = useAuthStore((s) => s.user?.id);
  const query = useQuery({
    queryKey: ['group', id],
    enabled: validId && !!userId,
    refetchInterval: (state) =>
      !isMockMode && !state.state.error && state.state.data?.status === 'PENDING' ? 3000 : false,
    retry: (count, error) => !(error instanceof GroupResponseError) && count < 1,
    queryFn: async () => {
      if (!validId) throw new GroupResponseError();
      const r = await api.GET('/api/groups/{groupId}', { params: { path: { groupId: id } } });
      if (!r.response.ok || r.data?.success === false) unwrap(r.data, r.error);
      return readGroupDetail(r.data?.success === true ? r.data.data : undefined, id, userId);
    },
  });
  return { ...query, error: validId ? query.error : new GroupResponseError(), id };
}
