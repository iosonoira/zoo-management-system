import {
  MedicalRecord,
  MedicalRecordDetail,
  NewMedicalRecord,
  Treatment,
  TreatmentStatus,
} from '../models/health';

/** Port for the health-service REST contract (`/medical-records`, `/treatments`). */
export abstract class HealthApi {
  /** Every record of one animal, newest examination first. */
  abstract listRecords(animalId: string): Promise<MedicalRecord[]>;
  abstract getRecord(id: string): Promise<MedicalRecordDetail>;
  abstract createRecord(input: NewMedicalRecord): Promise<MedicalRecord>;
  abstract prescribe(recordId: string, description: string): Promise<Treatment>;
  abstract updateTreatmentStatus(id: string, status: TreatmentStatus): Promise<Treatment>;
}
