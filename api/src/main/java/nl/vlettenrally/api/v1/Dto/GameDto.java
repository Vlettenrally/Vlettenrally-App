package nl.vlettenrally.api.v1.Dto;

import java.util.UUID;

public record GameDto (
    UUID id,
    String name,
    String description,
    String startTime,
        String endTime) {
     public GameDto(String name, String description, String startTime, String endTime) {
         this(UUID.randomUUID(), name, description, startTime, endTime);
     }
    }
