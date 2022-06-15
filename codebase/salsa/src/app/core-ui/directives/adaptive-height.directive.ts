import {
  Directive,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';

export interface AdaptiveHeightDirectiveOptions {
  minHeight?: number;
  targetHeight?: number;
  pageMargin?: number;
  property?: string;
  trigger?: any; // changing this value just triggers recalculation
}

const defaultOptions: AdaptiveHeightDirectiveOptions = {
  minHeight: 0,
  pageMargin: 15,
  property: 'max-height',
};

@Directive({ selector: '[adaptiveHeight]' })
export class AdaptiveHeightDirective implements OnChanges, OnInit, OnDestroy {
  @Input('adaptiveHeight') options: AdaptiveHeightDirectiveOptions;
  @Output() onHeightUpdate: EventEmitter<number> = new EventEmitter();
  private el: any;

  constructor(
    el: ElementRef,
    private zone: NgZone,
  ) {
    this.el = el.nativeElement;
  }

  ngOnInit() {
    this.zone.runOutsideAngular(() => {
      window.addEventListener('scroll', this._updateHeightBound);
      window.addEventListener('resize', this._updateHeightBound);
    });
    this._updateHeight();
  }

  ngOnChanges(): void {
    this.options = Object.assign({}, defaultOptions, this.options);
    this._updateHeight();
    this.zone.runOutsideAngular(() => {
      window.setTimeout(() => this._updateHeight());
    });
  }

  ngOnDestroy(): void {
    this.zone.runOutsideAngular(() => {
      window.removeEventListener('scroll', this._updateHeightBound);
      window.removeEventListener('resize', this._updateHeightBound);
    });
  }

  private _updateHeightBound = () => this._updateHeight();

  private _updateHeight(): void {
    if ($(this.el).is(':visible')) {
      const windowHeight = $(window).height() + $(window).scrollTop();
      const elementOffset = $(this.el).offset().top;
      const height = Math.max(this.options.minHeight, windowHeight - elementOffset - this.options.pageMargin);
      $(this.el).css(this.options.property, `${height}px`);
      if ('targetHeight' in this.options) {
        const elementMargin = Math.max(this.options.targetHeight - height, 0);
        $(this.el).css('margin-bottom', `${elementMargin}px`);
      }
      this.onHeightUpdate.emit(height);
    }
  }
}
