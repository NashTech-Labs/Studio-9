import { Component, Input, Pipe, PipeTransform } from '@angular/core';

import config from '../../config';
import { IProcess } from '../interfaces/process.interface';
import { ProcessService } from '../services/process.service';

@Pipe({ name: 'combineProcesses', pure: false })
export class CombineProcessesPipe implements PipeTransform {

  transform(processes: IProcess[]): IProcess {
    processes = processes.filter(_ => !!_);

    const ongoing: IProcess = processes.find(_ => _.status !== config.process.status.values.COMPLETED)
      || processes[processes.length - 1];

    const progress = processes.reduce((sum, process) => {
      sum += process.progress;
      return sum;
    }, 0);

    return ongoing ? {
      id: ongoing.id,
      ownerId: ongoing.ownerId,
      targetId: ongoing.targetId,
      started: ongoing.started,
      target: ongoing.target,
      status: ongoing.status,
      progress: (progress / processes.length),
      created: processes[0].created || '',
      jobType: ongoing.jobType,
    } : null;
  }
}

@Component({
  selector: 'process-indicator',
  template: `
    <div class="row">
      <div *ngIf="currentProcess" class="col-md-12">
        <div class="process-circle text-center text-muted"
          [ngSwitch]="currentProcess.status"
          [ngClass]="{
            'process-complete': currentProcess.status === '${IProcess.Status.COMPLETED}',
            'process-queued': currentProcess.status === '${IProcess.Status.QUEUED}',
            'process-running': currentProcess.status === '${IProcess.Status.RUNNING}',
            'process-failed': currentProcess.status === '${IProcess.Status.FAILED}',
            'process-cancelled': currentProcess.status === '${IProcess.Status.CANCELLED}'
          }">

          <i *ngSwitchCase="'${IProcess.Status.COMPLETED}'" class="glyphicon glyphicon-ok"></i>

          <i *ngSwitchCase="'${IProcess.Status.RUNNING}'" class="glyphicon glyphicon-time"></i>

          <i *ngSwitchCase="'${IProcess.Status.QUEUED}'" class="glyphicon glyphicon-tasks"></i>

          <i *ngSwitchCase="'${IProcess.Status.FAILED}'" class="glyphicon glyphicon-exclamation-sign"></i>

          <i *ngSwitchCase="'${IProcess.Status.CANCELLED}'" class="glyphicon glyphicon-minus-sign"></i>

          <timer class="timer" [start]="currentProcess.started || currentProcess.created"></timer>

          <div [ngSwitch]="!!message && currentProcess.status === '${IProcess.Status.RUNNING}'">
            <div *ngSwitchCase="true" class="message text-center">
              {{message}}
            </div>
            <div *ngSwitchCase="false" class="message text-center">
              {{ target || config.process.job.type.labels[currentProcess.jobType] ||
                config.asset.labels[currentProcess.target] || currentProcess.target }}
              is {{ config.process.status.labels[currentProcess.status] }}
            </div>
          </div>

          <div class="percentage text-light">
            {{currentProcess.progress ? (currentProcess.progress * 100).toFixed(0) + '%' : '...' }}
          </div>

          <div class="process-cancel">
            <button class="btn btn-xs"
              (click)="cancelProcess()"
            ><i class="glyphicon glyphicon-remove"></i> Cancel
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class ProcessIndicatorComponent {
  readonly config = config;
  @Input('process') currentProcess: IProcess;
  @Input() target: string = null;
  @Input() message: string = null;

  constructor(
    private processes: ProcessService,
  ) {}

  cancelProcess() {
    this.processes.cancel(this.currentProcess);
  }

}
