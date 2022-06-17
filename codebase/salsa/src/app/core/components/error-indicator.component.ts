import { Component, Input } from '@angular/core';

import { IProcess } from '../interfaces/process.interface';

@Component({
  selector: 'error-indicator',
  template: `
    <div class="row">
      <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3">
        <h1 class="text-center text-muted"><i class="glyphicon glyphicon-warning-sign"></i></h1>

        <h4 class="text-center text-muted">{{caption || 'Invalid'}}</h4>
        <h4 class="text-center text-muted text-light"
            >{{message || process?.cause || 'This ' + (target || 'entity') + " can't be displayed"}}</h4>
      </div>
    </div>`,
})
export class ErrorIndicatorComponent {
  @Input() caption: string = null;
  @Input() message: string = null;
  @Input() target: string = null;
  @Input() process: IProcess = null;
}
