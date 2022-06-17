import { ChangeDetectorRef, Component, Input, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { AppFormArray, AppFormGroup } from '../../utils/forms';
import { MiscUtils } from '../../utils/misc';

import { AppSelectOptionData } from './app-select.component';
import { IModalButton, ModalComponent } from './modal.component';

@Component({
  selector: 'sort-columns',
  template: `
    <button class="btn btn-secondary btn-xs" title="Sort" (click)="open()">
      <i class="glyphicon glyphicon-sort"></i></button>
    <app-modal #modal
      [caption]="'Select columns to sort'"
      [buttons]="[
        {'class': 'btn-primary', 'title': 'Sort', 'disabled': conditions.invalid},
        {'class': 'btn-secondary', 'title': 'Cancel', 'disabled': false}
      ]"
      (buttonClick)="onButtonClick($event)">
      <!--{{conditions.value | json}}-->
      <div class="row">
        <div class="col-xs-12">
          <label>Add Sort Column</label>
          <button title="Add Sort Column"
            (click)="conditions.push(newCondition())"
            class="btn btn-default"><i class="glyphicon glyphicon-plus"></i></button>
        </div>
      </div>
      <div class="row" *ngFor="let condition of conditions.controls;let i = index;let last = last">
        <div class="col-xs-5">
          <app-select [options]="columns" [control]="condition.controls.column"></app-select>
        </div>
        <div class="col-xs-5">
          <app-select [options]="ascDesc" [control]="condition.controls.order"></app-select>
        </div>
        <div class="col-xs-2">
          <button type="button"
            title="Remove Sort Column"
            (click)="conditions.removeAt(i)"
            class="btn btn-default"
            [disabled]="conditions.controls.length <= 1"
          ><i class="glyphicon glyphicon-remove"></i></button>
        </div>
      </div>
    </app-modal>
  `,
})
export class SortColumnsComponent {
  @Input() columns: AppSelectOptionData[];
  @Input() control: FormControl;

  readonly ascDesc = ['asc', 'desc'];
  readonly conditions = new AppFormArray([this.newCondition()]);
  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    private changeDetector: ChangeDetectorRef,
  ) {
    this.conditions.valueChanges.subscribe(() => {
      // dunno why it needs to be done
      // but template is not updated in terms of conditions.invalid
      this.changeDetector.markForCheck();
    });
  }

  open() {
    if (this.control.value) {
      const valueParsed = this.control.value.split(',').map(item => {
        return (item[0] === '-')
          ? {column: item.substring(1), order: 'desc'}
          : {column: item, order: 'asc'};
      });
      MiscUtils.fillForm(this.conditions, valueParsed);
    } else {
      MiscUtils.fillForm(this.conditions, [
        {column: '', order: 'asc'},
      ]);
    }
    this.modal.show();
  }

  newCondition() {
    return new AppFormGroup({
      column: new FormControl(null, Validators.required),
      order: new FormControl('asc', Validators.required),
    });
  }

  onButtonClick(button: IModalButton) {
    if (button.title === 'Sort') {
      const resultOrder = this.conditions.value.map(condition => {
        const order = condition.order === 'desc' ? '-' : '';
        return `${order}${condition.column}`;
      });
      this.control.setValue(resultOrder.join(','));
      this.modal.hide();
    }

    if (button.title === 'Cancel') {
      this.modal.hide();
    }
  }
}
