import { Card } from '@/components/ui/card';
import { Chart } from '@/components/charts';

export default function AdminReportsPage() {
  const data = [
    { month: 'Jan', commands: 80 },
    { month: 'Feb', commands: 100 },
    { month: 'Mar', commands: 120 },
    { month: 'Apr', commands: 150 },
    { month: 'May', commands: 180 },
    { month: 'Jun', commands: 200 },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Reports</h1>
      <Chart title="Commands per Month" data={data} xKey="month" yKey="commands" type="bar" />
      <Card title="Export">
        <p className="text-sm text-gray-500">Export reports as PDF or CSV.</p>
      </Card>
    </div>
  );
}
