import { Component, computed, inject, signal } from '@angular/core';
import { AnimalStore } from '../../../core/data/animal-store';
import { Animal, AnimalStatus, Enclosure, STATUS_ORDER } from '../../../core/models/animal';
import { STATUS_LABELS } from '../../../core/models/labels';
import { Icon } from '../../../core/ui/icon/icon';
import { AnimalPlate } from '../animal-plate/animal-plate';
import { EnclosureSign } from '../enclosure-sign/enclosure-sign';

type StatusFilter = 'ALL' | AnimalStatus;

interface EnclosureGroup {
  readonly enclosure: Enclosure;
  readonly animals: readonly Animal[];
}

@Component({
  selector: 'app-animal-list',
  imports: [AnimalPlate, EnclosureSign, Icon],
  templateUrl: './animal-list.html',
  styleUrl: './animal-list.scss',
})
export class AnimalList {
  protected readonly store = inject(AnimalStore);

  protected readonly query = signal('');
  protected readonly filter = signal<StatusFilter>('ALL');
  protected readonly statusLabels = STATUS_LABELS;

  protected readonly filters = computed(() => {
    const animals = this.store.animals();
    return [
      { value: 'ALL' as StatusFilter, label: 'All', count: animals.length },
      ...STATUS_ORDER.map((status) => ({
        value: status as StatusFilter,
        label: STATUS_LABELS[status].short,
        count: animals.filter((a) => a.status === status).length,
      })),
    ];
  });

  protected readonly visible = computed(() => {
    const q = this.query().trim().toLowerCase();
    const filter = this.filter();
    return this.store.animals().filter((a) => {
      const matchesStatus = filter === 'ALL' || a.status === filter;
      const matchesQuery =
        !q ||
        a.name.toLowerCase().includes(q) ||
        a.species.toLowerCase().includes(q) ||
        a.id.slice(0, 4).toLowerCase() === q.replace('#', '');
      return matchesStatus && matchesQuery;
    });
  });

  protected readonly groups = computed<EnclosureGroup[]>(() => {
    const visible = this.visible();
    const rank = (a: Animal) => (a.status === 'DECEASED' ? 1 : 0);
    return this.store
      .enclosures()
      .map((enclosure) => ({
        enclosure,
        animals: visible
          .filter((a) => a.enclosureId === enclosure.id)
          .sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name)),
      }))
      .filter((group) => group.animals.length > 0);
  });

  protected readonly occupiedEnclosures = computed(
    () => new Set(this.store.animals().map((a) => a.enclosureId)).size,
  );

  protected readonly skeleton = [[1, 2, 3, 4], [1, 2, 3], [1, 2]];

  protected readonly emptyMessage = computed(() => {
    const q = this.query().trim();
    const filter = this.filter();
    const status = filter === 'ALL' ? '' : STATUS_LABELS[filter].label.toLowerCase();
    const subject = q && status ? `“${q}” with status ${status}` : q ? `“${q}”` : `status ${status}`;
    return `No animal fits ${subject}. Search matches names, species and 4-character tag codes.`;
  });

  protected readonly filtered = computed(
    () => this.query().trim() !== '' || this.filter() !== 'ALL',
  );

  constructor() {
    void this.store.load();
  }

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  protected clear(): void {
    this.query.set('');
    this.filter.set('ALL');
  }
}
