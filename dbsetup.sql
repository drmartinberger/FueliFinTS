-- Rough reverse-engineered database structure for FueliFinTS
-- 1. Set your intended benutzerkennung for <benutzerkennung>
-- 2. Set your intended encrypted PIN for <encryptedPIN>
-- 3. Set your intended kundenid for <kundenid>
-- 4. Set <blz> and <bankname> as in config/fuelifints.properties
-- 5. Set your intended <kontoid>, <kontonummer> and <kontoinhaber>
-- 6. Set other values where feasible
CREATE TABLE benutzerkennungen (pin varchar(255), lastnn varchar(255), benutzerkennung varchar(255));
INSERT INTO benutzerkennungen (pin, lastnn, benutzerkennung) VALUES ('<encryptedPIN>', 'lastnn', '<benutzerkennung>');
CREATE TABLE kunden_benutzerkennungen (kundenid varchar(255), benutzerkennung varchar(255));
INSERT INTO kunden_benutzerkennungen (kundenid, benutzerkennung) VALUES ('<kundenid>', '<benutzerkennung>');
CREATE TABLE bpd (version varchar(255), blz varchar(255), bankname varchar(255), anzgesch varchar(255), lang varchar(255), hbciversion varchar(255), maxsize varchar(255));
INSERT INTO bpd (version, blz, bankname, anzgesch, lang, hbciversion, maxsize) VALUES ('1', '<blz>', '<bankname>', '1', 'GER', '300', '0'); -- blz and bankname should match fuelifints.properties
CREATE TABLE geschaeftsvorfaelle(bezeichnung varchar(255), segmentversion varchar(255), maxanzahl INT, param1 INT, param5 varchar(255), param3 varchar(255), param4 varchar(255), bpdversion varchar(255), hbciversion varchar(255));
INSERT INTO geschaeftsvorfaelle (bezeichnung, segmentversion, maxanzahl, param1, param5, param3, param4, bpdversion, hbciversion) VALUES ('Geschaeftsvorfall Bezeichnung', 'Segmentversion 1', 1, 1, '1', '1', '1', '1', '300');
CREATE TABLE messages_read(messageid varchar(255), loginid varchar(255), benutzerkennung varchar(255));
CREATE TABLE messages(messageid varchar(255), subject varchar(255), text varchar(255));
CREATE TABLE tempclientstuff3(benutzerkennung varchar(255), kundenid varchar(255), produkt varchar(255), produktversion varchar(255), kundensystemid varchar(255), dialogid varchar(255), tanid varchar(255), kontrollref varchar(255));
CREATE TABLE konten(kontoid VARCHAR(255), kontonummer VARCHAR(255), kontoinhaber VARCHAR(35), kontostand VARCHAR(35), tagesumsatz VARCHAR(35), tageslimit VARCHAR(35), kontoart VARCHAR(2), virtkontostand VARCHAR(35), kreditlinie_aktiv BOOLEAN, kreditlinie VARCHAR(35), ueberziehen BOOLEAN, konto_pre VARCHAR(255), blz_pre VARCHAR(255), iban_pre VARCHAR(255), empfaenger_pre VARCHAR(255));
INSERT INTO konten(kontoid, kontonummer, kontoinhaber, kontostand, tagesumsatz,tageslimit,kontoart,virtkontostand,kreditlinie_aktiv,kreditlinie,ueberziehen,konto_pre,blz_pre,iban_pre,empfaenger_pre) VALUES ('<kontoid>','<kontonummer>', '<kontoinhaber>','66.66','66.66','66.66', '90','66.66',true,'66.66',true,'','','','');
CREATE TABLE konten_bewegungen(kontoid VARCHAR(255), kontostand VARCHAR(35), buchungsdatum DATE, bewegungsid BIGINT(20));
-- comment out INSERT INTO konten_bewegungen, if you do not want to have account transactions
INSERT INTO konten_bewegungen(kontoid, kontostand, buchungsdatum, bewegungsid) VALUES ('<kontoid>', '66.66', '2022-11-11', 1);
CREATE TABLE kunden_konten(kontoid VARCHAR(255), kundenid VARCHAR(255));
INSERT INTO kunden_konten(kontoid, kundenid) VALUES ('<kontoid>', '<kundenid>');
CREATE TABLE login_konten_permission(kontoid VARCHAR(255), benutzerkennung varchar(255), ueberweisung VARCHAR(1), sdd VARCHAR(1), ade VARCHAR(1), azv VARCHAR(1), lastschrift VARCHAR(1), abbuchung VARCHAR(1), dtaus VARCHAR(1));
INSERT INTO login_konten_permission(kontoid, benutzerkennung, ueberweisung, sdd, ade, azv, lastschrift, abbuchung, dtaus) VALUES ('<kontoid>', '<benutzerkennung>', '1', '1', 'E', '1', '1', '1', '1');
CREATE TABLE logins(loginid VARCHAR(255), handy VARCHAR(255), ref_benutzerkennung VARCHAR(255));
INSERT INTO logins(loginid, handy, ref_benutzerkennung) VALUES (0, '0815666', '<benutzerkennung>');
CREATE TABLE tanliste_mobil(mtanid INT AUTO_INCREMENT PRIMARY KEY, mtan VARCHAR(255), used VARCHAR(255), loginid VARCHAR(255),benutzerkennung VARCHAR(255), request VARCHAR(255));
INSERT INTO tanliste_mobil(mtanid, mtan, used, loginid, benutzerkennung, request) VALUES (0, '123456', '0', 0, '<benutzerkennung>', 'request');
CREATE TABLE login_permission(tageslimit VARCHAR(35), tagesumsatz VARCHAR(35), lastschrift VARCHAR(1), ueberweisung VARCHAR(1), benutzerkennung varchar(255));
CREATE TABLE mt940(mt940id VARCHAR(255), mt940 BLOB, kontoid VARCHAR(255), buchungstag date);
CREATE TABLE vorabumsaetze(kontoid VARCHAR(255), buchungsdatum date, primanota VARCHAR(35), verwendungszweck VARCHAR(140), betrag VARCHAR(35), kontostand VARCHAR(35), wert VARCHAR(35), refimportid VARCHAR(35), gegenkontoname VARCHAR(35), gegenkontonr VARCHAR(35), gegenkontoblz VARCHAR(35), bewegungsid VARCHAR(35));


