import { useState, useEffect, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { api, unwrap, showError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';
import { Button, Input } from '@/shared/ui';
import { isMockMode } from '@/shared/api/config';
import { useSignupStore } from '@/shared/stores/signupStore';
import { AuthLayout } from './AuthLayout';
export function LoginPage() {
  useEffect(() => {
    useSignupStore.getState().clear();
  }, []);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const login = useMutation({
    mutationFn: async (body: { email: string; password: string }) => {
      const r = await api.POST('/api/auth/login', { body });
      return unwrap(r.data, r.error);
    },
    onSuccess: (data) => {
      if (!data) return;
      useAuthStore.getState().setTokens(data.accessToken, data.refreshToken, data.user);
      navigate('/request', { replace: true });
    },
    onError: showError,
  });
  function submit(e: FormEvent) {
    e.preventDefault();
    login.mutate({ email: email.trim().toLowerCase(), password });
  }
  return (
    <AuthLayout
      title="함께 가면, 더 가벼운 길"
      description="전남대 학생들과 함께 이동하고 교통비를 아껴 보세요."
    >
      <form onSubmit={submit} className="space-y-5">
        <Input
          label="학교 이메일"
          type="email"
          autoComplete="username"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="student@jnu.ac.kr"
        />
        <Input
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button fullWidth type="submit" loading={login.isPending}>
          로그인
        </Button>
      </form>
      <Link className="link w-full" to="/signup">
        학교 이메일로 회원가입
      </Link>
      {isMockMode && (
        <Button
          fullWidth
          variant="secondary"
          loading={login.isPending}
          onClick={() => login.mutate({ email: 'hong@jnu.ac.kr', password: 'demo12345' })}
        >
          목 데이터로 둘러보기
        </Button>
      )}
    </AuthLayout>
  );
}
