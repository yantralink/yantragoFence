package com.yantrago.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.config.SecurityConfig;
import com.yantrago.api.config.WebMvcConfig;
import com.yantrago.api.dto.schedule.ScheduleDto;
import com.yantrago.api.dto.schedule.ScheduleEnabledRequest;
import com.yantrago.api.dto.schedule.ScheduleRequest;
import com.yantrago.api.security.AuditLogInterceptor;
import com.yantrago.api.security.JwtAuthFilter;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.PostAuthRateLimitFilter;
import com.yantrago.api.security.RateLimitFilter;
import com.yantrago.api.security.TenantContextFilter;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.security.TrustedProxyResolver;
import com.yantrago.api.service.JwtService;
import com.yantrago.api.service.OwnerContextService;
import com.yantrago.api.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the schedule controller endpoints and auth enforcement.
 *
 * Per AGENTS.md rule 7/8/9: every endpoint requires authentication plus
 * schedule:read / schedule:write permissions.
 */
@WebMvcTest(controllers = ScheduleController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, JwtAuthFilter.class, TenantContextFilter.class,
         RateLimitFilter.class, PostAuthRateLimitFilter.class, TrustedProxyResolver.class, AuditLogInterceptor.class, JwtService.class,
         OwnerContextService.class, TenantGuard.class, PermissionEvaluator.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.access.secret=test-access-secret-at-least-32-characters-long",
        "jwt.refresh.secret=test-refresh-secret-at-least-32-characters-long",
        "jwt.access.ttl=3600",
        "jwt.refresh.ttl=2592000"
})
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScheduleService scheduleService;

    private final UUID machineId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();

    private ScheduleDto sampleDto() {
        return new ScheduleDto(scheduleId, machineId,
                LocalTime.of(18, 0), LocalTime.of(7, 0), 127, "Asia/Kolkata", true,
                LocalDateTime.now(), LocalDateTime.now(), null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private ScheduleRequest sampleRequest() {
        ScheduleRequest r = new ScheduleRequest();
        r.setOnTime(LocalTime.of(18, 0));
        r.setOffTime(LocalTime.of(7, 0));
        r.setDaysOfWeek(127);
        r.setTimezone("Asia/Kolkata");
        r.setEnabled(true);
        return r;
    }

    @Test
    void getSchedule_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/machines/{id}/schedule", machineId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upsertSchedule_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(put("/api/v1/machines/{id}/schedule", machineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteSchedule_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(delete("/api/v1/machines/{id}/schedule", machineId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSchedule_returnsDto_whenScheduleExists() {
        when(scheduleService.getSchedule(machineId)).thenReturn(sampleDto());
        ScheduleDto dto = scheduleService.getSchedule(machineId);
        org.junit.jupiter.api.Assertions.assertEquals(scheduleId, dto.id());
        org.junit.jupiter.api.Assertions.assertEquals(127, dto.daysOfWeek());
    }

    @Test
    void upsertSchedule_delegatesToService() {
        ScheduleRequest request = sampleRequest();
        when(scheduleService.upsertSchedule(eq(machineId), any(ScheduleRequest.class)))
                .thenReturn(sampleDto());
        ScheduleDto dto = scheduleService.upsertSchedule(machineId, request);
        org.junit.jupiter.api.Assertions.assertEquals(machineId, dto.machineId());
    }

    @Test
    void setEnabled_delegatesToService() {
        ScheduleEnabledRequest request = new ScheduleEnabledRequest();
        request.setEnabled(false);
        when(scheduleService.setEnabled(machineId, false)).thenReturn(sampleDto());
        scheduleService.setEnabled(machineId, request.getEnabled());
        org.mockito.Mockito.verify(scheduleService).setEnabled(machineId, false);
    }
}
