import { Component, Input } from '@angular/core';

import { ICVEvaluationTimeSpentSummary, ICVModelTrainTimeSpentSummary } from './time-spent.interfaces';

@Component({
  selector: 'cv-train-time-spent-summary',
  template: `
    <ng-template
      #commonValuesTemplate
      let-summary="summary"
    >
      <dt>Tasks Queued Time:</dt>
      <dd>{{summary.tasksQueuedTime | secondsToTime}}</dd>

      <dt>Total Job Time:</dt>
      <dd>{{summary.totalJobTime | secondsToTime}}</dd>

      <dt>Data Loading Time:</dt>
      <dd>{{summary.dataLoadingTime | secondsToTime}}</dd>
    </ng-template>

    <ng-template
      #pipelineTemplate
      let-summary="summary"
    >
      <ng-container *ngIf="summary && summary.pipelineDetails && summary.pipelineDetails.length">
        <div *ngFor="let pipelineDetail of summary.pipelineDetails">
          <strong>{{pipelineDetail.description}}:</strong> {{pipelineDetail.time | secondsToTime}}
        </div>
      </ng-container>
    </ng-template>

    <div class="row-flex">
      <div *ngIf="trainTimeSpentSummary as summary"
        class="col-md-6"
      >
        <div class="panel panel-default panel-max-height">
          <div class="panel-heading">
            <h3 class="panel-title">Train Time Details</h3>
          </div>
          <div class="panel-body">
            <dl class="dl-horizontal">
              <ng-container
                [ngTemplateOutlet]="commonValuesTemplate"
                [ngTemplateOutletContext]="{
                  summary: summary
                }"
              ></ng-container>

              <dt>Training Time:</dt>
              <dd>{{summary.trainingTime | secondsToTime}}
                <ng-container
                  [ngTemplateOutlet]="pipelineTemplate"
                  [ngTemplateOutletContext]="{ summary: summary }"
                ></ng-container>
              </dd>

              <dt>Initial Prediction Time:</dt>
              <dd>{{summary.initialPredictionTime | secondsToTime}}</dd>

              <dt>Model Saving Time:</dt>
              <dd>{{summary.modelSavingTime | secondsToTime}}</dd>
            </dl>
          </div>
        </div>
      </div>

      <div *ngIf="evaluationTimeSpentSummary as summary"
        class="col-md-6"
      >
        <div class="panel panel-default panel-max-height">
          <div class="panel-heading">
            <h3 class="panel-title">Evaluation Time Details</h3>
          </div>
          <div class="panel-body">
            <dl class="dl-horizontal">
              <ng-container
                [ngTemplateOutlet]="commonValuesTemplate"
                [ngTemplateOutletContext]="{
                  summary: summary
                }"
              ></ng-container>

              <dt>Model Loading Time:</dt>
              <dd>{{summary.modelLoadingTime | secondsToTime}}</dd>

              <dt>Score Time:</dt>
              <dd>{{summary.scoreTime | secondsToTime}}
                <ng-container
                  [ngTemplateOutlet]="pipelineTemplate"
                  [ngTemplateOutletContext]="{
                    summary: summary
                  }"
                ></ng-container>
              </dd>
            </dl>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class CVModelTimeSpentSummaryComponent {
  @Input() trainTimeSpentSummary: ICVModelTrainTimeSpentSummary;
  @Input() evaluationTimeSpentSummary: ICVEvaluationTimeSpentSummary;
}
