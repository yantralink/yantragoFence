import 'package:flutter/material.dart';
import 'package:yantrago/models/machine.dart';

/// Machine card — list item for a machine.
class MachineCard extends StatelessWidget {
  final Machine machine;
  final VoidCallback? onTap;

  const MachineCard({super.key, required this.machine, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        onTap: onTap,
        leading: CircleAvatar(
          backgroundColor: _statusColor(machine.status).withOpacity(0.2),
          child: Icon(
            Icons.electrical_services,
            color: _statusColor(machine.status),
          ),
        ),
        title: Text(machine.name),
        subtitle: Text(machine.imei),
        trailing: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: _statusColor(machine.status).withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            machine.status,
            style: TextStyle(
              color: _statusColor(machine.status),
              fontSize: 12,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ),
    );
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'ONLINE':
      case 'FENCING_ON':
        return Colors.green;
      case 'FAULT':
        return Colors.red;
      default:
        return Colors.grey;
    }
  }
}
