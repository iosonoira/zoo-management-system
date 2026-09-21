package it.zoo.health.infrastructure.rest.dto;

import java.util.List;

public record MedicalRecordPageResponse(
        List<MedicalRecordResponse> items,
        int page,
        int size,
        long total
) {}
