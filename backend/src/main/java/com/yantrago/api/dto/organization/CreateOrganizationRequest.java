package com.yantrago.api.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateOrganizationRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 100)
    private String slug;

    private String whiteLabelConfig;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getWhiteLabelConfig() { return whiteLabelConfig; }
    public void setWhiteLabelConfig(String whiteLabelConfig) { this.whiteLabelConfig = whiteLabelConfig; }
}
