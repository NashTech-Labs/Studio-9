import { Component, HostBinding, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { ISubscription, Subscription } from 'rxjs/Subscription';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IS9Project } from './s9-project.interfaces';
import { S9ProjectService } from './s9-project.service';
import { PackageOperationsComponent } from './package-operations.component';
import { IPackage } from './package.interfaces';

@Component({
  selector: 's9-project-packages',
  template: `
    <package-operations
      #packageOperations
      [hidden]="!selectedPackages.length"
      [(selectedItems)]="selectedPackages"
      (deleted)="onPackageDeleted()"
    ></package-operations>
    <asset-operations
      [hidden]="!!selectedPackages.length"
      [type]="config.asset.values.S9_PROJECT"
      [selectedItems]="[s9Project]"
      (onDelete)="onProjectDeleted()"
    >
      <h2 style="padding: 0; margin-top: 0;">
        {{s9Project?.name}}
      </h2>
    </asset-operations>

    <app-spinner [visibility]="(isLoading$ | async)"></app-spinner>

    <s9-project-session [s9Project]="s9Project"></s9-project-session>

    <process-indicator [process]="s9ProjectProcess"></process-indicator>

    <packages-list
      *ngIf="s9Project"
      [(selectedItems)]="selectedPackages"
      [searchParams]="{s9ProjectId: s9Project.id}"
      (onDelete)="onDeletePackage()"
    ></packages-list>
  `,
})
export class S9ProjectPackagesComponent implements OnInit, OnDestroy {
  @HostBinding('class') _cssClass = 'app-spinner-box';
  @ViewChild('packageOperations') packageOperations: PackageOperationsComponent;

  s9Project: IS9Project;
  selectedPackages: IPackage[] = [];
  s9ProjectProcess: IProcess;
  readonly config = config;
  protected _projectLoader: ReactiveLoader<IS9Project, TObjectId>;
  private _subscription: Subscription = new Subscription();
  private _processSubscription: ISubscription;

  constructor(
    protected s9Projects: S9ProjectService,
    private _router: Router,
    private _route: ActivatedRoute,
    private _processService: ProcessService,
    private readonly _eventService: EventService,
  ) {
    this._projectLoader = new ReactiveLoader(projectId => this.s9Projects.get(projectId));

    this._subscription.add(this._projectLoader.subscribe(s9Project => {
      this.s9Project = s9Project;
      this._handleProcessUpdates(s9Project);
    }));

    this._subscription.add(this._eventService.subscribe((event: IEvent) => {
      if (event.type === IEvent.Type.UPDATE_S9_PROJECT && this.s9Project && event.data.id === this.s9Project.id) {
        this._projectLoader.load(this.s9Project.id);
      }
      if (event.type === IEvent.Type.DELETE_S9_PROJECT && this.s9Project && this.s9Project.id === event.data.id) {
        this.onProjectDeleted();
      }
    }));
  }

  get isLoading$(): Observable<boolean> {
    return this._projectLoader.active;
  }

  ngOnInit(): void {
    this._subscription.add(this._route.params.subscribe((params) => {
      const projectId = params['projectId'];
      if (projectId) {
        this._projectLoader.load(projectId);
      } else {
        delete this.s9Project;
      }
    }));
  }

  ngOnDestroy(): void {
    this._subscription.unsubscribe();
    this._processSubscription && this._processSubscription.unsubscribe();
  }

  onProjectDeleted(): void {
    this._router.navigate(['/desk', 'develop', 'projects']);
  }

  onPackageDeleted(): void {
    this.selectedPackages = [];
  }

  onDeletePackage(): void {
    this.packageOperations.trash();
  }

  private _handleProcessUpdates(s9Project: IS9Project): void {
    this._processSubscription && this._processSubscription.unsubscribe();
    this._processSubscription = this.s9Projects.getActiveProcess(s9Project)
      .do(_ => this.s9ProjectProcess = _)
      .filter(Boolean)
      .flatMap(_ => this._processService.observe(_))
      .subscribe(() => this._projectLoader.load(s9Project.id));
  }
}
