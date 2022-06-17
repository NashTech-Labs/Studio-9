import { Component, HostBinding, OnDestroy, OnInit } from '@angular/core';
import {
  FormControl,
  FormGroup,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ActivityObserver } from '../utils/activity-observer';
import { ReactiveLoader } from '../utils/reactive-loader';

import {
  IS9Project,
  IS9ProjectUpdate,
} from './s9-project.interfaces';
import { S9ProjectService } from './s9-project.service';

@Component({
  selector: 's9-project-view',
  template: `
    <asset-operations
      [type]="config.asset.values.S9_PROJECT"
      [selectedItems]="[s9Project]"
      (onDelete)="onProjectDeleted()"
    >
      <h2 class="asset-view-title">
        {{s9Project?.name}}
      </h2>
    </asset-operations>

    <app-spinner [visibility]="_projectLoader.active | async"></app-spinner>

    <s9-project-session
      [s9Project]="s9Project"
    ></s9-project-session>

    <div class="row brand-tab">
      <div class="col-md-6">
        <app-input
          [label]="'Name'"
          [control]="form.controls['name']"
        ></app-input>

        <app-description
          [control]="form.controls['description']"
        ></app-description>
      </div>

      <div class="col-md-6">
        <div class="btn-group pull-right">
          <button type="button" class="btn btn-primary"
            [disabled]="form.invalid || form.pristine || form.disabled || (_savingObserver.active | async)"
            (click)="saveProject()">
            Update&nbsp;<i class="glyphicon glyphicon-ok"></i></button>
        </div>
      </div>
    </div>
  `,
})
export class S9ProjectViewComponent implements OnInit, OnDestroy {
  @HostBinding('class') _cssClass = 'app-spinner-box';

  readonly config = config;

  s9Project: IS9Project = null;
  s9ProjectProcess: IProcess;

  form: FormGroup;

  protected readonly _savingObserver = new ActivityObserver();
  protected readonly _projectLoader: ReactiveLoader<IS9Project, TObjectId>;
  private _subscriptions: Subscription[] = [];
  private _isSessionTabAutoOpenEnabled = false;

  private processSubscription: Subscription;

  constructor(
    private readonly _router: Router,
    private readonly _route: ActivatedRoute,
    private readonly _processService: ProcessService,
    private readonly _eventService: EventService,
    private readonly _s9ProjectService: S9ProjectService,
  ) {
    this._projectLoader = new ReactiveLoader(projectId => {
      this._isSessionTabAutoOpenEnabled = false;
      return this._s9ProjectService.get(projectId);
    });
    this._subscriptions.push(this._projectLoader.subscribe(s9Project => {
      this.setS9Project(s9Project);
      this.s9Project = s9Project;
    }));

    this._subscriptions.push(this._eventService.subscribe((event: IEvent) => {
      if (event.type === IEvent.Type.UPDATE_S9_PROJECT && this.s9Project && event.data.id === this.s9Project.id) {
        this._projectLoader.load(this.s9Project.id);
      }
      if (event.type === IEvent.Type.DELETE_S9_PROJECT && this.s9Project && this.s9Project.id === event.data.id) {
        this.onProjectDeleted();
      }
    }));

    this._initForm();
  }

  ngOnInit(): void {
    const paramsSubscription = this._route.params
      .subscribe((params) => {
        const projectId = params['projectId'];
        this.s9Project = null;
        if (projectId) {
          this._projectLoader.load(projectId);
        }
      });

    this._subscriptions.push(paramsSubscription);
  }

  ngOnDestroy(): void {
    this.processSubscription && this.processSubscription.unsubscribe();
    this._subscriptions.forEach((_) => _.unsubscribe());
  }

  setS9Project(s9Project: IS9Project) {
    this.s9Project = s9Project;
    this.form.reset(s9Project);

    this.processSubscription && this.processSubscription.unsubscribe();
    this.processSubscription = this._s9ProjectService.getActiveProcess(s9Project)
      .do(process => {
        this.s9ProjectProcess = process;
      })
      .filter(_ => !!_)
      .flatMap(process => {
        return this._processService.observe(process);
      })
      .subscribe(() => {
        this._projectLoader.load(s9Project.id);
      });
  }

  saveProject(): void {
    const update = {
      name: this.form.controls['name'].value,
      description: this.form.controls['description'].value,
    } as IS9ProjectUpdate;

    this._savingObserver.observe(this._s9ProjectService.update(this.s9Project.id, update));
  }

  onProjectDeleted(): void {
    this._router.navigate(['/desk', 'develop', 'projects']);
  }

  private _initForm(): void {
    this.form = new FormGroup({
      name: new FormControl(''),
      description: new FormControl(''),
    });
  }
}
