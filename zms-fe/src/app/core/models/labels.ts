import { AnimalStatus, Habitat, ZooRole } from './animal';
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
