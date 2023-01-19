# Fork of [FueliFinTS](https://github.com/petafuel/FueliFinTS)
Fueli FinTS is a server implementing the financial transaction services (FinTS) protocol for online banking. FinTS was formerly known as home banking computer interface (HBCI).

**This fork of [FueliFinTS](https://github.com/petafuel/FueliFinTS) addresses the following issues:**

1. Persistence layer was missing and has been added after rough reverse-engineering
2. For missing maven dependencies (to handle SEPA payments and account statements) skeleton classes have been implemented and added.
3. FIXMEs to deactivate PIN/mTAN/strong authentication where necessary for testing purposes. **CAUTION: Do not use this fork in production without fixing these issues!!!**
4. Added example configuration files and db script to set up a rudimentary database.
5. Some minor refactoring and cleanups

**Please find a complete list of all changes to the original implementation by this fork with this [Github Compare](https://github.com/drmartinberger/FueliFinTS/compare/main...petafuel:main) view.**

# Setup Steps

1. Configure `fuelifints.properties` 
2. Initialize keystore (to be set in `fuelifints.properties`)
3. Setup `FinTS_Produktregistrierungen_Lizenzdatei.csv` (to be set in `fuelifints.properties`, contains product client ids that are allowed to communicate with server) or set a `productinfo.csv.check=false` 
4. Define aeskey.properties with a valid 128-bit key (to be set in `fuelifints.properties`)
5. Setup file <blz>.banking2.properties (to set config params for persistence layer, blz is configured in `fuelifints.properties` and your database)
6. Init database:
   1. Install mysql
   2. Initialize database from mysql command line:
      1. Add database user e.g. `fintsuser` with `<password>` (both must be used in `connectionpool.properties` adapted from `connectionpool.properties.example`):
        `mysql> GRANT ALL PRIVILEGES ON *.* TO 'fintsuser'@'localhost' IDENTIFIED BY '<password>';`
      2. `mysql> create database fints;`
      3. `mysql> GRANT ALL ON fints.* TO 'fintsuser'@'localhost';` 
      4. `mysql> FLUSH PRIVILEGES;`
      5. Execute database setup script from terminal
      ```bash
      $ mysql -u fintsuser -p <password> fints < dbsetup.sql
      ``` 
