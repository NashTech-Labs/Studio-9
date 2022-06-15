import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { EventService, IEvent } from '../core/services/event.service';

import { IDashboard } from './dashboard.interface';
import { DashboardService } from './dashboard.service';

@Component({
  selector: 'app-view-visualize',
  template: `
    <asset-operations [type]="config.asset.values.DASHBOARD" [selectedItems]="[dashboard]"
      (onDelete)="_onDashboardDeleted()">
      <h2 style="padding: 0">{{dashboard?.name}}</h2>
    </asset-operations>
    <charts-dock-panel *ngIf="dashboard"
      [adaptiveHeight]="{minHeight: 500, pageMargin: 15, property: 'min-height'}"
      [dashboard]="dashboard"
      [editMode]="false"
    ></charts-dock-panel>
  `,

})
export class VisualizeViewComponent implements OnDestroy, OnInit {
  dashboard: IDashboard;
  readonly config = config;

  private eventsSubscription: Subscription;

  constructor(
    private dashboards: DashboardService,
    private route: ActivatedRoute,
    private router: Router,
    private events: EventService,
  ) {
  }

  ngOnInit(): void {
    this.route.params.forEach(params => {
      this.dashboards.get(params['dashboardId']).subscribe((data: IDashboard) => {
        this.dashboard = data;
      });
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_DASHBOARD && this.dashboard.id === event.data.id) {
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
