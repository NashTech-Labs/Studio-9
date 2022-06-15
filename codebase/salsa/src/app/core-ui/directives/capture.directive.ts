import { Directive, Input } from '@angular/core';

@Directive({
  selector: '[capture]',
  exportAs: 'capture',
})
export class CaptureDirective<T> {
  @Input('capture') value: T;
}
