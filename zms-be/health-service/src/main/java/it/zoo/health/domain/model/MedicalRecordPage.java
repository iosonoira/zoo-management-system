package it.zoo.health.domain.model;

import java.util.List;

public record MedicalRecordPage(List<MedicalRecord> items, int page, int size, long total) {}
