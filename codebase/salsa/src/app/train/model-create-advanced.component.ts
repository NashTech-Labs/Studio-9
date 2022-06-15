import { DecimalPipe } from '@angular/common';
import { Component, EventEmitter, OnDestroy, Output } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';

import { ITabularModel } from './model.interface';
import { trainConfig } from './train.config';

@Component({
  selector: 'model-create-advanced',
  template: `
    <app-check [label]="'Generate Variable Importance Summary'"
      [control]="form.controls['variableImportance']">
    </app-check>
    <app-check [label]="'Generate Model Explanation Columns'"
      [control]="form.controls['modelExplanation']">
    </app-check>
    <h3>Define Pipeline Stages</h3>
    <div class="panel-group" role="tablist">
      <div *ngFor="let stage of trainConfig.model.pipelineOrder"
        [capture]="form.controls['stages'].value.indexOf(stage) >= 0"
        #stageActive="capture"
        class="panel"
        [ngClass]="_expandedStage === stage ? 'panel-primary' : 'panel-default'"
      >
        <div class="panel-heading" role="tab"
          [stickyHeader]="_expandedStage === stage">
          <h4 class="panel-title">
            <span class="label train-toggle-btn"
              [ngClass]="stageActive.value ? 'label-success' : 'label-default'"
              (click)="_toggleStage(stage)"
            >
              <i class="glyphicon"
                [ngClass]="stageActive.value ? 'glyphicon-check' : 'glyphicon-unchecked'"
              ></i>
              {{stageActive.value ? 'do' : 'don\\'t do'}}
            </span>

            <a role="button" (click)="stageActive.value && _toggleStageCollapse(stage)">
              {{trainConfig.model.pipelineStage.labels[stage]}}
              <i class="glyphicon pull-right"
                [ngClass]="_expandedStage === stage ? 'glyphicon-chevron-up' : 'glyphicon-chevron-down'"
              ></i>
            </a>
          </h4>
        </div>

        <div
          role="tabpanel"
          class="panel-collapse collapse"
          [ngClass]="{'in': _expandedStage === stage}"
        >
          <ul
            *ngFor="let technique of trainConfig.model.pipelineStage.techniques[stage]"
            [capture]="form.controls['techniques'].value[stage].indexOf(technique) >= 0"
            #techniqueActive="capture"
            class="list-group"
          >
            <li class="list-group-item">
              <span class="label train-toggle-btn"
                [ngClass]="techniqueActive.value ? 'label-primary' : 'label-default'"
                (click)="_toggleTechnique(stage, technique)"
              >
                <i class="glyphicon"
                  [ngClass]="techniqueActive.value ? 'glyphicon-check' : 'glyphicon-unchecked'"
                ></i>
                {{techniqueActive.value ? 'try' : 'exclude'}}
              </span>
              {{trainConfig.model.stageTechnique.labels[technique]}}
            </li>
            <ng-template [ngIf]="techniqueActive.value && trainConfig.model.stageTechnique.params[technique].length > 0">
              <li *ngFor="let param of trainConfig.model.stageTechnique.params[technique]"
                class="list-group-item"
                [ngSwitch]="param.type"
              >
                <app-select *ngSwitchCase="'categorical'"
                  [label]="param.title"
                  [options]="param.options"
                  [multiple]="true"
                  [control]="form.controls['parameters'].controls[technique].controls[param.name]"
                ></app-select>
                <app-slider *ngSwitchCase="'continuous'"
                  [label]="param.title"
                  [range]="true"
                  [min]="param.min"
                  [max]="param.max"
                  [value]="form.controls['parameters'].controls[technique].controls[param.name].value"
                  (valueChange)="form.controls['parameters'].controls[technique].controls[param.name].setValue($event)"
                  [step]="param.step || 0.01"
                  [formatter]="_formatRange"
                ></app-slider>
              </li>
              <li *ngIf="trainConfig.model.stageTechnique.params[technique].length === 0"
                class="list-group-item"
              >
                No parameters required
              </li>
            </ng-template>
          </ul>
        </div>
      </div>
    </div>
  `,
})
export class ModelCreateAdvancedComponent implements OnDestroy {
  @Output() valueChange: EventEmitter<ITabularModel.TrainOptions> = new EventEmitter<ITabularModel.TrainOptions>();

  readonly config = config;
  readonly trainConfig = trainConfig;

  form: FormGroup;
  _expandedStage: ITabularModel.PipelineStage = null;

  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  private formSubscription: Subscription;

  constructor(
  ) {
    this.form = new FormGroup({
      variableImportance: new FormControl(false),
      modelExplanation: new FormControl(false),
      stages: new FormControl(this.trainConfig.model.pipelineOrder, Validators.minLength[1]),
      techniques: new FormGroup(this.trainConfig.model.pipelineStage.list.reduce((acc, stage) => {
        acc[stage] = new FormControl(
          this.trainConfig.model.pipelineStage.defaultTechniques[stage] || this.trainConfig.model.pipelineStage.techniques[stage],
        );
        return acc;
      }, {})),
      parameters: new FormGroup(this.trainConfig.model.stageTechnique.list.reduce((acc, technique) => {
        acc[technique] = new FormGroup(this.trainConfig.model.stageTechnique.params[technique].reduce((acc, param) => {
          if (param.type === 'categorical') {
            acc[param.name] = new FormControl(param.options);
          } else if (param.type === 'continuous') {
            acc[param.name] = new FormControl([param.min, param.max]);
          }
          return acc;
        }, {}));
        return acc;
      }, {})),
    });

    this.formSubscription = this.form.valueChanges.subscribe((value) => {
      this._emitValueChange(value);
    });
  }

  ngOnDestroy() {
    this.formSubscription && this.formSubscription.unsubscribe();
  }

  _toggleStageCollapse(stage: ITabularModel.PipelineStage) {
    this._expandedStage = (this._expandedStage === stage)
      ? null
      : stage;
  }

  _toggleStage(stage: ITabularModel.PipelineStage) {
    const formControl = this.form.controls['stages'];
    const currentStages: ITabularModel.PipelineStage[] = formControl.value || [];

    if (currentStages.indexOf(stage) >= 0) {
      if (trainConfig.model.requiredStages.indexOf(stage) < 0) {
        const newMetrics = currentStages.filter(_ => _ !== stage);
        formControl.setValue(newMetrics);
        if (this._expandedStage === stage) {
          this._expandedStage = null;
        }
      }
    } else {
      formControl.setValue([stage, ...currentStages]);
    }
  }

  _toggleTechnique(stage: ITabularModel.PipelineStage, technique: ITabularModel.StageTechnique) {
    const formControl = (<FormGroup> this.form.controls['techniques']).controls[stage];
    const currentTechniques: ITabularModel.StageTechnique[] = formControl.value || [];

    if (currentTechniques.indexOf(technique) >= 0) {
      const newTechniques = currentTechniques.filter(_ => _ !== technique);
      formControl.setValue(newTechniques);
    } else {
      formControl.setValue([...currentTechniques, technique]);
    }
  }

  _formatRange = (range: number[]) => {
    return range
      .map(_ => this._decimalPipe.transform(_, '1.0-5'))
      .join(' to ');
  };

  private _emitValueChange(value: ITabularModel.TrainOptions = this.form.value) {
    value.parameters = <any> this.trainConfig.model.stageTechnique.list.reduce((acc, technique) => {
      const valuesHash: {
        [k: string]: [number, number] | number[] | string[],
      } = <any> value.parameters[technique];
      acc[technique] = this.trainConfig.model.stageTechnique.params[technique].map((param): ITabularModel.StageTechniqueParameterConstraint => {
        if (param.type === 'categorical') {
          return {
            name: param.name,
            values: valuesHash[param.name],
          };
        } else if (param.type === 'continuous') {
          const [min, max] = <[number, number]> valuesHash[param.name];
          return {
            name: param.name,
            min, max,
          };
        }
      });
      return acc;
    }, {});
    this.valueChange.emit(value);
  }
}

