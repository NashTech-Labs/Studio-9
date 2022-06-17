import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-form-group',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="brand-form-group">
    <div class="brand-form-group-label" *ngIf="caption">
      {{caption}} <i class="glyphicon glyphicon-question-sign" *ngIf="helpText"
      tooltip
      data-toggle="tooltip"
      data-html="true"
      [tooltipTitle]="helpText"></i>
    </div>
    <div class="brand-form-group-body">
      <ng-content></ng-content>
    </div>
  </div>`,
})
export class AppFormGroupComponent {
  @Input() caption: string;
  @Input() helpText: string;
}
