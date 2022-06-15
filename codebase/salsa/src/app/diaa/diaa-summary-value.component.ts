import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'diaa-summary-value',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <i class="glyphicon" [ngClass]="{
      'glyphicon-arrow-up': value > originalValue,
      'glyphicon-arrow-down': value < originalValue,
      'glyphicon-minus': value === originalValue
    }"></i>
    {{value | number: '1.0-3'}}
    /
    {{originalValue | number: '1.0-3'}}
  `,
  styles: [`
    .glyphicon-arrow-up { color: green; }
    .glyphicon-arrow-down { color: red; }
    .glyphicon-minus { color: blue; }
  `],
})

export class DIAASummaryValueComponent {
  @Input() value: number;
  @Input() originalValue: number;
}
