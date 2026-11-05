import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { api, unwrap } from '@/shared/api/client';
export function useGroup() {
  const { groupId } = useParams();
  const id = Number(groupId);
  const query = useQuery({
    queryKey: ['group', id],
    enabled: Number.isSafeInteger(id) && id > 0,
    queryFn: async () => {
      const r = await api.GET('/api/groups/{groupId}', { params: { path: { groupId: id } } });
      return unwrap(r.data, r.error);
    },
  });
  return { ...query, id };
}
