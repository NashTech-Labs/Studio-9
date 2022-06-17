import { Component, HostBinding, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { UserService } from '../core/services/user.service';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { ActivityObserver } from '../utils/activity-observer';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';

@Component({
  selector: 'app-flow-context',
  template: `
    <div class="group">
      <button type="button" class="btn btn-primary btn-alt btn-block" (click)="switchFlow(null)">
        Create New Flow
      </button>
    </div>
    <div style="min-height:40px; position: relative">
      <app-spinner [visibility]="_userFlowsLoader.active | async" [height]="40"></app-spinner>
      <app-select *ngIf="flowList"
        [placeholder]="'- Select Flow -'"
        [value]="flowId"
        (valueChange)="switchFlow($event)"
        [options]="flowList"></app-select>
    </div>
    <div class="menu">
      <app-spinner [visibility]="flowId && !_flowTablesLoader.loaded" [height]="40"></app-spinner>
      <ul class="nav nav-stacked" *ngIf="flowId && flowTables.length">
        <li class="has-submenu" [ngClass]="{'open': menuOpen[0]}">
          <a (click)="menuOpen[0] = !menuOpen[0]">
            <i class="iconapp iconapp-tables"></i>
            <span>Tables</span>
          </a>
          <ul class="nav nav-pills submenu with-dropdown">
            <li *ngFor="let item of flowTables" [routerLinkActive]="['active']">
            <span class="dropdown pull-right" dropdown>
              <a data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                <i class="glyphicon glyphicon-option-horizontal"></i>
              </a>
              <ul class="dropdown-menu">
                <ng-template [ngIf]="item.status === config.table.status.values.ACTIVE">
                  <li>
                    <a [routerLink]="['/desk', 'flows', flowId, 'tables', item.id]"
                      class="dropdown-item link">
                      Preview
                    </a>
                  </li>
                  <li>
                    <a (click)="downloadObserver.isActive || download(item.id)"
                      class="dropdown-item link">
                      Download
                    </a>
                  </li>
                  <li role="separator" class="divider"></li>
                </ng-template>
                <li>
                  <a (click)="removeTable(item.id)" class="dropdown-item link">
                    Remove
                  </a>
                </li>
              </ul>
            </span>
              <a [title]="item.name"
                [routerLink]="['/desk', 'flows', flowId, 'tables', item.id]"
                [asset-status]="item.status"
                [asset-status-styles]="config.table.status.styles"
                tooltip
                data-toggle="tooltip"
                [attr.data-original-title]="config.table.status.labels[item.status]"
              >
                {{item.name}}
              </a>
            </li>
          </ul>
        </li>
      </ul>
    </div>
  `,
})
export class FlowContextComponent implements OnDestroy {
  @HostBinding('class') classes = 'fixed-width';
  readonly config = config;
  flowList: AppSelectOptionData[] = [];
  menuOpen: boolean[] = [true];
  flowId: TObjectId;
  flowTables: ITable[] = [];
  readonly downloadObserver = new ActivityObserver();
  readonly _flowTablesLoader: ReactiveLoader<IBackendList<ITable>, TObjectId>;
  readonly _userFlowsLoader: ReactiveLoader<IBackendList<IFlow>, null>;
  private _subscriptions: Subscription[] = [];

  constructor(
    private flows: FlowService,
    private router: Router,
    private user: UserService,
    private tables: TableService,
    private events: EventService,
    private route: ActivatedRoute,
  ) {
    this._flowTablesLoader = new ReactiveLoader((flowId) => this.flows.getTables(flowId));

    this._flowTablesLoader.subscribe((tablesList: IBackendList<ITable>) => {
      this.flowTables = tablesList.data;
    });

    this._userFlowsLoader = new ReactiveLoader(() => this.flows.getMyFlows());

    this._userFlowsLoader.subscribe((flowList: IBackendList<IFlow>) => {
      this.updateUserFlowList(flowList);
    });

    this._subscriptions.push(
      this.events.subscribe((event: IEvent) => {
        if (event.type === IEvent.Type.UPDATE_FLOW_TABLES && event.data === this.flowId) {
          this._flowTablesLoader.load(this.flowId);
        }
        if (event.type === IEvent.Type.UPDATE_FLOW_LIST) {
          this._userFlowsLoader.load();
        }
        if (event.type === IEvent.Type.UPDATE_USER_FLOWLIST) {
          this.updateUserFlowList(event.data);
        }
      }),
    );

    this._subscriptions.push(this.route.params.subscribe(params => {
      this.flowId = params['flowId'];
      if (this.flowId) {
        this._flowTablesLoader.load(this.flowId);
      } else {
        this.flowTables = [];
      }
    }));

    this._userFlowsLoader.load();
  }

  updateUserFlowList(flowList: IBackendList<IFlow>) {
    this.flowList = flowList.data.map((flow: IFlow) => {
      return {
        id: flow.id,
        text: flow.name,
      };
    });
  }

  ngOnDestroy() {
    this._subscriptions.forEach((sub: Subscription) => sub.unsubscribe());
  }

  switchFlow(id: TObjectId) {
    if (id) {
      this.router.navigate(['/desk', 'flows', id]);
    } else {
      this.router.navigate(['/desk', 'flows', 'create']);
    }
  }

  removeTable(id: TObjectId) {
    this.flows.removeTables(this.flows.data.view.id, id).subscribe(() => {
      if (this.router.url === `/desk/flows/${this.flows.data.view.id}/tables/${id}`) {
        this.router.navigate(['/desk', 'flows', this.flows.data.view.id, 'info']);
      }
    });
  }

  download(id: TObjectId) {
    this.downloadObserver.observe(this.tables.download(id, this.user.token()));
  }
}
