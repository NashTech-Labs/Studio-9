import { Component, EventEmitter, Input, Output } from '@angular/core';

import { IScriptDeployment } from './script-deployment.interface';

@Component({
  selector: 'deploy-script-deployment-pipeline-params',
  template: `
    <ng-container [ngSwitch]="mode">
      <deploy-script-deployment-cv-3stl-detection
        *ngSwitchCase="scriptDeploymentType.CV_3STL_DETECTION"
        [params]="params"
        [disabled]="disabled"
        (paramsChange)="paramsChange.emit($event)"
        (validityChange)="validityChange.emit($event)"
      ></deploy-script-deployment-cv-3stl-detection>
    </ng-container>
  `,
})
export class ScriptDeploymentPipelineParamsComponent {
  @Input() mode: IScriptDeployment.Mode;
  @Input() disabled: boolean;
  @Input() params: IScriptDeployment.Params;

  @Output() paramsChange = new EventEmitter<IScriptDeployment.Params>();
  @Output() validityChange = new EventEmitter<boolean>();

  readonly scriptDeploymentType = IScriptDeployment.Mode;
}
