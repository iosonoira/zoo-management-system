import { Enclosure } from '../models/animal';

/**
 * Enclosures exist in the backend only as UUIDs on `animals.enclosure_id`. Until an
 * enclosure resource exists, their names and habitats live here and both the mock and
 * the HTTP adapter read from this one list. The dev seed migration uses the same ids.
 */
export const ENCLOSURES: readonly Enclosure[] = [
  { id: '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70', name: 'Savanna Paddock', habitat: 'TERRESTRIAL' },
  { id: '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81', name: 'Big Cat Ridge', habitat: 'TERRESTRIAL' },
  { id: '2d8a4b3c-5e6f-4a01-9c3d-4e5f6a7b8c92', name: 'Primate Forest', habitat: 'TERRESTRIAL' },
  { id: '3e9b5c4d-6f7a-4b12-8d4e-5f6a7b8c9da3', name: 'Lagoon Pool', habitat: 'AQUATIC' },
  { id: '4fac6d5e-7a8b-4c23-9e5f-6a7b8c9d0eb4', name: 'Reptile House', habitat: 'AMPHIBIOUS' },
  { id: '5abd7e6f-8b9c-4d34-8f6a-7b8c9d0e1fc5', name: 'Wetland Marsh', habitat: 'AMPHIBIOUS' },
  { id: '6bce8f7a-9cad-4e45-9a7b-8c9d0e1f2ad6', name: 'Quarantine Unit', habitat: 'TERRESTRIAL' },
];
