import { Component } from '@angular/core';
import { Router } from '@angular/router';

import { FlowService } from './flow.service';


@Component({
  selector: 'flow-compose',
  template: 'Redirecting...',
})
export class FlowComposeComponent {

  constructor(private router: Router,
              private flows: FlowService) {
    if (this.flows.data.view) {
      // if there is the existing selected flow
      this.router.navigate(['/desk', 'flows', this.flows.data.view.id]);
    } else {
      // if there is no previously selected flow
      this.router.navigate(['/desk', 'flows', 'create']);
    }
  }
}
