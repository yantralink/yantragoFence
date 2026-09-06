import { Card } from '@/components/ui/card';

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
      <Card title="System Settings">
        <p className="text-sm text-gray-500">
          Configure system-wide settings including API URLs, notification preferences, and
          security policies.
        </p>
      </Card>
    </div>
  );
}
