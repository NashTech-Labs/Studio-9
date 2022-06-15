/* SystemJS module definition */
declare var module: NodeModule;
interface NodeModule {
  id: string;
}

interface JQuery {
  selectAreas(method: string, ...args): any;
  selectAreas(options?: object): JQuery;

  iCheck(method: string, ...args): any;
  iCheck(options?: object): JQuery;

  multiselect(method: string, ...args): any;
  multiselect(options?: object): JQuery;
}
declare var Cesium;
