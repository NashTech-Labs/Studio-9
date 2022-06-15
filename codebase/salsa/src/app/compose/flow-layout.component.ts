import { ChangeDetectorRef, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/operator/map';
import { Observable } from 'rxjs/Observable';
import { ReplaySubject } from 'rxjs/ReplaySubject';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { AclService } from '../services/acl.service';
import { ITable } from '../tables/table.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditComponent } from './flowstep-edit.component';

export class FlowData extends Observable<IFlow> {}
export class FlowDataSubject extends ReplaySubject<IFlow> {
  constructor() {
    super(1);
  }
}

@Component({
  selector: 'flow-default',
  template: `
    <asset-operations [type]="config.asset.values.FLOW" [selectedItems]="[flow]"
      (onAddTable)="onAddTable($event)" (onDelete)="_onFlowDeleted()"></asset-operations>
    <div class="bg-gray" style="padding: 5px 15px;" *ngIf="flow && acl.canUpdateFlow(flow)">
      <div class="row">
        <div class="col-xs-12 col-sm-12 col-md-8 col-lg-8 text-left">
          <label class="text-uppercase" style="padding-left: 15px">Add STEP:</label>
          <div class="btn-group btnssettings">
            <ng-template ngFor let-type [ngForOf]="config.flowstep.type.list">
              <button *featureToggle="config.flowstep.type.features[type]"
                [routerLinkActive]="['active']"
                [routerLink]="['/desk', 'flows', flow.id, 'steps', 'create', type]"
                [ngClass]="{'active': subComponent && subComponent?.flowstepEditForm?.value.type === type}"
                type="button" class="btn btn-default" tooltip data-toggle="tooltip" data-placement="bottom"
                [attr.data-original-title]="config.flowstep.type.labels[type]">
                <i class="{{config.flowstep.type.icons[type]}}"></i>
                <div>{{config.flowstep.type.labels[type]}}</div>
              </button>
            </ng-template>
          </div>
        </div>
        <div class="col-xs-12 col-sm-12 col-md-4 col-lg-4 text-right pt5" *ngIf="subComponent?.flowstepEditForm">
          <button type="button"
            [disabled]="(subComponent.flowstepEditForm.statusChanges | apply: _isInvalidForm | async) || (subComponent.saving | async)"
            (click)="submit()"
            class="btn btn-md btn-apply">{{!subComponent.flowstepEditForm.controls['id'].value ? 'Execute' : 'Update'}}
          </button>
        </div>
      </div>
    </div>
    <router-outlet (activate)="onActivate($event)"></router-outlet>
    <flowstep-navigation *ngIf="flow && flow.steps.length" [flow]="flow"></flowstep-navigation>
  `,
  providers: [{
    provide: FlowData,
    useClass: FlowDataSubject,
  }],
})
export class FlowLayoutComponent implements OnInit, OnDestroy {
  readonly config = config;
  subComponent: FlowstepEditComponent;
  flow: IFlow;
  private routeSubscription: Subscription;
  private processSubscription: Subscription;
  private flowLoader: ReactiveLoader<IFlow, TObjectId>;
  private subscriptions: Subscription[] = [];

  constructor(
    private flows: FlowService,
    private processes: ProcessService,
    private route: ActivatedRoute,
    private router: Router,
    private events: EventService,
    @Inject(FlowData) private flowData: ReplaySubject<IFlow>,
    readonly acl: AclService,
    private cdRef: ChangeDetectorRef,
  ) {
    this.flowLoader = new ReactiveLoader((flowId) => {
      return Observable.forkJoin(
        this.flows.get(flowId),
        this.flows.getTables(flowId),
      ).map(([flow, tables]) => flow);
    });

    this.flowLoader.subscribe((flow) => {
      this.flow = flow;
      this.flowData.next(flow);
      if (this.flow.status === config.flow.status.values.RUNNING) {
        this.processSubscription && this.processSubscription.unsubscribe();
        this.processSubscription = this.processes.subscribeByTarget(this.flow.id, IAsset.Type.FLOW, () => {
          this.flowLoader.load(flow.id);
        });
      }
    });

    this.subscriptions.push(this.events.subscribe(e => {
      if (this.flow && (e.type === IEvent.Type.UPDATE_FLOW ||
        e.type === IEvent.Type.UPDATE_FLOW_STEPS ||
        e.type === IEvent.Type.UPDATE_FLOW_TABLES) && e.data === this.flow.id
      ) {
        this.flowLoader.load(this.flow.id);
      }
      if (e.type === IEvent.Type.DELETE_FLOW && this.flow.id === e.data.id) {
        this._onFlowDeleted();
      }
    }));
  }

  ngOnInit(): void {
    this.routeSubscription = this.route.params.subscribe(params => {
      if (!this.flow || this.flow.id !== params['flowId']) {
        this.flowLoader.load(params['flowId']);
      }
    });
  }

  ngOnDestroy(): void {
    this.routeSubscription && this.routeSubscription.unsubscribe();
    this.subscriptions.forEach((sub: Subscription) => sub.unsubscribe());
    this.processSubscription && this.processSubscription.unsubscribe();
  }

  submit() {
    this.subComponent.submit();
  }

  _onFlowDeleted() {
    this.router.navigate(['/desk', 'flows', 'create']);
  }

  onActivate(component: FlowstepEditComponent) {
    this.subComponent = component;
    this.cdRef.detectChanges();
  }

  onAddTable(table: ITable) {
    this.flows.addTables(this.flow.id, table.id);
  }

  _isInvalidForm(status: Observable<'INVALID' | 'VALID'>): Observable<boolean> {
    return status.debounceTime(0).map(_ => {
      return _ !== 'VALID';
    });
  }
}

