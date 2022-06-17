import { BaseType, Selection } from 'd3';

export interface D3Lasso<E extends BaseType, D, PE extends BaseType, PD> {
  (_: Selection<E, D, PE, PD>): Selection<E, D, PE, PD>;

  items(): Selection<E, D, PE, PD>;

  possibleItems(): Selection<E, D, PE, PD>;

  notPossibleItems(): Selection<E, D, PE, PD>;

  selectedItems(): Selection<E, D, PE, PD>;

  on(event: string, callback: Function): D3Lasso<E, D, PE, PD>;

  hoverSelect(bool: boolean): D3Lasso<E, D, PE, PD>;

  targetArea(selection: Selection<E, D, PE, PD>): D3Lasso<E, D, PE, PD>;

  closePathSelect(bool: boolean): D3Lasso<E, D, PE, PD>;

  closePathDistance(distance: number): D3Lasso<E, D, PE, PD>;
}

export const lasso: <E extends BaseType, D, PE extends BaseType, PD>() => D3Lasso<E, D, PE, PD>;
