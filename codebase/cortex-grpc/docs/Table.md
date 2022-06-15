[Table Upload](../src/main/proto/cortex/api/job/table.uploading.proto)
======

Example messages can be found in [Sample.scala](../src/main/scala/cortex/api/job/table/Sample.scala)

TableUploadRequest
-------
| Field name | Description | Mandatory |
|---|---|---|
| source_file_path | Location of the s3 file | yes |
| delimeter | Comma(,) as a delimeter | yes |
| null_value | String representation of a null value | yes |
| file_type | File type should be CSV | yes |
| columns | Columns description of an input table | no |

TableUploadResponse
-------
| Field name | Description | Mandatory |
|---|---|---|
| columns | Columns description of an input table | yes |
| row_count | Count of rows | yes |

Column
------
| Field name | Description | Mandatory |
|---|---|---|
| name | Name of the column within table | yes |
| display_name | Name of the column to show in UI. This is usually original name from imported file | yes |
| data_type | Type of data | yes |
| variable_type | Type of the variable | yes |
| statistics | Either **NumericalStatistics** or **CategoricalStatistics** | yes |

ColumnInfo
------
| Field name | Description | Mandatory |
|---|---|---|
| name | Name of the table column within table | yes |
| display_name | Name of the column to show in UI. This is usually original name from imported file | no |
| data_type | Type of data | yes |
| variable_type | Type of the variable | no |

VariableTypeInfo
-----
| Field name | Description | Mandatory |
|---|---|---|
| variable_type | Enums as the VariableType  | yes |

NumericalStatistics
------
Represents numerical statistics per column

| Field name | Description | Mandatory |
|---|---|---|
| min | Minimum | yes |
| max | Maximum | yes |
| avg | Average | yes |
| std | Standard deviation | yes |
| std_population | Population standard deviation | yes |
| mean | Mean | yes |
| histogram |  Numerical histogram per column | yes |

CategoricalStatistics
------
Represents categorical statistics per column

| Field name | Description | Mandatory |
|---|---|---|
| unique_values_count | Number of unique values | yes |
| histogram | Categorical histogram per column | yes |

NumericalHistogramRow
------
| Field name | Description | Mandatory |
|---|---|---|
| min | Minimum | yes |
| max | Maximum | yes |
| count | Count | yes |


NumericalHistogram
------
| Field name | Description | Mandatory |
|---|---|---|
| value_range | Continuous histogram should contain N ranges, i.e. 120 triples of [min, max, count] | yes |

CategoricalHistogram
------
| Field name | Description | Mandatory |
|---|---|---|
| value_frequency | List of frequent values and count | yes |


CategoricalHistogramRow
------
Frequent value and its count

| Field name | Description | Mandatory |
|---|---|---|
| value | Frequent value | yes |
| count | Frequency or count of the value | yes |

Table
-------
| Field name | Description | Mandatory |
|---|---|---|
| meta | **TableMeta** | yes |
| columns | Columns description of an input table (should be fulfilled only in a request), refer **TableColumn** for column description | no |

DataSource
------
Represents general source of data to read rows from.
Currently only relation tables, but different types are TBD, such as CSV file

| Field name | Description | Mandatory |
|---|---|---|
| table | Table within database | yes |

DataType
------
| Value |
|---|
| STRING |
| INTEGER |
| DOUBLE |
| BOOLEAN |
| TIMESTAMP |

VariableType
------
| Value |
|---|
| CONTINUOUS |
| CATEGORICAL |

TableColumn
------
| Field name | Description | Mandatory |
|---|---|---|
| name | Name of the column within table | yes |
| data_type | Type of data | yes |
| variable_type | Type of variable | yes |

TableMeta
-------
| Field name | Description | Mandatory |
|---|---|---|
| schema | Name of the schema within a database | yes |
| name | Name of the table within schema | yes |

ProbabilityClassColumn
------
| Field name | Description | Mandatory |
|---|---|---|
| class_name | Name of the class | yes |
| column_name | Name of the table column with class probability values | yes |
