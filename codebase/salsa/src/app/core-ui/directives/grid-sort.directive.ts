import {
  Directive,
  ElementRef,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  Renderer,
  SimpleChanges,
} from '@angular/core';
import { FormControl } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

export interface IColumnSortConfig {
  name: string;
  alias?: string;
  style?: string;
  reverse?: boolean; // reverse on first selection
}

@Directive({
  selector: '[grid-sort]',
})
export class GridSortDirective implements OnChanges, OnDestroy {
  @Input('grid-sort') item: IColumnSortConfig;
  @Input('grid-sort-control') control: FormControl;
  controlSubscription: Subscription;

  constructor(
    private renderer: Renderer,
    private el: ElementRef,
  ) {}

  @HostListener('click')
  onClick() {
    if (this.item.alias) {
      const newSort = this.item.reverse
        ? (this.control.value === '-' + this.item.alias ? '' : '-') + this.item.alias
        : (this.control.value === this.item.alias ? '-' : '') + this.item.alias;

      this.control.setValue(newSort);
      this.updateStyle();
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if ('control' in changes) {
      this.controlSubscription && this.controlSubscription.unsubscribe();
      this.controlSubscription = this.control.valueChanges.subscribe(() => {
        this.updateStyle();
      });
    }
    this.updateStyle();
  }

  ngOnDestroy() {
    this.controlSubscription && this.controlSubscription.unsubscribe();
  }

  private updateStyle() {
    this.renderer.setElementClass(this.el.nativeElement, 'link', !!this.item.alias);
    if (this.item.alias && this.control.value) {
      const sortRules = this.control.value.split(',');
      const descResult = sortRules.indexOf('-' + this.item.alias) > -1;
      const ascResult = sortRules.indexOf(this.item.alias) > -1 && !descResult;
      this.renderer.setElementClass(this.el.nativeElement, 'asc-order', ascResult);
      this.renderer.setElementClass(this.el.nativeElement, 'desc-order', descResult);
    } else {
      this.renderer.setElementClass(this.el.nativeElement, 'asc-order', false);
      this.renderer.setElementClass(this.el.nativeElement, 'desc-order', false);
    }
  }
}
