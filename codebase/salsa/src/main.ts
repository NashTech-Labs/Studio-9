import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

import { AppModule } from './app/app.module';

const platform = platformBrowserDynamic();

platform.bootstrapModule(AppModule).then(moduleRef => {
  platform.onDestroy(() => {
    moduleRef.destroy();
  });
});

platform.onDestroy(() => {
  $('body').empty().append('<h1 class="text-center text-danger">Application stopped. See console for details.</h1>');
});

window['CESIUM_BASE_URL'] = '/assets/cesium/';
/** DIRTY HACK. See https://github.com/twbs/bootstrap/issues/21855 */
const originalGetPosition = (<any> $.fn.tooltip).Constructor.prototype.getPosition;
(<any> $.fn.tooltip).Constructor.prototype.getPosition = function ($element) {
  $element = $element || this.$element;
  const el = $element[0];
  const pos = originalGetPosition.call(this, $element || this.$element);
  const isSvg = (<any> window).SVGElement && el instanceof (<any> window).SVGElement;

  if (isSvg) {
    const elRect = el.getBoundingClientRect();
    return $.extend({}, pos, {
      top: elRect.top + window.pageYOffset,
      left: elRect.left + window.pageXOffset,
      bottom: elRect.bottom + window.pageYOffset,
      right: elRect.right + window.pageXOffset,
    });
  }

  return pos;
};
/** end of hack */
