import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

import * as _ from 'lodash';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { forkJoin } from 'rxjs/observable/forkJoin';

import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { AssetReferenceParameterDefinition } from '../core/interfaces/params.interface';
import { Pipeline, PipelineOperator } from '../pipelines/pipeline.interfaces';
import { PipelineService } from '../pipelines/pipeline.service';
import { ActivityObserver } from '../utils/activity-observer';
import { ReactiveLoader } from '../utils/reactive-loader';

import { OnlineAPIPramsForm } from './online-api-create.component';
import { IOnlineAPI } from './online-api.interface';

interface PipelineAssetParameterReference {
  stepId: string;
  name: string;
  caption: string;
  assetType: IAsset.Type;
}

@Component({
  selector: 'deploy-online-api-create-pipeline',
  template: `
    <app-form-group [caption]="'Pipeline Parameters'" *ngIf="!!_pipelineAssetParameters.length">
      <div class="row">
        <div class="col-md-6" *ngFor="let parameter of _pipelineAssetParameters">
          <library-selector
            [inputLabel]="parameter.caption"
            [value]="parameter | apply: getAssetSelection: this._value"
            (valueChange)="setAssetParameter(parameter, $event)"
            [available]="[parameter.assetType]"
          ></library-selector>
        </div>
      </div>
    </app-form-group>
  `,
})
export class OnlineAPICreatePipelineComponent implements OnChanges, OnlineAPIPramsForm<IAsset.Type.PIPELINE> {
  @Input() pipelineId: TObjectId;
  readonly validity = new BehaviorSubject<boolean>(false);

  readonly config = config;
  readonly _savingObserver = new ActivityObserver();

  protected _value: IOnlineAPI.Params<IAsset.Type.PIPELINE> = {
    steps: [],
  };

  protected _pipelineAssetParameters: PipelineAssetParameterReference[] = [];
  protected _loader: ReactiveLoader<[Pipeline, PipelineOperator[]], any>;

  constructor(
    pipelineService: PipelineService,
  ) {
    this._loader = new ReactiveLoader((id: TObjectId) => {
      return forkJoin(pipelineService.get(id), pipelineService.listAllOperators().map(_ => _));
    });
    this._loader.subscribe(([pipeline, operators]) => {

      // TODO: this component should use same approach as pipline experiment run popup when pipeline parameters are in place
      this._pipelineAssetParameters = pipeline.steps.reduce((acc, step) => {
        const operator = operators.find(_ => _.id === step.operator);
        if (operator) {
          const operatorParams: PipelineAssetParameterReference[] = operator.params
            .filter(param => {
              return param.type === 'assetReference' && param.assetType === IAsset.Type.CV_MODEL
                && (!param.conditions || !Object.keys(param.conditions).length);
            })
            .map((param: AssetReferenceParameterDefinition) => {
              return {
                stepId: step.id,
                name: param.name,
                caption: `${step.name || operator.name} / ${(param.caption || param.name)}`,
                assetType: param.assetType,
              };
            });
          acc.push(...operatorParams);
        }

        return acc;
      }, []);

      this._value = {
        steps: _.cloneDeep(pipeline.steps),
      };

      this._updateValidity();
    });

  }

  get value() {
    return this._value;
  }

  ngOnChanges(changes: SimpleChanges): void {
    this._loader.load(this.pipelineId);
  }

  setAssetParameter(parameter: PipelineAssetParameterReference, $event: LibrarySelectorValue) {
    const oldSteps = this._value.steps.filter(_ => _.id !== parameter.stepId);
    const curStep = _.cloneDeep(this._value.steps.find(_ => _.id === parameter.stepId));
    curStep.params[parameter.name] = $event.id;
    this._value = {
      steps: [...oldSteps, curStep],
    };
    this._updateValidity();
  }

  getAssetSelection = function(
    parameter: PipelineAssetParameterReference,
    value: IOnlineAPI.Params<IAsset.Type.PIPELINE>,
  ): LibrarySelectorValue {
    const curStep = value.steps.find(_ => _.id === parameter.stepId);

    const assetId = <string> curStep.params[parameter.name];

    return assetId
      ? {
        id: assetId,
        entity: parameter.assetType,
      }
      : null;
  };

  private _updateValidity() {
    const valid = _.every(this._pipelineAssetParameters, parameter => {
      const curStep = this._value.steps.find(_ => _.id === parameter.stepId);
      return curStep && curStep.params && curStep.params[parameter.name];
    });

    this.validity.next(valid);
  }


}
