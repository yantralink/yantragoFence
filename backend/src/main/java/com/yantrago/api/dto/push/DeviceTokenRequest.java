package com.yantrago.api.dto.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for registering a push device token.
 *
 * Per AGENTS.md rule 12: validation via Bean Validation.
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 */
public class DeviceTokenRequest {

    @NotBlank
    @Size(max = 512)
    private String token;

    @NotBlank
    @Size(max = 20)
    private String platform; // ANDROID | IOS | WEB

    @Size(max = 255)
    private String deviceLabel;

    @Size(max = 50)
    private String appVersion;

    // Getters and setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getDeviceLabel() { return deviceLabel; }
    public void setDeviceLabel(String deviceLabel) { this.deviceLabel = deviceLabel; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
}
