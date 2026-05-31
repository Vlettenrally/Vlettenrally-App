package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.UUID;

@RegisterForReflection
public record CheckInStateResponse(
    UUID mainDeviceId,
    String mainDeviceNickname,
    boolean detailsSubmitted,
    List<DeviceResponse> devices
) {}
