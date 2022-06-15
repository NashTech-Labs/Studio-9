import { Directive, ElementRef, Input, NgZone, OnChanges, OnDestroy, OnInit } from '@angular/core';

@Directive({ selector: '[stickyHeader]' })
export class StickyHeaderDirective implements OnChanges, OnInit, OnDestroy {
  @Input('stickyHeader') enabled: boolean = true;
  private el: any;

  constructor(
    el: ElementRef,
    private zone: NgZone,
  ) {
    this.el = el.nativeElement;
  }

  ngOnInit() {
    this.zone.runOutsideAngular(() => {
      window.addEventListener('scroll', this._update);
      window.addEventListener('resize', this._update);
    });
    this._update();
  }

  ngOnChanges(): void {
    this._update();
    this.zone.runOutsideAngular(() => {
      window.setTimeout(() => this._update());
    });
  }

  ngOnDestroy(): void {
    this.zone.runOutsideAngular(() => {
      window.removeEventListener('scroll', this._update);
      window.removeEventListener('resize', this._update);
    });
  }

  private _update = (): void => {
    if ($(this.el).is(':visible')) {
      const elementOffset = $(this.el).offset().top;
      const elementPositionSet = parseFloat($(this.el).css('top')) || 0;
      const elementPosition = $(this.el).position().top;
      const elementMargin = elementPosition - elementPositionSet;
      const parentHeight = $(this.el).offsetParent().height();
      const elementHeight = $(this.el).outerHeight();
      const windowScroll = $(window).scrollTop();

      const maxPosition = parentHeight - elementHeight - elementMargin;

      const targetPosition = Math.min(maxPosition, windowScroll - (elementOffset - elementPositionSet));
      if (this.enabled && targetPosition > 0) {
        $(this.el).css({
          position: 'relative',
          top: `${targetPosition}px`,
          zIndex: 1000,
        });
      } else {
        $(this.el).css({
          position: 'initial',
          top: '',
          zIndex: '',
        });
      }
    }
  }
}
