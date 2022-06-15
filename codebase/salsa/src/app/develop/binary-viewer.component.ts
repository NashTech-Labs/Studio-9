import { Component, Input } from '@angular/core';

import { IOpenedS9ProjectFile } from './s9-project.interfaces';

@Component({
  selector: 'binary-viewer',
  template: `
    <div class="app-spinner-box">
      <app-spinner *ngIf="!file"></app-spinner>
      <error-indicator *ngIf="file" message="This file is binary."></error-indicator>
    </div>
  `,
})
export class BinaryViewerComponent {
  @Input() file: IOpenedS9ProjectFile;
}
