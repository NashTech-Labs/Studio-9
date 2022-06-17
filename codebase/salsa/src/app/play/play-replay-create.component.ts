import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/operator/do';
import { Observable } from 'rxjs/Observable';

import { IFlow, IFlowInput, IFlowOutput } from '../compose/flow.interface';
import { FlowService } from '../compose/flow.service';
import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IBackendList } from '../core/interfaces/common.interface';
import { ITable, ITableColumn } from '../tables/table.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IPlayChildComponent } from './play-create.component';
import { IReplay } from './replay.interface';
import { ReplayService } from './replay.service';

@Component({
  selector: 'app-play-flow',
  template: `
    <app-spinner [visibility]="dataLoader.active | async"></app-spinner>
    <form *ngIf="dataLoader.loaded" [formGroup]="form" (ngSubmit)="form.valid && submit()" class="brand-tab">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'New Flow Name'" [control]="form.controls['name']"></app-input>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
        </div>
      </div>
      <app-tabs [tabs]="['Flow Summary', 'Input', 'Output']" [(active)]="activeTab" ></app-tabs>
      <div class="tab-content brand-tab">
        <div role="tabpanel" class="tab-pane graphic" [ngClass]="{'active': activeTab === 0}">
          <svg flow-graph [flow]="flow" [tables]="flowTables" [container]="'.graphic'"></svg>
        </div>
        <div role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 1}">
          <app-tabs [tabs]="sourceNames" [(active)]="activeSource"></app-tabs>
          <div class="tab-content brand-tab">
            <div class="tab-pane brand-tab"
              *ngFor="let source of form.controls['sources']['controls']; let i = index"
                [ngClass]="{'active': activeSource === i}">
                <div class="row">
                  <div class="col-xs-6">
                    <a>{{sourceNames[i]}}</a>
                  </div>
                  <div class="col-xs-6">
                    <library-selector
                      [inputLabel]="'Select Mapping Table'"
                      [value]="sourceTables[i]"
                      (valueChange)="sourceTables[i] = $event; findMatchingColumns(i)"
                      [available]="[config.asset.values.TABLE]"
                      [caption]="'Select Mapping Table'"
                    ></library-selector>
                  </div>
                </div>
                <div *ngFor="let column of source.controls.columnMappings.controls" class="row">
                  <div class="col-xs-6">
                    {{column['controls'].sourceColumn.controls['columnName'].value}}
                  </div>
                  <div class="col-xs-6">
                    <app-select
                      [control]="column['controls'].mappedColumn.controls['columnName']"
                      [options]="sourceTables[i]?.object['columns'] | tableColumnSelectOptions"></app-select>
                  </div>
                </div>
            </div>
          </div>
        </div>
        <div role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 2}">
          <div *ngFor="let item of form.controls['outputTableNames']['controls']; let i = index" class="row">
            <div class="col-xs-6">
              <a>{{derivedLabels[i]}}</a>
            </div>
            <div class="col-xs-6">
              <app-input [label]="'New Table Name'" [control]="item.controls['newTableName']"></app-input>
            </div>
          </div>
        </div>
      </div>
    </form>
  `,
})
export class PlayReplayCreateComponent implements OnChanges, IPlayChildComponent {
  @Input() flow: IFlow;
  @Output() changeValidity: EventEmitter<any> = new EventEmitter();
  readonly config = config;
  readonly form: FormGroup;
  activeTab: number = 0;
  activeSource: number = 0;
  flowTables: ITable[] = [];
  sourceNames: string[] = [];
  derivedNames: string[] = [];
  derivedLabels: string[] = [];
  sourceTables: LibrarySelectorValue[] = [];
  readonly dataLoader: ReactiveLoader<[IFlow, IBackendList<ITable>, IFlowInput[], IFlowOutput[]], any>;

  constructor(private flows: FlowService, private replays: ReplayService, private router: Router) {
    this.dataLoader = new ReactiveLoader(() => {
      return Observable.forkJoin(
        this.flows.get(this.flow.id),
        this.flows.getTables(this.flow.id),
        this.flows.inputs(this.flow.id),
        this.flows.outputs(this.flow.id),
      );
    });

    this.form = new FormGroup({
      flowId: new FormControl(null, Validators.required),
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      outputTableNames: new FormArray([]),
      sources: new FormArray([]),
    });

    //sends validity of the form back to parent
    this.form.valueChanges.subscribe(() => {
      this.changeValidity.emit(this.form.valid);
    });

    this.form.controls['name'].valueChanges.subscribe((value) => {
      (<FormArray> this.form.controls['outputTableNames']).controls.forEach((group: FormGroup, i: number) => {
        if (group.controls['newTableName'].pristine) {
          let newValue = this.derivedNames[i] + '_' + value;
          group.controls['newTableName'].setValue(newValue);
        }
      });
    });

    this.dataLoader.subscribe(_ => this._setData(_));
  }

  ngOnChanges() {
    // load data
    this.dataLoader.load();
  }

  submit() {
    return this.replays.create(this.form.value).do((replay: IReplay) => {
      this.router.navigate(['/desk', 'play', 'replays', replay.id]);
    });
  }

  findMatchingColumns(i) {
    (<FormArray> (<FormGroup> (<FormArray> this.form.controls['sources']).controls[i]).controls['columnMappings'])
      .controls
      .forEach((columnControl: FormGroup) => {
        let found = (<ITable> this.sourceTables[i].object).columns.find(column =>
          column.name === (<FormGroup> columnControl.controls['sourceColumn']).controls['columnName'].value);
        (<FormGroup> columnControl.controls['mappedColumn']).controls['columnName'].setValue(found ? found.name : null);
        (<FormGroup> columnControl.controls['mappedColumn']).controls['tableId'].setValue(this.sourceTables[i].id);
        columnControl.markAsDirty();
        columnControl.enable();
      });
  }

  private _setData([flow, tables, inputs, outputs]: [IFlow, IBackendList<ITable>, IFlowInput[], IFlowOutput[]]): void {
    this.form.controls['flowId'].setValue(this.flow.id);
    // need extended data
    Object.assign(this.flow, flow);
    this.flowTables = tables.data;
    // Inputs
    this.sourceNames = [];
    this._clearFromArray(<FormArray> this.form.controls['sources']);
    inputs.forEach((row: IFlowInput, i: number) => {
      this.sourceNames.push('Source ' + (i + 1).toString() + ' (' + row.tableName + ')');
      let source = new FormGroup({
        table: new FormControl(), // tmp control
        columnMappings: new FormArray([]),
      });
      row.columns.forEach((column: ITableColumn) => {
        let mappings = new FormGroup({
          sourceColumn: new FormGroup({
            tableId: new FormControl(row.tableId, Validators.required),
            columnName: new FormControl(column.name, Validators.required),
          }),
          mappedColumn: new FormGroup({
            tableId: new FormControl(null, Validators.required),
            columnName: new FormControl(null, Validators.required),
          }),
        });
        mappings.disable();
        (<FormArray> source.controls['columnMappings']).push(mappings);
      });
      (<FormArray> this.form.controls['sources']).push(source);
    });
    // outputs
    this.derivedLabels = [];
    this._clearFromArray(<FormArray> this.form.controls['outputTableNames']);
    outputs.forEach((table: IFlowOutput, i: number) => {
      this.derivedNames.push(table.tableName);
      this.derivedLabels.push('Derived ' + (i + 1).toString() + ' (' + table.tableName + ')');
      (<FormArray> this.form.controls['outputTableNames']).push(new FormGroup({
        newTableName: new FormControl(table.tableName, Validators.required),
        tableId: new FormControl(table.tableId, Validators.required),
      }));
    });
  }

  //noinspection JSMethodCanBeStatic
  private _clearFromArray(form: FormArray): void {
    let itemsCount = form.length;
    while (itemsCount > 0) {
      form.removeAt(--itemsCount);
    }
  }
}
