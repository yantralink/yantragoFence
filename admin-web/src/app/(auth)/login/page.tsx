'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/use-auth';
import { LoginForm } from '@/components/forms';
import { Card } from '@/components/ui/card';

export default function LoginPage() {
  const router = useRouter();
  const { login, loading, error, clearError } = useAuth();
  const [localError, setLocalError] = useState<string | null>(null);

  const handleLogin = async (email: string, password: string) => {
    setLocalError(null);
    clearError();
    try {
      await login(email, password);
      router.push('/dashboard');
    } catch {
      setLocalError(error || 'Login failed. Please check your credentials.');
    }
  };

  return (
    <div className="w-full max-w-md">
      <Card className="space-y-6">
        <div className="text-center">
          <div className="mb-2 text-4xl">⚡</div>
          <h1 className="text-2xl font-bold text-gray-900">YantraGO</h1>
          <p className="mt-1 text-sm text-gray-500">Machine Management Platform</p>
        </div>
        <LoginForm onSubmit={handleLogin} loading={loading} error={localError || error} />
      </Card>
    </div>
  );
}
