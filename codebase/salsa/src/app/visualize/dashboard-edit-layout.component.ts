import { Component, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';

import * as _ from 'lodash';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IAssetReference } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ActivityObserver } from '../utils/activity-observer';
import { MiscUtils } from '../utils/misc';

import { ChartComponentWidget } from './chart-component-widget';
import { ChartAbstract } from './chart.abstract';
import { ChartsDockComponent } from './charts-dock-panel.component';
import { DashboardCharts } from './charts/chart.interfaces';
import { DashboardEditState } from './dashboard-edit-state';
import { IDashboardCreate, IDashboardWidget, ILayout, ILayoutWidget } from './dashboard.interface';
import { DashboardService } from './dashboard.service';

interface ILayoutParseResult {
  layout: ILayout | ILayoutWidget;
  widgets: IDashboardWidget[];
}

@Component({
  selector: 'visualize-dashboard-edit-layout',
  template: `
    <div *ngIf="state" style="position: relative">
      <div class="row">
        <div class="col-xs-6">
          <app-input [label]="'Dashboard Name'" [control]="state.form.controls['name']"></app-input>
        </div>
        <div class="col-xs-6">
          <button
            [disabled]="!state.form.valid || (_savingObserver.active | async)"
            (click)="saveDashboard()"
            class="btn btn-primary"
          >Save</button>
        </div>
      </div>
      <div class="row">
        <div class="col-xs-6">
          <app-description [control]="state.form.controls['description']"></app-description>
        </div>
      </div>
    </div>
    <charts-dock-panel #layout
      [adaptiveHeight]="{minHeight: 500, pageMargin: 15, property: 'min-height'}"
      [dashboard]="state.form.value"
      [editMode]="true"
      [crossFilters]="state.form.controls['crossFilters'].value"
      (onLayoutModified)="onLayoutModified($event)"
    ></charts-dock-panel>
  `,
})
export class DashboardEditLayoutComponent implements OnInit, OnDestroy {
  config = config;
  @Input() state: DashboardEditState;
  readonly _savingObserver = new ActivityObserver();

  @ViewChild('layout') private layout: ChartsDockComponent;
  private _pendingWidget: ChartComponentWidget = null;
  private eventSubscription: Subscription;

  constructor(private events: EventService, private dashboards: DashboardService, private router: Router) {
  }

  saveDashboard() {
    const requestData: IDashboardCreate = Object.assign({}, this.state.form.value, {
      crossFilter: false,
    });
    // save only links to those inputs we really use
    requestData.inputs = requestData.widgets.map(_ => _.input).reduce((acc, input) => {
      if (!acc.find(_ => _.id === input.id && _.type === input.type)) {
        acc.push(input);
      }
      return acc;
    }, <IAssetReference[]> []);

    const observable = (this.state.dashboard && 'id' in this.state.dashboard)
      ? this.dashboards.update(this.state.dashboard.id, requestData)
      : this.dashboards.create(requestData);

    this._savingObserver.observe(observable).subscribe(dashboard => {
      this.router.navigate(['/desk/visualize/dashboards/', dashboard.id]);
    });
  }

  ngOnInit() {
    this.eventSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.ADD_CHART) {
        this.addChart(event.data.data);
      }
      if (event.type === IEvent.Type.ADD_DETACHED_CHART) {
        this.addDetachedChart(event.data.event, event.data.data);
      }
      if (event.type === IEvent.Type.CHART_EDIT) {
        const index = this.state.getWidgetIndexByGuid(event.data);
        if (index > -1) {
          this.state.selectWidget(index);
        }
      }
    });
  }

  ngOnDestroy() {
    this.eventSubscription && this.eventSubscription.unsubscribe();
  }

  addChart(data: IDashboardWidget) {
    this.layout.addComponent(data);
  }

  addDetachedChart(event: MouseEvent, data: IDashboardWidget) {
    if (this._pendingWidget && !this._pendingWidget.isAttached) {
      this._pendingWidget.dispose();
    }
    this._pendingWidget = this.layout.addComponentDetached(data, event);
  }


  onLayoutModified(data) {
    const parseResult: ILayoutParseResult = this.parseLayout(data.main);
    if (!_.isEqual(this.state.form.value.widgets || [], parseResult.widgets)) {
      MiscUtils.fillForm(this.state.form.controls.widgets, parseResult.widgets);
    }
    if (!_.isEqual(this.state.form.value.layout, parseResult.layout)) {
      this.state.form.controls.layout.setValue(parseResult.layout);
    }
  }

  private parseLayout(data): ILayoutParseResult {
    let widgets: IDashboardWidget[] = [];
    let recursion = (data) => {
      if (!data) {
        return null;
      }
      if (data.type === 'split-area') {
        return {
          type: data.type,
          sizes: data.sizes,
          orientation: data.orientation,
          children: data.children.map(item => {
            return recursion(item);
          }),
        };
      }
      if (data.type === 'tab-area') {
        const component: ChartAbstract<DashboardCharts.IChartOptions> = data.widget.instance;
        const id = widgets.length;
        widgets.push(component.config);
        return {
          type: data.type,
          widget: id,
        };
      }
    };
    return {
      layout: recursion(data),
      widgets: widgets,
    };
  }
}
