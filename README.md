# LN-FinTS - A fork of [FueliFinTS](https://github.com/petafuel/FueliFinTS) to use FinTS for a Bitcoin Lightning node with [LNbits](https://github.com/lnbits/lnbits)
Fueli FinTS is a server implementing the financial transaction services (FinTS) protocol for online banking. FinTS was formerly known as home banking computer interface (HBCI).

**This is a fork of [FueliFinTS](https://github.com/petafuel/FueliFinTS) addresses the following issues:**

1. Persistence layer was missing and has been added after rough reverse-engineering
2. For missing maven dependencies (to handle SEPA payments and account statements) skeleton classes have been implemented and added.
3. FIXMEs to deactivate PIN/mTAN/strong authentication where necessary for testing purposes. **CAUTION: Do not use this fork in production without fixing these issues!!!**
4. Added example configuration files and db script to set up a rudimentary database.
5. Some minor refactoring and cleanups
6. Adapted for Bitcoin Lightning Network integration with [LNbits](https://github.com/lnbits/lnbits#lnbits)

**Please find a complete list of all changes to the original implementation by this fork with this [Github Compare](https://github.com/petafuel/FueliFinTS/compare/main...drmartinberger:main) view.**

# Setup Steps

1. Configure `config/fuelifints.properties` 
2. Initialize keystore (to be set in `config/fuelifints.properties`)
3. Setup `FinTS_Produktregistrierungen_Lizenzdatei.csv` (to be set in `config/fuelifints.properties`, contains product client ids that are allowed to communicate with server) or set a `productinfo.csv.check=false` 
4. Setup `aeskey.properties` with a valid 128-bit key (file to be set in `config/fuelifints.properties`)
5. Setup file `config/12345678.banking2.properties` (to set config params for persistence layer, `bankcode = 12345678` is configured in `config/fuelifints.properties` and your database)
6. Derive encrypted PIN (for FinTS access) for your intended `<your-pin>, e.g. 123456789` and inject it into `dbsetup.sql`:

```bash
$ encryptedPIN=`mvn compile exec:java -Dexec.mainClass="net.petafuel.fuelifints.cryptography.aesencryption.AESUtil" -Dexec.args="<your-pin>" -q`
$ sed -i -e "s/REPLACE_ENCRYPTED_PIN/$encryptedPIN/g" dbsetup.sql
```

7. Init database:
   1. Install mysql
   2. Initialize database from mysql command line:
      1. Add database user e.g. `fintsuser` with `<password>` (both must be used in `connectionpool.properties` adapted from `connectionpool.properties.example`):
        `mysql> GRANT ALL PRIVILEGES ON *.* TO 'fintsuser'@'localhost' IDENTIFIED BY '<password>';`
      2. `mysql> create database fints;`
      3. `mysql> GRANT ALL PRIVILEGES ON fints.* TO 'fintsuser'@'localhost';` 
      4. `mysql> FLUSH PRIVILEGES;`
      5. Execute database setup script from terminal
      ```bash
      $ mysql -u fintsuser -p <password> fints < dbsetup.sql
      ``` 
7. Configure `lnbits.properties`

# Run Steps

Use `LN-FinTS-jar-with-dependencies.jar` (containing all needed dependencies) resulting from `mvn package` in a folder where there is a folder `config` with the files `fuelifints.properties` and `lnbits.properties` with the following command:
```bash
$ java -jar LN-FinTS-jar-with-dependencies.jar
```
Logs are written into folder `log`.