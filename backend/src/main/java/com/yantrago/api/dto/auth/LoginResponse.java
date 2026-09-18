package com.yantrago.api.dto.auth;

public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserInfo user;

    public LoginResponse() {}

    public LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserInfo user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }

    public static class UserInfo {
        private String id;
        private String email;
        private String fullName;
        private String organizationId;
        private String organizationName;
        private String role;
        private Boolean active;
        private String preferredLocale;
        private String phoneNumber;

        public UserInfo() {}

        public UserInfo(String id, String email, String fullName, String organizationId, String organizationName) {
            this.id = id;
            this.email = email;
            this.fullName = fullName;
            this.organizationId = organizationId;
            this.organizationName = organizationName;
        }

        public UserInfo(String id, String email, String fullName, String organizationId, String organizationName,
                        String role, Boolean active, String preferredLocale, String phoneNumber) {
            this(id, email, fullName, organizationId, organizationName);
            this.role = role;
            this.active = active;
            this.preferredLocale = preferredLocale;
            this.phoneNumber = phoneNumber;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getOrganizationId() { return organizationId; }
        public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
        public String getOrganizationName() { return organizationName; }
        public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public String getPreferredLocale() { return preferredLocale; }
        public void setPreferredLocale(String preferredLocale) { this.preferredLocale = preferredLocale; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    }
}
