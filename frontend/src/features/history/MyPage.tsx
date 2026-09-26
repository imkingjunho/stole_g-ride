import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, unwrap, showError } from '@/shared/api/client';
import type { components } from '@/generated/api';
import { useAuthStore } from '@/shared/stores/authStore';
import { Card, Button, Modal, Input } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { won } from '@/shared/api/format';
import { useStats } from './useHistory';
export function MyPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const stats = useStats();
  const [edit, setEdit] = useState(false);
  const [nickname, setNickname] = useState('');
  const [department, setDepartment] = useState('');
  const [grade, setGrade] = useState('');
  const profile = useQuery({
    queryKey: ['profile'],
    queryFn: async () => {
      const r = await api.GET('/api/users/me');
      return unwrap(r.data, r.error);
    },
  });
  const update = useMutation({
    mutationFn: async () => {
      const body: components['schemas']['UpdateProfileRequest'] = {
        nickname: nickname.trim(),
        department: department.trim() || null,
        grade: grade ? Number(grade) : null,
      };
      const r = await api.PATCH('/api/users/me', { body });
      return unwrap(r.data, r.error);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['profile'] });
      setEdit(false);
    },
    onError: showError,
  });
  const logout = useMutation({
    mutationFn: async () => {
      const r = await api.POST('/api/auth/logout');
      return unwrap(r.data, r.error);
    },
    onError: showError,
    onSettled: () => {
      useAuthStore.getState().logout();
      qc.clear();
      navigate('/login', { replace: true });
    },
  });
  if (!profile.data)
    return (
      <QueryState
        loading={profile.isLoading}
        error={profile.error}
        retry={() => void profile.refetch()}
      />
    );
  const p = profile.data;
  return (
    <section className="page">
      <h1 className="text-2xl font-bold">내 정보</h1>
      <Card>
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-brand-soft text-2xl font-bold text-brand">
          {p.nickname.slice(0, 1)}
        </div>
        <h2 className="text-xl font-bold">{p.nickname}</h2>
        <p className="mt-2 break-all text-sm text-slate-600">{p.email}</p>
        <p className="mt-2 text-sm">
          {p.department || '학과 미등록'} {p.grade ? `· ${p.grade}학년` : ''}
        </p>
        <p className="mt-2 text-xs text-slate-600">
          {p.gender === 'M' ? '남성' : '여성'} · 성별은 등록 후 변경할 수 없어요.
        </p>
        <Button
          className="mt-4"
          variant="secondary"
          onClick={() => {
            setNickname(p.nickname);
            setDepartment(p.department ?? '');
            setGrade(p.grade ? String(p.grade) : '');
            setEdit(true);
          }}
        >
          프로필 수정
        </Button>
      </Card>
      <Card>
        <h2 className="font-bold">나의 동승 기록</h2>
        {stats.data ? (
          <dl className="mt-4 grid grid-cols-2 gap-4">
            <div>
              <dt className="text-sm text-slate-600">누적 절감액</dt>
              <dd className="mt-1 text-xl font-bold text-saving">{won(stats.data.totalSaving)}</dd>
            </div>
            <div>
              <dt className="text-sm text-slate-600">동승 횟수</dt>
              <dd className="mt-1 text-xl font-bold">{stats.data.totalRides}회</dd>
            </div>
          </dl>
        ) : (
          <p className="mt-3 text-sm text-slate-600">누적 통계 준비 중</p>
        )}
      </Card>
      <Button
        fullWidth
        variant="secondary"
        loading={logout.isPending}
        onClick={() => logout.mutate()}
      >
        로그아웃
      </Button>
      <Modal open={edit} onClose={() => setEdit(false)} title="프로필 수정">
        <form
          className="space-y-4"
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            update.mutate();
          }}
        >
          <Input
            label="닉네임"
            required
            minLength={2}
            maxLength={20}
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
          />
          <Input
            label="학과"
            maxLength={50}
            value={department}
            onChange={(e) => setDepartment(e.target.value)}
          />
          <Input
            label="학년"
            type="number"
            min={1}
            max={6}
            value={grade}
            onChange={(e) => setGrade(e.target.value)}
          />
          <Button fullWidth type="submit" loading={update.isPending}>
            저장
          </Button>
        </form>
      </Modal>
    </section>
  );
}
