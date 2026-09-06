import { Card } from '@/components/ui/card';
import { Chart } from '@/components/charts';

export default function ReportsPage() {
  const data = [
    { month: 'Jan', commands: 120, alerts: 15 },
    { month: 'Feb', commands: 150, alerts: 10 },
    { month: 'Mar', commands: 180, alerts: 20 },
    { month: 'Apr', commands: 200, alerts: 12 },
    { month: 'May', commands: 250, alerts: 18 },
    { month: 'Jun', commands: 300, alerts: 22 },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Reports</h1>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Chart title="Commands per Month" data={data} xKey="month" yKey="commands" type="bar" color="#1a73e8" />
        <Chart title="Alerts per Month" data={data} xKey="month" yKey="alerts" type="bar" color="#ff9800" />
      </div>
      <Card title="Export">
        <p className="text-sm text-gray-500">Export reports as PDF or CSV.</p>
      </Card>
    </div>
  );
}
