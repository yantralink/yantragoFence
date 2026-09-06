// Domain models — mirrors the backend JPA entities.

export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'OPERATOR' | 'VIEWER';

export interface User {
  id: string;
  organizationId: string;
  email: string;
  fullName: string;
  role: UserRole;
  phoneNumber?: string;
  active: boolean;
}

export interface Organization {
  id: string;
  name: string;
  slug: string;
  active: boolean;
  createdAt: string;
}

export type MachineStatus = 'ONLINE' | 'OFFLINE' | 'FENCING_ON' | 'FENCING_OFF' | 'FAULT';
export type ProtocolType = 'YANTRAGO_FENCING' | 'CONCOX_V5' | 'JT808';

export interface Machine {
  id: string;
  organizationId: string;
  imei: string;
  name: string;
  model?: string;
  serialNumber?: string;
  protocolType: ProtocolType;
  status: MachineStatus;
  customerId?: string;
  simNumber?: string;
  firmwareVersion?: string;
  lastSeenAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface Customer {
  id: string;
  organizationId: string;
  name: string;
  email?: string;
  phoneNumber?: string;
  address?: string;
  active: boolean;
  createdAt: string;
}

export interface DeviceState {
  deviceId: string;
  imei: string;
  status: MachineStatus;
  voltage?: number;
  battery?: number;
  gsmSignal?: number;
  charging?: boolean;
  fencingOn?: boolean;
  outputVoltage?: number;
  current?: number;
  latitude?: number;
  longitude?: number;
  speed?: number;
  satellites?: number;
  lastUpdateAt?: string;
}

export interface Location {
  id: string;
  deviceId: string;
  imei: string;
  latitude: number;
  longitude: number;
  speed?: number;
  course?: number;
  satellites?: number;
  timestamp: string;
}

export interface Telemetry {
  id: string;
  deviceId: string;
  imei: string;
  voltage?: number;
  battery?: number;
  gsmSignal?: number;
  charging?: boolean;
  timestamp: string;
}

export type CommandStatus = 'PENDING' | 'QUEUED' | 'SENT' | 'ACK' | 'DONE' | 'FAILED';
export type CommandType = 'FENCING_ON' | 'FENCING_OFF' | 'RESTART' | 'QUERY_STATE';

export interface Command {
  id: string;
  machineId: string;
  imei: string;
  commandType: CommandType;
  status: CommandStatus;
  createdBy?: string;
  resultMessage?: string;
  sentAt?: string;
  ackedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export type AlertSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

export interface Alert {
  id: string;
  organizationId: string;
  deviceId: string;
  imei: string;
  alertType: string;
  severity: AlertSeverity;
  message: string;
  acknowledged: boolean;
  acknowledgedAt?: string;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  organizationId: string;
  userId: string;
  action: string;
  entityType: string;
  entityId?: string;
  details?: string;
  ipAddress?: string;
  createdAt: string;
}
