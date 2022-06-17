import { ArrayExt } from '@phosphor/algorithm/lib';
import { IDisposable } from '@phosphor/disposable/lib';
import { ElementExt } from '@phosphor/domutils/lib';
import { IMessageHandler, Message } from '@phosphor/messaging/lib';
import { ISignal, Signal } from '@phosphor/signaling/lib';
import { VirtualDOM, VirtualElement, h } from '@phosphor/virtualdom/lib';
import { Title, Widget } from '@phosphor/widgets/lib';

namespace Private {}

/**
 * A widget which displays titles as a single row or column of tabs.
 *
 * #### Notes
 * If CSS transforms are used to rotate nodes for vertically oriented
 * text, then tab dragging will not work correctly. The `tabsMovable`
 * property should be set to `false` when rotating nodes from CSS.
 */
export class SplitDockTab extends Widget implements EventListenerObject {
  /**
   * The renderer used by the tab.
   */
  readonly renderer: SplitDockTab.IRenderer;

  private _child: Widget = null;
  private _dragData: Private.IDragData | null = null;
  private _tabExpandRequested = new Signal<this, SplitDockTab.ITabCommonRequestArgs>(this);
  private _tabMinimizeRequested = new Signal<this, SplitDockTab.ITabCommonRequestArgs>(this);
  private _tabCollapseRequested = new Signal<this, SplitDockTab.ITabCommonRequestArgs>(this);
  private _tabCloseRequested = new Signal<this, SplitDockTab.ITabCommonRequestArgs>(this);
  private _tabDetachRequested = new Signal<this, SplitDockTab.ITabDetachRequestedArgs>(this);
  private _tabActivateRequested = new Signal<this, SplitDockTab.ITabCommonRequestArgs>(this);

  /**
   * Construct a new tab.
   *
   * @param options - The options for initializing the tab.
   */
  constructor(options: SplitDockTab.IOptions) {
    super({ node: Private.createNode() });
    this.addClass('p-SplitDockTab');
    this.setFlag(Widget.Flag.DisallowLayout);
    this.renderer = options.renderer || SplitDockTab.defaultRenderer;
    this._child = options.widget;

    // Connect to the title changed signal.
    this._child.title.changed.connect(this._onTitleChanged, this);

    this.update();
  }

  set expanded(value: boolean) {
    const expandIcon = this.contentNode.querySelector('.' + this.renderer.expandIconSelector);
    $(expandIcon).toggleClass(this.renderer.collapseIconSelector, value);
  }

  /**
   * Dispose of the resources held by the widget.
   */
  dispose(): void {
    this._releaseMouse();
    this._child = null;
    super.dispose();
  }

  /**
   * A signal emitted when a tab is clicked by the user.
   *
   * #### Notes
   * If the clicked tab is not the current tab, the clicked tab will be
   * made current and the `currentChanged` signal will be emitted first.
   *
   * This signal is emitted even if the clicked tab is the current tab.
   */
  get tabActivateRequested(): ISignal<this, SplitDockTab.ITabCommonRequestArgs> {
    return this._tabActivateRequested;
  }

  get tabMinimizeRequested(): ISignal<this, SplitDockTab.ITabCommonRequestArgs> {
    return this._tabMinimizeRequested;
  }

  get tabExpandRequested(): ISignal<this, SplitDockTab.ITabCommonRequestArgs> {
    return this._tabExpandRequested;
  }

  get tabCollapseRequested(): ISignal<this, SplitDockTab.ITabCommonRequestArgs> {
    return this._tabCollapseRequested;
  }

  get tabCloseRequested(): ISignal<this, SplitDockTab.ITabCommonRequestArgs> {
    return this._tabCloseRequested;
  }

  /**
   * A signal emitted when a tab is dragged beyond the detach threshold.
   *
   * #### Notes
   * This signal is emitted when the user drags a tab with the mouse,
   * and mouse is dragged beyond the detach threshold.
   *
   * The consumer of the signal should call `releaseMouse` and remove
   * the tab in order to complete the detach.
   *
   * This signal is only emitted once per drag cycle.
   */
  get tabDetachRequested(): ISignal<this, SplitDockTab.ITabDetachRequestedArgs> {
    return this._tabDetachRequested;
  }

  get child(): Widget {
    return this._child;
  }

  hide(): void {
    this.child.hide();
    super.hide();
  }

  show(): void {
    this.child.show();
    super.show();
  }

  /**
   * The tab content node.
   *
   * #### Notes
   * This is the node which holds the tab nodes.
   *
   * Modifying this node directly can lead to undefined behavior.
   */
  get contentNode(): HTMLUListElement {
    return this.node as HTMLUListElement;
  }

  /**
   * Release the mouse and restore the non-dragged tab positions.
   *
   * #### Notes
   * This will cause the tab to stop handling mouse events and to
   * restore the tabs to their non-dragged positions.
   */
  releaseMouse(): void {
    this._releaseMouse();
  }

  /**
   * Handle the DOM events for the tab.
   *
   * @param event - The DOM event sent to the tab.
   *
   * #### Notes
   * This method implements the DOM `EventListener` interface and is
   * called in response to events on the tab's DOM node.
   *
   * This should not be called directly by user code.
   */
  handleEvent(event: Event): void {
    switch (event.type) {
      case 'mousedown':
        this._evtMouseDown(event as MouseEvent);
        break;
      case 'mousemove':
        this._evtMouseMove(event as MouseEvent);
        break;
      case 'mouseup':
        this._evtMouseUp(event as MouseEvent);
        break;
      case 'keydown':
        this._evtKeyDown(event as KeyboardEvent);
        break;
      case 'contextmenu':
        event.preventDefault();
        event.stopPropagation();
        break;
    }
  }

  /**
   * A message handler invoked on a `'before-attach'` message.
   */
  protected onBeforeAttach(msg: Message): void {
    this.node.addEventListener('mousedown', this);
  }

  /**
   * A message handler invoked on an `'after-detach'` message.
   */
  protected onAfterDetach(msg: Message): void {
    this.node.removeEventListener('mousedown', this);
    this._releaseMouse();
  }

  /**
   * A message handler invoked on an `'update-request'` message.
   */
  protected onUpdateRequest(msg: Message): void {
    let renderer = this.renderer;
    VirtualDOM.render(
      renderer.renderTab(this._child),
      this.contentNode,
    );
  }

  /**
   * Handle the `'keydown'` event for the tab.
   */
  private _evtKeyDown(event: KeyboardEvent): void {
    // Stop all input events during drag.
    event.preventDefault();
    event.stopPropagation();

    // Release the mouse if `Escape` is pressed.
    if (event.keyCode === 27) {
      this._releaseMouse();
    }
  }

  /**
   * Handle the `'mousedown'` event for the tab.
   */
  private _evtMouseDown(event: MouseEvent): void {
    // Do nothing if it's not a left or middle mouse press.
    if (event.button !== 0 && event.button !== 1) {
      return;
    }

    // Do nothing if a drag is in progress.
    if (this._dragData) {
      return;
    }

    // Lookup the tab nodes.
    let tabs = this.contentNode.children;

    // Find the index of the pressed tab.
    let index = ArrayExt.findFirstIndex(tabs, tab => {
      return ElementExt.hitTest(tab, event.clientX, event.clientY);
    });

    // Do nothing if the press is not on a tab.
    if (index === -1) {
      return;
    }

    // Pressing on a tab stops the event propagation.
    event.preventDefault();
    event.stopPropagation();

    // Initialize the non-measured parts of the drag data.
    this._dragData = {
      tab: tabs[index] as HTMLElement,
      index: index,
      pressX: event.clientX,
      pressY: event.clientY,
      tabPos: -1,
      tabSize: -1,
      tabPressPos: -1,
      targetIndex: -1,
      tabLayout: null,
      contentRect: null,
      override: null,
      dragActive: false,
      dragAborted: false,
      detachRequested: false,
    };

    // Add the document mouse up listener.
    document.addEventListener('mouseup', this, true);

    // Do nothing else if the middle button is clicked.
    if (event.button === 1) {
      return;
    }

    // Do nothing else if the close icon is clicked.
    let closeIcon = tabs[index].querySelector('.' + this.renderer.closeIconSelector);
    if (closeIcon && closeIcon.contains(event.target as HTMLElement)) {
      return;
    }

    document.addEventListener('mousemove', this, true);
    document.addEventListener('keydown', this, true);
    document.addEventListener('contextmenu', this, true);

    if ($(event.target).hasClass('p-SplitDockTab-tabLabel')) {
      // Emit the tab activate request signal.
      this._tabActivateRequested.emit({
        widget: this._child,
      });
    }
  }

  /**
   * Handle the `'mousemove'` event for the tab.
   */
  private _evtMouseMove(event: MouseEvent): void {
    // Do nothing if no drag is in progress.
    let data = this._dragData;
    if (!data) {
      return;
    }

    // Suppress the event during a drag.
    event.preventDefault();
    event.stopPropagation();

    // Lookup the tab nodes.
    let tab = this.contentNode.children[0] as HTMLElement;

    // Bail early if the drag threshold has not been met.
    if (!data.detachRequested && !Private.dragExceeded(data, event)) {
      return;
    }

    // Emit the detach requested signal if the threshold is exceeded.
    if (!data.detachRequested) {
      // Only emit the signal once per drag cycle.
      data.detachRequested = true;

      // Setup the arguments for the signal.
      let clientX = event.clientX;
      let clientY = event.clientY;
      let widget = this._child;

      // Emit the tab detach requested signal.
      this._tabDetachRequested.emit({ widget, tab, clientX, clientY });

      // Bail if the signal handler aborted the drag.
      if (data.dragAborted) {
        return;
      }
    }
  }

  /**
   * Handle the `'mouseup'` event for the document.
   */
  private _evtMouseUp(event: MouseEvent): void {
    // Do nothing if it's not a left or middle mouse release.
    if (event.button !== 0 && event.button !== 1) {
      return;
    }

    // Do nothing if no drag is in progress.
    const data = this._dragData;
    if (!data) {
      return;
    }

    // Stop the event propagation.
    event.preventDefault();
    event.stopPropagation();

    // Remove the extra mouse event listeners.
    document.removeEventListener('mousemove', this, true);
    document.removeEventListener('mouseup', this, true);
    document.removeEventListener('keydown', this, true);
    document.removeEventListener('contextmenu', this, true);

    // Clear the drag data.
    this._dragData = null;

    let widget = this._child;

    // Emit the close requested signal if the middle button is released.
    if (event.button === 1) {
      this._tabCloseRequested.emit({ widget });
      return;
    }

    // Emit the close requested signal if the close icon was released.
    const minimizeIcon = this.contentNode.querySelector('.' + this.renderer.minimizeIconSelector);
    const expandIcon = this.contentNode.querySelector('.' + this.renderer.expandIconSelector);
    const collapseIcon = this.contentNode.querySelector('.' + this.renderer.collapseIconSelector);
    const closeIcon = this.contentNode.querySelector('.' + this.renderer.closeIconSelector);

    if (this.child instanceof SplitDockTab.IWidget) {
      this.child.icons.forEach(_ => {
        const icon = this.contentNode.querySelector('.' + _.selector);
        if (icon && icon.contains(event.target as HTMLElement)) {
          _.processMessage(new Message('click'));
        }
      });
    }

    if (minimizeIcon && minimizeIcon.contains(event.target as HTMLElement)) {
      this._tabMinimizeRequested.emit({ widget });
      return;
    } else if (collapseIcon && collapseIcon.contains(event.target as HTMLElement)) {
      this._tabCollapseRequested.emit({ widget });
      return;
    } else if (expandIcon && expandIcon.contains(event.target as HTMLElement)) {
      this._tabExpandRequested.emit({ widget });
      return;
    } else if (closeIcon && widget.title.closable && closeIcon.contains(event.target as HTMLElement)) {
      // Ignore the release if the title is not closable.
      this._tabCloseRequested.emit({ widget });
      return;
    }
  }

  /**
   * Release the mouse and restore the non-dragged tab positions.
   */
  private _releaseMouse(): void {
    // Do nothing if no drag is in progress.
    let data = this._dragData;
    if (!data) {
      return;
    }

    // Clear the drag data reference.
    this._dragData = null;

    // Remove the extra mouse listeners.
    document.removeEventListener('mousemove', this, true);
    document.removeEventListener('mouseup', this, true);
    document.removeEventListener('keydown', this, true);
    document.removeEventListener('contextmenu', this, true);

    // Indicate the drag has been aborted. This allows the mouse
    // event handlers to return early when the drag is canceled.
    data.dragAborted = true;

    // If the drag is not active, there's nothing more to do.
    if (!data.dragActive) {
      return;
    }

    // Clear the cursor override.
    data.override!.dispose();

    // Clear the dragging style classes.
    data.tab.classList.remove('p-mod-dragging');
    this.removeClass('p-mod-dragging');
  }

  /**
   * Handle the `changed` signal of a title object.
   */
  private _onTitleChanged(): void {
    this.update();
  }
}

/**
 * The namespace for the `SplitDockTab` class statics.
 */
export
namespace SplitDockTab {
  /**
   * An options object for creating a tab.
   */
  export
  interface IOptions {
    widget: Widget;

    /**
     * A renderer to use with the tab.
     *
     * The default is a shared renderer instance.
     */
    renderer?: IRenderer;
  }

  /**
   * The arguments object for the most of SplitDockTab signals signal.
   */
  export
  interface ITabCommonRequestArgs {
    /**
     * The widget
     */
    readonly widget: Widget;
  }

  /**
   * The arguments object for the `tabDetachRequested` signal.
   */
  export
  interface ITabDetachRequestedArgs extends ITabCommonRequestArgs {
    /**
     * The node representing the tab.
     */
    readonly tab: HTMLElement;

    /**
     * The current client X position of the mouse.
     */
    readonly clientX: number;

    /**
     * The current client Y position of the mouse.
     */
    readonly clientY: number;
  }

  export abstract class IWidget extends Widget {
    abstract readonly icons: ITabIcon[];
  }

  export interface ITabIcon extends IMessageHandler {
    selector: string;
    tooltip?: string;
  }

  /**
   * A renderer for use with a tab.
   */
  export interface IRenderer {
    readonly minimizeIconSelector: string;

    readonly expandIconSelector: string;

    readonly collapseIconSelector: string;

    readonly closeIconSelector: string;

    /**
     * Render the virtual element for a tab.
     */
    renderTab(widget: Widget): VirtualElement;
  }

  /**
   * The default implementation of `IRenderer`.
   *
   * #### Notes
   * Subclasses are free to reimplement rendering methods as needed.
   */
  export class Renderer implements IRenderer {
    /**
     * Selectors which matches the icons node in a tab.
     */
    readonly refreshIconSelector = 'p-SplitDockTab-tabRefreshIcon';

    readonly minimizeIconSelector = 'p-SplitDockTab-tabMinimizeIcon';

    readonly expandIconSelector = 'p-SplitDockTab-tabExpandIcon';

    readonly collapseIconSelector = 'p-SplitDockTab-tabCollapseIcon';

    readonly closeIconSelector = 'p-SplitDockTab-tabCloseIcon';

    readonly filterIconSelector = 'p-SplitDockTab-tabFilterIcon';

    readonly pinIconSelector = 'p-SplitDockTab-tabPinIcon';

    /**
     * Construct a new renderer.
     */
    constructor() {
    }

    renderTab(widget: Widget): VirtualElement {
      const className = this.createTabClass(widget.title);

      const icons = widget instanceof IWidget
        ? widget.icons.map(_ => this.renderTabIcon(_))
        : [];

      return (
        h.div({ className, title: widget.title.caption },
          this.renderIcon(widget.title),
          this.renderLabel(widget.title),
          icons,
          this.renderMinimizeIcon(),
          this.renderExpandIcon(),
          this.renderCloseIcon(widget.title),
        )
      );
    }

    /**
     * Render the custom icon element for a tab.
     */
    renderTabIcon(icon: ITabIcon): VirtualElement {
      return h.div({ className: icon.selector, title: icon.tooltip || '' });
    }

    /**
     * Render the icon element for a tab.
     */
    renderIcon(title: Title<any>): VirtualElement {
      return h.div({ className: this.createIconClass(title) });
    }

    /**
     * Render the label element for a tab.
     */
    renderLabel(title: Title<any>): VirtualElement {
      return h.div({ className: 'p-SplitDockTab-tabLabel' }, title.label);
    }

    renderMinimizeIcon(): VirtualElement {
      return h.div({ className: this.minimizeIconSelector, title: 'Minimize' });
    }

    renderExpandIcon(): VirtualElement {
      return h.div({ className: this.expandIconSelector, title: 'Expand' });
    }

    renderCloseIcon(title: Title<any>): VirtualElement {
      return title.closable ? h.div({ className: this.closeIconSelector, title: 'Close' }) : null;
    }

    /**
     * Create the class name for the tab.
     */
    createTabClass(title: Title<any>): string {
      let name = 'p-SplitDockTab-tab p-mod-current';
      if (title.className) {
        name += ` ${title.className}`;
      }
      if (title.closable) {
        name += ' p-mod-closable';
      }
      return name;
    }

    /**
     * Create the class name for the tab icon.
     */
    createIconClass(title: Title<any>): string {
      let name = 'p-SplitDockTab-tabIcon';
      let extra = title.icon;
      return extra ? `${name} ${extra}` : name;
    }
  }

  /**
   * The default `Renderer` instance.
   */
  export const defaultRenderer = new Renderer();
}


/**
 * The namespace for the module implementation details.
 */
namespace Private {
  /**
   * The start drag distance threshold.
   */
  export const DRAG_THRESHOLD = 5;

  /**
   * The detach distance threshold.
   */
  export const DETACH_THRESHOLD = 20;

  /**
   * A struct which holds the drag data for a tab.
   */
  export
  interface IDragData {
    /**
     * The tab node being dragged.
     */
    tab: HTMLElement;

    /**
     * The index of the tab being dragged.
     */
    index: number;

    /**
     * The mouse press client X position.
     */
    pressX: number;

    /**
     * The mouse press client Y position.
     */
    pressY: number;

    /**
     * The offset left/top of the tab being dragged.
     *
     * This will be `-1` if the drag is not active.
     */
    tabPos: number;

    /**
     * The offset width/height of the tab being dragged.
     *
     * This will be `-1` if the drag is not active.
     */
    tabSize: number;

    /**
     * The original mouse X/Y position in tab coordinates.
     *
     * This will be `-1` if the drag is not active.
     */
    tabPressPos: number;

    /**
     * The tab target index upon mouse release.
     *
     * This will be `-1` if the drag is not active.
     */
    targetIndex: number;

    /**
     * The array of tab layout objects snapped at drag start.
     *
     * This will be `null` if the drag is not active.
     */
    tabLayout: ITabLayout[] | null;

    /**
     * The bounding client rect of the tab content node.
     *
     * This will be `null` if the drag is not active.
     */
    contentRect: ClientRect | null;

    /**
     * The disposable to clean up the cursor override.
     *
     * This will be `null` if the drag is not active.
     */
    override: IDisposable | null;

    /**
     * Whether the drag is currently active.
     */
    dragActive: boolean;

    /**
     * Whether the drag has been aborted.
     */
    dragAborted: boolean;

    /**
     * Whether a detach request as been made.
     */
    detachRequested: boolean;
  }

  /**
   * An object which holds layout data for a tab.
   */
  export
  interface ITabLayout {
    /**
     * The left/top margin value for the tab.
     */
    margin: number;

    /**
     * The offset left/top position of the tab.
     */
    pos: number;

    /**
     * The offset width/height of the tab.
     */
    size: number;
  }

  /**
   * Create the DOM node for a tab.
   */
  export function createNode(): HTMLDivElement {
    return document.createElement('div');
  }

  /**
   * Test if the event exceeds the drag threshold.
   */
  export function dragExceeded(data: IDragData, event: MouseEvent): boolean {
    let dx = Math.abs(event.clientX - data.pressX);
    let dy = Math.abs(event.clientY - data.pressY);
    return dx >= DRAG_THRESHOLD || dy >= DRAG_THRESHOLD;
  }

}
