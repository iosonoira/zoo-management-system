import { Component, computed, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RouterLink, provideRouter } from '@angular/router';
import { ActivityStore } from '../../../core/data/activity-store';
import { AnimalStore } from '../../../core/data/animal-store';
import { ENCLOSURES } from '../../../core/data/enclosure-directory';
import { Animal, ZooRole } from '../../../core/models/animal';
import { can } from '../../../core/models/permissions';
import { Session } from '../../../core/session/session';
import { Icon } from '../../../core/ui/icon/icon';
import { EnclosureSign } from '../enclosure-sign/enclosure-sign';
import { StatusTrack } from '../status-track/status-track';
import { AnimalDetail } from './animal-detail';

// The sections load and render on their own, and have their own specs: here they are labelled
// stand-ins, so the page’s order and its wiring can be read without their stores.
@Component({ selector: 'app-activity-section', template: '<h2>Activity</h2>' })
class ActivityStub {
  readonly animal = input.required<Animal>();
  readonly announce = output<string>();
}

@Component({ selector: 'app-feeding-section', template: '<h2>Feeding</h2>' })
class FeedingStub {
  readonly animal = input.required<Animal>();
  readonly announce = output<string>();
}

@Component({ selector: 'app-health-section', template: '<h2>Health</h2>' })
class HealthStub {
  readonly animal = input.required<Animal>();
  readonly announce = output<string>();
}

@Component({ selector: 'app-status-sheet', template: '' })
class StatusSheetStub {
  readonly animal = input.required<Animal>();
  readonly changed = output<Animal>();
}

@Component({ selector: 'app-transfer-sheet', template: '' })
class TransferSheetStub {
  readonly animal = input.required<Animal>();
  readonly moved = output<Animal>();
}

const ZURI: Animal = {
  id: 'zuri',
  name: 'Zuri',
  species: 'African lion',
  dangerous: true,
  habitat: 'TERRESTRIAL',
  enclosureId: ENCLOSURES[1].id,
  arrivalDate: '2026-02-14',
  status: 'HEALTHY',
  createdBy: 'admin.rossi',
  updatedBy: 'vet.bianchi',
};

describe('AnimalDetail', () => {
  async function setUp(role: ZooRole) {
    const animals = signal<Animal>(ZURI);
    const animalStore = {
      state: signal('ready'),
      byId: () => animals(),
      enclosureById: computed(() => new Map(ENCLOSURES.map((e) => [e.id, e]))),
      enclosureName: (id: string) => ENCLOSURES.find((e) => e.id === id)?.name ?? id,
      load: vi.fn(async () => {}),
      retry: vi.fn(async () => {}),
      apply: (animal: Animal) => animals.set(animal),
    };
    const activity = { refresh: vi.fn(async () => {}) };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AnimalStore, useValue: animalStore },
        { provide: ActivityStore, useValue: activity },
        {
          provide: Session,
          useValue: {
            role: signal(role),
            username: signal('keeper.conti'),
            can: (permission: Parameters<typeof can>[1]) => can(role, permission),
          },
        },
      ],
    });
    TestBed.overrideComponent(AnimalDetail, {
      set: {
        imports: [
          RouterLink,
          DatePipe,
          Icon,
          EnclosureSign,
          StatusTrack,
          ActivityStub,
          FeedingStub,
          HealthStub,
          StatusSheetStub,
          TransferSheetStub,
        ],
      },
    });
    const fixture = TestBed.createComponent(AnimalDetail);
    fixture.componentRef.setInput('id', ZURI.id);
    fixture.detectChanges();
    await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement;
    return {
      fixture,
      root,
      activity,
      animalStore,
      /** The sections in reading order, by their heading. */
      order: () =>
        Array.from(root.querySelectorAll('.sections > *')).map(
          (el) => el.querySelector('h2')!.textContent!.trim(),
        ),
    };
  }

  it('puts Activity first for a keeper, before Feeding', async () => {
    const { order } = await setUp('zoo-keeper');
    expect(order()).toEqual(['Activity', 'Feeding', 'Status', 'Location', 'Health', 'Record']);
  });

  it('puts Activity first for a vet, before Status and Location', async () => {
    const { order } = await setUp('zoo-vet');
    expect(order()).toEqual(['Activity', 'Status', 'Location', 'Health', 'Feeding', 'Record']);
  });

  it('puts Activity first for an admin, before Status and Location', async () => {
    const { order } = await setUp('zoo-admin');
    expect(order()).toEqual(['Activity', 'Status', 'Location', 'Health', 'Feeding', 'Record']);
  });

  it('gives Activity a row of its own, below the hero', async () => {
    const { root } = await setUp('zoo-vet');
    const activity = root.querySelector('app-activity-section')!;
    expect(activity.classList.contains('lead')).toBe(true);
    expect(activity.parentElement!.classList.contains('sections')).toBe(true);
    expect(
      root.querySelector('.hero')!.compareDocumentPosition(activity) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('reloads Activity and the bell after a status change from this page', async () => {
    const { fixture, activity } = await setUp('zoo-vet');
    expect(activity.refresh).not.toHaveBeenCalled();

    fixture.debugElement
      .query(By.directive(StatusSheetStub))
      .componentInstance.changed.emit({ ...ZURI, status: 'IN_TREATMENT' });

    expect(activity.refresh).toHaveBeenCalledTimes(1);
  });

  it('reloads Activity and the bell after a transfer from this page', async () => {
    const { fixture, activity } = await setUp('zoo-keeper');
    fixture.debugElement
      .query(By.directive(TransferSheetStub))
      .componentInstance.moved.emit({ ...ZURI, enclosureId: ENCLOSURES[0].id });

    expect(activity.refresh).toHaveBeenCalledTimes(1);
  });

  it('passes Activity’s confirmations on to the page’s live region', async () => {
    const { fixture, root } = await setUp('zoo-keeper');
    fixture.debugElement
      .query(By.directive(ActivityStub))
      .componentInstance.announce.emit('Acknowledged by keeper.conti. Zuri is taken in charge.');
    fixture.detectChanges();
    expect(root.querySelector('.toast.show')!.textContent).toContain('Zuri is taken in charge.');
  });
});
