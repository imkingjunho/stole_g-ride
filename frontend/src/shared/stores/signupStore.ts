import { create } from 'zustand';
interface SignupState {
  email: string;
  code: string;
  expiresAt: number;
  setEmail: (email: string, seconds: number) => void;
  setCode: (code: string) => void;
  clear: () => void;
}
// 인증 코드·비밀번호를 영구 저장하지 않는다. 새로고침하면 처음부터 다시 시작한다.
export const useSignupStore = create<SignupState>((set) => ({
  email: '',
  code: '',
  expiresAt: 0,
  setEmail: (email, seconds) => set({ email, code: '', expiresAt: Date.now() + seconds * 1000 }),
  setCode: (code) => set({ code }),
  clear: () => set({ email: '', code: '', expiresAt: 0 }),
}));
