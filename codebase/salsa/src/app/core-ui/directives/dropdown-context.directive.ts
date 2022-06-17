import { Directive, ElementRef, OnDestroy, OnInit, Renderer } from '@angular/core';

import { DropdownContextService } from '../services/dropdown-context.service';


@Directive({
  selector: '[dropdown-context]',
})
export class DropDownContextDirective implements OnInit, OnDestroy {
  private el: any;
  private parentEl: any;
  private listeners: Function[] = [];

  constructor(
    el: ElementRef,
    private renderer: Renderer,
    private service: DropdownContextService,
  ) {
    this.el = el.nativeElement;
    this.parentEl = el.nativeElement.parentElement;
  }

  ngOnInit() {
    // Initiating handlers
    this.listeners.push(this.renderer.listen(this.el, 'contextmenu', event => {
      event.preventDefault();
      this.renderer.setElementClass(this.parentEl, 'open', !this.parentEl.classList.contains('open'));
    }));

    // Register in service for clearing
    this.service.register(this.parentEl);
  }

  ngOnDestroy() {
    this.listeners.map((fn: Function) => fn());
    this.service.deregister(this.parentEl);
  }
}
