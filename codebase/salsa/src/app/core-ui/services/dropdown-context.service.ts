import { Injectable } from '@angular/core';

@Injectable()
export class DropdownContextService {
  private dropdowns: any[] = [];

  constructor() {
    document.addEventListener('click', () => {
      this.dropdowns.forEach(dropdown => dropdown.classList.remove('open'));
    });
    document.addEventListener('contextmenu', (event: Event) => {
      let dropdowns = this.dropdowns;
      dropdowns.forEach(dropdown => {
        if (!dropdown.contains(event.target)) {
          dropdown.classList.remove('open');
        }
      });
    });
  }

  register(el: any) {
    this.dropdowns.push(el);
  }

  deregister(el: any) {
    this.dropdowns = this.dropdowns.filter(elem => elem !== el);
  }
}
