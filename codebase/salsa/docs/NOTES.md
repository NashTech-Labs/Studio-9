
### Backend

- token-based authentication
- tables are create based on imported (aka uploaded) dataset or by posting table structure (dataless table)
- async processes observed by Process entity/endpoints
- could we login by email/pass?


### Table

- table structude is defined during creation and cannot be changed later



### Table / Creation (as Import)

- creation table from dataset
    1. frontend: user drop/select file(s) with datasets
    2. frontend: zip each file and post to the backend: POST /tables/import
    3. backend: parse/validate files and create tables with derived structure
    4. backend: returns table object with detail for created table (or error)
    5. frontend: imported or queued dataset are displayed in status (created tables could be opened from there)
    N. frontend: user is able to rename table, columns or specify column types later

- creation table without dataset: to create dataless tables with defined structure
(is not used on the frontend): POST /tables

- experimental: extra endpoint for async dataset importing
(returns process object, not table): POST /tables/import?process=true

