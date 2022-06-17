import { Directive, ElementRef, Input, OnDestroy, OnInit } from '@angular/core';

import { DragImage } from 'ng2-dnd';

import { DndUtil } from '../../utils/dnd';

@Directive({
  selector: '[dragImage]', // lets keep "dnd" library naming convention
})
export class DragImageDirective implements OnInit, OnDestroy {
  @Input('dragImage') title: string;

  private el: any;
  private dragImageElement: HTMLElement;

  constructor(elRef: ElementRef) {
    this.el = elRef.nativeElement;
  }

  ngOnInit() {
    this.el.addEventListener('dragstart', this.dragStart);
    this.el.addEventListener('dragend', this.dragEnd);
  }

  ngOnDestroy() {
    this.el.removeEventListener('dragstart', this.dragStart);
    this.el.removeEventListener('dragend', this.dragEnd);
  }

  private dragStart: Function = (event: DragEvent) => {
    this.dragImageElement = DndUtil.getDragImageElement(this.title);
    document.body.appendChild(this.dragImageElement);
    (<any> event.dataTransfer).setDragImage(
      (new DragImage(this.dragImageElement)).imageElement,
      this.dragImageElement.offsetWidth / 2,
      this.dragImageElement.offsetHeight / 2,
    );
  };

  private dragEnd: Function = (event: Event) => {
    this.dragImageElement && this.dragImageElement.parentNode.removeChild(this.dragImageElement);
  };
}
