import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { ICVPrediction } from './cv-prediction.interface';

@Component({
  selector: 'cv-prediction-time-spent-summary',
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
      <div *ngIf="prediction.predictionTimeSpentSummary as summary"
        class="col-md-6"
      >
        <div class="panel panel-default panel-max-height">
          <div class="panel-heading">
            <h3 class="panel-title">Prediction Time Details</h3>
          </div>
          <div class="panel-body">
            <dl class="dl-horizontal">
              <ng-container
                [ngTemplateOutlet]="commonValuesTemplate"
                [ngTemplateOutletContext]="{
                  summary: summary
                }"
              ></ng-container>

              <dt>Prediction Time:</dt>
              <dd>{{summary.predictionTime | secondsToTime}}
                <ng-container
                  [ngTemplateOutlet]="pipelineTemplate"
                  [ngTemplateOutletContext]="{ summary: summary }"
                ></ng-container>
              </dd>

              <dt>Model Loading Time:</dt>
              <dd>{{summary.modelLoadingTime | secondsToTime}}</dd>
            </dl>
          </div>
        </div>
      </div>

      <div *ngIf="prediction.evaluationTimeSpentSummary as summary"
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
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CVPredictionTimeSpentSummaryComponent {
  @Input() prediction: ICVPrediction;
}
