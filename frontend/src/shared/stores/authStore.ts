import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { components } from '@/generated/api';

type UserProfile = components['schemas']['UserProfile'];

interface AuthState {
  sessionVersion: number;
  accessToken: string | null;
  refreshToken: string | null;
  user: UserProfile | null;
  setTokens: (accessToken: string, refreshToken: string, user: UserProfile) => void;
  logout: () => void;
}

/** 토큰 회전과 명시적인 로그인/로그아웃 세대를 구분한다. 비밀번호는 저장하지 않는다. */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      sessionVersion: 0,
      accessToken: null,
      refreshToken: null,
      user: null,
      setTokens: (accessToken, refreshToken, user) =>
        set((state) => ({
          accessToken,
          refreshToken,
          user,
          sessionVersion: state.sessionVersion + 1,
        })),
      logout: () =>
        set((state) => ({
          accessToken: null,
          refreshToken: null,
          user: null,
          sessionVersion: state.sessionVersion + 1,
        })),
    }),
    { name: 'gachiga-auth' },
  ),
);
