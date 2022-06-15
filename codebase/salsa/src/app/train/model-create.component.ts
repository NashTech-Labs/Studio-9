import { Component, EventEmitter, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import 'rxjs/add/operator/do';
import { Subscription } from 'rxjs/Subscription';

import { FlowService } from '../compose/flow.service';
import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { IModalButton, ModalComponent } from '../core-ui/components/modal.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { mocksMode } from '../core/core.mocks-only';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IExperimentPipelineForm } from '../experiments/experiment-pipeline.component';
import { ITable, ITableColumnExt } from '../tables/table.interface';
import { ActivityObserver } from '../utils/activity-observer';

import { IModelColumn, IModelHelpers, ITabularTrainPipeline } from './model.interface';
import { trainConfig } from './train.config';

export enum PendingType {
  INPUT_TABLE = 'INPUT_TABLE',
  HOLD_OUT_TABLE = 'HOLD_OUT_TABLE',
  OUT_OF_TIME_TABLE = 'OUT_OF_TIME_TABLE',
}

@Component({
  selector: 'model-create',
  template: `
    <div class="row">
      <div class="col-md-6">
        <library-selector
          [inputLabel]="'Input Data'"
          [available]="[config.asset.values.TABLE, config.asset.values.FLOW]"
          [value]="inputEntity"
          (valueChange)="onInputChange($event)"
          [caption]="'Select Training Input Data'"
        ></library-selector>
      </div>
    </div>
    <div class="row" *ngIf="inputEntity?.entity === config.asset.values.FLOW">
      <div class="col-md-6">
        <app-select [label]="'Specify Training Table'"
          [value]="form.value['input']"
          [options]="_flowTables | map: _tableToSelectOption"
          (valueChange)="onFlowTableChange($event)"></app-select>
      </div>
    </div>

    <app-form-group
      [caption]="'Validation Data'"
      [helpText]="trainConfig.hints.validationHelpText"
    >
      <div class="row">
        <div class="col-md-6">
          <library-selector
            [inputLabel]="'Hold-Out Data'"
            [allowReset]="true"
            [available]="[config.asset.values.TABLE]"
            [(value)]="holdOutInput"
            (valueChange)="trySetHoldOut($event)"
            [caption]="'Select Hold-Out Table'"></library-selector>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <library-selector
            [inputLabel]="'Out-Of-Time Data'"
            [allowReset]="true"
            [available]="[config.asset.values.TABLE]"
            [(value)]="outOfTimeInput"
            (valueChange)="trySetOutOfTimeTable($event)"
            [caption]="'Select Out-Of-Time Table'"></library-selector>
        </div>
        <ng-template [mocksOnly]="true">
          <div class="col-md-6" *ngIf="form.value['outOfTimeInput'] || form.value['holdOutInput']">
            <app-input [label]="'Validation Threshold'" [control]="form.controls['validationThreshold']"
              [type]="'number'" [min]="0" [max]="1" [step]="0.1"
            ></app-input>
          </div>
        </ng-template>
      </div>
    </app-form-group>
    <div class="row">
      <div class="col-md-6">
        <app-select [label]="'Sampling Weight Column'"
          [allowNull]="true"
          [control]="form.controls['samplingWeightColumn']"
          [options]="inputTable?.columns | tableColumnSelectOptions"
        ></app-select>
      </div>
    </div>
    <app-tabs
      [tabs]="TABS | map: _tabToLabelFn"
      [hiddenTabs]="hiddenTabs"
      [(active)]="activeTab"></app-tabs>
    <!-- Tab panes -->
    <div class="flex-col" [adaptiveHeight]="{minHeight: 450}" [hidden]="activeTab !== TAB_INPUT">
      <div *ngIf="!form.value['input']" style="min-height: 10em;background: white;padding-top: 1px;">
        <h4 class="text-center text-muted text-light brand-tab">No Table Selected</h4>
      </div>
      <table-view-embed *ngIf="form.value['input']" [editMode]="true"
        [columnOptions]="form.value | apply: _prepareColumnOptions"
        [id]="form.value['input']" (columnOptionsChange)="onColumnOptionsValue($event)"
      ></table-view-embed>
    </div>
    <ng-template [mocksOnly]="true">
      <div class="panel"
        [hidden]="activeTab !== TAB_ADVANCED">
        <div class="panel-body">
          <model-create-advanced
            (valueChange)="form.controls['trainOptions'].setValue($event)"
          ></model-create-advanced>
        </div>
      </div>
    </ng-template>
    <div class="flex-col" [adaptiveHeight]="{minHeight: 450}" [hidden]="activeTab !== TAB_HOLD_OUT_INPUT">
      <table-view-embed *ngIf="form.value['holdOutInput']"
        [modelHelpers]="form.value | apply: _prepareModelHelpers"
        [id]="form.value['holdOutInput']"
      ></table-view-embed>
    </div>
    <div class="flex-col" [adaptiveHeight]="{minHeight: 450}" [hidden]="activeTab !== TAB_OUT_OF_TIME_INPUT">
      <table-view-embed *ngIf="form.value['outOfTimeInput']"
        [modelHelpers]="form.value | apply: _prepareModelHelpers"
        [id]="form.value['outOfTimeInput']"
      ></table-view-embed>
    </div>
    <app-modal #tableWarningModal
      [caption]="'Warning'"
      [buttons]="[{'class': 'btn-primary', 'title': 'Yes'}, {'class': 'btn-secondary', 'title': 'No'}]"
      (buttonClick)="onTableWarningModalButton($event)">
      Table you have selected doesn't have some of currently selected columns. Are you sure want to selected this table?
    </app-modal>
  `,
})
export class ModelCreateComponent implements OnDestroy, OnInit, IExperimentPipelineForm {
  @Output() validityChange: EventEmitter<boolean>;
  @Output() dataChange: EventEmitter<{ [key: string]: any; }>;

  inputEntity: LibrarySelectorValue;
  readonly config = config;
  readonly trainConfig = trainConfig;
  readonly TAB_INPUT = 0;
  readonly TAB_HOLD_OUT_INPUT = 1;
  readonly TAB_OUT_OF_TIME_INPUT = 2;
  readonly TAB_ADVANCED = 3;
  readonly TABS = [
    { label: 'Input Table', index: this.TAB_INPUT },
    { label: 'Hold-Out Table Input', index: this.TAB_HOLD_OUT_INPUT },
    { label: 'Out-Of-Time Table Input', index: this.TAB_OUT_OF_TIME_INPUT },
    { label: 'Advanced Options', index: this.TAB_ADVANCED },
  ];
  readonly covariateConfig = config.model.column.covariate;
  readonly form: FormGroup;
  readonly _savingObserver = new ActivityObserver();
  activeTab: number = 0;
  inputTable: ITable;
  holdOutInput: LibrarySelectorValue;
  outOfTimeInput: LibrarySelectorValue;
  _flowTables: ITable[] = [];
  hiddenTabs: number[] = [
    this.TAB_HOLD_OUT_INPUT,
    this.TAB_OUT_OF_TIME_INPUT,
    this.TAB_ADVANCED,
  ];
  @ViewChild('tableWarningModal') private tableWarningModal: ModalComponent;
  private _pendingInputTable: ITable;
  private formSubscription: Subscription = new Subscription();
  private modelHelpers: IModelHelpers;
  private pendingType: PendingType;

  constructor(
    private flows: FlowService,
  ) {
    this.form = new FormGroup({
      input: new FormControl(null, Validators.required), // sends on server control
      holdOutInput: new FormControl(null),
      outOfTimeInput: new FormControl(null),
      responseColumns: new FormControl([], [
        Validators.required,
        Validators.minLength(1),
        Validators.maxLength(1),
      ]),
      predictorColumns: new FormControl([], [
        Validators.required,
        Validators.minLength(1),
      ]),
      trainOptions: new FormControl(null),
      validationThreshold: new FormControl(0.95),
      samplingWeightColumn: new FormControl(null),
    });

    this.formSubscription.add(this.form.valueChanges.subscribe((value) => {
      this.toggleTab(this.TAB_HOLD_OUT_INPUT, !!value.holdOutInput);
      this.toggleTab(this.TAB_OUT_OF_TIME_INPUT, !!value.outOfTimeInput);

      this.modelHelpers = { predictorColumns: value['predictorColumns'] };

      if (value['responseColumns'].length) {
        Object.assign(this.modelHelpers, {
          responseColumn: value['responseColumns'][0],
        });
      }

      this.dataChange.emit(this.form.valid ? this.getPipelineDataToSubmit() : null);
    }));
    this.formSubscription.add(this.form.statusChanges.subscribe(() => this.validityChange.emit(this.form.valid)));

    this.toggleTab(this.TAB_ADVANCED, mocksMode);
  }

  ngOnInit(): void {
    // emit initial validity
    this.validityChange.emit(this.form.valid);
  }

  onModalAction(set: boolean) {
    switch (this.pendingType) {
      case PendingType.INPUT_TABLE:
        if (set) {
          this.setTable(this._pendingInputTable);
        } else {
          this.inputEntity = {
            id: this.form.value['input'],
            entity: IAsset.Type.TABLE,
            object: null,
          };
        }
        break;
      case PendingType.HOLD_OUT_TABLE:
        if (set) {
          this.setHoldOut(<ITable> this.holdOutInput.object);
        } else {
          this.holdOutInput = {
            id: this.form.value['holdOutInput'],
            entity: IAsset.Type.TABLE,
            object: null,
          };
        }
        break;
      case PendingType.OUT_OF_TIME_TABLE:
        if (set) {
          this.setOutOfTime(<ITable> this.holdOutInput.object);
        } else {
          this.outOfTimeInput = {
            id: this.form.value['outOfTimeInput'],
            entity: IAsset.Type.TABLE,
            object: null,
          };
        }
        break;
      default:
        throw new Error('I\'m a teapot');
    }
  }

  onTableWarningModalButton(button: IModalButton) {
    this.tableWarningModal.hide();
    this.onModalAction(button.title === 'Yes');
  }

  filterTableColumns(table: ITable) {
    const selectedPredictors = (<IModelColumn[]> this.form.controls['predictorColumns'].value).filter(value => value && value.name);
    const selectedResponses = (<IModelColumn[]> this.form.controls['responseColumns'].value).filter(value => value && value.name);
    if (table) {
      const filteredPredictors = selectedPredictors
        .filter(predictor => table.columns.map(column => column.name).indexOf(predictor.name) > -1);
      const filteredResponses = selectedResponses
        .filter(response => table.columns.map(column => column.name).indexOf(response.name) > -1);
      this.form.controls['predictorColumns'].setValue(filteredPredictors);
      this.form.controls['responseColumns'].setValue(filteredResponses);
    }
  }

  checkTableColumns(table: ITable): boolean {
    if (!table) {
      return true;
    }
    const selectedColumns = (<IModelColumn[]> this.form.controls['predictorColumns'].value)
      .concat(<IModelColumn[]> this.form.controls['responseColumns'].value)
      .filter(value => value && value.name);

    return selectedColumns.every(selectedColumn => !!table.columns.find(tableColumn => tableColumn.name === selectedColumn.name));
  }

  ngOnDestroy() {
    this.formSubscription && this.formSubscription.unsubscribe();
  }

  getPipelineDataToSubmit(): ITabularTrainPipeline {
    return {
      input: this.form.value.input,
      holdOutInput: this.form.value.holdOutInput,
      outOfTimeInput: this.form.value.outOfTimeInput,
      predictorColumns: this.form.value.predictorColumns.map((item: any) => {
        return {
          name: item.name,
          displayName: item.displayName,
          dataType: item.dataType,
          variableType: item.variableType,
        };
      }),
      responseColumn: this.form.value.responseColumns.map((item: any) => {
        return {
          name: item.name,
          displayName: item.displayName,
          dataType: item.dataType,
          variableType: item.variableType,
        };
      }).shift(),
      trainOptions: this.form.value.trainOptions,
      validationThreshold: this.form.value.validationThreshold,
      samplingWeightColumn: this.form.value.samplingWeightColumn,
    };
  }

  toggleTab(index: number, show: boolean) {
    if (show) {
      this.hiddenTabs = this.hiddenTabs.filter(_ => _ !== index);
    } else if (this.hiddenTabs.indexOf(index) === -1) {
      this.hiddenTabs.push(index);
    }
  }

  trySetTable(value: ITable) {
    if (value) {
      if (this.checkTableColumns(value)) {
        this.setTable(value);
      } else {
        this.pendingType = PendingType.INPUT_TABLE;
        this._pendingInputTable = value;
        this.tableWarningModal.show();
      }
    }
  }

  setTable(value: ITable) {
    this.inputTable = value;
    this.form.controls['input'].setValue(value.id);
  }


  trySetHoldOut(value: LibrarySelectorValue) {
    this.holdOutInput = value;
    if (value) {
      if (this.checkTableColumns(<ITable> value.object)) {
        this.setHoldOut(<ITable> value.object);
      } else {
        this.pendingType = PendingType.HOLD_OUT_TABLE;
        this.tableWarningModal.show();
      }
    } else {
      this.setHoldOut(null);
    }
  }

  setHoldOut(value: ITable) {
    this.form.controls['holdOutInput'].setValue(value ? value.id : null);
    this.filterTableColumns(value);
  }

  trySetOutOfTimeTable(value: LibrarySelectorValue) {
    this.outOfTimeInput = value;
    if (value) {
      if (this.checkTableColumns(<ITable> value.object)) {
        this.setOutOfTime(<ITable> value.object);
      } else {
        this.pendingType = PendingType.OUT_OF_TIME_TABLE;
        this.tableWarningModal.show();
      }
    } else {
      this.setOutOfTime(null);
    }
  }

  setOutOfTime(value: ITable) {
    this.form.controls['outOfTimeInput'].setValue(value ? value.id : null);
    this.filterTableColumns(value);
  }

  onFlowTableChange(value: TObjectId) {
    const table = this._flowTables.find(table => table.id === value);
    this.trySetTable(table);
  }

  onColumnOptionsValue(data: ITableColumnExt[]) {
    const columnsData: ITableColumnExt[] = data || [];
    let predictorColumns = [];
    let responseColumns = [];

    columnsData.forEach(item => {
      if (item.covariateType === this.covariateConfig.values.RESPONSE) {
        responseColumns.push(item);
      }

      if (item.covariateType === this.covariateConfig.values.PREDICTOR) {
        predictorColumns.push(item);
      }
    });

    this.form.controls['responseColumns'].setValue(responseColumns);
    this.form.controls['predictorColumns'].setValue(predictorColumns);
  }

  onInputChange(value: LibrarySelectorValue) {
    this._flowTables = [];
    if (value.entity === IAsset.Type.TABLE) {
      this.trySetTable(<ITable> value.object);
    } else if (value.entity === IAsset.Type.FLOW) {
      this.flows.getTables(value.id).subscribe(_ => {
        this._flowTables = _.data;
      });
    } else {
      throw new Error('I\'m a teapot');
    }
  }

  _tableToSelectOption(table: ITable): AppSelectOptionData {
    return {
      id: table.id,
      text: table.name,
    };
  }

  _tabToLabelFn(item) {
    return item.label;
  }

  _prepareColumnOptions = (value): ITableColumnExt[] => {
    return value['responseColumns'].concat(value['predictorColumns']);
  };

  _prepareModelHelpers = (value): IModelHelpers => {
    return {
      responseColumn: value['responseColumns'] ? value['responseColumns'][0] : null,
      predictorColumns: value['predictorColumns'],
    };
  };
}

