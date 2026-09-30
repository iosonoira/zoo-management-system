import { AnimalStatus, Habitat, ZooRole } from './animal';
import { TreatmentStatus } from './health';
import { PlanStatus } from './feeding';
import { Severity } from './notification';
import { IconName } from '../ui/icon/icon';

export interface StatusLabel {
  readonly label: string;
  readonly short: string;
  readonly description: string;
  readonly icon: IconName;
}

export const STATUS_LABELS: Record<AnimalStatus, StatusLabel> = {
  HEALTHY: {
    label: 'Healthy',
    short: 'Healthy',
    description: 'No concerns. Normal care routine.',
    icon: 'check',
  },
  UNDER_OBSERVATION: {
    label: 'Under observation',
    short: 'Observation',
    description: 'Watched closely for symptoms or behaviour changes.',
    icon: 'eye',
  },
  IN_TREATMENT: {
    label: 'In treatment',
    short: 'Treatment',
    description: 'Receiving veterinary care.',
    icon: 'cross',
  },
  DECEASED: {
    label: 'Deceased',
    short: 'Deceased',
    description: 'Permanent. The record stays for history and can no longer change.',
    icon: 'ribbon',
  },
};

/** Each severity owns a word and a pictogram, so it never reads by colour alone. */
export const SEVERITY_LABELS: Record<Severity, { label: string; icon: IconName }> = {
  CRITICAL: { label: 'Critical', icon: 'alert' },
  WARNING: { label: 'Warning', icon: 'bang' },
  INFO: { label: 'Info', icon: 'info' },
};

export const HABITAT_LABELS: Record<Habitat, { label: string; icon: IconName }> = {
  TERRESTRIAL: { label: 'Terrestrial', icon: 'terrestrial' },
  AQUATIC: { label: 'Aquatic', icon: 'aquatic' },
  AMPHIBIOUS: { label: 'Amphibious', icon: 'amphibious' },
};

export const ROLE_LABELS: Record<ZooRole, string> = {
  'zoo-keeper': 'Keeper',
  'zoo-vet': 'Vet',
  'zoo-admin': 'Admin',
};

export interface StateLabel {
  readonly label: string;
  readonly description: string;
  readonly icon: IconName;
}

export const TREATMENT_STATUS_LABELS: Record<TreatmentStatus, StateLabel> = {
  PRESCRIBED: {
    label: 'Prescribed',
    description: 'Written up, not started yet.',
    icon: 'status',
  },
  ACTIVE: {
    label: 'Active',
    description: 'Being given now.',
    icon: 'cross',
  },
  COMPLETED: {
    label: 'Completed',
    description: 'Course finished.',
    icon: 'check',
  },
  CANCELLED: {
    label: 'Cancelled',
    description: 'Stopped before it finished.',
    icon: 'close',
  },
};

export const PLAN_STATUS_LABELS: Record<PlanStatus, StateLabel> = {
  ACTIVE: {
    label: 'Active',
    description: 'Meals are given on this schedule.',
    icon: 'check',
  },
  SUSPENDED: {
    label: 'Suspended',
    description: 'Paused. No feedings are recorded until it resumes.',
    icon: 'lock',
  },
  ENDED: {
    label: 'Ended',
    description: 'Closed for good. Kept for history.',
    icon: 'close',
  },
};
