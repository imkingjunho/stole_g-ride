import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { api, unwrap, showError } from '@/shared/api/client';
import { useToastStore } from '@/shared/stores/toastStore';
import { Button, Input } from '@/shared/ui';
import { useCountdown } from '@/shared/ui/useCountdown';
import { AuthLayout } from './AuthLayout';
export function PasswordResetPage() {
  const [email, setEmail] = useState('');
  const [sentTo, setSentTo] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [retryAt, setRetryAt] = useState(0);
  const remaining = useCountdown(retryAt);
  const navigate = useNavigate();
  const send = useMutation({
    mutationFn: async (address: string) => {
      const r = await api.POST('/api/auth/password-reset/request', { body: { email: address } });
      unwrap(r.data, r.error);
      return address;
    },
    onSuccess: (address) => {
      setSentTo(address);
      setCode('');
      setRetryAt(Date.now() + 60000);
    },
    onError: showError,
  });
  const reset = useMutation({
    mutationFn: async () => {
      const r = await api.POST('/api/auth/password-reset/confirm', {
        body: { email: sentTo, code, password },
      });
      unwrap(r.data, r.error);
    },
    onSuccess: () => {
      setPassword('');
      setConfirm('');
      useToastStore
        .getState()
        .notify('비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.', 'success');
      navigate('/login', { replace: true });
    },
    onError: showError,
  });
  function submit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      useToastStore.getState().notify('비밀번호가 서로 달라요.', 'error');
      return;
    }
    reset.mutate();
  }
  return (
    <AuthLayout
      title="비밀번호 찾기"
      description="학교 이메일을 인증하고 새 비밀번호를 설정해 주세요."
    >
      {!sentTo ? (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            send.mutate(email.trim().toLowerCase());
          }}
        >
          <Input
            label="학교 이메일"
            type="email"
            required
            autoComplete="email"
            placeholder="student@jnu.ac.kr"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Button fullWidth type="submit" loading={send.isPending}>
            인증 메일 받기
          </Button>
        </form>
      ) : (
        <form className="space-y-4" onSubmit={submit}>
          <p className="break-all text-sm text-slate-600">
            {sentTo}로 보낸 6자리 코드를 10분 안에 입력해 주세요.
          </p>
          <Input
            label="인증 코드"
            required
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
          <Input
            label="새 비밀번호"
            type="password"
            required
            autoComplete="new-password"
            minLength={8}
            maxLength={64}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Input
            label="새 비밀번호 확인"
            type="password"
            required
            autoComplete="new-password"
            minLength={8}
            maxLength={64}
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
          <Button fullWidth type="submit" loading={reset.isPending}>
            비밀번호 바꾸기
          </Button>
          <Button
            fullWidth
            variant="secondary"
            disabled={remaining > 0 || reset.isPending}
            loading={send.isPending}
            onClick={() => send.mutate(sentTo)}
          >
            {remaining > 0 ? `${remaining}초 후 다시 받기` : '인증 메일 다시 받기'}
          </Button>
          <Button
            variant="ghost"
            disabled={reset.isPending || send.isPending}
            onClick={() => {
              setSentTo('');
              setCode('');
            }}
          >
            이메일 다시 입력
          </Button>
        </form>
      )}
      <Link className="link" to="/login">
        로그인으로 돌아가기
      </Link>
    </AuthLayout>
  );
}
