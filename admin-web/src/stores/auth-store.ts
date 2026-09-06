import { create } from 'zustand';
import type { User } from '@/types';
import { authService } from '@/lib/auth';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  fetchUser: () => Promise<void>;
  clearError: () => void;
}

/**
 * Auth store — Zustand store for authentication state.
 *
 * Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
 */
export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  loading: false,
  error: null,

  login: async (email: string, password: string) => {
    set({ loading: true, error: null });
    try {
      const result = await authService.login({ email, password });
      set({
        user: result.user,
        isAuthenticated: true,
        loading: false,
      });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Login failed';
      set({ loading: false, error: message, isAuthenticated: false });
      throw err;
    }
  },

  logout: async () => {
    set({ loading: true });
    try {
      await authService.logout();
    } finally {
      set({ user: null, isAuthenticated: false, loading: false });
    }
  },

  fetchUser: async () => {
    set({ loading: true });
    try {
      const user = await authService.getCurrentUser();
      set({ user, isAuthenticated: true, loading: false });
    } catch {
      set({ user: null, isAuthenticated: false, loading: false });
    }
  },

  clearError: () => set({ error: null }),
}));
