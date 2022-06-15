import {
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { ISubscription, Subscription } from 'rxjs/Subscription';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { deployConfig } from './deploy.config';
import { IScriptDeployment } from './script-deployment.interface';
import { ScriptDeploymentService } from './script-deployment.service';


@Component({
  selector: 'deploy-script-deployment-view',
  template: `
    <asset-operations
      [type]="config.asset.values.SCRIPT_DEPLOYMENT"
      [selectedItems]="[scriptDeployment]"
      (onDelete)="onDelete()"
    ></asset-operations>

    <app-spinner [visibility]="!scriptDeployment"></app-spinner>

    <ng-container *ngIf="scriptDeployment">
      <form [formGroup]="form" (ngSubmit)="form.valid && submit()">
        <div class="row">
          <div class="col-md-6">
            <app-input
              [label]="'Deployment Name'"
              [control]="form.controls.name"
            ></app-input>
          </div>
          <div class="col-md-6">
            <div class="pull-right">
              <button
                class="btn btn-md btn-apply"
                type="button"
                (click)="download()"
                [disabled]="scriptDeployment.status !== sdStatus.READY  || (isSaving$ | async)"
              >
                Download
              </button>

              <button
                class="btn btn-md btn-apply"
                type="submit"
                [disabled]="form.invalid || form.disabled || form.pristine || (isSaving$ | async)"
              >
                Update
              </button>
            </div>
          </div>
        </div>
        <div class="row">
          <div class="col-md-6">
            <app-description [control]="form.controls.description"></app-description>
          </div>
        </div>
        <div class="row">
          <div class="col-md-6">
            <app-input
              [label]="'Pipeline'"
              [value]="deployConfig.scriptDeployment.mode.labels[scriptDeployment.mode]"
              [disabled]="true"
            ></app-input>
          </div>
          <div class="col-md-6">
            <app-input
              [label]="'Hardware'"
              [value]="deployConfig.scriptDeployment.hardwareMode.labels[scriptDeployment.hardwareMode]"
              [disabled]="true"
            ></app-input>
          </div>
        </div>
      </form>
      <app-form-group [caption]="'Pipeline params'">
        <deploy-script-deployment-pipeline-params
          [mode]="scriptDeployment.mode"
          [disabled]="true"
          [params]="scriptDeployment.params"
        ></deploy-script-deployment-pipeline-params>
      </app-form-group>

      <process-indicator [process]="sdProcess"></process-indicator>
    </ng-container>
  `,
})
export class ScriptDeploymentViewComponent implements OnInit, OnDestroy {
  scriptDeployment: IScriptDeployment;
  readonly config = config;
  readonly deployConfig = deployConfig;
  readonly sdStatus = IScriptDeployment.Status;
  readonly _savingObserver = new ActivityObserver();
  readonly form: AppFormGroup<{
    name: FormControl;
    description: FormControl;
  }>;
  readonly _loader: ReactiveLoader<IScriptDeployment, TObjectId>;

  sdProcess: IProcess;

  private _subscription = new Subscription();
  private _processSubscription: ISubscription;


  get isSaving$(): Observable<boolean> {
    return this._savingObserver.active;
  }

  constructor(
    private _route: ActivatedRoute,
    private _router: Router,
    private _sdService: ScriptDeploymentService,
    private _eventService: EventService,
    private _processService: ProcessService,
  ) {
    this.form = new AppFormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
    });

    this._loader = new ReactiveLoader((id: TObjectId) => this._sdService.get(id));

    this._loader.subscribe((sd: IScriptDeployment) => {
      this.scriptDeployment = sd;

      this._processSubscription && this._processSubscription.unsubscribe();
      this._processSubscription = this._sdService.getActiveProcess(sd)
        .do(process => this.sdProcess = process)
        .filter(Boolean)
        .flatMap(process => this._processService.observe(process))
        .subscribe(() => this._loader.load(sd.id));

      this._initForm(sd);
      MiscUtils.fillForm(this.form, sd);
    });
  }

  ngOnInit(): void {
    this._subscription.add(this._route.params.subscribe(params => {
      this._loader.load(params['itemId']);
    }));

    this._subscription.add(this._eventService.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_SCRIPT_DEPLOYMENT
        && this.scriptDeployment
        && this.scriptDeployment.id === event.data.id
      ) {
        this.onDelete();
      }
    }));
  }

  ngOnDestroy(): void {
    this._subscription.unsubscribe();
    this._processSubscription && this._processSubscription.unsubscribe();
  }

  submit(): void {
    this._savingObserver
      .observe(this._sdService.update(this.scriptDeployment.id, this.form.value))
      .subscribe((sd: IScriptDeployment) => {
        this.form.reset(sd);
      });
  }

  download(): void {
    this._savingObserver.observe(this._sdService.download(this.scriptDeployment.id));
  }

  onDelete(): void {
    this._router.navigate(['/desk', 'deploy', 'create']);
  }

  private _initForm(sd: IScriptDeployment): void {
    this.form.reset(sd);
  }
}
