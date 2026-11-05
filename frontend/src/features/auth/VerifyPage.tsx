import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Navigate, useNavigate } from 'react-router-dom';
import { api, unwrap, showError } from '@/shared/api/client';
import { isMockMode } from '@/shared/api/config';
import { useSignupStore } from '@/shared/stores/signupStore';
import { Button, Input } from '@/shared/ui';
import { useCountdown } from '@/shared/ui/useCountdown';
import { AuthLayout } from './AuthLayout';
export function VerifyPage() {
  const draft = useSignupStore();
  const [code, setCode] = useState(draft.code);
  const remaining = useCountdown(draft.expiresAt);
  const navigate = useNavigate();
  const resend = useMutation({
    mutationFn: async () => {
      const r = await api.POST('/api/auth/signup', { body: { email: draft.email } });
      return unwrap(r.data, r.error);
    },
    onSuccess: (d) => {
      if (d) {
        draft.setEmail(d.email, d.expiresInSeconds);
        setCode('');
      }
    },
    onError: showError,
  });
  if (!draft.email) return <Navigate to="/signup" replace />;
  return (
    <AuthLayout
      title="인증 코드를 입력해 주세요"
      description={`${draft.email}로 보낸 6자리 코드예요.`}
    >
      <form
        className="space-y-5"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          draft.setCode(code);
          navigate('/signup/profile');
        }}
      >
        <Input
          label="인증 코드"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]{6}"
          required
          maxLength={6}
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
          hint={`남은 시간 ${Math.floor(remaining / 60)}분 ${remaining % 60}초`}
        />
        {remaining === 0 && (
          <p role="alert" className="text-sm text-danger">
            인증 시간이 지났어요. 코드를 다시 받아 주세요.
          </p>
        )}
        <p className="text-sm text-slate-600">코드가 5회 일치하지 않으면 30분간 인증이 제한돼요.</p>
        <Button fullWidth type="submit" disabled={remaining === 0 || code.length !== 6}>
          프로필 입력으로
        </Button>
      </form>
      <Button fullWidth variant="ghost" onClick={() => resend.mutate()} loading={resend.isPending}>
        코드 다시 받기
      </Button>
      {isMockMode && <p className="text-sm text-slate-600">체험용 인증 코드: 482913</p>}
    </AuthLayout>
  );
}
