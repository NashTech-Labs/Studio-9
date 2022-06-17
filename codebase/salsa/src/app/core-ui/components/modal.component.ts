import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  Renderer2,
} from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { TOOLTIP_CONTAINER_REF } from '../directives/tooltip.directive';

export interface IModalButton {
  'title': string;
  'class'?: string;
  'disabled'?: boolean;
  'id'?: string;
}

@Component({
  selector: 'app-modal',
  styles: [`
    .modal-content {
      background: linear-gradient(to right, #e7e8ed 0%, #f3f4f9 20%);
      border-radius: 0;
    }
    .modal-header {
      border-bottom: 0;
    }
    .modal-title {
      text-transform: uppercase;
      font-family: "Montserrat", sans-serif;
      font-size: 12px;
      font-weight: bold;
    }
    .modal-title .pre-title {
      color: #5b6f9a;
    }
    :host /deep/ .tab-pane {
      background: white;
      padding-top: 1px;
    }
  `],
  template: `
    <div class="modal fade" [id]="id" role="dialog">
      <div class="modal-dialog {{sizeClass}}" role="document">
        <div class="modal-content" *ngIf="shown">
          <div class="modal-header" *ngIf="caption">
            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
              <i class="glyphicon glyphicon-remove-circle" aria-hidden="true"></i>
            </button>
            <h4 class="modal-title"><span class="pre-title" *ngIf="captionPrefix">{{captionPrefix + ': '}}</span>{{caption}}</h4>
          </div>
          <div class="modal-body app-spinner-box">
            <div [ngClass]="{'limited-height': limitedHeight}">
              <ng-content></ng-content>
            </div>
          </div>
          <div *ngIf="buttons" class="modal-footer">
            <button *ngFor="let button of buttons" type="button" (click)="onButton(button)" [disabled]="button.disabled"
              class="btn {{button.class || 'btn-default'}}">{{button.title}}</button>
          </div>
        </div><!-- /.modal-content -->
      </div><!-- /.modal-dialog -->
    </div><!-- /.modal -->
  `,
  providers: [{
    provide: TOOLTIP_CONTAINER_REF,
    useValue: 'body',
  }],
})
export class ModalComponent implements AfterViewInit, OnInit, OnDestroy {

  @Input('modalId') id: string = '';
  @Input() captionPrefix: string;
  @Input() caption: string;
  @Input() sizeClass: string = '';
  @Input() buttons: IModalButton[];
  @Input() limitedHeight: boolean = false;

  @Output() buttonClick: EventEmitter<IModalButton> = new EventEmitter();
  @Output() onClosed: EventEmitter<void> = new EventEmitter<void>();

  modalEl: any;
  shown: boolean = false;
  private keyPressSubscription: Function;

  constructor(
    private el: ElementRef,
    renderer: Renderer2,
  ) {
    this.keyPressSubscription = renderer.listen('body', 'keyup', (event: KeyboardEvent) => {
      if (event.keyCode === 27) {
        this.hide();
      }
    });
  }

  ngOnInit() {
    this.modalEl = $(this.el.nativeElement).children('.modal');
    this.modalEl.appendTo('body');
    this.modalEl.on('hidden.bs.modal', () => {
      this.shown = false;
      this.onClosed.emit();
    });
  }

  ngAfterViewInit() {
    this.shown && this.show();
  }

  ngOnDestroy() {
    if (this.modalEl) {
      this.modalEl.modal('removeBackdrop');
      this.modalEl.remove();
    }
    this.keyPressSubscription();
  }

  onButton(button: IModalButton): void {
    this.buttonClick.emit(button);
  }

  show(): Observable<void> {
    this.shown = true;
    this.modalEl && this.modalEl.modal({
      show: true,
      backdrop: 'static',
      keyboard: false,
    });

    return this.onClosed.asObservable();
  }

  hide() {
    this.modalEl && this.modalEl.modal('hide');
  }
}

