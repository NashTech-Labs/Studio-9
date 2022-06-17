import { AfterViewChecked, AfterViewInit, Directive, ElementRef, HostBinding, HostListener } from '@angular/core';

import { Debounce } from '../../utils/debounce.decorator';

@Directive({
  selector: 'textarea[autosize]',
})
export class TextareaAutosizeDirective implements AfterViewInit, AfterViewChecked {
  @HostBinding('rows') rows = '1';
  @HostBinding('style.overflow') overflow = 'hidden';

  constructor(
    private elem: ElementRef,
  ) {
  }

  ngAfterViewInit(): void {
    this.resize();
  }

  @Debounce(300)
  ngAfterViewChecked(): void {
    this.resize();
  }

  @HostListener('input')
  resize() {
    const textarea = this.elem.nativeElement as HTMLTextAreaElement;
    // Reset textarea height to auto that correctly calculate the new height
    textarea.style.height = 'auto';
    // Set new height
    textarea.style.height = `${textarea.scrollHeight}px`;
  }
}
