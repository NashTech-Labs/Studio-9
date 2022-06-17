import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-fake-control',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="form-group p0">
      <div class="input-group">
        <label class="input-group-addon input-group-label disabled"
          *ngIf="label || iconBefore"
          [ngClass]="{'label-bare': iconBefore && !label}">
          <i *ngIf="iconBefore" class="glyphicon" [ngClass]="iconBefore"></i>
          {{label}}
        </label>
        <input class="form-control"
          [ngClass]="{'has-label': label}"
          [value]="value" disabled>
        <span *ngIf="iconAfter" class="input-group-addon disabled">
          <i class="glyphicon" [ngClass]="iconAfter"></i>
        </span>
      </div>
    </div>
  `,
})
export class AppFakeControlComponent {
  @Input() label: string = null;
  @Input() value: string | number = null;
  @Input() iconBefore: string = null;
  @Input() iconAfter: string = 'glyphicon-ok';
}
