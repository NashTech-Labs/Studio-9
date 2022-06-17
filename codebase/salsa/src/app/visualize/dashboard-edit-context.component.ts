import { ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Subject } from 'rxjs/Subject';
import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectValueType } from '../core-ui/components/app-select.component';
import { LibrarySelectorComponent, LibrarySelectorValue } from '../core/components/library-selector.component';
import { IAssetReference, IBackendList } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { DashboardEditState } from './dashboard-edit-state';
import { IDashboard } from './dashboard.interface';
import { DashboardService } from './dashboard.service';

const DRAG_THRESHOLD = 5;

@Component({
  selector: 'visualize-dashboard-edit-context',
  template: `
    <library-selector
      #selector
      [hidden]="true"
      [inputLabel]="'Add Table/Model To Dashboard'"
      [value]="null"
      (valueChange)="onAssetSelect($event)"
      [available]="[config.asset.values.TABLE, config.asset.values.MODEL]"
    ></library-selector>
    <div class="group">
      <button type="button"
        class="btn btn-primary btn-alt btn-block"
        [routerLink]="['/desk', 'visualize', 'dashboards', 'create']"
      >Create New Dashboard
      </button>
    </div>
    <div style="min-height:40px; position: relative">
      <app-spinner [visibility]="!dashboardList" [height]="40"></app-spinner>
      <app-select *ngIf="dashboardList.length"
        [placeholder]="'- Select Dashboard -'"
        [value]="state?.dashboard?.id"
        (valueChange)="switchDashboard($event)"
        [options]="dashboardList"
      ></app-select>
    </div>
    <ng-template [ngIf]="state">
      <app-spinner [visibility]="!state.ready"></app-spinner>
      <div *ngIf="state.ready" class="menu">
        <div class="glued-nav" [ngClass]="{'shifted': !!state.widgetForm || !!state.previewInput}">
          <ul class="nav nav-stacked glued">
            <li class="has-submenu" (mousemove)="onInputMouseMove($event)"
              [ngClass]="{'open': menuOpen[0]}">
              <a (click)="menuOpen[0] = !menuOpen[0]">
                <i class="glyphicon glyphicon-th-list"></i>
                <span>Inputs</span>
              </a>
              <ul class="nav nav-pills submenu with-dropdown">
                <li *ngFor="let item of state.tableList" [ngClass]="{'active': item && state.isCurrentTable(item.id)}">
                  <span class="dropdown pull-right">
                    <a (click)="onInputAddClick({id: item.id,type:config.asset.values.TABLE })">
                      <i class="glyphicon glyphicon-plus"></i>
                    </a>
                    <a (click)="onInputRemoveClick({id: item.id,type:config.asset.values.TABLE })">
                      <i class="glyphicon glyphicon-trash"></i>
                    </a>
                  </span>
                  <a class="dot-iconapp iconapp-tables"
                    (mousedown)="onInputMouseDown($event, {id: item.id, type: config.asset.values.TABLE})"
                    (mouseup)="onInputMouseUp()"
                    [title]="item.name">
                    {{item.name}}
                  </a>
                </li>
                <li *ngFor="let item of state.modelList" [ngClass]="{'active': item && state.isCurrentModel(item.id)}">
                  <span class="dropdown pull-right">
                    <a (click)="onInputAddClick({id: item.id,type:config.asset.values.MODEL })">
                      <i class="glyphicon glyphicon-plus"></i>
                    </a>
                    <a (click)="onInputRemoveClick({id: item.id,type:config.asset.values.MODEL })">
                      <i class="glyphicon glyphicon-trash"></i>
                    </a>
                  </span>
                  <a class="dot-iconapp iconapp-models"
                    (mousedown)="onInputMouseDown($event, {id: item.id, type: config.asset.values.MODEL})"
                    (mouseup)="onInputMouseUp()"
                    [title]="item.name">
                    {{item.name}}
                  </a>
                </li>
                <li *ngIf="!state.modelList.length && !state.tableList.length"><a>No inputs</a></li>
                <li style="padding: 3px 15px">
                  <button type="button" class="btn btn-primary btn-alt btn-block" (click)="selector.show()">
                    Add Table/Model
                  </button>
                </li>
              </ul>
            </li>
            <li *mocksOnly="true" class="has-submenu" [ngClass]="{'open': menuOpen[1]}">
              <a (click)="menuOpen[1] = !menuOpen[1]">
                <i class="iconapp iconapp-visuals"></i>
                <span>Charts</span>
              </a>
              <ul class="nav nav-pills submenu with-dropdown">
                <li *ngFor="let item of state.form.value.widgets;let i = index">
                  <span class="dropdown pull-right">
                      <i class="glyphicon glyphicon-edit"></i>
                  </span>
                  <a (click)="state.selectWidget(i)" [title]="item.name">
                    {{item.name}}
                  </a>
                </li>
                <li *ngIf="!state.form.value.widgets || !state.form.value.widgets.length"><a>No charts</a></li>
              </ul>
            </li>
          </ul>
          <chart-edit-context *ngIf="state.widgetForm" [state]="state"></chart-edit-context>
          <ul *ngIf="state.previewInput" class="nav nav-stacked glued">
            <li class="open">
              <a class="brand-background">
                <i class="glyphicon glyphicon-menu-left"
                  (click)="state.navigateBack()" title="Back to Dashboard Layout"></i>
                <span>{{state.getPreviewAsset()?.name}}</span>
                <i class="iconapp"
                  [ngClass]="{'iconapp-tables': state.previewInput.type === config.asset.values.TABLE, 'iconapp-models': state.previewInput.type === config.asset.values.MODEL}"
                ></i>
              </a>
            </li>
          </ul>
          <div class="clear"></div>
        </div>
      </div>
    </ng-template>
  `,
})
export class DashboardEditContextComponent implements OnInit, OnDestroy {
  config = config;
  menuOpen: boolean[] = [true, true];
  state: DashboardEditState;
  dashboardList: { id: string; text: string; }[] = [];

  readonly _dashboardsLoader: ReactiveLoader<IBackendList<IDashboard>, any>;

  @ViewChild('selector') readonly selector: LibrarySelectorComponent;
  private eventsSubscription: ISubscription;
  private _inputMouseDownEvent: {
    event: MouseEvent,
    asset: IAssetReference,
  };

  constructor(
    private events: EventService,
    private route: ActivatedRoute,
    private router: Router,
    private dashboards: DashboardService,
    private cd: ChangeDetectorRef,
  ) {
    this._dashboardsLoader = new ReactiveLoader(() => this.dashboards.list());
    this._dashboardsLoader.subscribe((data: IBackendList<IDashboard>) => {
      this.dashboardList = data.data.filter(_ => _).map(dashboard => {
        return { id: dashboard.id, text: dashboard.name };
      });
    });
  }

  ngOnInit() {
    this.route.data.forEach((data: { subject: Subject<DashboardEditState> }) => {
      data.subject.subscribe(state => {
        this.state = state;
        this.cd.detectChanges();
      });
    });
    /*this.eventsSubscription = this.events.subscribe((event: IEvent) => {
      if (event.type === IEvent.Type.UPDATE_MODEL_LIST) {
        this._modelsLoader.load();
      }
      if (event.type === IEvent.Type.UPDATE_TABLE_LIST) {
        this._tablesLoader.load();
      }
    });*/
    this._dashboardsLoader.load();
  }

  ngOnDestroy() {
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }

  onAssetSelect(selection: LibrarySelectorValue) {
    if (selection) {
      const inputs = this.state.form.value.inputs || [];
      if (inputs.findIndex(asset => asset.id === selection.id && asset.type === selection.entity) < 0) {
        inputs.push({ id: selection.id, type: selection.entity });
        this.state.form.controls.inputs.setValue(inputs);
      }
    }
  }

  onInputMouseDown(event: MouseEvent, asset: IAssetReference) {
    MiscUtils.cancelEvent(event);
    this._inputMouseDownEvent = { event, asset };
  }

  onInputAddClick(input: IAssetReference) {
    this.state.navigateBack();
    const pendingMessage = {
      data: this.state.generateWidget(input),
    };
    window.setTimeout(() => {
      this.events.emit(IEvent.Type.ADD_CHART, pendingMessage);
    });
  }

  onInputRemoveClick(input: IAssetReference) {
    this.state.removeInput(input);
  }

  onInputMouseUp() {
    MiscUtils.cancelEvent();
    if (this._inputMouseDownEvent) { // if we did not drag item account this as a click and open up an asset
      this.state.setPreviewInput(this._inputMouseDownEvent.asset);
    }
    this._inputMouseDownEvent = null;
  }

  onInputMouseMove(event: MouseEvent) {
    if (this._inputMouseDownEvent) {
      const dx = Math.abs(event.clientX - this._inputMouseDownEvent.event.clientX);
      const dy = Math.abs(event.clientY - this._inputMouseDownEvent.event.clientY);
      if (dx >= DRAG_THRESHOLD || dy >= DRAG_THRESHOLD) {
        this.state.navigateBack();
        const pendingMessage = {
          event: event,
          data: this.state.generateWidget(this._inputMouseDownEvent.asset),
        };
        this._inputMouseDownEvent = null;
        window.setTimeout(() => {
          this.events.emit(IEvent.Type.ADD_DETACHED_CHART, pendingMessage);
        });
      }
    }
  }

  switchDashboard(dashboardId: AppSelectValueType) {
    this.state.navigateBack();
    if (dashboardId) {
      this.router.navigate(['/desk/visualize/dashboards/', String(dashboardId), 'edit']);
    } else {
      this.router.navigate(['/desk/visualize/dashboards/create']);
    }
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      ADD_CHART = 'ADD_CHART',
      ADD_DETACHED_CHART = 'ADD_DETACHED_CHART',
    }
  }
}
