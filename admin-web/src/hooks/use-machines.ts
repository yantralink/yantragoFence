'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import type { Machine, Command } from '@/types';

/**
 * useMachines hook — fetches and manages machines via React Query.
 */
export function useMachines() {
  return useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<Machine[]>('/api/machines');
      return data;
    },
  });
}

export function useMachine(id: string) {
  return useQuery<Machine>({
    queryKey: ['machine', id],
    queryFn: async () => {
      const { data } = await apiClient.get<Machine>(`/api/machines/${id}`);
      return data;
    },
    enabled: !!id,
  });
}

export function useSendCommand() {
  const queryClient = useQueryClient();
  return useMutation<
    Command,
    Error,
    { machineId: string; imei: string; commandType: string }
  >({
    mutationFn: async (params) => {
      const { data } = await apiClient.post<Command>('/api/commands', params);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['commands'] });
    },
  });
}
