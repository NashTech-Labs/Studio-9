import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IOnlineTriggeredJob } from './online-job.interface';
import { OnlineJobService } from './online-job.service';

@Component({
  selector: 'deploy-online-job-view',
  template: `
    <asset-operations [type]="config.asset.values.ONLINE_JOB" [selectedItems]="[job]" (onDelete)="onDelete()"></asset-operations>
    <app-spinner [visibility]="!job"></app-spinner>
    <ng-template [ngIf]="job && _jobLoader.loaded">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Online Job Name'" [control]="form.controls['name']"></app-input>
        </div>
        <div class="col-md-4">
          <app-check
            [label]="'Enabled'"
            [value]="true"
            [control]="form.controls.enabled"
          ></app-check>
        </div>
        <div class="col-md-2">
          <div class="pull-right">
            <button class="btn btn-md btn-apply" (click)="_savingObserver.observe(submit())"
              [disabled]="form.invalid || (_savingObserver.active | async)">
              Update
            </button>
          </div>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-description [control]="form.controls['description']"></app-description>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <library-selector
            [inputLabel]="'Target'"
            [value]="{id: job.target.id, entity: job.target.type}"
            [disabled]="true"
            [available]="[config.asset.values.CV_MODEL]"
          ></library-selector>
        </div>
        <div class="col-md-12" [ngSwitch]="job.target.type">
          <deploy-online-job-cv-options *ngSwitchCase="config.asset.values.CV_MODEL"
            [modelId]="job.target.id"
            [options]="job.options"
            [disabled]="true"
          ></deploy-online-job-cv-options>
        </div>
      </div>
    </ng-template>
  `,
})
export class OnlineJobViewComponent implements OnInit, OnDestroy {
  readonly config = config;
  readonly _savingObserver = new ActivityObserver();
  readonly form: AppFormGroup<{
    name: FormControl;
    description: FormControl;
    enabled: FormControl,
  }>;
  readonly _jobLoader: ReactiveLoader<IOnlineTriggeredJob, TObjectId>;
  job: IOnlineTriggeredJob;
  private routeSubscription: ISubscription;
  private eventsSubscription: ISubscription;

  constructor(
    private jobs: OnlineJobService,
    private route: ActivatedRoute,
    private router: Router,
    private events: EventService,
  ) {
    this.form = new AppFormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      enabled: new FormControl(false),
    });

    this._jobLoader = new ReactiveLoader((jobId) => this.jobs.get(jobId)); //@TODO pagination

    this._jobLoader.subscribe((job: IOnlineTriggeredJob) => {
      this.job = job;
      MiscUtils.fillForm(this.form, job);
    });
  }

  ngOnInit() {
    this.routeSubscription = this.route.params.subscribe(params => {
      this._jobLoader.load(params['onlineJobId']);
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_ONLINE_JOB && this.job.id === event.data.id) {
        this.onDelete();
      }
    });
  }

  ngOnDestroy() {
    this.routeSubscription && this.routeSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }

  submit() {
    return this.jobs.update(this.job.id, this.form.value);
  }

  onDelete() {
    this.router.navigate(['/desk', 'deploy', 'create']);
  }
}
