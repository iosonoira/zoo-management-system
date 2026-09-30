import { Notification } from '../models/notification';

// Animal IDs from demo-data.ts (with names for reference)
const LULU_ID = '81a2b3c4-4f6e-4c91-8e8f-0a2b4c6e8d39'; // Ring-tailed lemur
const PEPE_ID = '92b3c4d5-5a7f-4da2-9f9a-1b3c5d7f9e4a'; // Ring-tailed lemur
const ZURI_ID = '3b5f6a7c-1c3b-4f6e-9b5c-7d9e1f3b5a06'; // African lion
const BRUNO_ID = '19d4e5f6-0b2a-4e5d-8a4b-6c8d0e2a4f95'; // Aldabra giant tortoise
const NIA_ID = 'c43e9a1b-7f82-4c3b-8d2e-4a6b8c0e2d73'; // Plains zebra
const SIBILLA_ID = '2c3d4e5f-0fce-42f7-8e4f-6a8b0c2e4d9f'; // Green anaconda
const ONDA_ID = 'd6f7a8b9-7c9b-4fc4-9b1c-3d5e7f9b1a6c'; // Harbour seal
const BLU_ID = 'f8a9b0c1-8dac-40d5-8c2d-4e6f8a0c2b7d'; // Humboldt penguin
const ASHA_ID = '5d7a8b9e-2d4c-4a7f-8c6d-8e0f2a4c6b17'; // Sumatran tiger

// Enclosure IDs from enclosure-directory.ts
const SAVANNA = '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70'; // Savanna Paddock
const BIG_CATS = '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81'; // Big Cat Ridge
const PRIMATES = '2d8a4b3c-5e6f-4a01-9c3d-4e5f6a7b8c92'; // Primate Forest
const LAGOON = '3e9b5c4d-6f7a-4b12-8d4e-5f6a7b8c9da3'; // Lagoon Pool
const REPTILES = '4fac6d5e-7a8b-4c23-9e5f-6a7b8c9d0eb4'; // Reptile House
const WETLAND = '5abd7e6f-8b9c-4d34-8f6a-7b8c9d0e1fc5'; // Wetland Marsh
const QUARANTINE = '6bce8f7a-9cad-4e45-9a7b-8c9d0e1f2ad6'; // Quarantine Unit

function hoursAgo(hours: number): string {
  const now = new Date();
  now.setHours(now.getHours() - hours);
  return now.toISOString();
}

function minutesAfter(iso: string, minutes: number): string {
  const date = new Date(iso);
  date.setMinutes(date.getMinutes() + minutes);
  return date.toISOString();
}

function transferMessage(name: string, species: string, fromId: string, toId: string): string {
  return `${name} (${species}) was transferred from enclosure ${fromId} to enclosure ${toId}`;
}

/** Demo notifications seeded with consistent animal data. Spread over ~10 days. */
export const DEMO_NOTIFICATIONS: readonly Notification[] = [
  // Registration 1 (newest, INFO, open)
  {
    id: 'notif-01',
    animalId: LULU_ID,
    eventType: 'ANIMAL_REGISTERED',
    severity: 'INFO',
    message: 'Lulu (Ring-tailed lemur) was registered',
    occurredAt: hoursAgo(2),
    performedBy: 'admin.rossi',
    name: 'Lulu',
    species: 'Ring-tailed lemur',
    dangerous: false,
    previousStatus: null,
    newStatus: null,
    fromEnclosureId: null,
    toEnclosureId: PRIMATES,
    acknowledgedBy: null,
    acknowledgedAt: null,
  },
  // Transfer of dangerous animal Zuri, back from quarantine (open WARNING)
  {
    id: 'notif-02',
    animalId: ZURI_ID,
    eventType: 'ANIMAL_TRANSFERRED',
    severity: 'WARNING',
    message: transferMessage('Zuri', 'African lion', QUARANTINE, BIG_CATS),
    occurredAt: hoursAgo(4),
    performedBy: 'keeper.conti',
    name: 'Zuri',
    species: 'African lion',
    dangerous: true,
    previousStatus: null,
    newStatus: null,
    fromEnclosureId: QUARANTINE,
    toEnclosureId: BIG_CATS,
    acknowledgedBy: null,
    acknowledgedAt: null,
  },
  // Status change to DECEASED for Bruno (open CRITICAL)
  {
    id: 'notif-03',
    animalId: BRUNO_ID,
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity: 'CRITICAL',
    message: 'Bruno (Aldabra giant tortoise) status changed from HEALTHY to DECEASED',
    occurredAt: hoursAgo(6),
    performedBy: 'vet.bianchi',
    name: 'Bruno',
    species: 'Aldabra giant tortoise',
    dangerous: false,
    previousStatus: 'HEALTHY',
    newStatus: 'DECEASED',
    fromEnclosureId: null,
    toEnclosureId: null,
    acknowledgedBy: null,
    acknowledgedAt: null,
  },
  // Status change to IN_TREATMENT for Sibilla (acknowledged WARNING)
  {
    id: 'notif-04',
    animalId: SIBILLA_ID,
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity: 'WARNING',
    message: 'Sibilla (Green anaconda) status changed from HEALTHY to IN_TREATMENT',
    occurredAt: hoursAgo(24),
    performedBy: 'vet.bianchi',
    name: 'Sibilla',
    species: 'Green anaconda',
    dangerous: true,
    previousStatus: 'HEALTHY',
    newStatus: 'IN_TREATMENT',
    fromEnclosureId: null,
    toEnclosureId: null,
    acknowledgedBy: 'vet.bianchi',
    acknowledgedAt: minutesAfter(hoursAgo(24), 15),
  },
  // Transfer non-dangerous animal Onda (acknowledged INFO)
  {
    id: 'notif-05',
    animalId: ONDA_ID,
    eventType: 'ANIMAL_TRANSFERRED',
    severity: 'INFO',
    message: transferMessage('Onda', 'Harbour seal', WETLAND, LAGOON),
    occurredAt: hoursAgo(36),
    performedBy: 'keeper.conti',
    name: 'Onda',
    species: 'Harbour seal',
    dangerous: false,
    previousStatus: null,
    newStatus: null,
    fromEnclosureId: WETLAND,
    toEnclosureId: LAGOON,
    acknowledgedBy: 'keeper.conti',
    acknowledgedAt: minutesAfter(hoursAgo(36), 8),
  },
  // Status change to UNDER_OBSERVATION for Nia (open WARNING)
  {
    id: 'notif-06',
    animalId: NIA_ID,
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity: 'WARNING',
    message: 'Nia (Plains zebra) status changed from HEALTHY to UNDER_OBSERVATION',
    occurredAt: hoursAgo(48),
    performedBy: 'vet.bianchi',
    name: 'Nia',
    species: 'Plains zebra',
    dangerous: false,
    previousStatus: 'HEALTHY',
    newStatus: 'UNDER_OBSERVATION',
    fromEnclosureId: null,
    toEnclosureId: null,
    acknowledgedBy: null,
    acknowledgedAt: null,
  },
  // Registration 2 for Pepe (acknowledged INFO)
  {
    id: 'notif-07',
    animalId: PEPE_ID,
    eventType: 'ANIMAL_REGISTERED',
    severity: 'INFO',
    message: 'Pepe (Ring-tailed lemur) was registered',
    occurredAt: hoursAgo(216),
    performedBy: 'admin.rossi',
    name: 'Pepe',
    species: 'Ring-tailed lemur',
    dangerous: false,
    previousStatus: null,
    newStatus: null,
    fromEnclosureId: null,
    toEnclosureId: PRIMATES,
    acknowledgedBy: 'admin.rossi',
    acknowledgedAt: minutesAfter(hoursAgo(216), 5),
  },
  // Status change to UNDER_OBSERVATION for Blu (acknowledged WARNING)
  {
    id: 'notif-08',
    animalId: BLU_ID,
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity: 'WARNING',
    message: 'Blu (Humboldt penguin) status changed from HEALTHY to UNDER_OBSERVATION',
    occurredAt: hoursAgo(96),
    performedBy: 'vet.bianchi',
    name: 'Blu',
    species: 'Humboldt penguin',
    dangerous: false,
    previousStatus: 'HEALTHY',
    newStatus: 'UNDER_OBSERVATION',
    fromEnclosureId: null,
    toEnclosureId: null,
    acknowledgedBy: 'vet.bianchi',
    acknowledgedAt: minutesAfter(hoursAgo(96), 10),
  },
  // Status change to IN_TREATMENT for Pepe (acknowledged WARNING)
  {
    id: 'notif-09',
    animalId: PEPE_ID,
    eventType: 'ANIMAL_STATUS_CHANGED',
    severity: 'WARNING',
    message: 'Pepe (Ring-tailed lemur) status changed from HEALTHY to IN_TREATMENT',
    occurredAt: hoursAgo(120),
    performedBy: 'vet.bianchi',
    name: 'Pepe',
    species: 'Ring-tailed lemur',
    dangerous: false,
    previousStatus: 'HEALTHY',
    newStatus: 'IN_TREATMENT',
    fromEnclosureId: null,
    toEnclosureId: null,
    acknowledgedBy: 'vet.bianchi',
    acknowledgedAt: minutesAfter(hoursAgo(120), 20),
  },
  // Transfer of Asha dangerous tiger (acknowledged WARNING)
  {
    id: 'notif-10',
    animalId: ASHA_ID,
    eventType: 'ANIMAL_TRANSFERRED',
    severity: 'WARNING',
    message: transferMessage('Asha', 'Sumatran tiger', QUARANTINE, BIG_CATS),
    occurredAt: hoursAgo(144),
    performedBy: 'keeper.conti',
    name: 'Asha',
    species: 'Sumatran tiger',
    dangerous: true,
    previousStatus: null,
    newStatus: null,
    fromEnclosureId: QUARANTINE,
    toEnclosureId: BIG_CATS,
    acknowledgedBy: 'keeper.conti',
    acknowledgedAt: minutesAfter(hoursAgo(144), 12),
  },
];
