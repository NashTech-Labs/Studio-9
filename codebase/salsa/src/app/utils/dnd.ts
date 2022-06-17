export class DndUtil {
  static getDragImageElement(title?: string): HTMLElement {
    let dragImageElement = document.createElement('div');
    dragImageElement.classList.add('draggable-cloned-wrapper');
    dragImageElement.style.position = 'absolute';
    dragImageElement.style.top = '-1000px';
    dragImageElement.style.padding = '5px';
    dragImageElement.innerHTML = DndUtil.getTableTemplate(title);
    return dragImageElement;
  }

  // TODO: what about word-breaking?
  private static getTableTemplate(tableName: string = 'Table'): string {
    return `
        <div class="dragged-wrapper">
            <i class="icons icon-doc" style="font-size: 2em;"></i>
            <div class="dragged-title">${tableName}</div>
        </div>
    `;
  }
}
