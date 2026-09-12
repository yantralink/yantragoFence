package com.yantrago.api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class UserDto {

    private UUID id;
    private UUID organizationId;
    private String email;
    private String fullName;
    private String phone;
    private Boolean isActive;
    private Boolean isLocked;
    private String preferredLocale;

    public UserDto() {}

    public UserDto(UUID id, UUID organizationId, String email, String fullName,
                   String phone, Boolean isActive, Boolean isLocked) {
        this(id, organizationId, email, fullName, phone, isActive, isLocked, "en");
    }

    public UserDto(UUID id, UUID organizationId, String email, String fullName,
                   String phone, Boolean isActive, Boolean isLocked, String preferredLocale) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.isActive = isActive;
        this.isLocked = isLocked;
        this.preferredLocale = preferredLocale;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean isLocked) { this.isLocked = isLocked; }
    public String getPreferredLocale() { return preferredLocale; }
    public void setPreferredLocale(String preferredLocale) { this.preferredLocale = preferredLocale; }
}
