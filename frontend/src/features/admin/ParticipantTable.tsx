import { Badge, Button } from '@/shared/ui';
import type { Operations } from './operations';

export type ParticipantView = 'waiting' | 'expiring' | 'matched';

interface Props {
  ops: Operations;
  view: ParticipantView;
  hub: string;
  search: string;
  limit: number;
  onMore: () => void;
}

export function ParticipantTable({ ops, view, hub, search, limit, onMore }: Props) {
  const term = search.trim().toLocaleLowerCase('ko-KR');
  function matches(row: { hub: string; nickname: string }) {
    return (!hub || row.hub === hub) && row.nickname.toLocaleLowerCase('ko-KR').includes(term);
  }
  const waiting = ops.queue.filter((row) => (view !== 'expiring' || row.expiring) && matches(row));
  const matched = ops.members.filter(matches);
  const isWaiting = view !== 'matched';
  const total = isWaiting ? waiting.length : matched.length;
  return (
    <>
      {view === 'expiring' && !ops.expiresKnown && (
        <p className="ops-note">만료 시각을 받은 요청만 표시해요.</p>
      )}
      <div className="ops-roster">
        <table aria-label="현재 참여자 목록">
          <thead>
            <tr>
              <th>닉네임</th>
              <th>출발 거점</th>
              {isWaiting ? (
                <>
                  <th>희망 인원</th>
                  <th>대기 시간</th>
                  <th>만료까지</th>
                </>
              ) : (
                <>
                  <th>그룹</th>
                  <th>그룹 상태</th>
                </>
              )}
            </tr>
          </thead>
          <tbody>
            {isWaiting
              ? waiting.slice(0, limit).map((row) => (
                  <tr key={row.nickname + row.requestedAt}>
                    <th scope="row">{row.nickname}</th>
                    <td>{row.hub}</td>
                    <td>
                      {row.preferredSize == null
                        ? row.remaining === null
                          ? '미제공'
                          : '미지정'
                        : row.preferredSize + '명'}
                    </td>
                    <td>{row.elapsed}분</td>
                    <td>
                      {row.remaining === null ? (
                        '미제공'
                      ) : row.expiring ? (
                        <Badge variant="estimated">{row.remaining}분 이내</Badge>
                      ) : (
                        row.remaining + '분 이내'
                      )}
                    </td>
                  </tr>
                ))
              : matched.slice(0, limit).map((row) => (
                  <tr key={row.groupId + ':' + row.nickname}>
                    <th scope="row">{row.nickname}</th>
                    <td>{row.hub}</td>
                    <td>#{row.groupId}</td>
                    <td>
                      <Badge variant={row.confirmed ? 'success' : 'neutral'}>
                        {row.confirmed ? '확정' : '수락 중'}
                      </Badge>
                    </td>
                  </tr>
                ))}
          </tbody>
        </table>
      </div>
      {total === 0 ? (
        <p className="ops-empty">
          {hub || search.trim()
            ? '조건에 맞는 참여자가 없어요.'
            : {
                waiting: '현재 기다리는 사람이 없어요.',
                expiring: ops.expiresKnown
                  ? '2분 이내에 만료될 요청이 없어요.'
                  : '확인할 수 있는 만료 임박 요청이 없어요.',
                matched: '진행 중인 매칭이 없어요.',
              }[view]}
        </p>
      ) : (
        <div className="ops-table-foot">
          <span>
            전체 {total}명 · {Math.min(total, limit)}명 표시
          </span>
          {total > limit && (
            <Button variant="secondary" onClick={onMore}>
              25명 더 보기
            </Button>
          )}
        </div>
      )}
    </>
  );
}
