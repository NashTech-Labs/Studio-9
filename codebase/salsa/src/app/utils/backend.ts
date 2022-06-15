export class Csv {
  // ref: http://stackoverflow.com/a/1293163/2343
  // This will parse a delimited string into an array of
  // arrays. The default delimiter is the comma, but this
  // can be overriden in the second argument. */
  static toArray(strData: string, strDelimiter: string = ',', limit = 100000) {

    // Create a regular expression to parse the CSV values.
    let objPattern = new RegExp(
      (
        // Delimiters.
        '(\\' + strDelimiter + '|\\r?\\n|\\r|^)' +

        // Quoted fields.
        '(?:\"([^\"]*(?:\"\"[^\"]*)*)\"|' +

        // Standard fields.
        '([^\"\\' + strDelimiter + '\\r\\n]*))'
      ),
      'gi',
    );

    let arrData = [[]], // Create an array to hold our data. Give the array a default empty first row.
      arrMatches; // Create an array to hold our individual pattern matching groups.

    // Keep looping over the regular expression matches
    // until we can no longer find a match.
    strData = strData.trim();
    /* tslint:disable:no-conditional-assignment */
    while (arrMatches = objPattern.exec(strData)) {
      /* tslint:enable */
      let strMatchedDelimiter = arrMatches[1], // Get the delimiter that was found.
        strMatchedValue;

      // Check to see if the given delimiter has a length
      // (is not the start of string) and if it matches
      // field delimiter. If id does not, then we know
      // that this delimiter is a row delimiter.
      if (strMatchedDelimiter.length &&
        strMatchedDelimiter !== strDelimiter) {

        if (arrData.length >= limit) {
          break;
        }
        // Since we have reached a new row of data,
        // add an empty row to our data array.
        arrData.push([]);
      }

      // Now that we have our delimiter out of the way,
      // let's check to see which kind of value we
      // captured (quoted or unquoted).
      // We found a quoted value. When we capture
      // this value, unescape any double quotes.
      // Else we found a non-quoted value.
      strMatchedValue = arrMatches[2] ? arrMatches[2].replace(new RegExp( '\"\"', 'g' ), '\"') : arrMatches[3];

      // Now that we have our value string, let's add
      // it to the data array.
      arrData[arrData.length - 1].push(strMatchedValue);
    }

    return arrData;
  }
}
