import { Component, HostBinding, Input, Pipe, PipeTransform } from '@angular/core';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';

import { DashboardEditState, IColumnInfo } from './dashboard-edit-state';
import { IDashboard } from './dashboard.interface';
import { TabularDataRequest } from './visualize.interface';

@Pipe({ name: 'metricslist' })
export class MetricsListPipe implements PipeTransform {
  transform(items: IColumnInfo[], metrics: TabularDataRequest.Aggregation[]): (IColumnInfo & { type: IDashboard.DashboardAggregationType })[] {
    return items.map(item => {
      const foundMetric = metrics.find(metric => metric.columnName === item.name);
      return {
        ...item,
        type: foundMetric ? foundMetric.aggregator : null,
      };
    });
  }
}

@Pipe({ name: 'attributeslist' })
export class AttributesListPipe implements PipeTransform {
  transform(items: IColumnInfo[], attributes: string[]): (IColumnInfo & { isActive: boolean })[] {
    return items.map(item => {
      const isActive = !!attributes.find(attribute => attribute === item.name);
      return {
        ...item,
        isActive,
      };
    });
  }
}

@Component({
  selector: 'chart-edit-context',
  template: `
    <ul class="nav nav-stacked" *ngIf="state.widgetForm">
      <li class="open">
        <a class="brand-background">
          <i class="glyphicon glyphicon-menu-left"
            (click)="state.navigateBack()" title="Back to Dashboard Layout"></i>
          <span>{{state.widgetForm.controls['name'].value}}</span>
          <i class="iconapp iconapp-visuals"></i>
        </a>
      </li>
      <li class="has-submenu" [ngClass]="{'open': menuOpen[0]}">
        <a (click)="menuOpen[0] = !menuOpen[0]">
          <i class="glyphicon glyphicon-th-list"></i>
          <span>Input</span>
        </a>
        <ul class="nav nav-pills submenu with-dropdown">
          <li *ngFor="let item of state.tableList" [ngClass]="{'active': item && state.isCurrentTable(item.id)}">
            <span class="dropdown pull-right">
              <a (click)="state.setWidgetTable(item.id)">
                <i [ngClass]="{'glyphicon glyphicon-ok': state.isCurrentTable(item.id)}"></i>
              </a>
            </span>
            <a class="dot-iconapp iconapp-tables"
              (click)="state.setWidgetTable(item.id)"
              [title]="item.name">
              {{item.name}}
            </a>
          </li>
          <li *ngFor="let item of state.modelList" [ngClass]="{'active': item && state.isCurrentModel(item.id)}">
            <span class="dropdown pull-right">
              <a (click)="state.setWidgetModel(item.id)">
                <i [ngClass]="{'glyphicon glyphicon-ok': state.isCurrentModel(item.id)}"></i>
              </a>
            </span>
            <a class="dot-iconapp iconapp-models"
              (click)="state.setWidgetModel(item.id)"
              [title]="item.name">
              {{item.name}}
            </a>
          </li>
          <li *ngIf="!state.tableList.length && !state.modelList.length"><a>No inputs available</a></li>
        </ul>
      </li>
      <li class="has-submenu" [ngClass]="{'open': menuOpen[2]}">
        <a (click)="menuOpen[2] = !menuOpen[2]">
          <i class="glyphicon glyphicon-stats"></i>
          <span>Metrics</span>
        </a>
        <ul class="nav nav-pills submenu with-dropdown tiny">
          <li *ngFor="let item of state.metrList | metricslist : state.widgetForm.controls.metrics.value"
            [ngClass]="{'active': !!item.type}">
            <span class="dropdown pull-right" dropdown>
              <a data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                <i class="glyphicon glyphicon-option-horizontal"></i>
              </a>
              <ul class="dropdown-menu">
                <li *ngFor="let type of types" [ngClass]="{'active': item.type === type.id}">
                  <a class="dropdown-item link" (click)="state.setMetric(item.name, type.id)">
                    {{type.text}}
                  </a>
                </li>
                <ng-template [ngIf]="!!item.type">
                  <li role="separator" class="divider"></li>
                  <li><a (click)="state.setMetric(item.name)" class="dropdown-item link">Remove</a></li>
                </ng-template>
              </ul>
            </span>
            <a *ngIf="!item.type" [title]="item.displayName">
              {{item.displayName}}
            </a>
            <a *ngIf="!!item.type" [title]="item.displayName">
              {{item.type}}({{item.displayName}})
            </a>
          </li>
          <li *ngIf="!state.metrList.length"><a>No Metrics Available</a></li>
        </ul>
      </li>
      <li class="has-submenu" [ngClass]="{'open': menuOpen[1]}">
        <a (click)="menuOpen[1] = !menuOpen[1]">
          <i class="glyphicon glyphicon-tags"></i>
          <span>Attributes</span>
        </a>
        <ul class="nav nav-pills submenu with-dropdown tiny">
          <li *ngFor="let item of state.attrList | attributeslist : state.widgetForm.controls.attributes.value"
            [ngClass]="{'active': item.isActive}"
            (click)="state.toggleAttribute(item.name, !item.isActive)">
            <span class="dropdown pull-right">
              <a>
                <i [ngClass]="{'glyphicon glyphicon-ok': item.isActive}"></i>
              </a>
            </span>
            <a [title]="item.displayName">
              {{item.displayName}}
            </a>
          </li>
          <li *ngIf="!state.attrList.length"><a>No Attributes</a></li>
        </ul>
      </li>
    </ul>`,
})
export class ChartEditContextComponent {
  @Input() state: DashboardEditState;
  @HostBinding('class') classes = 'glued';
  readonly types = AppSelectOptionData.fromList(config.chart.options.aggregationType.list, config.chart.options.aggregationType.labels);
  menuOpen: boolean[] = [true, true, true];
}
