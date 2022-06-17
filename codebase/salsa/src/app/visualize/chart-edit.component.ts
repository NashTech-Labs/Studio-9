import {
  Component,
  ComponentFactoryResolver,
  ComponentRef,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild,
  ViewContainerRef,
} from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import * as _ from 'lodash';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { MiscUtils } from '../utils/misc';

import { ChartAbstract } from './chart.abstract';
import { ChartFactory } from './chart.factory';
import { ChartOptionsAbstract } from './charts/chart-options';
import { DashboardEditState } from './dashboard-edit-state';
import { IDashboardWidget } from './dashboard.interface';
import { TabularDataRequest } from './visualize.interface';

@Component({
  selector: 'chart-edit',
  template: `
    <div style="position: relative">
      <div class="row">
        <div class="col-xs-6">
          <div class="tabpanel">
            <!-- Nav tabs -->
            <ul class="nav nav-tabs" role="tablist">
              <li role="presentation" [ngClass]="{'active': activeTab === 0}">
                <a (click)="activeTab = 0">Chart Basics</a>
              </li>
              <li role="presentation" [ngClass]="{'active': activeTab === 1}">
                <a (click)="activeTab = 1">Chart Options</a>
              </li>
              <li *ngIf="state.widgetForm.value.input?.type === config.asset.values.TABLE"
                role="presentation" [ngClass]="{'active': activeTab === 2}">
                <a (click)="activeTab = 2">Filters</a>
              </li>
              <li *ngIf="state.widgetForm.value.input?.type === config.asset.values.MODEL"
                role="presentation" [ngClass]="{'active': activeTab === 3}">
                <a (click)="activeTab = 3">Simulation</a>
              </li>
              <li *ngIf="state.widgetForm.value.input?.type === config.asset.values.TABLE"
                role="presentation" [ngClass]="{'active': activeTab === 4}">
                <a (click)="activeTab = 4">Grouping</a>
              </li>
            </ul>
          </div>
          <div class="panel panel-default" [hidden]="activeTab !== 0">
            <div class="panel-body">
              <app-input [label]="'Chart Name'" [control]="state.widgetForm.controls['name']"
              ></app-input>
              <div class="btn-group btn-group-justified" role="group" aria-label="Chart Types">
                <div class="btn-group btn-group-lg" role="group">
                  <button type="button" class="btn btn-default btn-lg "
                    title="Line Chart"
                    (click)="state.widgetForm.controls['type'].setValue(config.chart.type.values.LINE)"
                    [ngClass]="{'active': state.widgetForm.value.type === config.chart.type.values.LINE}">
                    <i class="fa fa-line-chart"></i></button>
                </div>
                <div class="btn-group btn-group-lg" role="group">
                  <button type="button" class="btn btn-default btn-lg"
                    title="Bar Chart"
                    (click)="state.widgetForm.controls['type'].setValue(config.chart.type.values.BAR)"
                    [ngClass]="{'active': state.widgetForm.value.type === config.chart.type.values.BAR}">
                    <i class="fa fa-bar-chart"></i></button>
                </div>
                <div class="btn-group btn-group-lg" role="group">
                  <button type="button" class="btn btn-default btn-lg"
                    title="Pie Chart"
                    (click)="state.widgetForm.controls['type'].setValue(config.chart.type.values.PIE)"
                    [ngClass]="{'active': state.widgetForm.value.type === config.chart.type.values.PIE}">
                    <i class="fa fa-pie-chart"></i></button>
                </div>
                <div class="btn-group btn-group-lg" role="group">
                  <button type="button" class="btn btn-default btn-lg"
                    title="Scatter Plot"
                    (click)="state.widgetForm.controls['type'].setValue(config.chart.type.values.SCATTER)"
                    [ngClass]="{'active': state.widgetForm.value.type === config.chart.type.values.SCATTER}">
                    <i class="fa fa-braille"></i></button>
                </div>
                <div class="btn-group btn-group-lg" role="group">
                  <button type="button" class="btn btn-default btn-lg"
                    title="Geo Heat Map"
                    (click)="state.widgetForm.controls['type'].setValue(config.chart.type.values.GEO)"
                    [ngClass]="{'active': state.widgetForm.value.type === config.chart.type.values.GEO}">
                    <i class="fa fa-map"></i></button>
                </div>
                <div class="btn-group btn-group-lg" role="group">
                  <button type="button" class="btn btn-default btn-lg"
                    title="Table"
                    (click)="state.widgetForm.controls['type'].setValue(config.chart.type.values.TABLE)"
                    [ngClass]="{'active': state.widgetForm.value.type === config.chart.type.values.TABLE}">
                    <i class="fa fa-table"></i></button>
                </div>
                <div class="btn-group btn-group-lg" role="group">
                  <button type="button" class="btn btn-default btn-lg"
                    title="One-Dimensional Scatter Plot"
                    (click)="state.widgetForm.controls['type'].setValue(config.chart.type.values.ONEDSCATTER)"
                    [ngClass]="{'active': state.widgetForm.value.type === config.chart.type.values.ONEDSCATTER}">
                    <i class="fa fa-microchip"></i></button>
                </div>
              </div>
              <ng-template [ngIf]="_selectedMetrics.length > 1">
                <h3>Metrics Order:</h3>
                <div class="list-group" style="cursor: move;"
                  dnd-sortable-container [sortableData]="_selectedMetrics">
                  <li class="list-group-item" *ngFor="let metric of _selectedMetrics; let i = index"
                    dnd-sortable
                    [sortableIndex]="i"
                    (onDragEnd)="setMetrics(_selectedMetrics)">
                    {{metric.columnName | apply: _getMetricDisplayName: state}}
                  </li>
                </div>
              </ng-template>
              <ng-template [ngIf]="_selectedAttributes.length > 1">
                <h3>Attributes Order:</h3>
                <div class="list-group" style="cursor: move;"
                  dnd-sortable-container [sortableData]="_selectedAttributes">
                  <li class="list-group-item" *ngFor="let attr of _selectedAttributes; let i = index"
                    dnd-sortable
                    [sortableIndex]="i"
                    (onDragEnd)="setAttributes(_selectedAttributes)">
                    {{attr | apply: _getAttributeDisplayName: state}}
                  </li>
                </div>
              </ng-template>
            </div>
          </div>
          <div class="panel panel-default" [hidden]="activeTab !== 1">
            <div class="panel-body">
              <div #chartOptions></div>
            </div>
          </div>
          <div *ngIf="state.widgetForm.value.input?.type === config.asset.values.TABLE"
            class="panel panel-default" [hidden]="activeTab !== 2">
            <chart-edit-filters [state]="state"></chart-edit-filters>
          </div>
          <div *ngIf="state.widgetForm.value.input?.type === config.asset.values.MODEL"
            class="panel panel-default" [hidden]="activeTab !== 3"
            style="height:320px;overflow-y: auto;overflow-x: hidden;padding: 10px;background: white;border: 1px solid #C0C0C0;border-top: none;"
          >
            <chart-generators-selector
              [label]="'Predictors Values'"
              [modelId]="state.widgetForm.value.input?.id"
              [generators]="state.widgetForm.controls['generators'].value"
              (generatorsChange)="state.widgetForm.controls['generators'].setValue($event)"
              [chartGenerators]="state.widgetForm.controls['chartGenerators'].value"
              (chartGeneratorsChange)="state.widgetForm.controls['chartGenerators'].setValue($event)"
            ></chart-generators-selector>
          </div>
          <div *ngIf="state.widgetForm.value.input?.type === config.asset.values.TABLE"
            class="panel panel-default" [hidden]="activeTab !== 4">
            <chart-edit-groups [state]="state"></chart-edit-groups>
          </div>
        </div>
        <div class="col-xs-6">
          <div class="p-Widget p-DockPanel" style="margin-bottom: 10px;">
            <div class="p-Widget p-SplitDockTab p-DockPanel-tab">
              <div title="" class="p-SplitDockTab-tab p-mod-current p-mod-closable">
                <div class="p-SplitDockTab-tabIcon"></div>
                <div class="p-SplitDockTab-tabLabel">{{state.widgetForm.controls['name'].value}}</div>
                <div class="p-SplitDockTab-tabFilterIcon" (click)="instance.toggleFilters()"></div>
                <div class="p-SplitDockTab-tabRefreshIcon" (click)="instance.refresh()"></div>
                <div class="p-SplitDockTab-tabExportIcon" (click)="instance.download()"></div>
              </div>
            </div>
            <div class="p-Widget p-DockPanel-widget" style="height: 350px;">
              <div #widgetPlaceholder></div>
            </div>
          </div>
        </div>
        <div class="col-xs-12 flex-col" [adaptiveHeight]="{property: 'height', minHeight: 250, targetHeight: 500}">
          <table-view-embed
            *ngIf="state.widgetForm.value.input?.type === config.asset.values.TABLE"
            [id]="state.widgetForm.value.input.id"></table-view-embed>
          <model-view-embed
            *ngIf="state.widgetForm.value.input?.type === config.asset.values.MODEL"
            [modelId]="state.widgetForm.value.input.id"
          ></model-view-embed>
        </div>
      </div>
    </div>
  `,
})
export class ChartEditComponent implements OnChanges, OnDestroy {
  config = config;
  @Input() state: DashboardEditState;
  activeTab: number = 0;
  _selectedMetrics: TabularDataRequest.Aggregation[] = [];
  _selectedAttributes: string[] = [];
  @ViewChild('widgetPlaceholder', { read: ViewContainerRef }) private widgetPlaceholder: ViewContainerRef;
  @ViewChild('chartOptions', { read: ViewContainerRef }) private chartOptions: ViewContainerRef;
  private formSubscription: Subscription;
  private compRef: ComponentRef<ChartAbstract<any>>;
  private optsRef: ComponentRef<ChartOptionsAbstract>; //@TODO make common
  private groupsForm: FormArray = new FormArray([
    new FormGroup({
      columnName: new FormControl(null, Validators.required),
      mergedValue: new FormControl(null, Validators.required),
      values: new FormControl([], Validators.minLength(1)),
    }),
  ]);
  private _compRefFiltersSubscription: Subscription;

  constructor(
    private componentFactoryResolver: ComponentFactoryResolver,
  ) {
  }

  get instance() {
    return this.compRef.instance;
  }

  _getMetricDisplayName = function (columnName: string, state: DashboardEditState): string {
    const column = state.metrList.find(column => column.name === columnName);
    if (!column) {
      throw new Error('Could not find column');
    }
    return column.displayName || column.name;
  };

  _getAttributeDisplayName = function (columnName: string, state: DashboardEditState): string {
    const column = state.attrList.find(column => column.name === columnName);
    if (!column) {
      throw new Error('Could not find column');
    }
    return column.displayName || column.name;
  };

  ngOnChanges() {
    this.formSubscription && this.formSubscription.unsubscribe();
    this.formSubscription = MiscUtils
      .distinctUntilChangedDeep(this.state.widgetForm.valueChanges)
      .debounceTime(100).subscribe(_ => this.syncFromFormValue(_));

    this.syncFromFormValue(this.state.widgetForm.value);
  }

  ngOnDestroy() {
    this.compRef && this.compRef.destroy();
    this.optsRef && this.optsRef.destroy();
    this.formSubscription && this.formSubscription.unsubscribe();
    this._compRefFiltersSubscription && this._compRefFiltersSubscription.unsubscribe();
  }

  setMetrics(metrics: TabularDataRequest.Aggregation[]) {
    this.state.widgetForm.controls['metrics'].setValue(metrics.slice());
  }

  setAttributes(attributes: string[]) {
    this.state.widgetForm.controls['attributes'].setValue(attributes.slice());
    this.checkHierarchy(attributes);
  }

  private syncFromFormValue(widget: IDashboardWidget) {
    const oldConfig = this.compRef ? this.compRef.instance.config : null;
    const newConfig: IDashboardWidget = _.cloneDeep(widget);

    this._selectedMetrics = newConfig.metrics;
    this._selectedAttributes = newConfig.attributes;

    MiscUtils.fillForm(this.groupsForm, newConfig.groups);

    // change replace ComponentWidget to another
    if (!oldConfig || !_.isEqual(oldConfig.type, newConfig.type)) {
      this.compRef && this.compRef.destroy();
      this.compRef = ChartFactory.createComponentInstance(this.widgetPlaceholder, this.componentFactoryResolver, _.cloneDeep(newConfig));
      this.optsRef && this.optsRef.destroy();
      this.optsRef = ChartFactory.createOptionsComponentInstance(this.chartOptions,
        this.componentFactoryResolver, this.state);
      this._compRefFiltersSubscription && this._compRefFiltersSubscription.unsubscribe();
      this._compRefFiltersSubscription = this.compRef.instance.filtersChange.asObservable().subscribe(filters => {
        this.state.widgetForm.controls['filters'].setValue(filters, {
          emitEvent: false,
        });
      });
    } else if (this.compRef) {
      this.compRef.instance.config = _.cloneDeep(newConfig);
    }
  }

  private checkHierarchy(attributes: string[]) {
    const currentInput = this.state.getCurrentInput();
    if (currentInput) {
      const stats = this.state.getCurrentInput().stats;
      const uniqueCounts = attributes.map(attribute => {
        return stats.find(_ => _.columnName === attribute).uniqueCount || 1;
      });

      const isHierarchy = uniqueCounts.slice(0, -1).every((count, idx) => {
        return count < uniqueCounts[idx + 1];
      });

      let options = (<IDashboardWidget> this.state.widgetForm.value).options;
      if (options.hierarchy !== isHierarchy && isHierarchy) {
        options.hierarchy = isHierarchy;
        this.state.widgetForm.controls['options'].setValue(options);
        if (this.optsRef) {
          this.optsRef.instance.state = this.state;
        }
      }
    }
  }
}
