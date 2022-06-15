require('../../lib/jquery.selectareas');

import {
  AfterViewInit,
  Directive,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
} from '@angular/core';

import { IImageArea } from '../../lib/jquery.selectareas';

export interface ITagArea extends IImageArea {}

@Directive({
  selector: '[tag-image]',
  exportAs: 'tagImageDirective',
})
export class TagImageDirective implements AfterViewInit, OnChanges, OnDestroy {
  @Input() tags: ITagArea[];
  @Input() scale: number = 1;
  @Input() allowEdit: boolean = true;
  @Output() tagsChange = new EventEmitter<ITagArea[]>();
  @Output() scaleChange = new EventEmitter<number>();
  private el: any;
  private originalWidth: number;
  private originalHeight: number;
  private loaded: boolean = false;

  constructor(el: ElementRef, private zone: NgZone) {
    this.el = el.nativeElement;
  }

  ngAfterViewInit() {
    this.init();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (!this.loaded) {
      return;
    }

    if ('scale' in changes && changes['scale'].previousValue) {
      $(this.el).selectAreas('destroy');
      this.el.style.width = this.originalWidth * this.scale + 'px';
      this.el.style.height = this.originalHeight * this.scale + 'px';
      this.initPlugin();
    }
    if ('tags' in changes) {
      const oldTags: ITagArea[] = $(this.el).selectAreas('areas');
      this.tags.forEach((tag, i) => {
        if (!oldTags[i]) {
          $(this.el).selectAreas('add', this._scaleTag(tag), true);
        } else if (!this._equals(tag, oldTags[i])) {
          $(this.el).selectAreas('set', i, this._scaleTag(tag), true);
        }
      });
      if (oldTags.length > this.tags.length) {
        for (let i = this.tags.length; i < oldTags.length; i++) {
          $(this.el).selectAreas('remove', i);
        }
      }
    }
  }

  focus(index: number) {
    $(this.el).selectAreas('focus', index);
  }

  blurAll() {
    $(this.el).selectAreas('blurAll');
  }

  init() {
    this.loaded = true;
    let img = new Image();
    const $el = $(this.el);
    const initializer = () => {
      if (!$el.is(':visible')) {
        window.setTimeout(initializer, 100);
        return;
      }
      if (img.width !== this.el.width) {
        this.scale = this.el.width / img.width;
      }
      this.originalWidth = img.width;
      this.originalHeight = img.height;
      this.scaleChange.emit(this.scale);
      this.initPlugin();
    };
    img.onload = initializer;
    img.src = this.el.src;
  }

  ngOnDestroy() {
    $(this.el).selectAreas('destroy');
  }

  private initPlugin() {
    this.zone.runOutsideAngular(() => {
      $(this.el).selectAreas({
        allowNudge: false,
        allowEdit: this.allowEdit,
        areas: this.tags.map((tag: ITagArea) => this._scaleTag(tag)),
        onChanged: (val: any, index: number, changes: ITagArea[]) => {
          if (!this.allowEdit) {
            return;
          }

          const newTags = changes.map(tagArea => {
            return {
              label: tagArea.label,
              width: Math.round(tagArea.width / this.scale),
              height: Math.round(tagArea.height / this.scale),
              x: Math.round(tagArea.x / this.scale),
              y: Math.round(tagArea.y / this.scale),
            };
          });

          if (!this._equals(newTags, this.tags)) {
            this.zone.run(() => {
              this.tags = newTags;
              this.tagsChange.emit(newTags);
            });
          }
        },
      });
    });
  }

  private _scaleTag(tag: ITagArea) {
    return {
      label: tag.label,
      x: tag.x * this.scale,
      y: tag.y * this.scale,
      width: tag.width * this.scale,
      height: tag.height * this.scale,
    };
  }

  private _equals(a: any, b: any): boolean {
    if (a === b) {
      return true;
    }

    if ((typeof a) !== (typeof b)) {
      return false;
    }

    if (Array.isArray(a)) {
      return Array.isArray(b) &&
        a.length === b.length &&
        a.every((item: any, i: number) => this._equals(item, b[i]));
    }
    // objects only!!!
    return Object.keys(a).filter((key: string) => {
        return b[key] !== a[key];
      }).length === 0;
  }
}
