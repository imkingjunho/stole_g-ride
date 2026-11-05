import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, unwrap, showError } from '@/shared/api/client';
import { useSignupStore } from '@/shared/stores/signupStore';
import { Button, Input } from '@/shared/ui';
import { AuthLayout } from './AuthLayout';
export function SignupPage() {
  const [email, setEmail] = useState('');
  const navigate = useNavigate();
  const send = useMutation({
    mutationFn: async () => {
      const r = await api.POST('/api/auth/signup', { body: { email: email.trim().toLowerCase() } });
      return unwrap(r.data, r.error);
    },
    onSuccess: (d) => {
      if (d) {
        useSignupStore.getState().setEmail(d.email, d.expiresInSeconds);
        navigate('/signup/verify');
      }
    },
    onError: showError,
  });
  return (
    <AuthLayout
      title="학교 이메일로 시작해요"
      description="전남대 웹메일로 인증 코드를 보내드릴게요."
    >
      <form
        className="space-y-5"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          send.mutate();
        }}
      >
        <Input
          label="학교 이메일"
          type="email"
          pattern="[^@\s]+@jnu\.ac\.kr"
          required
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          hint="@jnu.ac.kr 주소만 사용할 수 있어요."
        />
        <Button fullWidth type="submit" loading={send.isPending}>
          인증 코드 받기
        </Button>
      </form>
    </AuthLayout>
  );
}
