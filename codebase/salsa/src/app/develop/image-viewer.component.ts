import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  Renderer2,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

import { Observable } from 'rxjs/Observable';
import { combineLatest } from 'rxjs/observable/combineLatest';
import { ReplaySubject } from 'rxjs/ReplaySubject';

import { ReactiveLoader } from '../utils/reactive-loader';

@Component({
  selector: 'image-viewer',
  template: `
    <div #imageContainer class="app-spinner-box">
      <app-spinner [visibility]="_imageLoader.active | async"></app-spinner>
      <error-indicator *ngIf="_imageLoader.loaded && !_loadedImage"
        title="Can't display image"
        message="Your browser doesn't support such type of images."
      ></error-indicator>
    </div>
  `,
})
export class ImageViewerComponent implements OnChanges, AfterViewInit {
  @Input() src: string;
  @Input() alt: string;
  @ViewChild('imageContainer') imageContainer: ElementRef;

  private _imageContainer$ = new ReplaySubject<ElementRef>(1);
  private _imageLoader: ReactiveLoader<HTMLImageElement, string>;
  private _loadedImage: HTMLImageElement = null;
  constructor(
    private renderer: Renderer2,
  ) {
    this._imageLoader = new ReactiveLoader<HTMLImageElement | null, string>((src: string) => {
      if (this._loadedImage) {
        this._loadedImage.remove();
      }
      return new Observable(observer => {
        const img: HTMLImageElement = this.renderer.createElement('img');
        // Push image to observable only if it successfully rendered by browser. Otherwise push null.
        img.onload = () => {
          if (img.width > 0 && img.height > 0) {
            observer.next(img);
          } else {
            observer.next(null);
          }
          observer.complete();
        };
        img.onerror = (e) => {
          observer.next(null);
          observer.complete();
        };
        img.src = src;

        return () => {
          img.onload = undefined;
          img.onerror = undefined;
        };
      });
    });
    combineLatest(this._imageLoader.value, this._imageContainer$).subscribe(([img, container]) => {
      if (img) {
        if (this.alt) {
          img.alt = this.alt;
        }
        this.renderer.appendChild(container.nativeElement, img);
      }
      this._loadedImage = img;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('src' in changes && this.src) {
      this._imageLoader.load(this.src);
    }
    if ('alt' in changes && this.alt && this._loadedImage) {
      this._loadedImage.alt = this.alt;
    }
  }

  ngAfterViewInit(): void {
    this.imageContainer && this._imageContainer$.next(this.imageContainer);
  }
}
