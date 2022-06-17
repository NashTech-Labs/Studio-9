import { Directive, ElementRef, Input, NgZone, OnChanges, OnDestroy, OnInit } from '@angular/core';

@Directive({selector: 'div[core-project-breadcrumbs]'})
export class ProjectBreadcrumbsDirective implements OnInit, OnChanges, OnDestroy {
  @Input() selectedFolderId: string;
  private el: HTMLDivElement;
  private el$: any;

  constructor(
    el: ElementRef,
    private zone: NgZone,
  ) {
    this.el = el.nativeElement;
  }


  ngOnInit() {
    this.zone.runOutsideAngular(() => {
      let dropdownMenu;

      $(window).on('show.bs.dropdown', function (e) {

        // grab the menu
        dropdownMenu = $(e.target).find('.dropdown-menu.to-body');

        // detach it and append it to the body
        $('body').append(dropdownMenu.detach());

        // grab the new offset position
        const eOffset = $(e.target).offset();

        // make sure to place it where it would normally go (this could be improved)
        dropdownMenu.css({
          'display': 'block',
          'top': eOffset.top + $(e.target).outerHeight(),
          'left': eOffset.left,
        });
      });

      $(window).on('hide.bs.dropdown', function (e) {
        if (dropdownMenu) {
          $(e.target).append(dropdownMenu.detach());
          dropdownMenu.hide();
        }
      });

      window.addEventListener('resize', this._onResize);
    });
    this._togglePlaceholder();
  }

  ngOnChanges() {
    this._togglePlaceholder();
  }

  ngOnDestroy(): void {
    this.zone.runOutsideAngular(() => {
      window.removeEventListener('resize', this._onResize);
    });
  }

  private _onResize = () => this._togglePlaceholder();

  private _togglePlaceholder() {
    // wait until page rerenders
    this.el$ = $(this.el);
    setTimeout(() => {
      const containerWidth = this.el$.width();
      const liElements = Array.from(this.el$.find('.breadcrumb > li'));
      const totalLiWidth = liElements
        .reduce((acc: number, _: HTMLLIElement) => {
          const el$ = $(_);
          acc += <number> el$.outerWidth();
          return acc;
        }, 0);
      if (totalLiWidth > containerWidth) {
        this.el$.removeClass('from-left');
      } else {
        this.el$.addClass('from-left');
      }
    }, 0);
  }
}
