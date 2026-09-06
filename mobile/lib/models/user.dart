/// User model — represents an authenticated user.
class User {
  final String id;
  final String organizationId;
  final String email;
  final String fullName;
  final String role;
  final String? phoneNumber;
  final bool active;

  const User({
    required this.id,
    required this.organizationId,
    required this.email,
    required this.fullName,
    required this.role,
    this.phoneNumber,
    required this.active,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] as String,
      organizationId: json['organizationId'] as String,
      email: json['email'] as String,
      fullName: json['fullName'] as String? ?? json['name'] as String? ?? '',
      role: json['role'] as String? ?? 'USER',
      phoneNumber: json['phoneNumber'] as String?,
      active: json['active'] as bool? ?? true,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'organizationId': organizationId,
        'email': email,
        'fullName': fullName,
        'role': role,
        'phoneNumber': phoneNumber,
        'active': active,
      };

  bool get isAdmin => role == 'ADMIN' || role == 'SUPER_ADMIN';
  bool get isSuperAdmin => role == 'SUPER_ADMIN';
}
