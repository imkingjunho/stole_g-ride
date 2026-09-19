import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { components } from '@/generated/api';

type UserProfile = components['schemas']['UserProfile'];

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserProfile | null;
  setTokens: (accessToken: string, refreshToken: string, user: UserProfile) => void;
  logout: () => void;
}

/**
 * 로그인 상태. 새로고침해도 유지되도록 localStorage 에 보관한다.
 *
 * Phase 0 에는 인증이 꺼져 있어 비어 있는 것이 정상이다.
 * 실제 로그인 연결은 오승원이 T1-2·T1-5 에서 한다.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setTokens: (accessToken, refreshToken, user) => set({ accessToken, refreshToken, user }),
      logout: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    { name: 'gachiga-auth' },
  ),
);
