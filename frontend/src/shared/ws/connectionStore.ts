import { create } from 'zustand';
export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected';
export const useConnectionStore = create<{ status: ConnectionStatus }>(() => ({
  status: 'disconnected',
}));
