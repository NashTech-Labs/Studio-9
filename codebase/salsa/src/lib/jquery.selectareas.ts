const cancelEvent = function (e) {
  const event = e || window.event || {};
  event.cancelBubble = true;
  event.returnValue = false;
  /* tslint:disable:no-unused-expression */
  event.stopPropagation && event.stopPropagation();
  event.preventDefault && event.preventDefault();
  /* tslint:enable */
};

export interface IImageArea {
  label: string;
  x?: number;
  y?: number;
  z?: number;
  width?: number;
  height?: number;
}

class ImageArea {
  private $image: JQuery;
  private options: any;
  private $outline: JQuery;
  private $selection: JQuery;
  private $resizeHandlers: {[p: string]: JQuery} = {};
  private $btDelete: JQuery;
  private $input: JQuery;
  private $label: JQuery;
  private resizeHorizontally = true;
  private resizeVertically = true;
  private selectionOffset = [0, 0];
  private selectionOrigin = [0, 0];
  private area: IImageArea = {
    label: 'New Label',
    x: 0,
    y: 0,
    z: 0,
    height: 0,
    width: 0,
  };

  constructor(
    private parent: ImageSelectAreas,
  ) {
    this.$image = this.parent.$image;
    this.options = this.parent.options;

    // Initialize an outline layer and place it above the trigger layer
    this.$outline = $('<div class=\'select-areas-outline\' />')
      .css({
        opacity: this.options.outlineOpacity,
        position: 'absolute',
      })
      .insertAfter(this.parent.$trigger);

    // Initialize a selection layer and place it above the outline layer
    this.$selection = $('<div />')
      .addClass('select-areas-background-area')
      .css({
        background: '#fff url(' + this.$image.attr('src') + ') no-repeat',
        backgroundSize: this.$image.width() + 'px',
        position: 'absolute',
      })
      .attr('title', this.area.label)
      .insertAfter(this.$outline);

    // Initialize all handlers
    if (this.options.allowResize) {
      $.each(['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'], (key, card) => {
        this.$resizeHandlers[card] = $('<div class=\'select-areas-resize-handler ' + card + '\'/>')
          .css({
            opacity: 0.5,
            position: 'absolute',
            cursor: card + '-resize',
          })
          .insertAfter(this.$selection)
          .mousedown((e) => this.pickResizeHandler(e))
          .bind('touchstart', (e) => this.pickResizeHandler(e));
      });
    }

    const bindToInput = ($obj) => {
      $obj.on('keydown', function(e) {
        const event = e || window.event || {};
        event.cancelBubble = true;
        event.stopPropagation && event.stopPropagation();
      });
      $obj.on('keyup', (e) => this.changeLabel(e));
      return $obj;
    };

    this.$input = bindToInput($('<input type="text" class="tag-label" value=""/>').val(this.area.label));
    this.$label = $('<div class=\'selection-label\'></div>').prepend(this.$input).insertAfter(this.$selection);

    // initialize delete button
    if (this.options.allowDelete) {
      const bindToDelete = ($obj) => {
        $obj.click((e) => this.deleteSelection(e))
          .bind('touchstart', (e) => this.deleteSelection(e))
          .bind('tap', (e) => this.deleteSelection(e));
        return $obj;
      };
      this.$btDelete = bindToDelete($('<div class=\'delete-area\' />'))
        .append(bindToDelete($('<div title="Remove" class="select-areas-delete-area" />')))
        .insertAfter(this.$selection);
    }

    if (this.options.allowMove) {
      this.$selection
        .mousedown((e) => this.pickSelection(e))
        .bind('touchstart', (e) => this.pickSelection(e));
    }

    if (this.options.allowEdit) {
      this.$label
        .mousedown((e) => this.pickInput(e))
        .bind('touchstart', (e) => this.pickInput(e));
    }

    this.focus();
  }

  getElementOffset(object) {
    const offset = $(object).offset();

    return [offset.left, offset.top];
  }

  getMousePosition(event: MouseEvent | any) {
    const imageOffset = this.getElementOffset(this.$image);

    if (!event.pageX) {
      if (event.originalEvent) {
        event = event.originalEvent;
      }

      if (event.changedTouches) {
        event = event.changedTouches[0];
      }

      if (event.touches) {
        event = event.touches[0];
      }
    }
    let x = event.pageX - imageOffset[0],
      y = event.pageY - imageOffset[1];

    x = (x < 0) ? 0 : (x > this.$image.width()) ? this.$image.width() : x;
    y = (y < 0) ? 0 : (y > this.$image.height()) ? this.$image.height() : y;

    return [x, y];
  }

  blur() {
    this.area.z = 0;
    this.$outline.removeClass('selected');
    this.refresh('blur');
  }

  focus(event?: JQuery.Event) {
    if (event) {
      $(':focus').blur();
    }
    this.parent.blurAll();
    this.area.z = 100;
    this.$outline.addClass('selected');
    this.refresh();
  }

  getData() {
    return this.area;
  }

  fireEvent(event: string) {
    this.$image.trigger(event, [this.parent.areaIndex(self), this.parent.areas()]);
  }

  off(...events) {
    $.each(events, (key, val) => {
      this.on(val);
    });
  }

  on(type, handler?: (e: JQuery.Event) => any) {
    let browserEvent, mobileEvent;
    switch (type) {
      case 'start':
        browserEvent = 'mousedown';
        mobileEvent = 'touchstart';
        break;
      case 'move':
        browserEvent = 'mousemove';
        mobileEvent = 'touchmove';
        break;
      case 'stop':
        browserEvent = 'mouseup';
        mobileEvent = 'touchend';
        break;
      default:
        return;
    }
    if (handler && jQuery.isFunction(handler)) {
      $(window.document).on(browserEvent, handler).on(mobileEvent, handler);
    } else {
      $(window.document).off(browserEvent).off(mobileEvent);
    }
  }

  updateSelection() {
    // Update the outline layer
    this.$outline.css({
      cursor: 'default',
      width: this.area.width,
      height: this.area.height,
      left: this.area.x,
      top: this.area.y,
      'z-index': this.area.z,
    });

    // Update the selection layer
    this.$selection.css({
      backgroundPosition: ( -this.area.x - 2) + 'px ' + ( -this.area.y - 2) + 'px',
      cursor: this.options.allowMove ? 'move' : 'default',
      width: (this.area.width - 4 > 0) ? (this.area.width - 4) : 0,
      height: (this.area.height - 4 > 0) ? (this.area.height - 4) : 0,
      left: this.area.x + 2,
      top: this.area.y + 2,
      'z-index': this.area.z + 2,
    });
  }

  updateResizeHandlers(show?: boolean) {
    if (!this.options.allowResize) {
      return;
    }
    if (show) {
      $.each(this.$resizeHandlers, (name, $handler) => {
        const
          semiwidth = Math.round($handler.width() / 2),
          semiheight = Math.round($handler.height() / 2),
          vertical = name[0],
          horizontal = name[name.length - 1];
        let top, left;

        if (vertical === 'n') {             // ====== North* ======
          top = -semiheight;

        } else if (vertical === 's') {      // ====== South* ======
          top = this.area.height - semiheight - 1;

        } else {                            // === East & West ===
          top = Math.round(this.area.height / 2) - semiheight - 1;
        }

        if (horizontal === 'e') {           // ====== *East ======
          left = this.area.width - semiwidth - 1;

        } else if (horizontal === 'w') {    // ====== *West ======
          left = -semiwidth;

        } else {                            // == North & South ==
          left = Math.round(this.area.width / 2) - semiwidth - 1;
        }

        $handler.css({
          display: 'block',
          left: this.area.x + left,
          top: this.area.y + top,
          'z-index': this.area.z + 1,
        });
      });
    } else {
      $('.select-areas-resize-handler').each(function () {
        $(this).css({display: 'none'});
      });
    }
  }

  updateLabel(active: boolean) {
    const rightSide = this.area.x >= 0.7 * this.$image.width();
    const bottomSide = this.area.y >= 0.7 * this.$image.height();
    if (this.$label) {
      this.$label.css({
        left: !rightSide ? this.area.x : 'auto',
        right: rightSide ? this.$image.width() - this.area.x - this.area.width : 'auto',
        top: !bottomSide ? this.area.y + this.area.height : 'auto',
        bottom: bottomSide ? this.$image.height() - this.area.y : 'auto',
        'z-index': this.area.z + 100,
      });
    }
    if (this.$input) {
      this.$input.css({
        width: this.options.allowEdit ? Math.max(this.area.width, 70) : 'auto',
        'text-align': rightSide ? 'right' : 'left',
      });
      this.$input.prop('readonly', !(active && this.options.allowEdit));
      if (this.$input.is(':focus') && !active) {
        this.$input.blur();
      }
    }
  }

  updateBtDelete(visible: boolean) {
    if (this.$btDelete) {
      this.$btDelete.css({
        display: visible ? 'block' : 'none',
        left: this.area.x + this.area.width + 1,
        top: this.area.y - this.$btDelete.outerHeight() - 1,
        'z-index': this.area.z + 1,
      });
    }
  }

  updateCursor(cursorType: string) {
    this.$outline.css({
      cursor: cursorType,
    });

    this.$selection.css({
      cursor: cursorType,
    });
  }

  refresh(sender?: string) {
    switch (sender) {
      case 'startSelection':
        this.parent._refresh();
        this.updateSelection();
        this.updateResizeHandlers();
        this.updateBtDelete(true);
        this.updateLabel(false);
        break;

      case 'pickSelection':
      case 'pickResizeHandler':
        this.updateResizeHandlers();
        break;

      case 'pickInput':
        this.updateLabel(true);
        break;

      case 'resizeSelection':
        this.updateSelection();
        this.updateResizeHandlers();
        this.updateCursor('crosshair');
        this.updateBtDelete(true);
        this.updateLabel(false);
        break;

      case 'moveSelection':
        this.updateSelection();
        this.updateResizeHandlers();
        this.updateCursor('move');
        this.updateBtDelete(true);
        this.updateLabel(false);
        break;

      case 'blur':
        this.updateSelection();
        this.updateResizeHandlers();
        this.updateBtDelete(false);
        this.updateLabel(false);
        break;

      // case 'releaseSelection':
      default:
        this.updateSelection();
        this.updateResizeHandlers(true);
        this.updateBtDelete(true);
        this.updateLabel(false);
    }
  }

  startSelection(event: JQuery.Event) {
    cancelEvent(event);

    // Reset the selection size
    this.area.width = this.options.minSize[0];
    this.area.height = this.options.minSize[1];
    this.focus(event);
    this.on('move', (e) => this.resizeSelection(e));
    this.on('stop', (e) => this.releaseSelection(e));

    // Get the selection origin
    this.selectionOrigin = this.getMousePosition(event);
    if (this.selectionOrigin[0] + this.area.width > this.$image.width()) {
      this.selectionOrigin[0] = this.$image.width() - this.area.width;
    }
    if (this.selectionOrigin[1] + this.area.height > this.$image.height()) {
      this.selectionOrigin[1] = this.$image.height() - this.area.height;
    }
    // And set its position
    this.area.x = this.selectionOrigin[0];
    this.area.y = this.selectionOrigin[1];

    this.refresh('startSelection');
  }

  pickSelection(event: JQuery.Event) {
    cancelEvent(event);
    this.focus(event);
    this.on('move', (e) => this.moveSelection(e));
    this.on('stop', (e) => this.releaseSelection(e));

    const mousePosition = this.getMousePosition(event);

    // Get the selection offset relative to the mouse position
    this.selectionOffset[0] = mousePosition[0] - this.area.x;
    this.selectionOffset[1] = mousePosition[1] - this.area.y;

    this.refresh('pickSelection');
  }

  pickInput(event: JQuery.Event) {
    cancelEvent(event);
    this.focus(event);
    this.refresh('pickInput');
    this.$input.focus();
  }

  pickResizeHandler(event: JQuery.Event) {
    cancelEvent(event);
    this.focus(event);

    const card = (<HTMLElement> event.target).className.split(' ')[1];
    if (card[card.length - 1] === 'w') {
      this.selectionOrigin[0] += this.area.width;
      this.area.x = this.selectionOrigin[0] - this.area.width;
    }
    if (card[0] === 'n') {
      this.selectionOrigin[1] += this.area.height;
      this.area.y = this.selectionOrigin[1] - this.area.height;
    }
    if (card === 'n' || card === 's') {
      this.resizeHorizontally = false;
    } else if (card === 'e' || card === 'w') {
      this.resizeVertically = false;
    }

    this.on('move', (e) => this.resizeSelection(e));
    this.on('stop', (e) => this.releaseSelection(e));

    this.refresh('pickResizeHandler');
  }

  resizeSelection(event: JQuery.Event) {
    cancelEvent(event);
    this.focus(event);

    const mousePosition = this.getMousePosition(event);
    const options = this.options;

    // Get the selection size
    let height = mousePosition[1] - this.selectionOrigin[1],
      width = mousePosition[0] - this.selectionOrigin[0];

    // If the selection size is smaller than the minimum size set it to minimum size
    if (Math.abs(width) < options.minSize[0]) {
      width = (width >= 0) ? options.minSize[0] : -options.minSize[0];
    }
    if (Math.abs(height) < options.minSize[1]) {
      height = (height >= 0) ? options.minSize[1] : -options.minSize[1];
    }
    // Test if the selection size exceeds the image bounds
    if (this.selectionOrigin[0] + width < 0 || this.selectionOrigin[0] + width > this.$image.width()) {
      width = -width;
    }
    if (this.selectionOrigin[1] + height < 0 || this.selectionOrigin[1] + height > this.$image.height()) {
      height = -height;
    }
    // Test if the selection size is bigger than the maximum size (ignored if minSize > maxSize)
    if (options.maxSize[0] > options.minSize[0] && options.maxSize[1] > options.minSize[1]) {
      if (Math.abs(width) > options.maxSize[0]) {
        width = (width >= 0) ? options.maxSize[0] : -options.maxSize[0];
      }

      if (Math.abs(height) > options.maxSize[1]) {
        height = (height >= 0) ? options.maxSize[1] : -options.maxSize[1];
      }
    }

    // Set the selection size
    if (this.resizeHorizontally) {
      this.area.width = width;
    }
    if (this.resizeVertically) {
      this.area.height = height;
    }
    // If any aspect ratio is specified
    if (options.aspectRatio) {
      // Calculate the new width and height
      if ((width > 0 && height > 0) || (width < 0 && height < 0)) {
        if (this.resizeHorizontally) {
          height = Math.round(width / options.aspectRatio);
        } else {
          width = Math.round(height * options.aspectRatio);
        }
      } else {
        if (this.resizeHorizontally) {
          height = -Math.round(width / options.aspectRatio);
        } else {
          width = -Math.round(height * options.aspectRatio);
        }
      }
      // Test if the new size exceeds the image bounds
      if (this.selectionOrigin[0] + width > this.$image.width()) {
        width = this.$image.width() - this.selectionOrigin[0];
        height = (height > 0) ? Math.round(width / options.aspectRatio) : -Math.round(width / options.aspectRatio);
      }

      if (this.selectionOrigin[1] + height < 0) {
        height = -this.selectionOrigin[1];
        width = (width > 0) ? -Math.round(height * options.aspectRatio) : Math.round(height * options.aspectRatio);
      }

      if (this.selectionOrigin[1] + height > this.$image.height()) {
        height = this.$image.height() - this.selectionOrigin[1];
        width = (width > 0) ? Math.round(height * options.aspectRatio) : -Math.round(height * options.aspectRatio);
      }

      // Set the selection size
      this.area.width = width;
      this.area.height = height;
    }

    if (this.area.width < 0) {
      this.area.width = Math.abs(this.area.width);
      this.area.x = this.selectionOrigin[0] - this.area.width;
    } else {
      this.area.x = this.selectionOrigin[0];
    }
    if (this.area.height < 0) {
      this.area.height = Math.abs(this.area.height);
      this.area.y = this.selectionOrigin[1] - this.area.height;
    } else {
      this.area.y = this.selectionOrigin[1];
    }

    this.fireEvent('changing');
    this.refresh('resizeSelection');
  }

  moveSelection(event: JQuery.Event) {
    if (!this.options.allowMove) {
      return;
    }
    cancelEvent(event);
    this.focus(event);

    const mousePosition = this.getMousePosition(event);
    this.moveTo({
      x: mousePosition[0] - this.selectionOffset[0],
      y: mousePosition[1] - this.selectionOffset[1],
    });

    this.fireEvent('changing');
  }

  moveTo(point) {
    // Set the selection position on the x-axis relative to the bounds
    // of the image
    if (point.x > 0) {
      if (point.x + this.area.width < this.$image.width()) {
        this.area.x = point.x;
      } else {
        this.area.x = this.$image.width() - this.area.width;
      }
    } else {
      this.area.x = 0;
    }
    // Set the selection position on the y-axis relative to the bounds
    // of the image
    if (point.y > 0) {
      if (point.y + this.area.height < this.$image.height()) {
        this.area.y = point.y;
      } else {
        this.area.y = this.$image.height() - this.area.height;
      }
    } else {
      this.area.y = 0;
    }
    this.refresh('moveSelection');
  }

  releaseSelection(event) {
    cancelEvent(event);
    this.off('move', 'stop');

    // Update the selection origin
    this.selectionOrigin[0] = this.area.x;
    this.selectionOrigin[1] = this.area.y;

    // Reset the resize constraints
    this.resizeHorizontally = true;
    this.resizeVertically = true;

    this.fireEvent('changed');

    this.refresh('releaseSelection');
  }

  changeLabel(event: KeyboardEvent) {
    this.area.label = (<HTMLInputElement> event.target).value;
    this.$selection.attr('title', this.area.label);

    this.fireEvent('changed');
  }

  deleteSelection(event: JQuery.Event, silent?: boolean) {
    cancelEvent(event);
    this.$selection.remove();
    this.$outline.remove();
    $.each(this.$resizeHandlers, function (card, $handler) {
      $handler.remove();
    });
    if (this.$btDelete) {
      this.$btDelete.remove();
    }
    this.$label.remove();
    this.parent._remove(this.parent.areaIndex(self));
    if (!silent) {
      this.fireEvent('changed');
    }
  }

  nudge(point) {
    point.x = this.area.x;
    point.y = this.area.y;
    if (point.d) {
      point.y = this.area.y + point.d;
    }
    if (point.u) {
      point.y = this.area.y - point.u;
    }
    if (point.l) {
      point.x = this.area.x - point.l;
    }
    if (point.r) {
      point.x = this.area.x + point.r;
    }
    this.moveTo(point);
    this.fireEvent('changed');
  }

  set(dimensions: IImageArea, silent?: boolean) {
    this.area = $.extend(this.area, dimensions);
    this.selectionOrigin[0] = this.area.x;
    this.selectionOrigin[1] = this.area.y;
    this.$input.val(this.area.label);
    this.$selection.attr('title', this.area.label);
    if (silent) {
      this.refresh('blur');
    } else {
      this.fireEvent('changed');
    }
  }

  contains(point) {
    return (point.x >= this.area.x) && (point.x <= this.area.x + this.area.width) &&
      (point.y >= this.area.y) && (point.y <= this.area.y + this.area.height);
  }
}

class ImageSelectAreas {
  $image: JQuery;
  $holder: JQuery;
  options: any;
  $trigger: JQuery;
  private _areas: ImageArea[];

  constructor(object, customOptions) {
    const defaultOptions = {
      allowEdit: true,
      allowMove: true,
      allowResize: true,
      allowSelect: true,
      allowDelete: true,
      allowNudge: true,
      aspectRatio: 0,
      minSize: [0, 0],
      maxSize: [0, 0],
      width: 0,
      maxAreas: 0,
      outlineOpacity: 0.5,
      overlayOpacity: 0.5,
      areas: [],
      onChanging: null,
      onChanged: null,
    };

    this.options = $.extend(defaultOptions, customOptions);

    if (!this.options.allowEdit) {
      this.options.allowSelect = this.options.allowMove = this.options.allowResize = this.options.allowDelete = false;
    }

    this._areas = [];

    // Initialize the image layer
    this.$image = $(object);

    this.$image.css({
      width: this.options.width || this.$image.width(),
      height: this.options.height || this.$image.height(),
    });

    if (this.options.onChanging) {
      this.$image.on('changing', this.options.onChanging);
    }
    if (this.options.onChanged) {
      this.$image.on('changed', this.options.onChanged);
    }
    if (this.options.onLoaded) {
      this.$image.on('loaded', this.options.onLoaded);
    }

    // Initialize an image holder
    this.$holder = $('<div />')
      .css({
        position: 'relative',
        width: this.$image.width(),
        height: this.$image.height(),
      });

    // Wrap the holder around the image
    this.$image.wrap(this.$holder);

    // Initialize a trigger layer and place it above the overlay layer
    this.$trigger = $('<div />')
      .css({
        top: 0,
        left: 0,
        backgroundColor: '#000000',
        opacity: 0,
        position: 'absolute',
        width: this.$image.width(),
        height: this.$image.height(),
      })
      .insertAfter(this.$image);

    $.each(this.options.areas, (key, area) => {
      this._add(area, true);
    });


    this.blurAll();
    this._refresh();

    if (this.options.allowSelect) {
      // Bind an event handler to the 'mousedown' event of the trigger layer
      this.$trigger
        .mousedown((e) => this.newArea(e))
        .on('touchstart', (e) => this.newArea(e));
    }
    if (this.options.allowNudge) {
      $('html').keydown((e) => { // move selection with arrow keys
        const codes = {
            37: 'l',
            38: 'u',
            39: 'r',
            40: 'd',
          },
          direction = codes[e.which];
        let selectedArea;

        if (direction) {
          this._eachArea(function (area) {
            if (area.getData().z === 100) {
              selectedArea = area;
              return false;
            }
          });
          if (selectedArea) {
            const move = {};
            move[direction] = 1;
            selectedArea.nudge(move);
            cancelEvent(e);
          }
        }
      });
    }
  }

  remove(id, silent?: boolean) {
    if (this._areas[id]) {
      this._areas[id].deleteSelection(null, silent);
    }
  }

  newArea(event?: JQuery.Event) {
    this.blurAll();
    if (this.options.maxAreas && this.options.maxAreas <= this.areas().length) {
      return -1;
    }
    const id = this._areas ? this._areas.length : 0;

    this._areas[id] = new ImageArea(this);
    if (event) {
      this._areas[id].startSelection(event);
    }
    return id;
  }

  focus(id) {
    if (this._areas[id]) {
      this._areas[id].focus();
      this._refresh();
    }
  }

  set(id, options, silent?: boolean) {
    if (this._areas[id]) {
      this._areas[id].set(options, silent);
      if (!silent) {
        this._areas[id].focus();
      }
      this._refresh();
    }
  }

  add(options, silent) {
    this.blurAll();
    if ($.isArray(options)) {
      $.each(options, (key, val) => {
        this._add(val, silent);
      });
    } else {
      this._add(options, silent);
    }
    this._refresh();
    if (!this.options.allowSelect && !this.options.allowMove && !this.options.allowResize && !this.options.allowDelete) {
      this.blurAll();
    }
  }

  reset() {
    this._eachArea((area, id) => {
      this.remove(id);
    });
    this._refresh();
  }

  destroy() {
    for (let i = this._areas.length - 1; i >= 0; i--) {
      this.remove(i, true);
    }
    this.$holder.remove();
    this.$trigger.remove();
    this.$image.css('width', '').css('position', '').unwrap();
    this.$image.removeData('mainImageSelectAreas');
  }

  areas() {
    const ret = [];
    this._eachArea(function (area) {
      ret.push(area.getData());
    });
    return ret;
  }

  areaIndex(area) {
    return this._areas.indexOf(area);
  }

  blurAll() {
    this._eachArea(function (area) {
      area.blur();
    });
  }

  contains(point) {
    let res = false;
    this._eachArea(function (area) {
      if (area.contains(point)) {
        res = true;
        return false;
      }
    });
    return res;
  }

  _refresh() {
    // @todo blurring
    // const nbAreas = this.areas().length;
    // if (nbAreas) {
    //   this.$image.addClass('blurred');
    // } else {
    //   this.$image.removeClass('blurred');
    // }
    this.$trigger.css({
      cursor: this.options.allowSelect ? 'crosshair' : 'default',
    });
  }

  _remove(id) {
    this._areas.splice(id, 1);
    this._refresh();
  }

  private _add(options, silent) {
    const id = this.newArea();
    this.set(id, options, silent);
  }

  private _eachArea(cb: Function) {
    $.each(this._areas, function (id, area) {
      return cb(area, id);
    });
  }
}

(function ($) {
  const selectAreas = function (object, options?) {
    const $object = $(object);
    if (!$object.data('mainImageSelectAreas')) {
      const mainImageSelectAreas = new ImageSelectAreas(object, options);
      $object.data('mainImageSelectAreas', mainImageSelectAreas);
      $object.trigger('loaded');
    }
    return $object.data('mainImageSelectAreas');
  };

  $.fn.selectAreas = function (customOptions: string | object, ...args) {
    if (typeof customOptions === 'string' && ImageSelectAreas.prototype[customOptions]) { // Method call
      const ret = ImageSelectAreas.prototype[customOptions].apply(selectAreas(this), args);
      return typeof ret === 'undefined' ? this : ret;

    } else if (typeof customOptions === 'object' || !customOptions) { // Initialization
      // Iterate over each object
      this.each(function() {
        //tslint:disable-next-line:no-this-assignment
        const currentObject = this,
          image = new Image();

        // And attach selectAreas when the object is loaded
        image.onload = () => {
          setTimeout(() => {
            selectAreas(currentObject, customOptions);
          }, 50);
        };

        // Reset the src because cached images don't fire load sometimes
        image.src = currentObject.src;

      });
      return this;

    } else {
      $.error('Method ' + customOptions + ' does not exist on jQuery.selectAreas');
    }
  };
})(jQuery);
