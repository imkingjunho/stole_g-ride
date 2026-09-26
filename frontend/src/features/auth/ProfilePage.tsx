import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Navigate, useNavigate } from 'react-router-dom';
import { api, unwrap, showError, ApiError } from '@/shared/api/client';
import type { components } from '@/generated/api';
import { useSignupStore } from '@/shared/stores/signupStore';
import { useToastStore } from '@/shared/stores/toastStore';
import { Button, Input } from '@/shared/ui';
import { AuthLayout } from './AuthLayout';
export function ProfilePage() {
  const draft = useSignupStore();
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [gender, setGender] = useState<components['schemas']['Gender']>('M');
  const [department, setDepartment] = useState('');
  const [grade, setGrade] = useState('');
  const signup = useMutation({
    mutationFn: async () => {
      if (new TextEncoder().encode(password).length > 72)
        throw new Error('비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요.');
      const r = await api.POST('/api/auth/verify', {
        body: {
          email: draft.email,
          code: draft.code,
          password,
          nickname: nickname.trim(),
          gender,
          department: department.trim() || null,
          grade: grade ? Number(grade) : null,
        },
      });
      return unwrap(r.data, r.error);
    },
    onSuccess: () => {
      setPassword('');
      useToastStore.getState().notify('가입이 완료됐어요. 로그인해 주세요.', 'success');
      navigate('/login', { replace: true });
    },
    onError: (e) => {
      showError(e);
      if (
        e instanceof ApiError &&
        (e.code === 'VERIFY_CODE_MISMATCH' || e.code === 'VERIFY_LOCKED')
      )
        navigate('/signup/verify');
    },
  });
  if (signup.isSuccess) return <Navigate to="/login" replace />;
  if (!draft.email || !draft.code) return <Navigate to="/signup" replace />;
  return (
    <AuthLayout title="어떻게 불러드릴까요?" description="동승자에게는 닉네임만 보여요.">
      <form
        className="space-y-5"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          signup.mutate();
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
          label="비밀번호"
          type="password"
          required
          minLength={8}
          maxLength={64}
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint="8~64자, 한글을 포함하면 최대 72바이트"
        />
        <fieldset>
          <legend className="mb-2 text-sm font-semibold">성별 · 등록 후 변경 불가</legend>
          <div className="flex gap-3">
            {(['M', 'F'] as const).map((g) => (
              <label
                key={g}
                className="flex min-h-touch flex-1 items-center justify-center gap-2 rounded-control border border-slate-500"
              >
                <input
                  type="radio"
                  name="gender"
                  value={g}
                  checked={gender === g}
                  onChange={() => setGender(g)}
                />
                {g === 'M' ? '남성' : '여성'}
              </label>
            ))}
          </div>
        </fieldset>
        <Input
          label="학과 (선택)"
          maxLength={50}
          value={department}
          onChange={(e) => setDepartment(e.target.value)}
        />
        <label className="block text-sm font-semibold">
          학년 (선택)
          <select className="field mt-2" value={grade} onChange={(e) => setGrade(e.target.value)}>
            <option value="">선택 안 함</option>
            {[1, 2, 3, 4, 5, 6].map((g) => (
              <option key={g} value={g}>
                {g}학년
              </option>
            ))}
          </select>
        </label>
        <Button fullWidth type="submit" loading={signup.isPending}>
          가입 완료
        </Button>
      </form>
    </AuthLayout>
  );
}
