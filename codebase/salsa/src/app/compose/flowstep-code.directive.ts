import { Directive, ElementRef, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { FormControl } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { ITable } from '../tables/table.interface';
import {
  EXCEL_SUPPORTED_FUNCTIONS,
  SQL_SUPPORTED_FUNCTIONS,
  SQL_SUPPORTER_OPERATORS,
} from '../utils/code.constants';

const CodeMirror = require('codemirror');
require('codemirror/mode/sql/sql.js');
require('codemirror/addon/hint/show-hint.js');
require('codemirror/addon/display/placeholder.js');

interface ISuggestion {
  text: string;
  displayText: string;
  typeOfCodeNode: string;
  brief: string;
  render: Function;
}

/**
 * Usage:
 *  <textarea formControlName="field" flowstepCode="excel" code-table="ITable" code-options="any"></textarea>
 *  <input formControlName="field" flowstepCode="sql" code-options="any">
 */
@Directive({
  selector: '[flowstepCode]',
})
export class FlowstepCodeDirective implements OnInit, OnChanges, OnDestroy {
  @Input('flowstepCode') mode: string;
  @Input() codeOptions: any;
  @Input() codeTables: ITable[];
  @Input() codeReadonly: boolean;
  @Input() field: FormControl;

  private DEFAULT_OPTIONS = {
    // lineWrapping: true,
    height: 65,
  };

  private CI_AC_CODE_NODE_TYPES = {
    'var': 'var',
    'fn': 'fn',
    'op': 'op',
    'delimiter': 'del',
  };

  private CI_AC_CODE_NODE_FA_ICONS = {
    'var': 'icon-shuffle',
    'fn': 'icon-size-actual',
    'op': 'icon-energy',
  };


  private columnSuggestions: ISuggestion[] = [];
  private excelSuggestions: ISuggestion[] = [];
  private sqlFnSuggestions: ISuggestion[] = [];
  private sqlOpSuggestions: ISuggestion[] = [];

  private el: HTMLElement;
  private instance: any;
  private fieldSubscription: Subscription;

  constructor(el: ElementRef) {
    this.el = el.nativeElement;
  }

  public ngOnInit() {
    if (!CodeMirror) return false;

    let options = this.setOptions();

    this.instance = CodeMirror.fromTextArea(<HTMLTextAreaElement> this.el, options);
    options.value && this.instance.setValue(options.value);

    this.instance.on('change', (editor: any) => {
      if (editor.getDoc()) {
        let value = editor.getDoc().getValue();
        value !== this.field.value && this.field.setValue(value);
      }
    });

    this.instance.setSize(null, options.height); // only for height

    // if (this.mode === config.code.mode.EXCEL) {
    //   // now disallow adding newlines in the following simple way
    //   this.instance.on('beforeChange', function (instance, change) {
    //     let newtext = change.text.join('').replace(/\n/g, ''); // remove ALL \n !
    //     change.update(change.from, change.to, [newtext]);
    //     return true;
    //   });
    // }

    if (options.hintOptions) {
      this.instance.on('blur', () => {
        this.removeTooltips();
      });

      this.instance.on('focus', cm => {
        !cm.state.completionActive && cm.showHint(cm);
      });

      this.instance.on('keyup', (cm, event) => {
        // Preventing auto-completion when hitting ignoring keys
        if (event.keyCode < 65 || event.keyCode > 90) return; // Only letter key codes

        // Preventing auto-completion when selecting row from dropdown
        if (cm.state.completionActive && cm.state.completionActive.widget) return;

        cm.showHint(cm);
      });

      this.instance.on('cursorActivity', (cm) => !this.codeReadonly && this.hintUsage(cm));
    }

    //excel
    this.excelSuggestions = EXCEL_SUPPORTED_FUNCTIONS.filter(func => !func['disabled']).map((func: any) => {
      return this.newSuggestion(func.name, func.brief, this.CI_AC_CODE_NODE_TYPES.fn);
    });

    //sql
    this.sqlFnSuggestions = SQL_SUPPORTED_FUNCTIONS.filter(func => !func['disabled']).map((func: any) => {
      return this.newSuggestion(func.name, func.brief, this.CI_AC_CODE_NODE_TYPES.fn);
    });

    this.sqlOpSuggestions = SQL_SUPPORTER_OPERATORS.map((op: any) => {
      return this.newSuggestion(op.name, 'SQL', this.CI_AC_CODE_NODE_TYPES.op);
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (this.instance) {
      this.instance.setOption('readOnly', this.codeReadonly ? 'nocursor' : false);
      if (this.codeOptions) {
        Object.keys(this.codeOptions).forEach((key: string) => this.instance.setOption(key, this.codeOptions[key]));
      }
    }

    if ('field' in changes) {
      if (this.instance && this.field.value !== this.instance.getValue()) {
        this.instance.setValue(this.field.value);
      }
      this.fieldSubscription && this.fieldSubscription.unsubscribe();
      this.fieldSubscription = this.field.valueChanges.subscribe(value => {
        if (this.instance && value !== this.instance.getValue()) {
          this.instance.setValue(value);
        }
      });
    }

    //common
    this.columnSuggestions = [];
    if (this.codeTables && this.codeTables.length) {
      this.codeTables.forEach((codeTable: ITable) => {
        if (codeTable && codeTable.columns) {
          codeTable.columns.forEach((column) => {
            this.columnSuggestions.push(this.newSuggestion(
              column.name,
              column.displayName,
              this.CI_AC_CODE_NODE_TYPES.var,
              column.dataType,
            ));
          });
        }
      });
    }
  }

  ngOnDestroy() {
    this.removeTooltips();
    this.fieldSubscription && this.fieldSubscription.unsubscribe();
    this.instance = undefined;
  }

  // Set options for CodeMirror constructor
  private setOptions() {
    let options = <any> Object.assign({}, this.DEFAULT_OPTIONS, this.codeOptions);
    delete options.mode;

    if (this.mode === config.code.mode.SQL) {
      Object.assign(options, {
        extraKeys: { 'Ctrl-Space': 'autocomplete' },
        mode: 'text/x-pgsql',
        hintOptions: {
          hint: this.hint.bind(this),
          completeSingle: false,
          closeOnUnfocus: true,
        },
      });
    }

    if (this.mode === config.code.mode.EXCEL) {
      Object.assign(options, {
        extraKeys: { 'Ctrl-Space': 'autocomplete' },
        hintOptions: {
          hint: this.hint.bind(this),
          completeSingle: false,
          closeOnUnfocus: true,
        },
      });
    }

    Object.assign(options, {
      readOnly: this.codeReadonly ? 'nocursor' : false,
    });

    return options;
  }

  private hint = (cm) => {
    return new Promise((resolve) => {
      setTimeout(() => { // Getting word - range of start-end positions ( [s : e) ) - at the current cursor
        let cursor = cm.getCursor(), line = cm.getLine(cursor.line);
        let start = cursor.ch, end = cursor.ch;

        while (start && /\w/.test(line.charAt(start - 1))) --start;
        while (end < line.length && /\w/.test(line.charAt(end))) ++end;

        let word = line.slice(start, end).toLowerCase();

        let suggestionLists = [
          this.columnSuggestions,
        ];
        if (this.mode === config.code.mode.EXCEL) {
          suggestionLists.push(this.excelSuggestions);
        }
        if (this.mode === config.code.mode.SQL) {
          suggestionLists.push(this.sqlFnSuggestions);
          suggestionLists.push(this.sqlOpSuggestions);
        }

        // Init total auto-completion list
        let filteredSuggestionList: ISuggestion[] = suggestionLists
          .map(_ => {
            return _.filter((item: ISuggestion) => {
              return item.text.toLowerCase().indexOf(word) === 0 || item.text === '';
            });
          })
          .filter(_ => _.length > 0)
          .reduce((acc, list) => {
            if (acc.length) {
              acc.push(this.newSuggestion('', null, this.CI_AC_CODE_NODE_TYPES.delimiter));
            }
            return acc.concat(list);
          }, []);

        if (filteredSuggestionList.length > 0) {
          return resolve({
            list: filteredSuggestionList,
            from: CodeMirror.Pos(cursor.line, start),
            to: CodeMirror.Pos(cursor.line, end),
          });
        } else {
          return resolve(null);
        }
      }, 100);
    })
      .then(result => {
        // Callback for moving cursor left by one char when function inserted
        if (result) {
          CodeMirror.on(result, 'pick', (comp) => {
            if (comp.typeOfCodeNode === this.CI_AC_CODE_NODE_TYPES.fn) {
              let cursor = cm.getCursor();
              if (comp.text.indexOf('()') !== -1) {
                cursor.ch -= 1;
                cm.doc.setCursor(cursor);
              } else {
                cursor.ch += 1;
                cm.doc.setCursor(cursor);
              }
            }
          });

          CodeMirror.on(result, 'close', () => {
            this.removeTooltips('.CodeMirror-Excel-tooltip-autocomplete');
            cm.curOp && this.hintUsage(cm);
          });

          CodeMirror.on(result, 'select', (completion, el) => {
            this.removeTooltips();

            // show tooltip
            if (completion.brief) {
              this.tooltip(el.parentNode.getBoundingClientRect().right + window.pageXOffset,
                el.getBoundingClientRect().top + window.pageYOffset, completion.brief, true);
            }
          });
        } else {
          // Force logic after closing auto-completion dropdown, cause we have no suggestionList
          this.removeTooltips('.CodeMirror-Excel-tooltip-autocomplete');
          cm.curOp && this.hintUsage(cm);
        }

        return result;
      });
  };

  private newSuggestion(name: string, brief: string, type: string, valueType?: string): ISuggestion {
    return {
      text: type === this.CI_AC_CODE_NODE_TYPES.fn ? name + '()'  : name,
      displayText: type === this.CI_AC_CODE_NODE_TYPES.fn ? name + ': ' + 'Function' : (valueType ? name + ': ' + valueType : name),
      typeOfCodeNode: type,
      brief: brief,
      render: this._hintRender,
    };
  }

  private _hintRender = (el, data, cursor) => {
    if (cursor.typeOfCodeNode) {
      const icon = document.createElement('i');

      switch (cursor.typeOfCodeNode) {
        case this.CI_AC_CODE_NODE_TYPES.op:
          icon.className = this.CI_AC_CODE_NODE_FA_ICONS.op;
          break;
        case this.CI_AC_CODE_NODE_TYPES.var:
          icon.className = this.CI_AC_CODE_NODE_FA_ICONS.var;
          break;
        case this.CI_AC_CODE_NODE_TYPES.fn:
          icon.className = this.CI_AC_CODE_NODE_FA_ICONS.fn;
          break;
        case this.CI_AC_CODE_NODE_TYPES.delimiter:
          icon.className = '';
      }
      if (icon.className !== '') {
        el.appendChild(icon);
        el.appendChild(document.createTextNode('\u00A0'));
      }
      if (cursor.typeOfCodeNode !== this.CI_AC_CODE_NODE_TYPES.delimiter) {
        el.appendChild(document.createTextNode(cursor.displayText));
      } else {
        el.appendChild(document.createElement('hr'));
      }
    }

    el.addEventListener('mouseover', () => {
      if (el.hintId !== null && this.instance && this.instance.state.completionActive.widget) {
        const widget = this.instance.state.completionActive.widget;
        widget.changeActive(el.hintId);
      }
    });
  };

  private hintUsage(cm) {
    // Preventing usage hint when there is auto-completion
    if (cm.state.completionActive && cm.state.completionActive.widget) {
      return;
    }

    this.removeTooltips();

    let word = this.findWord(cm);

    let fn: string = '';

    if (this.mode === config.code.mode.EXCEL) {
      fn = word ? <any> EXCEL_SUPPORTED_FUNCTIONS.filter(func => !func['disabled']).find(func => func.name === word) : '';
    }

    if (this.mode === config.code.mode.SQL) {
      fn = word ? <any> SQL_SUPPORTED_FUNCTIONS.filter(func => !func['disabled']).find(func => func.name === word) : '';
    }

    if (fn) {
      // Cursor is placed at the function name
      let place = cm.cursorCoords(null, 'page');
      this.tooltip(place.right + 1, place.bottom, this.makeFnHint(fn, -1));
    } else {
      // Trying to find is the cursor placed within function arguments

      // Getting current function name
      let cursor = cm.getCursor(), line = cm.getLine(cursor.line);
      let point = cursor.ch;
      let countRightBrackets = 0;
      let countCommas = 0;

      while (point > 0) {
        if (line.charAt(point - 1) === ')') {
          countRightBrackets++;

        } else if (line.charAt(point - 1) === '(') {
          if (countRightBrackets === 0) {
            break;
          } else {
            countRightBrackets--;
          }
        } else if (line.charAt(point - 1) === ',') {
          !countRightBrackets && countCommas++;
        }

        point--;
      }

      if (point !== 0) {
        let start = point - 1, end = point - 1;

        while (start && /\w/.test(line.charAt(start - 1))) --start;
        while (end < line.length && /\w/.test(line.charAt(end))) ++end;

        let word = line.slice(start, end);

        if (word) {

          if (this.mode === config.code.mode.EXCEL) {
            fn = <any> EXCEL_SUPPORTED_FUNCTIONS.filter(func => !func['disabled']).find(func => func.name === word);
          }

          if (this.mode === config.code.mode.SQL) {
            fn = <any> SQL_SUPPORTED_FUNCTIONS.filter(func => !func['disabled']).find(func => func.name === word);
          }

          if (fn) {
            // Making tooltip
            let place = cm.cursorCoords(null, 'page');
            this.tooltip(place.right + 1, place.bottom, this.makeFnHint(fn, countCommas));
          }
        }
      }
    }
  }

  private findWord(cm): string {
    // Getting word - range of start-end positions ( [s : e) ) - at the current cursor
    let cursor = cm.getCursor(), line = cm.getLine(cursor.line);
    let start = cursor.ch, end = cursor.ch;

    while (start && /\w/.test(line.charAt(start - 1))) --start;
    while (end < line.length && /\w/.test(line.charAt(end))) ++end;

    return line.slice(start, end);
  }

  private tooltip(x, y, content, autocomplete: boolean = false) {
    let el = this.createElement('div', 'CodeMirror-Excel-tooltip', content);

    autocomplete && el.classList.add('CodeMirror-Excel-tooltip-autocomplete');

    el.style.left = x + 'px';
    el.style.top = y + 'px';

    document.body.appendChild(el);
    return el;
  }

  private makeFnHint(fn, highlightArgIndex = 0) {
    let hint = this.createElement('div', null);

    (highlightArgIndex > fn.arguments.length - 1) && (highlightArgIndex = fn.arguments.length - 1);
    // Fn signature
    let signature = this.createElement('div', null);

    signature.appendChild(this.createElement('span', null, fn.name));
    signature.appendChild(this.createElement('span', null, '('));

    for (let i = 0; i < fn.arguments.length; i++) {
      signature.appendChild(this.createElement(
        i === highlightArgIndex ? 'strong' : 'span',
        null,
        fn.arguments[i].title + (fn.arguments[i].type ? ': ' + fn.arguments[i].type : ''),
      ));

      i !== fn.arguments.length - 1 && signature.appendChild(this.createElement('span', null, ', '));
    }

    signature.appendChild(this.createElement('span', null, ')'));
    fn.type && signature.appendChild(this.createElement('span', null, ': ' + fn.type));
    hint.appendChild(signature);

    // Fn description
    /*
     let descr = this.createElement('div', null, fn.description);
     hint.appendChild(descr);
     */

    // Fn arguments
    /*
     for (let i = 0; i < fn.arguments.length; i++) {
     hint.appendChild(document.createElement('br'));
     hint.appendChild(this.createElement('div', null, fn.arguments[i].title + ': ' + fn.arguments[i].type));
     hint.appendChild(this.createElement('div', null, fn.arguments[i].description));
     }
     */

    return hint;
  }

  private removeTooltips(selector: string = '.CodeMirror-Excel-tooltip') {
    Array.prototype.slice.call(document.querySelectorAll(selector)).forEach(el => {
      this.removeElement(el);
    });
  }

  private removeElement(el) {
    el.remove();
  }

  private createElement(tagname, cls, ...els) {
    let el = document.createElement(tagname);
    if (cls) {
      el.className = cls;
    }

    for (let i = 0; i < els.length; ++i) {
      let elArgument = els[i];
      if (typeof elArgument === 'string') {
        elArgument = document.createTextNode(elArgument);
        el.appendChild(elArgument);
      }

      if (typeof elArgument === 'object') {
        el.appendChild(elArgument);
      }
    }

    return el;
  }
}
