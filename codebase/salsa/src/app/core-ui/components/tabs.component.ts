import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';

export interface IEditEvent {
  value: string;
  index: number;
}
@Component({
  selector: 'app-tabs',
  template: `
    <div class="tabpanel">
      <ul class="nav nav-tabs">
        <ng-template ngFor let-tab let-i="index" [ngForOf]="tabs">
          <li class="nav-item brand-tab-hover" [ngClass]="{'active': active === i}"
            *ngIf="hiddenTabs.indexOf(i) === -1">
            <a class="nav-link" (click)="setActive(i)">
              <span *ngIf="!editTab[i]">{{tab}}</span>
              <input *ngIf="editable && editTab[i]"
                (change)="_onTabEdit($event, i)"
                (blur)="editTab[i] = false"
                (keypress)="_onKeyPress($event, i)"
                [value]="tab"
                [placeholder]="placeholders[i]">
              <i *ngIf="editable && !editTab[i]" class="glyphicon glyphicon-edit pull-right brand-edit-tab"
                (click)="editTab[i] = true"></i>
              <i *ngIf="closeable" class="glyphicon glyphicon-remove pull-right brand-edit-tab"
                (click)="closeTab(i)"
              ></i>
            </a>
          </li>
        </ng-template>
      </ul>
    </div>
    <ng-content></ng-content>
  `,
})
export class TabsComponent implements OnChanges {
  @Input() tabs: string[];
  @Input() active: number = 0;
  @Input() editable: boolean = false;
  @Input() placeholders: string[] = [];
  @Input() hiddenTabs: number[] = [];
  @Output() activeChange: EventEmitter<number> = new EventEmitter<number>();
  @Output() onTabEdit: EventEmitter<IEditEvent> = new EventEmitter<IEditEvent>();
  @Output() onTabClose: EventEmitter<number> = new EventEmitter<number>();
  editTab: boolean[] = [];

  get closeable(): boolean {
    return !!this.onTabClose.observers.length;
  }

  ngOnChanges() {
    this.editTab = [];
    this.tabs.forEach((_, i: number) => {
      this.editTab[i] = false;
    });
  }

  setActive(i: number) {
    this.active = i;
    this.activeChange.emit(i);
    this.tabs.forEach((_, i: number) => {
      if (this.active !== i) {
        this.editTab[i] = false;
      }
    });
  }

  closeTab(i: number) {
    this.onTabClose.emit(i);
  }

  private _onTabEdit(event: any, i: number) { // @todo specify event type
    this.tabs[i] = event.target.value;
    this.onTabEdit.emit({ value: event.target.value, index: i });
    this.editTab[i] = false;
  }

  private _onKeyPress(event: any, i: number) { // @todo specify event type
    if (event.key === 'Enter') {
      this._onTabEdit(event, i);
    }
  }
}
