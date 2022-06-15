import { AfterViewInit, Directive, ElementRef, HostBinding, HostListener, OnDestroy } from '@angular/core';

@Directive({ selector: '[showMore]' })
export class ShowMoreDirective implements AfterViewInit, OnDestroy {
  @HostBinding('class.show-more') class = true;
  private _mutationObserver: MutationObserver;

  constructor(private el: ElementRef) {
    this._mutationObserver = new MutationObserver(this._makeCollapsible.bind(this));
  }

  @HostListener('click') onClick(): void {
    if (this.element.classList.contains('collapse')) {
      jQuery(this.element).collapse('toggle');
    }
  }

  ngAfterViewInit(): void {
    this._makeCollapsible();
    this._mutationObserver.observe(this.element, { characterData: true, subtree: true });
  }

  ngOnDestroy(): void {
    this._mutationObserver.disconnect();
  }

  private get element(): HTMLDivElement {
    return this.el.nativeElement;
  }

  private _makeCollapsible(): void {
    if (this.element.scrollHeight > this.element.offsetHeight) {
      this.element.classList.add('collapse');
      this.element.setAttribute('aria-expanded', 'false');
      this.element.setAttribute('tooltip', 'true');
      this.element.setAttribute('title', this.element.innerText);
    } else if (this.element.classList.contains('collapse')) {
      this.element.classList.remove('collapse');
      this.element.removeAttribute('aria-expanded');
      this.element.removeAttribute('tooltip');
      this.element.removeAttribute('title');
    }
  }
}
