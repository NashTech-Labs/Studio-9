import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Subject } from 'rxjs/Subject';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { EventService, IEvent } from '../core/services/event.service';

import { DashboardEditState, DashboardEditStateFactory } from './dashboard-edit-state';
import { IDashboard } from './dashboard.interface';
import { DashboardService } from './dashboard.service';

@Component({
  selector: 'visualize-dashboard-edit',
  template: `
    <asset-operations [type]="config.asset.values.DASHBOARD" [selectedItems]="[state?.dashboard]"
      (onDelete)="_onDashboardDeleted()">
      <h2 style="padding: 0" *ngIf="state" [ngSwitch]="true">
        <ng-template [ngSwitchCase]="!!state.widgetForm || !!state.previewInput">
          <a (click)="state.navigateBack()">{{state.form.value.name || '{New Dashboard}'}}</a>
        </ng-template>
        <ng-template ngSwitchDefault>
          {{state.form.value.name || '{New Dashboard}'}}
        </ng-template>

        <ng-template [ngIf]="!!state.widgetForm">
          / {{state.widgetForm.controls['name'].value}}
        </ng-template>
        <ng-template [ngIf]="!!state.previewInput">
          / {{state.getPreviewAsset().name}} (asset)
        </ng-template>
      </h2>
    </asset-operations>
    <ng-container *ngIf="state" [ngSwitch]="true">
      <chart-edit *ngSwitchCase="!!state.widgetForm"
        [state]="state"
      ></chart-edit>
      <table-view-embed *ngSwitchCase="state.previewInput && state.previewInput.type === config.asset.values.TABLE"
        [adaptiveHeight]="{minHeight: 450}"
        [id]="state.previewInput.id"
      ></table-view-embed>
      <model-view-embed *ngSwitchCase="state.previewInput && state.previewInput.type === config.asset.values.MODEL"
        [adaptiveHeight]="{minHeight: 450}"
        [modelId]="state.previewInput.id"
      ></model-view-embed>
      <visualize-dashboard-edit-layout *ngSwitchDefault
        [state]="state"
      ></visualize-dashboard-edit-layout>
    </ng-container>
  `,
})
export class DashboardEditComponent implements OnDestroy, OnInit {
  readonly config = config;

  state: DashboardEditState;

  private bridge: Subject<DashboardEditState>;
  private eventsSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private dashboards: DashboardService,
    private stateFactory: DashboardEditStateFactory,
    private events: EventService,
  ) {
  }

  ngOnInit() {
    this.route.data.forEach((data: { subject: Subject<DashboardEditState> }) => {
      this.bridge = data.subject;
    });

    this.route.params.forEach(params => {
      if (params['dashboardId']) {
        this.dashboards.get(params['dashboardId']).subscribe((data: IDashboard) => {
          this.bridge.next(this.state = this.stateFactory.createInstance(data));
        });
      } else {
        this.bridge.next(this.state = this.stateFactory.createInstance());
      }
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_DASHBOARD && this.state && this.state.dashboard.id === event.data.id) {
        this._onDashboardDeleted();
      }
    });
  }

  ngOnDestroy() {
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }

  _onDashboardDeleted() {
    this.router.navigate(['/desk', 'visualize']);
  }
}
