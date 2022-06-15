import {
  Directive,
  ElementRef,
  Inject,
  InjectionToken,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Optional,
} from '@angular/core';


export const TOOLTIP_CONTAINER_REF: InjectionToken<string> =
  new InjectionToken('TOOLTIP_CONTAINER_REF');


@Directive({
  selector: '[tooltip]',
})
export class TooltipDirective implements OnChanges, OnInit, OnDestroy {
  @Input() tooltipTitle: string;
  @Input() tooltipContainer: string;

  private $el: JQuery;

  constructor(
    el: ElementRef,
    @Optional() @Inject(TOOLTIP_CONTAINER_REF) containerRef: string,
  ) {
    this.$el = $(el.nativeElement);
    if (containerRef) {
      this.tooltipContainer = containerRef;
    }
  }

  ngOnChanges() {
    this.$el.attr('data-original-title', this.tooltipTitle);

    // TODO: find more appropriate way to detect shown tooltip
    if (this.$el.attr('aria-describedby')) {
      this.$el.tooltip('show');
    }
  }

  ngOnInit() {
    this.$el.tooltip && this.$el.tooltip({
      container: this.tooltipContainer || false,
    });
  }

  ngOnDestroy() {
    this.$el.tooltip && this.$el.tooltip('destroy');
  }
}
