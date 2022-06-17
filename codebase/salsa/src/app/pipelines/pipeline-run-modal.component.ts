import { Component, OnDestroy, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { of } from 'rxjs/observable/of';
import { Subscription } from 'rxjs/Subscription';

import { ModalComponent } from '../core-ui/components/modal.component';
import { ParameterDefinition, ParameterValues } from '../core/interfaces/params.interface';
import { ExperimentType, IExperimentFull } from '../experiments/experiment.interfaces';
import { ExperimentService } from '../experiments/experiment.service';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';

import { IGenericExperiment, Pipeline, PipelineOperator } from './pipeline.interfaces';
import { PipelineService } from './pipeline.service';

@Component({
  selector: 'pipeline-run-modal',
  template: `
    <app-modal #modal
      [captionPrefix]="'Run pipeline'"
      [caption]="pipeline?.name"
      [limitedHeight]="true"
      [buttons]="[{'class': 'btn-primary', disabled: form.invalid || (isSaving$ | async), title: 'Run' }]"
      (buttonClick)="onSubmit()">
      <app-spinner [visibility]="!pipeline || (isSaving$ | async)"></app-spinner>
      <ng-container *ngIf="pipeline">
        <form [formGroup]="form">
          <div class="form-group">
            <app-input
              [label]="'Experiment Name'"
              [control]="form.controls.name">
            </app-input>
          </div>
          <div class="form-group">
            <app-input
              [label]="'Description'"
              [control]="form.controls.description">
            </app-input>
          </div>
          <div class="form-group" *ngFor="let item of pipelineParameters">
            <pipeline-operator-params
              *ngIf="item.parameters.length"
              [parameters]="item.parameters"
              [value]="item.step.params"
              (valueChange)="updateStepParams(item.step, $event)"
            ></pipeline-operator-params>
          </div>
        </form>
      </ng-container>
    </app-modal>
  `,
})
export class PipelineRunModalComponent implements OnDestroy {
  @ViewChild('modal') modal: ModalComponent;

  form: AppFormGroup<{
    name: FormControl,
    description: FormControl,
  }>;
  pipeline: Pipeline;
  operators: {[key: string]: PipelineOperator} = null;
  pipelineParameters: {step: Pipeline.Step, parameters: ParameterDefinition[]}[] = [];
  private _subscription = new Subscription();
  private readonly _savingObserver: ActivityObserver = new ActivityObserver();

  constructor(
    private _pipelineService: PipelineService,
    private _experimentService: ExperimentService,
    private _router: Router,
  ) {
    this.form = new AppFormGroup({
      name: new FormControl('', Validators.required),
      description: new FormControl(''),
    });
  }

  get isSaving$(): Observable<boolean> {
    return this._savingObserver.active;
  }

  ngOnDestroy(): void {
    this._subscription.unsubscribe();
  }

  open(pipelines: Pipeline[]): Observable<void> {
    if (pipelines.length !== 1) {
      throw new Error('Something went wrong');
    }

    this.pipeline = null;
    this.form.reset({
      name: '',
      description: '',
    });

    const pipeline = pipelines[0];

    const pipeline$ = pipeline.steps ? of(_.clone(pipeline)) : this._pipelineService.get(pipeline.id);
    const operators$ = this._pipelineService.listAllOperators();

    this._subscription.add(
      forkJoin(pipeline$, operators$).subscribe(
        ([pipeline, operators]) => {
          this.operators = _.keyBy(operators, _ => _.id);
          this.pipeline = pipeline;
          this.pipelineParameters = this._getPipelineParameters(pipeline);
        },
      ),
    );

    return this.modal.show();
  }

  _getPipelineParameters(pipeline: Pipeline): {step: Pipeline.Step, parameters: ParameterDefinition[]}[] {
    return pipeline.steps.filter(step => !!step.pipelineParameters).map(step => {
      const stepPipelineParams: ParameterDefinition[] = this.operators[step.operator].params
        .filter(param => param.name in step.pipelineParameters)
        .map((param) => {
          return {...param,
            caption: step.pipelineParameters[param.name] || param.caption,
            conditions: _.pickBy(param.conditions, (_c, paramName) => paramName in step.pipelineParameters),
          };
        });

      return {
        step: {...step},
        parameters: stepPipelineParams,
      };
    });
  }

  updateStepParams(step: Pipeline.Step, values: Partial<ParameterValues>): void {
    _.extend(step.params, values);
  }

  onSubmit(): void {
    if (this.form.valid && this.pipeline) {
      this._subscription.add(
        this._savingObserver
          .observe(this._save())
          .do((experiment: IExperimentFull) => {
            this.modal.hide();
            // wait until modal hided
            setTimeout(() => this._router.navigate(['/desk', 'experiments', experiment.id]), 500);
          })
          .subscribe(),
      );
    }
  }

  private _save(): Observable<IExperimentFull> {
    return this._experimentService.create({
      ...this.form.value,
      type: ExperimentType.GenericExperiment,
      pipeline: <IGenericExperiment.Pipeline> {
        steps: this.pipelineParameters.map(({step}) => {
          const availableParams = this.operators[step.operator].params
            .filter(param => this._pipelineService.isParameterAvailable(param, step.params))
            .map(param => param.name);

          return {
            ...step,
            params: _.pick(step.params, ...availableParams),
          };
        }),
      },
    });
  }
}
