import { Component, Host, OnDestroy, OnInit } from '@angular/core';

import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { IAsset, IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { ProcessService } from '../core/services/process.service';
import { ITable } from '../tables/table.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { FlowData } from './flow-layout.component';
import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';

@Component({
  selector: 'flow-graph',
  template: `
    <div *ngIf="flow && flow.steps.length && flowTables.length" class="graphic">
      <svg flow-graph [flow]="flow" [tables]="flowTables" [container]="'.graphic'"></svg>
    </div>

    <div *ngIf="flow && !flow.steps.length">
      <h1 class="text-center text-muted"><i class="icon-exclamation icons"></i></h1>
      <h4 class="text-center text-muted">Empty flow</h4>
      <div><h4 class="text-center text-muted text-light">There are no steps yet</h4></div>
    </div>

    <div class="row" *ngIf="flow && flow.steps.length && !doesFlowContainCorrectStep() && flowProcesses[flow.id]">
      <process-indicator [process]="flowProcesses[flow.id]"></process-indicator>
    </div>
  `,
})
export class FlowGraphComponent implements OnInit, OnDestroy {
  flow: IFlow;
  flowTables: ITable[] = [];
  flowProcesses: {[id: string]: IProcess};
  private processSubscription: ISubscription;
  private _flowTablesLoader: ReactiveLoader<IBackendList<ITable>, TObjectId>;

  constructor(
    @Host() private flowData: FlowData,
    private flows: FlowService,
    private processes: ProcessService,
  ) {
    this._flowTablesLoader = new ReactiveLoader((flowId) => this.flows.getTables(flowId));

    this._flowTablesLoader.subscribe((tablesList: IBackendList<ITable>) => {
      this.flowTables = tablesList.data;
    });
  }

  doesFlowContainCorrectStep() {
    return this.flow.steps.some(step => {
      return step.status === config.flowstep.status.values.DONE;
    });
  }

  ngOnInit() {
    this.flowProcesses = this.processes.data.targets[config.asset.aliasesPlural[config.asset.values.FLOW]];
    this.flowData.forEach((flow: IFlow) => {
      this.setFlow(flow);
    });
  }

  ngOnDestroy() {
    this.processSubscription && this.processSubscription.unsubscribe();
  }

  private setFlow(flow: IFlow) {
    this.flow = flow;
    if (flow.status === config.flow.status.values.RUNNING) {
      this.processSubscription && this.processSubscription.unsubscribe();
      this.processSubscription = this.processes.subscribeByTarget(flow.id, IAsset.Type.FLOW, () => {
        this._flowTablesLoader.load(flow.id);
      });
    }
    this.flowTables = [];
    this._flowTablesLoader.load(flow.id);
  }
}

