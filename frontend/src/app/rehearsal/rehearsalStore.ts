import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { RehearsalStepId } from './scenarios';
interface RehearsalState {
  active: RehearsalStepId | null;
  checked: Partial<Record<RehearsalStepId, boolean>>;
  start: (step: RehearsalStepId) => void;
  mark: (step: RehearsalStepId, checked: boolean) => void;
  reset: () => void;
}
export const useRehearsalStore = create<RehearsalState>()(
  persist(
    (set) => ({
      active: null,
      checked: {},
      start: (active) => set({ active }),
      mark: (step, checked) => set((state) => ({ checked: { ...state.checked, [step]: checked } })),
      reset: () => set({ active: null, checked: {} }),
    }),
    { name: 'gachiga-rehearsal-v1', storage: createJSONStorage(() => sessionStorage) },
  ),
);
