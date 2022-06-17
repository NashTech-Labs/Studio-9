/*
  static query(datasetField: string, flowsteps: FlowstepService): AsyncValidatorFn {
    // TODO: add examples for params

    return (control: AbstractControl): Promise<{[key: string]: any}> => {
      let dataset: AbstractControl = control.root.find(datasetField);

      return new Promise((resolve) => {
        flowsteps.sqlParsing(control.value).subscribe(
          (data: any) => {
            data['datasets'].forEach((item: ITable, index: number) => {
              dataset.value[index] = item.id; // @todo make with FormArray.setValue
            });

            resolve(null);
          },
          (data: IBackendError) => {
            data = JSON.parse(data);
            resolve({query: data.error.message});
            control.setErrors(Object.assign(control.errors, {query: data.error.message}), true);
          }
        );
      })
    }
  }

  // TODO: get table id only instead of table object
  static formula(getTable: Function, getColumn: Function, flowsteps: FlowstepService): AsyncValidatorFn {
    // TODO: add examples for params

    return (control: AbstractControl): Promise<{[key: string]: any}> => {
      let table = getTable(control.root);
      let column = getColumn(control.root);

      return new Promise(resolve => {
        if (!table || !column) { return resolve({formula: false}); }

        flowsteps.sqlParsingInsert({
          expression: control.value,
          datasetId: table.datasetId,
          column: column,
        }).subscribe(
          () => {
            resolve(null);
          },
          (data: IBackendError) => {
            control.setErrors(Object.assign(control.errors, {formula: data.error.message}), true);
            resolve({formula: data.error.message});
          }
        );
      });
    };
  }*/
