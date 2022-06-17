import { Directive, ElementRef, Input, OnChanges, OnDestroy } from '@angular/core';
import { FormControl } from '@angular/forms';

import { merge } from 'rxjs/observable/merge';
import { Subscription } from 'rxjs/Subscription';

@Directive({ selector: '[formControlValidator]' })
export class FormControlValidatorDirective implements OnChanges, OnDestroy {
  // Highlight "invalid" fields (using Bootstrap classes)
  @Input('formControlValidator') control: FormControl;

  private subscription: Subscription;

  private readonly el: HTMLElement;

  constructor(el: ElementRef) {
    this.el = el.nativeElement;
  }

  _getForm(control) {
    // find root control recursively
    return control === control.root ? control : this._getForm(control.root);
  }

  ngOnChanges() {
    this.subscription && this.subscription.unsubscribe();
    if (this.control) {
      this._isValid(this.control);

      this.subscription = merge(this.control.statusChanges, this.control.root.statusChanges).subscribe(() => {
        this._isValid(this.control);
      });
    }
  }

  ngOnDestroy(): void {
    this.subscription && this.subscription.unsubscribe();
  }

  _isValid(field): void {
    let el: JQuery = $(this.el);

    // @todo we disable submit button when not valid form but we can revert it any time /*&& field.root.submitted*/
    if (!field.valid && (!field.pristine || field.root.submitted)) { // experimental
      el.addClass('has-error');
    } else {
      el.removeClass('has-error');
    }
  }
}
