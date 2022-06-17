import { Directive, ElementRef, Input, OnDestroy, OnInit, Renderer } from '@angular/core';

// TODO: should we create our own dropdown directive to exclude jQuery bootstrap code using
// also we should combine this dropdown directive and dropdown-context directive
// also we should use some listener here to resolve the issue changing window size (dynamic add and drop 'dropup' class)

@Directive({
  selector: '[dropdown]',
})
export class DropDownDirective implements OnInit, OnDestroy {
  /** container selector that is expected to have overflow limitations */
  @Input('dropdownContainer') container: string = null;

  constructor(
    private _renderer: Renderer,
    private _el: ElementRef,
  ) {}

  ngOnInit() {
    $(this._el.nativeElement)
      .on('show.bs.dropdown', <JQuery.EventHandler<Element>> this._onShowBound)
      .on('hide.bs.dropdown', <JQuery.EventHandler<Element>> this._onHideBound);
  }

  ngOnDestroy() {
    $(this._el.nativeElement)
      .off('show.bs.dropdown', <JQuery.EventHandler<Element>> this._onShowBound)
      .off('hide.bs.dropdown', <JQuery.EventHandler<Element>> this._onHideBound);
  }

  private _onShowBound = (event: JQuery.Event) => this._onShow(event);
  private _onHideBound = (event: JQuery.Event) => this._onHide(event);

  private _onShow(eventObject: JQuery.Event) {
    const dropdownElement: HTMLElement = <HTMLElement> eventObject.currentTarget,
      container: HTMLElement = this.container ? $(dropdownElement).closest(this.container).get(0) : null;

    const fitToContainer = container &&
      (container.scrollHeight !== container.offsetHeight ||
        container.scrollWidth !== container.offsetWidth ||
        container.style.overflowY === 'scroll');

    const
      dropdownElementTop = dropdownElement.getBoundingClientRect().top,
      dorpdownElementHeight = dropdownElement.getBoundingClientRect().height,
      menuHeight = $(dropdownElement).find('.dropdown-menu').outerHeight(),
      windowHeight = $(window).height(),
      minTopPosition = fitToContainer ?
        Math.max(container.getBoundingClientRect().top, 0) : 0,
      maxBottomPosition = fitToContainer ?
        Math.min(container.getBoundingClientRect().bottom, windowHeight) : windowHeight;

    const isDropUp =
      // menu can be placed above
      (dropdownElementTop > menuHeight) && (minTopPosition < dropdownElementTop - menuHeight)
      // menu can not be placed below
      && ((maxBottomPosition - dropdownElementTop) < (dorpdownElementHeight + menuHeight));

    // in total, if viewable scroll area is small for rendering dropdown-menu
    // and we can scroll up and down for layout then we prefer non-dropup mode

    this._renderer.setElementClass(this._el.nativeElement.parentElement, 'dropup', isDropUp);

    // force layout overflow to visible
    if (this.container && !fitToContainer) {
      $(this.container).addClass('dropdown-container-overflow');
    }
  }

  private _onHide(eventObject: JQuery.Event) {
    // reset layout overflow to auto
    if (this.container) {
      const dropdownElement: HTMLElement = <HTMLElement> eventObject.currentTarget;
      $(dropdownElement).closest(this.container).removeClass('dropdown-container-overflow');
    }
  }
}
