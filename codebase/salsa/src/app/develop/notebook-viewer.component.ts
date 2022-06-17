import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
} from '@angular/core';

import 'clipboard';
import * as Prism from 'prismjs';
import 'prismjs/components/prism-python';
import 'prismjs/plugins/copy-to-clipboard/prism-copy-to-clipboard';
import 'prismjs/plugins/toolbar/prism-toolbar';

declare var nb: any;

@Component({
  selector: 'notebook-viewer',
  template: `<div [innerHtml]="code | safeHtml"></div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotebookViewerComponent implements OnChanges, AfterViewChecked {
  @Input() content: string = '';

  code: string = '';
  highlighted: boolean = false;

  constructor(private el: ElementRef) {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('content' in changes) {
      const notebook = nb.parse(JSON.parse(this.content));
      this.code = notebook.render().outerHTML;
      this.highlighted = false;
    }
  }

  ngAfterViewChecked(): void {
    if (this.code && !this.highlighted) {
      Prism.highlightAllUnder(this.el.nativeElement);
      this.highlighted = true;
    }
  }
}
