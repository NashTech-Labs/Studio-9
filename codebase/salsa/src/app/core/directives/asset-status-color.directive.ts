import { Directive, ElementRef, Input, OnChanges } from '@angular/core';

@Directive({
  selector: '[asset-status]',
})
export class AssetStatusColorDirective implements OnChanges {
  @Input('asset-status') status: string;
  @Input('asset-status-styles') styles: {[status: string]: string} = {};
  private el: HTMLElement;
  private baseClass: string;

  constructor(el: ElementRef) {
    this.el = el.nativeElement;
    this.baseClass = this.el.className;
  }

  ngOnChanges() {
    let cssClass = this.styles[this.status] || 'dot-primary';

    this.el.className = `${this.baseClass} ${cssClass}`;
  }
}
