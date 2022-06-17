#!/usr/bin/env node

require('get-random-port')(function(err, port) {
    if (err) {
        console.error(err);
        process.exit(1);
    }
    process.stdout.write(String(port));
});
