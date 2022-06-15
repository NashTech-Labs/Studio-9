import { Directive, HostListener, Input } from '@angular/core';

@Directive({
  selector : '[prevent-default-click]',
})
export class PreventDefaultClickDirective {
  @Input() href;
  @HostListener('click', ['$event']) preventDefault(event: MouseEvent) {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
      event.cancelBubble = true;
    }
  }
}
