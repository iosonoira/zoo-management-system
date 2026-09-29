export type TreatmentStatus = 'PRESCRIBED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface MedicalRecord {
  readonly id: string;
  readonly animalId: string;
  readonly reason: string;
  readonly diagnosis: string;
  readonly examinedOn: string;
  readonly veterinarian: string;
  readonly createdBy: string | null;
  readonly updatedBy: string | null;
}

export interface Treatment {
  readonly id: string;
  readonly medicalRecordId: string;
  readonly description: string;
  readonly status: TreatmentStatus;
  readonly startedOn: string | null;
  readonly endedOn: string | null;
  readonly createdBy: string | null;
  readonly updatedBy: string | null;
}

export interface MedicalRecordDetail extends MedicalRecord {
  readonly treatments: readonly Treatment[];
}

export interface NewMedicalRecord {
  readonly animalId: string;
  readonly reason: string;
  readonly diagnosis: string;
  readonly examinedOn: string;
  readonly veterinarian: string;
}

/** Same rules as `Treatment.canTransitionTo` in health-service. */
export const TREATMENT_TRANSITIONS: Readonly<Record<TreatmentStatus, readonly TreatmentStatus[]>> =
  {
    PRESCRIBED: ['ACTIVE', 'CANCELLED'],
    ACTIVE: ['COMPLETED', 'CANCELLED'],
    COMPLETED: [],
    CANCELLED: [],
  };

export function canTreatmentTransitionTo(
  treatment: Pick<Treatment, 'status'>,
  target: TreatmentStatus,
): boolean {
  return treatment.status !== target && TREATMENT_TRANSITIONS[treatment.status].includes(target);
}

export function isTreatmentOpen(treatment: Pick<Treatment, 'status'>): boolean {
  return treatment.status === 'PRESCRIBED' || treatment.status === 'ACTIVE';
}
