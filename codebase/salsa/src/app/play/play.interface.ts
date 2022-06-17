export interface IMappingPair {
  sourceColumn: IColumnIdentifier;
  mappedColumn: IColumnIdentifier;
}

export interface ISimpleMappingPair {
  sourceColumn: string;
  mappedColumn: string;
}

export interface IColumnIdentifier {
  tableId: string;
  columnName: string;
}
