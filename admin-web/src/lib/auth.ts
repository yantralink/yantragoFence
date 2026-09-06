import apiClient from './api-client';
import type { LoginRequest, LoginResponse, User } from '@/types';

/**
 * Auth service — login, logout, token management, current user.
 *
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 20: tokens stored in localStorage (client-side only,
 * no secrets committed to repo).
 */
export const authService = {
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const { data } = await apiClient.post<LoginResponse>('/api/auth/login', credentials);
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    localStorage.setItem('userId', data.user.id);
    localStorage.setItem('orgId', data.user.organizationId);
    return data;
  },

  async logout(): Promise<void> {
    try {
      await apiClient.post('/api/auth/logout');
    } finally {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('userId');
      localStorage.removeItem('orgId');
    }
  },

  async getCurrentUser(): Promise<User> {
    const { data } = await apiClient.get<User>('/api/auth/me');
    return data;
  },

  isAuthenticated(): boolean {
    if (typeof window === 'undefined') return false;
    const token = localStorage.getItem('accessToken');
    return !!token && token.length > 0;
  },

  getStoredUser(): { id: string; orgId: string } | null {
    if (typeof window === 'undefined') return null;
    const id = localStorage.getItem('userId');
    const orgId = localStorage.getItem('orgId');
    if (!id || !orgId) return null;
    return { id, orgId };
  },
};
