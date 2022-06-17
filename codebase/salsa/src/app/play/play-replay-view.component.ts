import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import { IFlow, IFlowInput, IFlowOutput, IFlowstep } from '../compose/flow.interface';
import { FlowService } from '../compose/flow.service';
import { FlowstepEditOptionsComponent } from '../compose/flowstep-edit-o.component';
import config from '../config';
import { IAsset, IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ITable } from '../tables/table.interface';
import { MiscUtils } from '../utils/misc';

import { IReplay } from './replay.interface';
import { ReplayService } from './replay.service';

@Component({
  selector: 'play-replay',
  template: `
    <asset-operations [type]="config.asset.values.REPLAY" [selectedItems]="[replay]"
      (onDelete)="_onReplayDeleted()"></asset-operations>
    <div class="row brand-tab">
      <div class="col-md-6">
        <app-input [label]="'Replay Name'" [value]="replay?.name" [disabled]="true"></app-input>
      </div>
      <div class="col-md-6">
        <div class="pull-right">
        </div>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-description [value]="replay?.description" [disabled]="true"></app-description>
      </div>
    </div>
    <app-spinner [visibility]="loading"></app-spinner>
    <div *ngIf="!loading" class="brand-tab">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Play Asset'" [value]="originalFlow?.name" [disabled]="true"></app-input>
        </div>
        <div class="col-md-6">
          <app-input [label]="'New Flow Name'" [value]="flow?.name" [disabled]="true"></app-input>
        </div>
      </div>
    </div>
    <app-tabs [tabs]="['Flow Summary', 'Input', 'Output']" [(active)]="activeTab" ></app-tabs>
    <div *ngIf="!loading" class="flex-col brand-tab" [adaptiveHeight]="{minHeight: 450}">
      <div class="flex-static graphic" *ngIf="activeTab === 0">
        <svg flow-graph [flow]="flow" [tables]="flowTables" [container]="'.graphic'"></svg>
      </div>
      <div class="flex-col" [hidden]="activeTab !== 1" [ngSwitch]="flow?.status">
        <div class="flex-col" *ngSwitchCase="config.flow.status.values.DONE">
          <app-tabs [tabs]="inputNames" [(active)]="activeInput"></app-tabs>
          <table-view-embed [hidden]="activeInput !== i" *ngFor="let inputId of inputs;let i = index" [id]="inputId"></table-view-embed>
        </div>
        <div *ngSwitchCase="config.flow.status.values.RUNNING">
          <div class="row">
            <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3">
              <div>
                <div class="p-y-3 p-x-3">
                  <h1 class="text-center text-muted"><i class="icon-clock icons"></i></h1>
                  <h4 class="text-center text-muted">Flow Is Running Input Tables Unavailable</h4>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="flex-col" [hidden]="activeTab !== 2" [ngSwitch]="replay?.status">
        <div class="flex-col" *ngSwitchCase="config.replay.status.values.DONE">
          <app-tabs [tabs]="outputNames" [(active)]="activeOutput"></app-tabs>
          <form *ngIf="flowstepForms[activeOutput]"
            [formGroup]="flowstepForms[activeOutput]" [ngSwitch]="flowstepForms[activeOutput]?.value.type">
            <flowstep-edit-insert *ngSwitchCase="config.flowstep.type.values.insert"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-insert>
            <flowstep-edit-aggregate *ngSwitchCase="config.flowstep.type.values.aggregate"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-aggregate>
            <flowstep-edit-join *ngSwitchCase="config.flowstep.type.values.join"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-join>
            <flowstep-edit-cluster *ngSwitchCase="config.flowstep.type.values.cluster"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-cluster>
            <flowstep-edit-query *ngSwitchCase="config.flowstep.type.values.query"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-query>
            <flowstep-edit-filter *ngSwitchCase="config.flowstep.type.values.filter"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-filter>
            <flowstep-edit-window *ngSwitchCase="config.flowstep.type.values.window"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-window>
            <flowstep-edit-map *ngSwitchCase="config.flowstep.type.values.map"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-map>
            <flowstep-edit-geojoin *ngSwitchCase="config.flowstep.type.values.geojoin"
              [form]="flowstepForms[activeOutput]"></flowstep-edit-geojoin>
          </form>
          <table-view-embed [hidden]="activeOutput !== i" *ngFor="let outputId of outputs;let i = index" [id]="outputId"></table-view-embed>
        </div>
        <process-indicator *ngSwitchCase="config.replay.status.values.RUNNING" [target]="'Replay'"
          [process]="flowProcesses[replay.flowId]"></process-indicator>
        <error-indicator *ngSwitchCase="config.replay.status.values.ERROR" [target]="'replay'"
          [process]="flowProcesses[replay.flowId]"></error-indicator>
      </div>
    </div>
  `,
})
export class PlayReplayViewComponent implements OnInit, OnDestroy {
  loading: boolean = false;
  replay: IReplay;
  flow: IFlow;
  originalFlow: IFlow;
  flowTables: ITable[] = [];
  activeTab: number = 2;
  activeInput: number = 0;
  activeOutput: number = 0;
  outputNames: TObjectId[] = [];
  inputNames: string[] = [];
  inputs: TObjectId[] = [];
  outputs: string[] = [];
  flowstepForms: FormGroup[] = [];
  readonly config = config;
  readonly flowProcesses: {[id: string]: IProcess} = {};
  private processSubscription: Subscription;
  private eventsSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private replays: ReplayService,
    private flows: FlowService,
    private processes: ProcessService,
    private events: EventService,
  ) {
    this.flowProcesses = this.processes.data.targets[config.asset.aliasesPlural[config.asset.values.FLOW]];
  }

  ngOnInit() {
    this.route.data.forEach((data: { replay: IReplay }) => {
      this.processSubscription && this.processSubscription.unsubscribe();

      this.replay = data.replay;
      this._loadReplay(data.replay);
      if (data.replay.status === config.replay.status.values.RUNNING) {
        this.processSubscription = this.processes.subscribeByTarget(data.replay.flowId, IAsset.Type.FLOW, () => {
          this.replays.get(this.replay.id).subscribe((replay: IReplay) => {
            this.replay = replay;
            this._loadReplay(data.replay, true);
          });
        });
      }
    });

    this.eventsSubscription = this.events.subscribe(event => {
      if (event.type === IEvent.Type.DELETE_REPLAY && this.replay.id === event.data.id) {
        this._onReplayDeleted();
      }
    });
  }

  ngOnDestroy() {
    this.processSubscription && this.processSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
  }


  _onReplayDeleted() {
    this.router.navigate(['/desk', 'play']);
  }

  private _loadReplay(replay: IReplay, silent?: boolean): void {
    if (!silent) {
      this.loading = true;
    }

    Observable.forkJoin(
      this.flows.get(replay.flowId),
      this.flows.get(replay.originalFlowId),
    ).subscribe(([flow, originalFlow]: IFlow[]) => {
      let done = flow.status === config.flow.status.values.DONE;
      Observable.forkJoin(
        this.flows.getTables(flow.id),
        done ? this.flows.inputs(flow.id) : Observable.of([]),
        done ? this.flows.outputs(flow.id) : Observable.of([]),
      ).subscribe(([flowTables, flowInputs, flowOutputs]: [IBackendList<ITable>, IFlowInput[], IFlowOutput[]]) => {
        // flows
        this.flow = flow;
        this.originalFlow = originalFlow;
        this.flowTables = flowTables.data;

        // inputs
        this.inputNames = [];
        this.inputs = [];
        flowInputs.forEach((table: IFlowInput) => {
          this.inputNames.push(table.tableName);
          this.inputs.push(table.tableId);
        });

        // outputs
        this.outputNames = [];
        this.outputs = [];
        flowOutputs.forEach((output: IFlowOutput, i: number) => {
          this.outputNames[i] = output.tableName;
          this.outputs[i] = output.tableId;
          const flowstep = this.flow.steps.find((step: IFlowstep) => step.output === output.tableId);
          if (flowstep) {
            this.flowstepForms[i] = FlowstepEditOptionsComponent.prepareFlowstepForm(flowstep.type);
            MiscUtils.fillForm(this.flowstepForms[i], flowstep, false);
          }
        });
        this.loading = false;
      });
    });
  }
}
