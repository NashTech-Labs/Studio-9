import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { IExperimentFull } from '../experiments/experiment.interfaces';
import { ExperimentService } from '../experiments/experiment.service';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { ITabularModel, ITabularTrainPipeline, ITabularTrainResult } from '../train/model.interface';
import { ModelService } from '../train/model.service';
import { ActivityObserver } from '../utils/activity-observer';
import { ReactiveLoader } from '../utils/reactive-loader';

import { diaaConfig } from './diaa.config';
import { IDIAA, IDIAACreate } from './diaa.interface';
import { DecileRangePipe } from './diaa.pipes';
import { DIAAService } from './diaa.service';

export const races = [
  { id: 'race_white', label: 'White' },
  { id: 'race_black', label: 'African-American' },
  { id: 'race_hispanic', label: 'Hispanic' },
  { id: 'race_asian', label: 'Asian' },
];
export const genders = [{ id: 'gender_male', label: 'Male' }, { id: 'gender_female', label: 'Female' }];
export const ages = [{ id: 'age_younger', label: 'Younger' }, { id: 'age_older', label: 'Older' }];


@Component({
  selector: 'diaa-create',
  template: `
    <asset-operations [type]="config.asset.values.DIAA" [selectedItems]="[]"></asset-operations>
    <form [formGroup]="form">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Analysis Name'" [control]="form.controls['name']"></app-input>
        </div>
        <div class="col-md-6">
          <div class="pull-right">
            <button class="btn btn-md btn-apply" (click)="submit()"
              [disabled]="form.invalid || (_savingObserver.active | async)">
              Check Model
            </button>
          </div>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <library-selector
            [inputLabel]="'Select Source Model'"
            [available]="[config.asset.values.MODEL]"
            [value]="{id: form.controls['modelId'].value, entity: config.asset.values.MODEL}"
            (valueChange)="_onSourceModelChanged($event)"
            [caption]="'Select Source Model'"></library-selector>
        </div>
        <div class="col-md-6">
          <app-input [label]="'Alternative Model Name'" [control]="form.controls['outputModelName']"></app-input>
        </div>
      </div>
      <div *mocksOnly="true" class="row">
        <div class="col-md-6">
          <app-fake-control *ngIf="table" [label]="'Sampling Weight Column'"
            [value]="(trainPipeline?.samplingWeightColumn | tableColumnDisplayName : table) || 'None'"
          ></app-fake-control>
        </div>
      </div>

      <app-spinner [visibility]="_loader.active | async"></app-spinner>
      <ng-template [ngIf]="model && table">
        <app-tabs [tabs]="['Original Model Summary', 'AIR/SMD Specification']" [(active)]="activeTab"></app-tabs>
        <div class="panel" *ngIf="activeTab === 0">
          <div class="panel-body">
            <model-summary
              [summary]="trainResult.summary"
              [holdOutSummary]="trainResult.holdOutSummary"
              [outOfTimeSummary]="trainResult.outOfTimeSummary"
              [trainOptions]="trainPipeline.trainOptions"
              [validationThreshold]="trainPipeline.validationThreshold"
            ></model-summary>
          </div>
        </div>

        <div class="panel" [hidden]="activeTab !== 1">
          <div class="panel-body">
            <h3>Specification</h3>
            <div class="row">
              <div class="col-md-6 col-sm-6 col-lg-6">
                <app-select [label]="'DIAA Objective'"
                  [control]="form.controls['diaaObjective']"
                  [options]="diaaObjectives">
                </app-select>
              </div>
              <div class="col-md-6 col-sm-6 col-lg-6">
                <app-select [label]="'Is Higher Score of Response Variable Favorable'"
                  [control]="form.controls['higherModelScoreFavorable']"
                  [options]="higherModelScoreFavorableOptions"></app-select>
              </div>
            </div>
            <ng-template [ngIf]="form.value.diaaObjective === diaaConfig.diaaObjectives.values.AIR">
              <div class="row">
                <div class="col-md-6 col-sm-6 col-lg-6">
                  <app-select [label]="'Mode'"
                    [control]="form.controls['airSpecification']['controls'].cutOffMode"
                    [options]="_cutOffModes">
                  </app-select>
                </div>
                <div class="col-md-6  col-sm-6 col-lg-6" [ngSwitch]="form.value.airSpecification.cutOffMode">
                  <app-slider *ngSwitchCase="'decile'"
                    [label]="'Decile'"
                    [min]="1" [max]="10" [step]="1"
                    [formatter]="_decileRangeFormatter"
                    [value]="form.value.airSpecification.decile"
                    (valueChange)="form.controls['airSpecification']['controls'].decile.setValue($event)"
                    [range]="true"
                  ></app-slider>
                  <app-input *ngSwitchCase="'percentile'"
                    [label]="'Percentile (up to %)'"
                    [type]="'number'"
                    [min]="1" [max]="100"
                    [control]="form.controls['airSpecification']['controls'].percentile"
                  ></app-input>
                  <app-input *ngSwitchCase="'probability'"
                    [label]="'Min. probability'"
                    [type]="'number'"
                    [control]="form.controls['airSpecification']['controls'].probability"
                  ></app-input>
                </div>
              </div>
            </ng-template>
            <div class="row">
              <div class="col-md-6 col-lg-4">
                <h3>Race</h3>
                <div class="row" *ngFor="let race of races">
                  <div class="col-md-8">
                    <app-select [label]="race.label"
                      [placeholder]="'-select column-'"
                      [control]="form.controls['protectedGroupsColumnMapping']['controls'][race.id]"
                      [options]="table.columns | tableColumnSelectOptions">
                    </app-select>
                  </div>
                </div>
              </div>
              <div class="col-md-6 col-lg-4">
                <h3>Gender</h3>
                <div class="row" *ngFor="let gender of genders">
                  <div class="col-md-8">
                    <app-select [label]="gender.label"
                      [placeholder]="'-select column-'"
                      [control]="form.controls['protectedGroupsColumnMapping']['controls'][gender.id]"
                      [options]="table.columns | tableColumnSelectOptions">
                    </app-select>
                  </div>
                </div>
              </div>
              <div class="col-md-6 col-lg-4">
                <h3>Age</h3>
                <div class="row" *ngFor="let age of ages">
                  <div class="col-md-8">
                    <app-select [label]="age.label"
                      [placeholder]="'-select column-'"
                      [control]="form.controls['protectedGroupsColumnMapping']['controls'][age.id]"
                      [options]="table.columns | tableColumnSelectOptions">
                    </app-select>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </ng-template>
    </form>
  `,
})
export class DIAACreateComponent {
  readonly config = config;
  readonly diaaConfig = diaaConfig;
  readonly form: FormGroup;
  readonly races = races;
  readonly genders = genders;
  readonly ages = ages;
  readonly diaaObjectives: AppSelectOptionData[] = AppSelectOptionData.fromList(diaaConfig.diaaObjectives.list,
    diaaConfig.diaaObjectives.labels);
  readonly _cutOffModes: AppSelectOptionData[] = [
    {id: 'decile', text: 'Decile'},
    {id: 'percentile', text: 'Percentile'},
    {id: 'probability', text: 'Probability of Positive Response'},
  ];
  readonly higherModelScoreFavorableOptions: AppSelectOptionData[] = [
    {id: 0, text: 'No'},
    {id: 1, text: 'Yes'},
  ];
  activeTab: number = 1;
  model: ITabularModel = null;
  trainPipeline: ITabularTrainPipeline;
  trainResult: ITabularTrainResult;
  table: ITable = null;
  readonly _savingObserver = new ActivityObserver();
  readonly _loader: ReactiveLoader<[ITabularModel, IExperimentFull, ITable], TObjectId>;
  private _decileRangePipe: DecileRangePipe = new DecileRangePipe();

  constructor(
    private service: DIAAService,
    private models: ModelService,
    private tables: TableService,
    private experimentService: ExperimentService,
    private router: Router,
  ) {
    this._loader = new ReactiveLoader<[ITabularModel, IExperimentFull, ITable], TObjectId>(id => {
      return this.models.get(id).flatMap((model: ITabularModel) => {
        return this.experimentService.get(model.experimentId).flatMap(experiment => {
          return Observable.forkJoin(
            Observable.of(model),
            Observable.of(experiment),
            this.tables.get((experiment.result as ITabularTrainResult).output),
          );
        });
      });
    }, true);

    this._loader.subscribe(([model, experiment, table]) => {
      this.model = model;
      this.table = table;
      this.trainPipeline = experiment.pipeline as ITabularTrainPipeline;
      this.trainResult = experiment.result as ITabularTrainResult;
      this.form.controls.diaaObjective.setValue(this.determineDIAAObjective());
    });

    const protectedGroupsColumnMappingFormGroup = new FormGroup({});

    races.forEach(race => {
      protectedGroupsColumnMappingFormGroup.addControl(race.id, new FormControl());
    });

    genders.forEach(gender => {
      protectedGroupsColumnMappingFormGroup.addControl(gender.id, new FormControl());
    });

    ages.forEach(age => {
      protectedGroupsColumnMappingFormGroup.addControl(age.id, new FormControl());
    });

    this.form = new FormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      outputModelName: new FormControl(null, Validators.required),
      modelId: new FormControl(null, Validators.required),
      airSpecification: new FormGroup({
        cutOffMode: new FormControl('decile'),
        decile: new FormControl([1, 1]),
        percentile: new FormControl(7),
        probability: new FormControl(0.45),
      }),
      higherModelScoreFavorable: new FormControl(null, Validators.required),
      diaaObjective: new FormControl(null, Validators.required),
      protectedGroupsColumnMapping: protectedGroupsColumnMappingFormGroup,
    });
  }

  submit(): void {
    const data = <IDIAACreate> this.form.value;
    data.higherModelScoreFavorable = !!this.form.value.higherModelScoreFavorable;
    this._savingObserver.observe(this.service.create(data)).subscribe((optimization: IDIAA) => {
      this.router.navigate(['/desk', 'diaa', optimization.id]);
    });
  }

  _onSourceModelChanged(event: LibrarySelectorValue) {
    this.activeTab = 1;
    const keys = [
      ...races.map(race => race.id),
      ...genders.map(gender => gender.id),
      ...ages.map(age => age.id),
    ];
    const emptyValue = keys.reduce((acc, key) => {
      acc[key] = null;
      return acc;
    }, {});
    this.form.patchValue({
      modelId: event.id,
      protectedGroupsColumnMapping: emptyValue,
    });

    this._loader.load(event.id);
  }

  _decileRangeFormatter = (value: [number, number]) => {
    return this._decileRangePipe.transform(value);
  };

  private determineDIAAObjective(): IDIAA.DIAAObjective {
    if (this.model.responseColumn.variableType === ITable.ColumnVariableType.CONTINUOUS) {
      return IDIAA.DIAAObjective.SMD;
    }
    return IDIAA.DIAAObjective.AIR;
  }
}
