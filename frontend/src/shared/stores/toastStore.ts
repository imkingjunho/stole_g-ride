import { create } from 'zustand';

interface ToastMessage {
  id: number;
  message: string;
  tone: 'info' | 'error' | 'success';
}
interface ToastState {
  messages: ToastMessage[];
  notify: (message: string, tone?: ToastMessage['tone']) => void;
  dismiss: (id: number) => void;
}
let nextId = 0;
export const useToastStore = create<ToastState>((set) => ({
  messages: [],
  notify: (message, tone = 'info') =>
    set((state) => ({ messages: [...state.messages, { id: ++nextId, message, tone }].slice(-1) })),
  dismiss: (id) =>
    set((state) => ({ messages: state.messages.filter((message) => message.id !== id) })),
}));
