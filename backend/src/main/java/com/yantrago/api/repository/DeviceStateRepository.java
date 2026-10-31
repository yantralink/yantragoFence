package com.yantrago.api.repository;

import com.yantrago.api.model.DeviceState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceStateRepository extends JpaRepository<DeviceState, UUID> {
}
