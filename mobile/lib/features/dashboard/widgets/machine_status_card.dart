import 'package:flutter/material.dart';

/// Machine status card — summary of machine counts by status.
class MachineStatusCard extends StatelessWidget {
  final int total;
  final int online;
  final int fencingOn;
  final int fault;
  final int offline;

  const MachineStatusCard({
    super.key,
    required this.total,
    required this.online,
    required this.fencingOn,
    required this.fault,
    required this.offline,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Machine Status', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 16),
            Row(
              children: [
                _StatusChip(label: 'Total', count: total, color: Colors.blue),
                _StatusChip(label: 'Online', count: online, color: Colors.green),
                _StatusChip(label: 'Fencing', count: fencingOn, color: Colors.orange),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                _StatusChip(label: 'Fault', count: fault, color: Colors.red),
                _StatusChip(label: 'Offline', count: offline, color: Colors.grey),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  final String label;
  final int count;
  final Color color;

  const _StatusChip({
    required this.label,
    required this.count,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Padding(
        padding: const EdgeInsets.only(right: 8),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
          decoration: BoxDecoration(
            color: color.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: color.withOpacity(0.3)),
          ),
          child: Column(
            children: [
              Text('$count', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: color)),
              Text(label, style: TextStyle(fontSize: 12, color: color)),
            ],
          ),
        ),
      ),
    );
  }
}
