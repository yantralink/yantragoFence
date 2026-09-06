'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/stores/auth-store';
import { authService } from '@/lib/auth';

/**
 * useAuth hook — manages auth state on the client side.
 *
 * Checks for existing session on mount and redirects to /login if unauthenticated.
 */
export function useAuth() {
  const router = useRouter();
  const { user, isAuthenticated, loading, error, login, logout, fetchUser, clearError } =
    useAuthStore();
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    const checkSession = async () => {
      if (authService.isAuthenticated()) {
        await fetchUser();
      }
      setInitialized(true);
    };
    checkSession();
  }, [fetchUser]);

  return {
    user,
    isAuthenticated,
    loading,
    error,
    initialized,
    login,
    logout,
    clearError,
    router,
  };
}
