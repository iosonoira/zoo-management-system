package it.zoo.health.domain.model;

import java.util.List;

public record MedicalRecordDetail(MedicalRecord record, List<Treatment> treatments) {}
