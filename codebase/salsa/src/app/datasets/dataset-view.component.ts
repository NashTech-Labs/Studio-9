import {
  Component, HostBinding,
  OnDestroy,
  OnInit,
} from '@angular/core';
import {
  FormControl,
  Validators,
} from '@angular/forms';
import {
  ActivatedRoute,
  Router,
} from '@angular/router';

import { of } from 'rxjs/observable/of';
import { map } from 'rxjs/operators/map';
import { takeUntil } from 'rxjs/operators/takeUntil';
import { tap } from 'rxjs/operators/tap';
import { Subject } from 'rxjs/Subject';
import {
  ISubscription,
} from 'rxjs/Subscription';

import config from '../config';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';
import { ReactiveLoader } from '../utils/reactive-loader';

import { BinaryDataset } from './dataset.interfaces';
import { BinaryDatasetService } from './dataset.service';

@Component({
  template: `
    <asset-operations
      *ngIf="!selectedItems.length"
      [type]="'${IAsset.Type.DATASET}'"
      [selectedItems]="[dataset]"
    >
      <app-input
        [control]="searchForm.controls['search']"
        [iconBefore]="'glyphicon-search'"
        [iconAfter]="'glyphicon-remove'"
        (iconAfterClick)="searchForm.controls['search'].setValue('')"
      ></app-input>
    </asset-operations>

    <dataset-file-operations
      *ngIf="selectedItems.length"
      [dataset]="dataset"
      [selectedItems]="selectedItems"
      (selectedItemsChange)="onSelectedItemsChange($event)"
    ></dataset-file-operations>
    <app-spinner [visibility]="loader.active | async"></app-spinner>
    <ng-container *ngIf="loader.loaded && dataset" [ngSwitch]="dataset.status">
      <form
        [formGroup]="form"
        (ngSubmit)="form.valid && submit()"
      >
        <div class="row">
          <div class="col-md-6 col-xs-12">
            <app-input
              label="Name"
              [control]="form.controls['name']"
            ></app-input>

            <app-description
              label="Description"
              [control]="form.controls['description']"
            ></app-description>
          </div>
          <div class="col-md-6 col-xs-12">
            <button
              type="submit"
              class="btn btn-primary pull-right"
              [disabled]="!form.valid || ((savingObserver.active) | async)"
            >
              Update
              <i class="glyphicon glyphicon-ok"></i>
            </button>
          </div>
        </div>
      </form>

      <dataset-files-list
        *ngSwitchCase="config.binaryDataset.status.values.IDLE"
        [dataset]="dataset"
        [searchForm]="searchForm"
        [selectedItems]="selectedItems"
        (selectedItemsChange)="onSelectedItemsChange($event)"
      ></dataset-files-list>

      <process-indicator *ngSwitchDefault
        [process]="datasetProcess">
        <button *ngIf="datasetProcess"
          type="button" class="btn btn-primary"
          (click)="onCancelProcess()">
          Cancel&nbsp;<i class="glyphicon glyphicon-ban-circle"></i>
        </button>
      </process-indicator>

      <error-indicator *ngSwitchCase="config.binaryDataset.status.values.ERROR"
        [process]="datasetProcess" [target]="'dataset'"></error-indicator>
    </ng-container>
  `,
})
export class DatasetViewComponent implements OnInit, OnDestroy {
  @HostBinding('class') _cssClass = 'app-spinner-box';
  config = config;

  protected dataset: BinaryDataset;
  protected selectedItems: BinaryDataset.File[] = [];

  protected datasetProcess: IProcess;
  protected readonly loader: ReactiveLoader<BinaryDataset, TObjectId>;

  protected readonly searchForm = new AppFormGroup({
    search: new FormControl(''),
  });

  protected readonly form = new AppFormGroup({
    name: new FormControl('', Validators.required),
    description: new FormControl(''),
  });
  protected readonly savingObserver = new ActivityObserver();

  private readonly _reloadOn = [
    IEvent.Type.UPDATE_DATASET_LIST,
    IEvent.Type.UPDATE_DATASET,
  ];
  private processSubscription: ISubscription;
  private readonly _destroy$: Subject<boolean> = new Subject<boolean>();

  constructor(
    private datasetService: BinaryDatasetService,
    private route: ActivatedRoute,
    private router: Router,
    private processService: ProcessService,
    private eventService: EventService,
  ) {
    this.loader = new ReactiveLoader(id => datasetService.get(id));
    this.loader.value
      .pipe(takeUntil(this._destroy$))
      .subscribe(dataset => {
        this._setDataset(dataset);
      });

    this._initDatasetLoading();
  }

  ngOnInit(): void {
    this.eventService.observable
      .pipe(takeUntil(this._destroy$))
      .subscribe((event) => {
        if (this._reloadOn.includes(event.type)) {
          this.loader.load(this.dataset.id);
        }
      });
  }

  ngOnDestroy(): void {
    this._destroy$.next(true);
    this._destroy$.complete();
  }

  onCancelProcess(): void {
    if (!this.datasetProcess) {
      return;
    }
    this.processService.cancel(this.datasetProcess);
  }

  submit(): void {
    const save$ = this.datasetService.update(this.dataset.id, this.form.value);

    this.savingObserver
      .observe(save$)
      .pipe(
        takeUntil(this._destroy$),
        tap((dataset) => this.loader.load(dataset.id)),
      ).subscribe();
  }

  protected onSelectedItemsChange(items: BinaryDataset.File[]): void {
    this.selectedItems = items;
  }

  private _initDatasetLoading(): void {
    this.route.params.pipe(
      takeUntil(this._destroy$),
      map(params => params['itemId']),
    ).subscribe(datasetId => {
      if (!datasetId) {
        this._navigateToParentRoute();
      } else {
        this.loader.load(datasetId);
      }
    });
  }

  private _navigateToParentRoute(): void {
    // TODO update to user AssetUrlService after merge with develop
    this.router.navigate(['/desk', 'library', 'datasets', 'all']);
  }

  private _setDataset(dataset: BinaryDataset) {
    this.dataset = dataset;
    this.searchForm.reset();
    this.form.setValue({
      name: dataset.name,
      description: dataset.description || null,
    });

    this.processSubscription && this.processSubscription.unsubscribe();
    this.datasetProcess = null;
    this.processSubscription = this.datasetService.getActiveProcess(dataset)
      .do(process => {
        this.datasetProcess = process;
      })
      .switchMap(process => process ? this.processService.observe(process) : of(null))
      .filter(_ => !!_)
      .subscribe(() => {
        this.loader.load(dataset.id);
      });
  }
}
