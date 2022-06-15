export const SQL_SUPPORTER_OPERATORS = [
  {name: 'SELECT'},
  {name: 'FROM'},
  {name: 'DISTINCT'},
  {name: 'WHERE'},
  {name: 'GROUP BY'},
  {name: 'ORDER BY'},
  {name: 'HAVING'},
  {name: 'ASC'},
  {name: 'DESC'},
  {name: 'AS'},
  {name: 'ALL'},
  {name: 'INTERSECT'},
  {name: 'EXCEPT'},
  {name: 'MINUS'},
  {name: 'BETWEEN'},
  {name: 'LIKE'},
  {name: 'IN'},
  {name: 'AND'},
  {name: 'OR'},
  {name: 'UNION'},
  {name: 'INNER JOIN'},
  {name: 'LEFT OUTER'},
  {name: 'RIGHT OUTER'},
  {name: 'TEXT'},
  {name: 'NUMERIC'},
  {name: 'DOUBLE'},
  {name: 'PRECISION'},
];
export const SQL_SUPPORTED_FUNCTIONS = [
  {
    name: 'CUME_DIST',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'Calculates the cumulative distribution of a value within a window or partition.',
    disabled: false,
  },
  {
    name: 'FIRST_VALUE',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The FIRST_VALUE returns the value of the specified expression with respect to the first row in the window frame.',
    disabled: false,
  },
  {
    name: 'LAG',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The LAG window function returns the values for a row at a given offset above (before) the current row in the partition.',
    disabled: false,
  },
  {
    name: 'LAST_VALUE',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The LAST_VALUE function returns the value of the expression with respect to the last row in the frame.',
    disabled: false,
  },
  {
    name: 'LEAD',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The LEAD window function returns the values for a row at a given offset below (after) the current row in the partition.',
    disabled: false,
  },
  {
    name: 'LISTAGG',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'For each group in a query, the LISTAGG window function orders the rows for each group according to the ORDER BY expression, then concatenates the values into a single string.',
    disabled: false,
  },
  {
    name: 'MEDIAN',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'Calculates the median value for the range of values in a window or partition. NULL values in the range are ignored.MEDIAN is an inverse distribution function that assumes a continuous distribution model.',
    disabled: false,
  },
  {
    name: 'NTH_VALUE',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The NTH_VALUE window function returns the expression value of the specified row of the window frame relative to the first row of the window.',
    disabled: false,
  },
  {
    name: 'PERCENTILE_CONT',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'PERCENTILE_CONT is an inverse distribution function that assumes a continuous distribution model. It takes a percentile value and a sort specification, and returns an interpolated value that would fall into the given percentile value with respect to the sort specification.',
    disabled: false,
  },
  {
    name: 'PERCENTILE_DISC',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'PERCENTILE_DISC is an inverse distribution function that assumes a discrete distribution model. It takes a percentile value and a sort specification and returns an element from the given set.',
    disabled: false,
  },
  {
    name: 'RATIO_TO_REPORT',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'Calculates the ratio of a value to the sum of the values in a window or partition.',
    disabled: false,
  },
  {
    name: 'STDDEV_POP',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The STDDEV_SAMP and STDDEV_POP window functions return the sample and population standard deviation of a set of numeric values (integer, decimal, or floating-point). ',
    disabled: false,
  },
  {
    name: 'STDDEV_SAMP',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The STDDEV_SAMP and STDDEV_POP window functions return the sample and population standard deviation of a set of numeric values (integer, decimal, or floating-point). ',
    disabled: false,
  },
  {
    name: 'STDDEV',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'Returns the statistical standard deviation of all values in the specified expression.',
    disabled: false,
  },
  {
    name: 'VAR_POP',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The VAR_SAMP and VAR_POP window functions return the sample and population variance of a set of numeric values (integer, decimal, or floating-point).',
    disabled: false,
  },
  {
    name: 'VAR_SAMP',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The VAR_SAMP and VAR_POP window functions return the sample and population variance of a set of numeric values (integer, decimal, or floating-point).',
    disabled: false,
  },
  {
    name: 'VARIANCE',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The VAR_SAMP and VAR_POP functions return the sample and population variance of a set of numeric values (integer, decimal, or floating-point). The result of the VAR_SAMP function is equivalent to the squared sample standard deviation of the same set of values.',
    disabled: false,
  },
  {
    name: 'DENSE_RANK',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The DENSE_RANK window function determines the rank of a value in a group of values, based on the ORDER BY expression in the OVER clause. If the optional PARTITION BY clause is present, the rankings are reset for each group of rows. Rows with equal values for the ranking criteria receive the same rank.',
    disabled: false,
  },
  {
    name: 'NTILE',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The NTILE window function divides ordered rows in the partition into the specified number of ranked groups of as equal size as possible and returns the group that a given row falls into.',
    disabled: false,
  },
  {
    name: 'PERCENT_RANK',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'Calculates the percent rank of a given row.',
    disabled: false,
  },
  {
    name: 'RANK',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'Order the table by the quantity sold (default ascending), and assign a rank to each row. ',
    disabled: false,
  },
  {
    name: 'ROW_NUMBER',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'Determines the ordinal number of the current row within a group of rows, counting from 1, based on the ORDER BY expression in the OVER clause. If the optional PARTITION BY clause is present, the ordinal numbers are reset for each group of rows. Rows with equal values for the ORDER BY expressions receive the different row numbers nondeterministically.',
    disabled: false,
  },
  {
    name: 'SUM',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The Sum() function returns the summed value of an expression.',
    disabled: false,
  },
  {
    name: 'COUNT',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The COUNT() function returns the number of rows that matches a specified criteria.',
    disabled: false,
  },
  {
    name: 'MAX',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The MAX() function returns the largest value of the selected column.',
    disabled: false,
  },
  {
    name: 'MIN',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The MIN() function returns the smallest value of the selected column.',
    disabled: false,
  },
  {
    name: 'AVG',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'The AVG() function returns the average value of a numeric column.',
    disabled: false,
  },
  {
    name: 'LEN',
    arguments: [
      {
        title: 'column_name',
      },
    ],
    brief: 'Returns the length of a text field',
    disabled: false,
  },
  {
    name: 'COALESCE',
    arguments: [
      {
        title: '... expression of any type',
      },
    ],
    brief: 'Evaluates the arguments in order and returns the current value of the first expression that initially does not evaluate to NULL',
    disabled: false,
  },
  {
    name: 'CONCAT',
    arguments: [
      {
        title: '... string[]',
      },
    ],
    brief: 'Concatenate all arguments. NULL arguments are ignored.',
    disabled: false,
  },
  {
    name: 'ABS',
    arguments: [
      {
        title: 'value',
      },
    ],
    brief: 'Returns the absolute value of a number. The absolute value of a number is the number without its sign.',
    disabled: false,
  },
  {
    name: 'CEILING',
    arguments: [
      {
        title: 'number',
      },
      {
        title: 'significance',
      },
    ],
    brief: 'Returns number rounded up, away from zero, to the nearest multiple of significance.',
    disabled: false,
  },
  {
    name: 'FLOOR',
    arguments: [
      {
        title: 'number',
      },
      {
        title: 'significance',
      },
    ],
    brief: 'The floor() method rounds a number DOWNWARDS to the nearest integer, and returns the result.If the passed argument is an integer, the value will not be rounded.',
    disabled: false,
  },
  {
    name: 'ROUND',
    arguments: [
      {
        title: 'column_name',
      },
      {
        title: 'decimals',
      },
    ],
    brief: 'Rounds a numeric field to the number of decimals specified',
    disabled: false,
  },
  {
    name: 'LN',
    arguments: [
      {
        title: 'number',
      },
    ],
    brief: 'Returns the natural logarithm of a number. Natural logarithms are based on the constant e.',
    disabled: false,
  },
  {
    name: 'LOG',
    arguments: [
      {
        title: 'number',
      },
      {
        title: '[base]',
      },
    ],
    brief: 'The log() function returns the natural logarithm of a number, or the logarithm of number to base.',
    disabled: false,
  },
  {
    name: 'SQRT',
    arguments: [
      {
        title: 'number',
      },
    ],
    brief: 'Gets square root',
    disabled: false,
  },
  {
    name: 'CBRT',
    arguments: [
      {
        title: 'number',
      },
    ],
    brief: 'Gets cube root',
    disabled: false,
  },
  {
    name: 'CEIL',
    arguments: [
      {
        title: 'number',
      },
    ],
    brief: 'Smallest integer not less than argument',
    disabled: false,
  },
  {
    name: 'CAST',
    arguments: [
      {
        title: 'column_name as TEXT | NUMERIC | ...',
      },
    ],
    brief: 'Casts column type',
    disabled: false,
  },
];

export const EXCEL_SUPPORTED_FUNCTIONS = [
  {
    name: 'ABS',
    arguments: [
      {
        title: 'value',
      },
    ],
    brief: 'Returns the absolute value of a number. The absolute value of a number is the number without its sign.',
    disabled: false,
  },
  {
    name: 'CEILING',
    arguments: [
      {
        title: 'number',
      },
      {
        title: 'significance',
      },
    ],
    brief: 'Returns number rounded up, away from zero, to the nearest multiple of significance.',
    disabled: false,
  },
  {
    name: 'CONCATENATE',
    arguments: [
      {
        title: 'text1',
      },
      {
        title: '[text2, ...]',
      },
    ],
    brief: 'Function to join two or more text strings into one string.',
    disabled: false,
  },
  {
    name: 'FLOOR',
    arguments: [
      {
        title: 'number',
      },
      {
        title: 'significance',
      },
    ],
    brief: 'The floor() method rounds a number DOWNWARDS to the nearest integer, and returns the result.If the passed argument is an integer, the value will not be rounded.',
    disabled: false,
  },
  {
    name: 'IF',
    arguments: [
      {
        title: 'logical_test',
      },
      {
        title: 'value_if_true',
      },
      {
        title: '[value_if_false]',
      },
    ],
    brief: 'The IF function allows you to make logical comparisons between a value and what you expect. In its simplest form1, the IF function says IF(Something is True, then do something, otherwise do something else)So an IF statement can have two results. The first result is if your comparison is True, the second if your comparison is False.',
    disabled: false,
  },
  {
    name: 'LN',
    arguments: [
      {
        title: 'number',
      },
    ],
    brief: 'Returns the natural logarithm of a number. Natural logarithms are based on the constant e.',
    disabled: false,
  },
  {
    name: 'LOG',
    arguments: [
      {
        title: 'number',
      },
      {
        title: '[base]',
      },
    ],
    brief: 'The log() function returns the natural logarithm of a number, or the logarithm of number to base.',
    disabled: false,
  },
  {
    name: 'LOG10',
    arguments: [
      {
        title: 'number',
      },
    ],
    brief: 'Returns the base-10 logarithm of a number.',
    disabled: false,
  },
  {
    name: 'ROUND',
    arguments: [
      {
        title: 'number',
      },
      {
        title: 'num_digits',
      },
    ],
    brief: 'The ROUND function rounds a number to a specified number of digits. For example, if cell A1 contains 23.7825, and you want to round that value to two decimal places.',
    disabled: false,
  },
  {
    name: 'ROUNDUP',
    arguments: [
      {
        title: 'number',
      },
      {
        title: 'num_digits',
      },
    ],
    brief: '',
    disabled: false,
  },
  {
    name: 'ROUNDDOWN',
    arguments: [
      {
        title: 'number',
      },
      {
        title: 'num_digits',
      },
    ],
    brief: '',
    disabled: false,
  },
];
