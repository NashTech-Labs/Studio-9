import { OnDestroy } from '@angular/core';
import { FormGroup } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import { MiscUtils } from '../../utils/misc';
import { DashboardEditState } from '../dashboard-edit-state';

export abstract class ChartOptionsAbstract implements OnDestroy {
  form: FormGroup;
  protected _state: DashboardEditState;
  private formSubscription: Subscription;

  ngOnDestroy() {
    this.formSubscription && this.formSubscription.unsubscribe();
  }

  set state(state: DashboardEditState) {
    this._state = state;
    this.formSubscription && this.formSubscription.unsubscribe();
    MiscUtils.fillForm(this.form, this._state.widgetForm.value.options);
    this.formSubscription = this.form.valueChanges.distinctUntilChanged().debounceTime(200).subscribe(value => {
      this._state.widgetForm.controls.options.setValue(value);
    });
  }

  get state() {
    return this._state;
  }
}
