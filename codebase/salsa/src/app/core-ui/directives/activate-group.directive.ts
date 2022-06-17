import { AfterViewInit, Directive, ElementRef, NgZone } from '@angular/core';

@Directive({ selector: '[activateGroup]' })
export class ActivateGroupDirective implements AfterViewInit {
  constructor(
    private el: ElementRef,
    private zone: NgZone,
  ) {
  }

  ngAfterViewInit(): void {
    const element = jQuery(this.el.nativeElement);
    this.zone.runOutsideAngular(() => {
      element.on('focus', () => {
        element.is('[readonly]') || element.parent().find('.input-group-addon').addClass('active');
      });
      element.on('blur', () => {
        element.parent().find('.input-group-addon').removeClass('active');
      });
    });
  }
}
