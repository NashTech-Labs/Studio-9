import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import 'rxjs/add/operator/do';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { TableService } from '../tables/table.service';
import { ITabularModel } from '../train/model.interface';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';

import { IPlayChildComponent } from './play-create.component';
import { IPrediction, IPredictionCreate } from './prediction.interface';
import { PredictionService } from './prediction.service';

// TODO: add validator's errors

@Component({
  selector: 'app-play-model',
  template: `
    <form [formGroup]="form" class="brand-tab">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Results Name'" [control]="form.controls['outputTableName']"></app-input>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
        </div>
      </div>
      <app-tabs [tabs]="['Model Info', 'Input']" [(active)]="activeTab" ></app-tabs>
      <div class="tab-content brand-tab">
        <div role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 0}">
          <model-view-embed
            [modelId]="model.id"
          ></model-view-embed>
        </div>
        <div role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 1}">
          <div class="panel" *ngVar="form.controls['input'] as source"><div class="panel-body">
            <app-spinner [visibility]="tableSelectObserver.active | async"></app-spinner>
            <div class="row">
              <div class="col-xs-6">
                <a>Input:</a>
              </div>
              <div class="col-xs-6">
                <library-selector
                  [inputLabel]="'Select Mapping Table'"
                  [value]="{id: source.controls['mappedTableId'].value, entity: config.asset.values.TABLE}"
                  (valueChange)="onChangeTableForMapping($event.id, source)"
                  [available]="[config.asset.values.TABLE]"
                  [caption]="'Select Asset'"></library-selector>
              </div>
            </div>
            <hr />
            <div class="row" *ngFor="let columnControl of source.controls.columns['controls']">
              <div class="col-md-6">
                <span dropdown class="pos-rel">
                  <a href="#"
                  class="dropdownColumn"
                  data-toggle="dropdown"
                  aria-haspopup="true"
                  aria-expanded="false">
                  {{ columnControl.value.displayName }}
                  </a>
                  <div class="dropdown-menu font-weight-normal text-nowrap" aria-labelledby="dropdownColumn">
                    <table class="table table-borderless-top table-no-margin-bottom">
                      <tr>
                        <td>Var Type</td>
                        <td>{{config.table.column.variableType.labels[columnControl.value.variableType]}}</td>
                      </tr>
                      <tr>
                        <td>Data Type</td>
                        <td>{{config.table.column.dataType.labels[columnControl.value.dataType]}}</td>
                      </tr>
                    </table>
                  </div>
                </span>
              </div>
              <div class="col-md-6">
                <app-select
                  [control]="columnControl.controls.value"
                  [options]="source.controls.mappedColumns.value | tableColumnSelectOptions | apply: _getAvailableInputColumns: selectedInputColumns : columnControl.controls.value.value"
                  [allowNull]="true"
                  (valueChange)="updateSelectedColumns()"
                ></app-select>
              </div>
            </div>
          </div></div>
        </div>
      </div>
    </form>
  `,
  styles: [`
    .dropdown-menu {
      left: 15px;
    }
  `],
})
export class PlayPredictionCreateComponent implements OnChanges, IPlayChildComponent {
  @Input() model: ITabularModel;
  @Output() changeValidity: EventEmitter<boolean> = new EventEmitter<boolean>();
  readonly config = config;
  readonly tableSelectObserver: ActivityObserver = new ActivityObserver();
  form: AppFormGroup<{ input: FormGroup; outputTableName: FormControl; description: FormControl; id: FormControl }>;
  activeTab: number = 0;
  selectedInputColumns: string[] = [];

  constructor(
    private tables: TableService,
    private predictions: PredictionService,
    private router: Router,
  ) {}

  ngOnChanges(changes: SimpleChanges) {
    if ('model' in changes) {
      this.onModelChanged();
    }
  }

  /** Store selected columns to filter for availability */
  updateSelectedColumns(): void {
    const tab = this.form['controls']['input'];
    this.selectedInputColumns = tab['controls']['columns']['controls']
      .map((_: FormControl) => _.value.value)
      .filter(_ => _);
  }

  _getAvailableInputColumns(
    allColumns: AppSelectOptionData[],
    selectedInputColumns: string[],
    currentValue: string,
  ): AppSelectOptionData[] {
    return allColumns.map(_ => ({
      ..._,
      disabled: _.id !== currentValue // let current option value in
        && !!selectedInputColumns.find(selectedCol =>  _.id === selectedCol),
    }));
  }

  submit() {
    let formValue = this.form.value;
    let prediction: IPredictionCreate = {
      modelId: formValue.id,
      name: formValue.outputTableName,
      description: formValue.description,
      input: formValue.input.mappedTableId,
      outputTableName: formValue.outputTableName,
      columnMappings: formValue.input.columns.map((column) => {
        return {
          sourceColumn: column.name,
          mappedColumn: column.value,
        };
      }),
    };
    return this.predictions.create(prediction).do((prediction: IPrediction) => {
      this.router.navigate(['/desk', 'play', 'predictions', prediction.id]);
    });
  }

  clear() {
    this.initForm();
  }

  onChangeTableForMapping(tableId: TObjectId, formGroup: FormGroup): void {
    this.tableSelectObserver.observe(this.tables.get(tableId).do(table => {
      formGroup.controls['mappedTableId'].setValue(table.id);

      let columns = table.columns;
      formGroup.controls['mappedColumns'] = new FormControl(columns);
      (<FormArray> formGroup.controls['columns']).controls.forEach(
        (columnControl: FormGroup) => {
          let found = columns.find(column => column.name === columnControl.controls['name'].value);
          columnControl.controls['value'].setValue(found ? found.name : null);
          columnControl.controls['value'].enable();
          columnControl.controls['value'].markAsDirty();
        },
      );
      this.updateSelectedColumns();
    })).subscribe();
  }

  private onModelChanged() {
    this.initForm();
    if (this.model) {
      this.generateTabFromTrainingData(this.model);
      this.activeTab = 0;
    }
  }

  private initForm() {
    this.form = new AppFormGroup({
      id: new FormControl(this.model.id),
      description: new FormControl(null),
      outputTableName: new FormControl(null, Validators.required),
      input: new FormGroup({}),
    });
    this.form.valueChanges.subscribe(() => {
      this.changeValidity.emit(this.form.valid);
    });
  }

  private generateTabFromTrainingData(model: ITabularModel): void {
    this.form.removeControl('input');

    const formGroup: FormGroup = new FormGroup({});

    formGroup.addControl('mappedTableId', new FormControl(null, Validators.required));
    formGroup.addControl('mappedColumns', new FormControl([], Validators.required));
    formGroup.addControl('columns', new FormArray([], Validators.required));
    model.predictorColumns.forEach(predictor => {
      const valueControl = new FormControl(null, Validators.required);
      valueControl.disable();
      (<FormArray> formGroup.controls['columns']).push(new FormGroup({
        name: new FormControl(predictor.name),
        displayName: new FormControl(predictor.displayName),
        dataType: new FormControl(predictor.dataType),
        variableType: new FormControl(predictor.variableType),
        value: valueControl,
      }));
    });

    this.form.addControl('input', formGroup);
  }
}
