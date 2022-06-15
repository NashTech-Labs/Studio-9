import { Component, HostBinding, HostListener, OnDestroy, OnInit } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Params, Router } from '@angular/router';

import { forkJoin } from 'rxjs/observable/forkJoin';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { HasUnsavedData } from '../core/core.interface';
import { mocksMode } from '../core/core.mocks-only';
import { TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';
import { ReactiveLoader } from '../utils/reactive-loader';

import { Pipeline, PipelineOperator } from './pipeline.interfaces';
import { PipelineService } from './pipeline.service';

@Component({
  selector: 'app-pipeline',
  template: `
    <app-spinner [visibility]="_loader.active | async"></app-spinner>

    <ng-container *ngIf="_loader.loaded || isCreateMode">
      <asset-operations
        [type]="config.asset.values.PIPELINE"
        [selectedItems]="pipeline ? [pipeline] : []"
        (onDelete)="_onPipelineDeleted()"
      >
        <h2>{{pipeline?.name}}</h2>
      </asset-operations>

      <div class="row">
        <div class="col-md-6">
          <app-input
            [label]="'Pipeline Name'"
            [control]="form.controls['name']"
            [disabled]="!isEditMode"
          ></app-input>

          <app-description
            [label]="'Description'"
            [control]="form.controls['description']"
            [disabled]="!isEditMode"
            [editMode]="true"
          ></app-description>
        </div>

        <div class="col-md-6 pull-right">
          <div class="form-group p0">
            <button *ngIf="isEditMode && !pipeline"
              class="btn btn-success"
              [disabled]="form.invalid || form.pristine || form.disabled || (_savingObserver.active | async)"
              (click)="create()"
            >
              Create
            </button>

            <button *ngIf="isEditMode && pipeline"
              class="btn btn-primary"
              [disabled]="form.invalid || form.pristine || form.disabled || (_savingObserver.active | async)"
              (click)="update()"
            >
              Update&nbsp;<i class="glyphicon glyphicon-ok"></i>
            </button>

            <button
              *ngIf="isEditMode"
              class="btn btn-secondary"
              (click)="navigateToView()"
            >
              Cancel
            </button>

          </div>
        </div>
      </div>

      <app-pipeline-canvas
        [steps]="pipeline?.steps || []"
        [isEditMode]="isEditMode"
        [availableOperators]="availableOperators"
        (canvasUpdated)="_onCanvasUpdated($event)"
      ></app-pipeline-canvas>
    </ng-container>
  `,
})
export class PipelineComponent implements OnInit, OnDestroy, HasUnsavedData {
  @HostBinding('class') readonly _cssClass = 'app-spinner-box';

  readonly config = config;
  pipeline: Pipeline;
  availableOperators: PipelineOperator[] = [];
  isEditMode: boolean = false;
  isCreateMode: boolean = false;

  form: AppFormGroup<{
    name: FormControl;
    description: FormControl,
    steps: FormControl,
  }>;

  protected readonly _loader: ReactiveLoader<[Pipeline, PipelineOperator[]], TObjectId>;
  protected readonly _savingObserver = new ActivityObserver();

  private _eventsSubscription: Subscription;
  private _subscriptions: Subscription[] = [];
  private _routeDataSubscription: Subscription;

  constructor(
    private _pipelineService: PipelineService,
    private _activatedRoute: ActivatedRoute,
    private _router: Router,
    private _events: EventService,
  ) {
    this._loader = new ReactiveLoader((id: TObjectId) => {
      return forkJoin(this._pipelineService.get(id), this._pipelineService.listAllOperators());
    });
    const loaderSubscription = this._loader.subscribe(this._onPipelineLoaded.bind(this));
    this._subscriptions.push(loaderSubscription);

    this._initForm();

    this._routeDataSubscription = this._activatedRoute.data.subscribe(data => {
      this.isEditMode = !!data.edit;
    });
  }

  ngOnInit(): void {
    const paramsSubscription = this._activatedRoute.params.subscribe((params: Params) => {
      if ('pipelineId' in params) {
        this._loader.load(params['pipelineId']);
      } else {
        this.isCreateMode = true;
      }
    });

    this._subscriptions.push(paramsSubscription);

    this._eventsSubscription = this._events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_PIPELINE && this.pipeline && this.pipeline.id === event.data.id) {
        this._onPipelineDeleted();
      }
    });
  }

  @HostListener('window:beforeunload', ['$event'])
  public onPageUnload($event: BeforeUnloadEvent) {
    if (this.hasUnsavedData()) {
      $event.returnValue = true;
    }
  }

  hasUnsavedData(): boolean {
    return this.form && this.form.dirty;
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }

  _onPipelineLoaded([pipeline, operators]: [Pipeline, PipelineOperator[]]): void {
    if (mocksMode) {
      console.log('Pipeline loaded:', pipeline);
    }

    this.pipeline = pipeline;
    this.form.reset(pipeline);

    this.availableOperators = operators;
  }

  _onPipelineDeleted(): void {
    this._router.navigate(['/desk', 'pipelines']);
  }

  _onCanvasUpdated(steps: Pipeline.Step[]): void {
    this.form.controls['steps'].setValue(steps);
    this.form.markAsDirty();
  }

  navigateToView(): void {
    const base = ['/desk', 'pipelines'];
    this._router.navigate(this.pipeline ? base.concat(this.pipeline.id) : base);
  }

  create(): void {
    const pipeline = this.form.value;

    const save$ = this._pipelineService.create(pipeline)
      .do((savedPipeline: Pipeline) => {
        this.form.reset();
        this._router.navigate(['/desk', 'pipelines', savedPipeline.id]);
      });

    this._savingObserver.observe(save$);
  }

  update(): void {
    const pipeline = this.form.value;

    const save$ = this._pipelineService.update(this.pipeline.id, pipeline)
      .do((savedPipeline: Pipeline) => {
        this.form.reset();
        this._router.navigate(['/desk', 'pipelines', savedPipeline.id]);
      });

    this._savingObserver.observe(save$);
  }

  private _initForm(): void {
    this.form = new AppFormGroup({
      name: new FormControl('', Validators.required),
      description: new FormControl(''),
      steps: new FormControl([]),
    });
  }
}
