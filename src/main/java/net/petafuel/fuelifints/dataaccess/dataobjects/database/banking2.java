package net.petafuel.fuelifints.dataaccess.dataobjects.database;

import net.petafuel.dbutils.DbHelper;
import net.petafuel.fuelifints.cryptography.aesencryption.AESUtil;
import net.petafuel.fuelifints.dataaccess.dataobjects.BankMessageObject;
import net.petafuel.fuelifints.dataaccess.dataobjects.PermissionDataObject;
import net.petafuel.fuelifints.dataaccess.dataobjects.ReturnDataObject;
import net.petafuel.fuelifints.dataaccess.dataobjects.gateway.sms;
import net.petafuel.fuelifints.model.client.ClientProductInfo;
import net.petafuel.fuelifints.model.client.LegitimationInfo;
import net.petafuel.fuelifints.support.CkontoLocal;
import net.petafuel.fuelifints.support.Payments;
import net.petafuel.jsepa.model.Document;
import net.petafuel.mt94x.Konto;
import net.petafuel.mt94x.Satz;
import net.petafuel.mt94x.Umsatz;
import net.petafuel.mt94x.Writer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;

import javax.naming.NamingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static net.petafuel.fuelifints.support.Payments.convertKnrBlzToIBAN;
import static net.petafuel.fuelifints.support.Payments.derfNullSein;
import static org.apache.commons.lang3.StringUtils.*;

/**
 * delimiter $$
 * CREATE TABLE `publickeys3` (
 * `BENUTZERKENNUNG` int(9) unsigned DEFAULT '0',
 * `PUBKEY` blob,
 * `SCHLUESSELART` tinyint(4) unsigned DEFAULT NULL,
 * `SCHLUESSELNUMMER` int(3) unsigned DEFAULT NULL,
 * `SCHLUESSELVERSION` int(3) unsigned DEFAULT NULL,
 * `FREIGESCHALTET` int(1) unsigned DEFAULT NULL,
 * `MODULO` Text,
 * `EXPONENT` varchar(30) DEFAULT NULL,
 * `pubkeyid` int(10) unsigned NOT NULL AUTO_INCREMENT,
 * PRIMARY KEY (`pubkeyid`),
 * KEY `idx_benutzerkennung` (`BENUTZERKENNUNG`)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8$$
 * <p/>
 * ALTER TABLE `messages_read` ADD COLUMN `benutzerkennung` BIGINT(20) UNSIGNED NOT NULL DEFAULT '0'  AFTER `loginid`
 * , DROP PRIMARY KEY
 * , ADD PRIMARY KEY (`messageid`, `loginid`, `benutzerkennung`) ;
 * <p/>
 * ALTER TABLE `vorabumsaetze` CHANGE COLUMN `GEGENKONTONR` `GEGENKONTONR` VARCHAR(45) NULL DEFAULT NULL
 * , CHANGE COLUMN `GEGENKONTOBLZ` `GEGENKONTOBLZ` VARCHAR(11) NULL DEFAULT NULL  ;
 * ALTER TABLE `login_konten_permission` ADD COLUMN `BENUTZERKENNUNG` BIGINT(20) NOT NULL DEFAULT '0'  AFTER `KONTOID`
 * , DROP PRIMARY KEY
 * , ADD PRIMARY KEY (`LOGINID`, `KONTOID`, `BENUTZERKENNUNG`) ;
 * <p/>
 * update login_konten_permission lkp set lkp.benutzerkennung=(select ref_benutzerkennung from logins l where l.loginid=lkp.loginid);
 */
@SuppressWarnings("deprecation")
public class banking2 {
    private static final String seed = "412132343421";
    private static final Logger LOG = LogManager.getLogger(banking2.class);
    public static SimpleDateFormat sdf_sql = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat sdf_tan = new SimpleDateFormat("yyyyMMddHHmmss");
    private static String dbprofile = "banking2";
    private CkontoLocal ckonto;
    private final Charset charsetISO88591 = StandardCharsets.ISO_8859_1;
    private final Properties configuration;
    private final SecureRandom random = new SecureRandom();
    private boolean configCreateVorabumsaetze = false;
    private final HashMap<String, List<BankMessageObject>> localMessageList = new HashMap<>();
    private final HashMap<String, String> localUserReference = new HashMap<>();
    private Boolean mailAvaible = null;

    /**
     * Übergibt die Konfiguration mit
     *
     * @param configuration Konfiguration
     */
    public banking2(Properties configuration) {
        this.configuration = configuration;
        configCreateVorabumsaetze = "true".equals(config(properties.createVorabumsaetze, "false"));
        dbprofile = config(properties.bankCode, "12345678");
        LOG.info(format("createVorabumsätze ist %s", (configCreateVorabumsaetze ? "an" : "aus")));
    }

    private Connection getConnection() throws SQLException {
        try {
            return new DbHelper().getConnection(dbprofile);
        } catch (NamingException e) {
            LOG.warn(format("Fehler beim Verbindung holen: %s", e.getMessage()), e);
            throw new SQLException(e);
        }
    }

    /**
     * Lädt eine Einstellung aus der Konfiguration, falls nicht vorhanden, gibt es die defaultValue zurück
     *
     * @param property     Einstellungs-Name
     * @param defaultValue Vorgabe wert, falls nicht in der Konfig hinterlegt
     * @return Konfigeinstellung oder defaultValue wenn nicht vorhanden
     */
    public String config(String property, String defaultValue) {
        return configuration.getProperty(property, defaultValue);
    }

    private void configLog(String property) {
        LOG.debug(format("config: %s: %s", property, configuration.getProperty(property, "[not-set]")));
    }

    /**
     * Fügt eine neue Zeile in TempClientStuff ein
     *
     * @param dialogId          Dialog-ID
     * @param legitimationInfo  LegitimationInfo
     * @param clientProductInfo ClientProductInfo
     * @return gibt die neue Kunden_System_ID zurück
     */
    private String insertTempclientstaff(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo) {
        String userSystemId = null;
        Connection connection = null;
        try {
            String tempUserSystemid = generateTan(9);
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.tempclientstuff3.insert);
            pS.setString(1, legitimationInfo.getUserId());                    //	Benutzerkennung
            pS.setString(2, getCustomerIdByConfig(legitimationInfo));                //	Kundenid
            pS.setString(3, derfNullSein(clientProductInfo.getClientProductName()));      //	Kundenprodukt
            pS.setString(4, derfNullSein(clientProductInfo.getClientProductVersion()));      //	Kundenprodukt-Version
            pS.setString(5, tempUserSystemid);                                //	Kundensystemid
            pS.setString(6, dialogId);        //	dialogid
            pS.execute();
            userSystemId = tempUserSystemid;
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim anlegen einer KundenSystemID: %s", e.getMessage()), legitimationInfo), e.getMessage());
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return userSystemId;
    }

    /**
     * Prüft ob eine Kundenid gefragt wird
     *
     * @return if true, it is required
     */
    public boolean isKundenIdNotRequired() {
        return config("textKennung", "Benutzerkennung").equals(config("textKundenId", "Kunden-ID")) || config("textKundenId", "").equals("");
    }

    /**
     * @param legitimationInfo legitimationInfo
     * @return Kundenid
     */
    public String getCustomerIdByConfig(LegitimationInfo legitimationInfo) {
        return getCustomerIdByConfig(legitimationInfo.getUserId(), legitimationInfo.getCustomerId());
    }

    /**
     * @param userId     UserId
     * @param fallbackId CustomerIdFallback
     * @return gibt die Kundenid zurück
     */
    private String getCustomerIdByConfig(String userId, String fallbackId) {
        return isKundenIdNotRequired() ? getCustomerId(userId) : fallbackId;
    }

    /**
     * Generiert eine KundenSystemId und hinterlegt diese in Tempclientstuff
     *
     * @param dialogId          Dialog-ID
     * @param legitimationInfo  LegitimationInfo
     * @param clientProductInfo ClientProductInfo
     * @return Kunden-System-ID
     */
    public String getOrGenerateUserSystemId(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo) {
        String userSystemId = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.tempclientstuff3.select);
            if (setPreparedStatementParams(pS, legitimationInfo, clientProductInfo, false)) {
                ResultSet resultSet = pS.executeQuery();
                if (resultSet.next()) {
                    userSystemId = resultSet.getString("kundensystemid");
                    LOG.info(addTagUser(format("Kunden-System-ID gefunden: %s", userSystemId), legitimationInfo));
                    clientProductInfo.setUserSystemId(userSystemId);
                    PreparedStatement pU = connection.prepareStatement(sql.tempclientstuff3.update_dialogid);
                    pU.setString(1, dialogId);
                    if (setPreparedStatementParams(pU, legitimationInfo, clientProductInfo, true)) {
                        pU.execute();
                    }
                } else {
                    LOG.info(addTagUser("Kunden-System-ID nicht gefunden - ProduktName:" + clientProductInfo.getClientProductName() + " - ProduktVersion: " + clientProductInfo.getClientProductVersion(), legitimationInfo));
                }
                new DbHelper().closeQuietly(resultSet);
            }
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("KundenSystemID konnte nicht geholt werden: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        if (userSystemId == null) {
            LOG.info(addTagUser("Kunden-System-ID wird angelegt", legitimationInfo));
            userSystemId = insertTempclientstaff(dialogId, legitimationInfo, clientProductInfo);
            LOG.info(addTagUser(format("Kunden-System-ID: %s", userSystemId), legitimationInfo));
        }
        return userSystemId;
    }

    /**
     * Entwertet einen iTAN/mTAN zum Dialog
     *
     * @param dialogId         Dialog-ID
     * @param legitimationInfo legitimansionsinformation
     */
    public void devalueTan(String dialogId, LegitimationInfo legitimationInfo) {
        LOG.info(addTagUser(format("Dialog-ID: %s", dialogId), legitimationInfo));
        String tanid = getTanId(dialogId, legitimationInfo);
        if (tanid != null) {
            Connection connection = null;
            try {
                connection = getConnection();
                if (isMobileTan(tanid)) {
                    PreparedStatement pS = connection.prepareStatement(sql.tanliste.mobil.update);
                    pS.setString(1, String.valueOf(unixTimeStamp()));
                    pS.setString(2, tanid.replace("-", ""));
                    pS.execute();
                } else {
                    PreparedStatement pS = connection.prepareStatement(sql.tanliste.update);
                    pS.setString(1, sdf_tan.format(new java.util.Date()));
                    pS.setString(2, tanid);
                    pS.execute();
                }

            } catch (SQLException e) {
                LOG.warn(addTagUser(format("Fehler beim Entwerten der TAN-ID: %s, Fehler: %s", tanid, e.getMessage()), legitimationInfo), e);
            } finally {
                new DbHelper().closeQuietly(connection);
            }
        } else {
            LOG.warn(addTagUser(format("TAN zum Entwerten nicht wieder gefunden, Dialog-ID (%s) nicht gefunden?", dialogId), legitimationInfo));
        }
    }

    /**
     * Prüft ob eine TAN-ID für mTAN oder iTAN ist
     *
     * @param tanid TAN-ID
     * @return true wenn mTAN
     */
    private boolean isMobileTan(String tanid) {
        return (tanid.contains("-"));
    }

    /**
     * Prüft ob die PIN Freigeschalten ist
     *
     * @param legitimationInfo LegitimationInfo
     * @return true wenn freigeschalten
     */
    public boolean isPinUsable(LegitimationInfo legitimationInfo) {
        boolean returnBoolean = false;
        Connection connection = null;
        try {
            connection = getConnection();
            String pin = getFieldByUserId("pin", legitimationInfo);
            if (!pin.startsWith("!") && !pin.startsWith("*") && !pin.startsWith("#")) {
                returnBoolean = true;
            }
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Abfragen der PIN-Änderungsberechtigung: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnBoolean;
    }

    /**
     * Holt eine Kennung-Spalte aus der Datenbank
     *
     * @param field            Feldname
     * @param legitimationInfo LegitimationInfo
     * @return Feldwert|null wenn nicht gefunden
     */
    private String getFieldByUserId(String field, LegitimationInfo legitimationInfo) {
        String returnString = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.kennungen.select);
            pS.setString(1, legitimationInfo.getUserId());
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnString = resultSet.getString(field);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Holen der Kennung-Daten: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    public Map<String, Boolean> getPermittedOperations(LegitimationInfo legitimationInfo, String kontonummer) {
        Map<String, Boolean> permittedOperations = new HashMap<>();
        try (Connection connection = getConnection()) {
            PreparedStatement pS = connection.prepareStatement(sql.login.konten.permission.select);
            pS.setString(1, legitimationInfo.getUserId());
            pS.setString(2, kontonummer);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                String ueberweisung = resultSet.getString(1);
                String sdd = resultSet.getString(2);
                String ade = resultSet.getString(3);
                String avz = resultSet.getString(4);
                String lastschrift = resultSet.getString(5);
                permittedOperations.put("ueberweisung", "1".equals(ueberweisung));
                permittedOperations.put("sdd", "1".equals(sdd));
                permittedOperations.put("ade", "E".equals(ade));
                permittedOperations.put("avz", "1".equals(avz));
                permittedOperations.put("lastschrift", "1".equals(lastschrift));
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der Berechtigung, Spalte für Benutzerkennung in 'login_konten_permission' nicht angelegt?: %s", e.getMessage()), legitimationInfo), e);
        }
        return permittedOperations;
    }


    /**
     * Prüft in login_konten_permission ob die Spalte/das Feld den Wert 1 hat
     *
     * @param legitimationInfo LegitimationInfo
     * @param kontonummer      Kontonummer
     * @param field            Feldname
     * @return gibt zurück, ob die Operation erlaubt ist
     */
    public boolean isOperationPermit(LegitimationInfo legitimationInfo, String kontonummer, String field) {
        boolean returnBoolean = false;
        Connection connection = null;
        String lpIsset = getLpPermission(legitimationInfo, field);
        if (lpIsset != null) return lpIsset.equals("1");
        if (field != null) {
            try {
                connection = getConnection();
                PreparedStatement pS = connection.prepareStatement(sql.login.konten.permission.select);
                pS.setString(1, legitimationInfo.getUserId());
                pS.setString(2, kontonummer);
                ResultSet resultSet = pS.executeQuery();
                returnBoolean = resultSet.next() && resultSet.getString(field).trim().equals("1") && resultSet.getString("ade").trim().equals("E");
                new DbHelper().closeQuietly(resultSet);
            } catch (SQLException e) {
                LOG.warn(addTagUser(format("Fehler beim holen der Berechtigung, Spalte für Benutzerkennung in 'login_konten_permission' nicht angelegt?: %s", e.getMessage()), legitimationInfo), e);
            } finally {
                new DbHelper().closeQuietly(connection);
            }
        }
        LOG.info(addTagUser(format("Berechtigung: %s:%s, Kontonummer: %s", field, (returnBoolean ? "true" : "false"), kontonummer), legitimationInfo));
        return returnBoolean;
    }

    /**
     * Generiert eine Auftragsreferenz zu dem Hashwert und hinterlegt diese in Tempclientstuff
     *
     * @param dialogId         Dialog-ID
     * @param auftragsHashwert AuftragsHashwert
     * @return Auftragsreferenz
     */
    public String generateAuftragsreferenz(String dialogId, byte[] auftragsHashwert, LegitimationInfo legitimationInfo) {
        String Auftragsreferenz = generateAuftragsreferenz(auftragsHashwert, legitimationInfo);
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.tempclientstuff3.update_srvref_by_dialogId);
            pS.setString(1, Auftragsreferenz);
            pS.setString(2, dialogId);
            pS.execute();

        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Anlegen der Auftragsreferenz: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return Auftragsreferenz;
    }

    /**
     * generiert eine Auftragsreferenz zu dem Hashwert
     *
     * @param auftragsHashwert Auftragshashwert
     * @return Auftragsreferenz
     */
    private String generateAuftragsreferenz(byte[] auftragsHashwert, LegitimationInfo legitimationInfo) {
        LOG.info(addTagUser(format("generateAuftragsreferenz: %s", (new String(auftragsHashwert))), legitimationInfo));
        long checksum = 0;
        for (byte b : auftragsHashwert) {
            checksum += Math.abs(b);
        }
        LOG.info(addTagUser(format("checksum: %s, Auftragsreferenz: %s", checksum, (checksum % 1000000)), legitimationInfo));
        return "" + (checksum % 1000000);
    }

    /**
     * Holt die TAN-ID / mTAN-ID zu einem Dialog zurück
     *
     * @param dialogId         Dialog-ID
     * @param legitimationInfo LegitimationInfo
     * @return TAN-ID/mTAN-ID, null wenn Dialog nicht gefunden wird
     */
    @SuppressWarnings("UnusedParameters")
    private String getTanId(String dialogId, LegitimationInfo legitimationInfo) {
        String returnString = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement tcs = connection.prepareStatement(sql.tempclientstuff3.select_tanid_by_dialogid);
            tcs.setString(1, dialogId);
            ResultSet resultSet = tcs.executeQuery();
            if (resultSet.next()) {
                returnString = resultSet.getString(sql.tempclientstuff3.col_tanid);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der TAN-ID für den Dialog: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    /**
     * Holt die alle freien TANs einer iTAN-Liste zurück
     *
     * @param tanlistennr      TAN-Listen-Nummer
     * @param legitimationInfo LegitimationInfo
     * @return TAN-Liste mit freien TANs
     */
    private List<Map<String, String>> getNextFreeTanFromlist(String tanlistennr, LegitimationInfo legitimationInfo) {
        List<Map<String, String>> returnMapList = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement tanliste = connection.prepareStatement(sql.tanliste.select_unused_by_tanlistenid_kennung);
            tanliste.setString(1, tanlistennr);
            tanliste.setString(2, legitimationInfo.getUserId());
            ResultSet resultSet = tanliste.executeQuery();
            returnMapList = toMapList(resultSet);
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der nächsten TAN aus der Liste %s: %s", tanlistennr, e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnMapList;
    }

    public Integer getInt(String value) {
//		LOG.debug("getInt"+value);
        if (value != null) return parseInt(value);
        return null;
    }

    private Map<String, String> toMap(ResultSet resultSet) {
        Map<String, String> returnMap = null;
        if (resultSet != null) {
            try {
                ResultSetMetaData metaData = resultSet.getMetaData();
                returnMap = new HashMap<>();
                int count = metaData.getColumnCount();
                for (int i = 1; i < count + 1; i++) {

//					LOG.debug(StringUtils.lowerCase(metaData.getColumnName(i)) + ":"+metaData.getColumnLabel(i)+":" + resultSet.getString(i));
                    returnMap.put(StringUtils.lowerCase(metaData.getColumnLabel(i)), resultSet.getString(i));
                }
            } catch (SQLException e) {
                LOG.warn(format("toMap: %s", e.getMessage()), e);
            }
        }
        return returnMap;
    }

    private List<Map<String, String>> toMapList(ResultSet resultSet) {
        List<Map<String, String>> returnMapList = new ArrayList<>();
        if (resultSet != null) {
            try {
                while (resultSet.next()) {
                    returnMapList.add(toMap(resultSet));
                }
            } catch (SQLException e) {
                LOG.warn(format("toMapList: %s", e.getMessage()), e);
            }
        }
        return returnMapList;
    }

    /**
     * Aktiviert eine neue TAN-Liste und gibt diese Zurück
     *
     * @param legitimationInfo LegitimationInfo
     * @return SQL-Resultset
     */
    private Map<String, String> activateAndGetNextTanListe(LegitimationInfo legitimationInfo) {
        Map<String, String> returnMap = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement tanlistenlog = connection.prepareStatement(sql.tanliste.nlog.select_status);
            tanlistenlog.setString(1, legitimationInfo.getUserId());
            tanlistenlog.setString(2, "0");
            ResultSet resultSet = tanlistenlog.executeQuery();
            if (resultSet.next()) {
                String tanlistenid = resultSet.getString("tanlistenid");
                try {
                    PreparedStatement pSu = connection.prepareStatement(sql.tanliste.nlog.update_by_tanlistenid);
                    pSu.setString(1, tanlistenid);
                    pSu.execute();
                    resultSet.beforeFirst();
                    if (resultSet.next()) {
                        returnMap = toMap(resultSet);
                    }
                    new DbHelper().closeQuietly(resultSet);
                } catch (SQLException sqe1) {
                    LOG.warn(addTagUser(format("Fehler beim aktivieren der neuen TAN-Liste %s: %s", tanlistenid, sqe1.getMessage()), legitimationInfo), sqe1);
                }
            }
        } catch (SQLException sqle2) {
            LOG.warn(addTagUser(format("Fehler beim holen der inaktiven TAN-Listen für Kennung: %s", sqle2.getMessage()), legitimationInfo), sqle2);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnMap;
    }

    /**
     * Gibt eine TAN für iTAN-ID zurück
     *
     * @param tanId            iTAN-ID
     * @param legitimationInfo LegitimationInfo
     * @return TAN (verschlüsselt)
     */
    private String getTanById(String tanId, LegitimationInfo legitimationInfo) {
        String returnString = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.tanliste.select_by_tanid);
            pS.setString(1, tanId);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnString = resultSet.getString("tan");
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der TAN zur TAN-ID: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    /**
     * Holt eine TAN für mTAN-ID zurück
     *
     * @param tanId            mTAN-ID
     * @param legitimationInfo LegitimationInfo
     * @return TAN (verschlüsselt)
     */
    private String getTanByIdMobile(String tanId, LegitimationInfo legitimationInfo) {
        String returnString = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.tanliste.mobil.select);
            pS.setString(1, tanId);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnString = resultSet.getString("tan");
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der mTAN zur mTAN-ID: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    /**
     * Setzt eine TAN-ID zu einem Dialog
     *
     * @param dialogid          Dialog-ID
     * @param tanId             TAN-ID (unsigned für iTAN, signed für mTAN)
     * @param legitimationInfo  LegitimationInfo
     * @param clientProductInfo ClientProductInfo
     * @return gibt bei Erfolg true zurück
     */
    public boolean setTanId(String dialogid, String tanId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo) {
        Boolean returnBoolean = false;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.tempclientstuff3.update_tanid_by_kid);
            pS.setString(1, tanId);
            pS.setString(2, dialogid);
            pS.setString(3, legitimationInfo.getUserId());
            pS.setString(4, getCustomerIdByConfig(legitimationInfo));
            pS.setString(5, derfNullSein(clientProductInfo.getClientProductName()));
            pS.setString(6, derfNullSein(clientProductInfo.getClientProductVersion()));
            pS.setString(7, clientProductInfo.getUserSystemId());
            pS.execute();
            returnBoolean = true;
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim setzen der TAN-ID: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnBoolean;
    }

    /**
     * Fügt einen Geschäftsvorfall in die Tabelle
     *
     * @param globalParameter PermissionDataObject.GlobalParameter
     * @param bpdVersion      BPD-Version
     * @param hbciVersion     HBCI-Version
     */
    public void insertGeschaeftsvorfall(PermissionDataObject.GlobalParameter globalParameter, String bpdVersion, String hbciVersion) {
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pSi = connection.prepareStatement(sql.geschaeftsvorfaelle.insert);
            pSi.setString(1, globalParameter.getSegmentName());
            pSi.setString(2, String.valueOf(globalParameter.getSegmentVersion()));
            pSi.setString(3, String.valueOf(globalParameter.getMaximaleAnzahlAuftraege()));
            pSi.setString(4, globalParameter.getAnzahlSingaturenMindestens()); //param1
            pSi.setString(5, globalParameter.getSicherheitsKlasse()); //param5
            pSi.setString(6, globalParameter.getFints_3_parameter()); //param3
            pSi.setString(7, globalParameter.getFints_4_parameter()); //param4
            pSi.setString(8, bpdVersion);
            pSi.setString(9, hbciVersion);
            pSi.execute();
        } catch (SQLException e) {
            LOG.warn(format("Fehler beim Anlegen des Geschäftsvorfalls: %s", e.getMessage()), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
    }

    /**
     * Entfernt alle Geschäftsvorfälle zu einer BPD/HBCI-Version
     *
     * @param bpdversion  BPD-Version
     * @param hbciversion HBCI-Version
     */
    public void resetGeschaeftsvorfaelle(String bpdversion, String hbciversion) {
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pSi = connection.prepareStatement(sql.geschaeftsvorfaelle.delete);
            pSi.setString(1, bpdversion);
            pSi.setString(2, hbciversion);
            pSi.execute();
        } catch (SQLException e) {
            LOG.warn(format("Fehler beim Zurücksetzen der Geschäftsvorfälle: %s", e.getMessage()), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
    }

    /**
     * Holt Vorabumsätze aus der Datenbank
     * soll null zurückgeben, wenn keine Vorabumsätze vorhanden sind
     *
     * @param bankCode         Bankleitzahl (für generierung)
     * @param kontonummer      Kontonummer
     * @param legitimationInfo LegitimationInfo
     * @param kontowaehrung    Kontowährung
     * @param vonDatum         Start-Datum
     * @param bisDatum         End-Datum
     * @return Byte-Array mit vorabumsätze
     */
    public byte[] getNichtGebuchteUmsaetze(String bankCode, String kontonummer, LegitimationInfo legitimationInfo, String kontowaehrung, Date vonDatum, Date bisDatum) {

        byte[] returnBytes = null;
        Konto konto = new Konto();
        konto.setBankleitzahl(bankCode);
        konto.setNummer(kontonummer);
        konto.setWaehrung(kontowaehrung);
        Writer.setAddSeperator(true);
        Writer writer = new Writer(konto);
        Connection connection = null;
        if (vonDatum == null) {
            vonDatum = getMinDate();
        }
        if (bisDatum == null) {
            bisDatum = new Date();
        }
        LOG.info(addTagUser(format("hole vorabumsätze für Kontonummer %s von %s bis %s", kontonummer, sdf_sql.format(vonDatum), sdf_sql.format(bisDatum)), legitimationInfo));
        boolean found = false;
        try {
            Satz s = new Satz();
            s.setKonto(konto);
            String kontoid = getFieldByKontonummer(kontonummer, "kontoid", legitimationInfo);
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.vorabumsaetze.select);
            pS.setString(1, kontoid);
            pS.setDate(2, new java.sql.Date(vonDatum.getTime()));
            pS.setDate(3, new java.sql.Date(bisDatum.getTime()));
            ResultSet resultSet = pS.executeQuery();
            int umsatznummer = 0;
            Konto umsatzKonto;
            while (resultSet.next()) {
                Umsatz u = new Umsatz(s, umsatznummer);
                u.setBuchungsdatum(resultSet.getDate("buchungsdatum"));
                u.setValuta(resultSet.getDate("buchungsdatum"));
                u.setBetrag(resultSet.getDouble("betrag"));
                u.setVerwendungszweck(resultSet.getString("vwz"));
                umsatzKonto = new Konto();
                umsatzKonto.setInhaber(resultSet.getString("gegenname"));
                if (resultSet.getString("gegenkonto").length() <= 10) {
                    umsatzKonto.setNummer(resultSet.getString("gegenkonto"));
                    umsatzKonto.setBankleitzahl(resultSet.getString("gegenblz"));
                } else {
                    umsatzKonto.setBankleitzahl("");
                    umsatzKonto.setNummer("");
                    umsatzKonto.setIban(resultSet.getString("gegenkonto"));
                    umsatzKonto.setSwiftcode(resultSet.getString("gegenblz"));
                }
                u.setGegenkonto(umsatzKonto);
                writer.addUmsatz(u);
                umsatznummer++;
                found = true;
            }
            LOG.info(addTagUser(format("gefundene Vorabumsätze: %s", umsatznummer), legitimationInfo));
            new DbHelper().closeQuietly(resultSet);
            if (found) {
                returnBytes = writer.write().getBytes(String.valueOf(charsetISO88591));
            }
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Holen der Vorabumsätze: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnBytes;

    }

    /**
     * Holt die Benutzerkennung aus der Datenbank (kunden_benutzerkennungen)
     *
     * @param userId Kennung
     * @return Kunden-ID, null wenn es keine dazu gibt
     */
    public String getCustomerId(String userId) {
        if (userId == null || userId.equals("")) {
            return null;
        }

        String returnString = localUserReference.getOrDefault(userId, null);
        if (returnString == null) {
            Connection connection = null;
            try {
                connection = getConnection();
                PreparedStatement pS = connection.prepareStatement(sql.kunden.kennungen.select);
                pS.setString(1, userId);
                ResultSet resultSet = pS.executeQuery();
                if (resultSet.next()) {
                    returnString = resultSet.getString("kundenid");
                    localUserReference.put(userId, returnString);
                } else {
                    LOG.info(addTagUser("Kundenid zu Benutzerkennung nicht gefunden", userId, ""));

                }
                new DbHelper().closeQuietly(resultSet);
            } catch (SQLException e) {
                LOG.warn(addTagUser(format("Fehler beim holen der Kundenid zur Benutzerkennung: %s", e.getMessage()), userId, ""), e);
            } finally {
                new DbHelper().closeQuietly(connection);
            }
            LOG.info(addTagUser(format("Kunden-ID gefunden: %s", returnString), userId, ""));
        }
        return returnString;
    }

    private boolean hasUserSystemId(ClientProductInfo clientProductInfo) {
        return (!derfNullSein(clientProductInfo.getUserSystemId()).equals(""));
    }

    /**
     * Holt TempClientStuff?
     *
     * @param legitimationInfo  LegitimationInfo
     * @param clientProductInfo ClientProductInfo
     * @return Dialog-ID?
     */
    @SuppressWarnings("unused")
    private String getTempClientStuff(LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo) {
        Connection connection = null;
        String dialogid = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(hasUserSystemId(clientProductInfo) ? sql.tempclientstuff3.select_kid : sql.tempclientstuff3.select);
            setPreparedStatementParams(pS, legitimationInfo, clientProductInfo, hasUserSystemId(clientProductInfo));
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                dialogid = resultSet.getString("dialogid");
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der Dialog-ID: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return dialogid;
    }

    private boolean setPreparedStatementParams(PreparedStatement pS, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, boolean withKundenSystemId) {
        try {
            int params = pS.getParameterMetaData().getParameterCount();
            int len = (withKundenSystemId) ? 5 : 4;
            int min = params - len;
            pS.setString(min + 1, legitimationInfo.getUserId());
            pS.setString(min + 2, getCustomerIdByConfig(legitimationInfo));
            pS.setString(min + 3, derfNullSein(clientProductInfo.getClientProductName()));
            pS.setString(min + 4, derfNullSein(clientProductInfo.getClientProductVersion()));
            if (withKundenSystemId) {
                pS.setString(min + 5, clientProductInfo.getUserSystemId());
            }
            return true;
        } catch (SQLException e) {
            LOG.error(addTagUser(format("Fehler beim Setzen der Parameter für ein PreparedStatement: %s", e.getMessage()), legitimationInfo), e);
            return false;
        }
    }

    /**
     * Holt die aktiven TAN-Listen aus der Datenbank
     *
     * @param legitimationInfo Kennung
     * @return SQL-Resultset aus der Datenbank, null wenn es keine gibt
     */
    private Map<String, String> getActiveTanListe(LegitimationInfo legitimationInfo) {
        Map<String, String> returnMap = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement tanlistenlog = connection.prepareStatement(sql.tanliste.nlog.select_status);
            tanlistenlog.setString(1, legitimationInfo.getUserId());
            tanlistenlog.setString(2, "1");
            ResultSet resultSet = tanlistenlog.executeQuery();
            if (resultSet.next()) {
                returnMap = toMap(resultSet);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der aktiven TAN-Listen für Kennung: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnMap;
    }

    /**
     * Holt die Buchungstyp ID zum Textschlüssel aus der Datenbank
     *
     * @param textschluessel   Textschlüssel
     * @param returnString     defaultValue
     * @param legitimationInfo LegitimationInfo
     * @return falls der Textschlüssel nicht existiert, gibts den defaultValue
     */
    private String getBuchungstypid(String textschluessel, String returnString, LegitimationInfo legitimationInfo) {
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.buchungstypen.select);
            pS.setString(1, textschluessel);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnString = resultSet.getString("buchungstypid");
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der Buchungstypid zu Textschlüssel %s: %s", textschluessel, e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    /**
     * Holt den nächsten Counter von Daueraufträge (für Sammler)
     *
     * @param legitimationInfo LegitimationInfo
     * @param add              ob der counter gezählt werden soll
     * @return Sammler counter
     */
    public int getNextCounterDauerauftraege(LegitimationInfo legitimationInfo, boolean add) {
        int returnInt = (add) ? 1 : 0;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.dauerauftraege.select.counter);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnInt = resultSet.getInt("counter") + (add ? 1 : 0);
            }
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehlen beim holen des nächsten DauerauftragsCounter: %s", e.getMessage()), legitimationInfo));
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnInt;
    }

    /**
     * Holt den nächsten DTAUS-Counter aus der Datenbank
     * es wird ein Pseudo-Login verwendet, da der Counter-Login-Basiert ist
     *
     * @param legitimationInfo LegitimationInfo
     * @param add              soll er um 1 aufaddiert werden?
     * @return Counter-Wert
     */
    public int getNextCounterDTAUS(LegitimationInfo legitimationInfo, boolean add) {
        String pseudoLogin = getPseudoLogin(legitimationInfo);
        int returnInt = (add) ? 1 : 0;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.dtaus.select_counter);
            pS.setString(1, pseudoLogin);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnInt = resultSet.getInt("counter") + (add ? 1 : 0);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen des nächsten Counters für %s: %s", pseudoLogin, e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnInt;
    }

    private String getPseudoLogin(LegitimationInfo legitimationInfo) {
        return "_" + legitimationInfo.getUserId();
    }

    /**
     * Hilfsfunktion für Kunde
     *
     * @param field            Feld aus der Kunden-Tabelle
     * @param legitimationInfo legitimationInfo
     * @return Feldwert
     */
    private String getFieldByCustomerId(String field, LegitimationInfo legitimationInfo) {
        String returnString = null;
        Connection connection = null;
        String customerIdByConfig = getCustomerIdByConfig(legitimationInfo);
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.kunden.select);
            pS.setString(1, customerIdByConfig);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnString = resultSet.getString(field);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Holen der Kunden-Daten: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    /**
     * Gibt ein Feld aus der Konten-Tabelle wieder
     *
     * @param kontonummer      Kontonummer
     * @param field            Feldname
     * @param legitimationInfo LegitimationInfo
     * @return Feldwert
     */
    public String getFieldByKontonummer(String kontonummer, String field, LegitimationInfo legitimationInfo) {
        Map<String, String> kontorow = getKontoRow(kontonummer, legitimationInfo);
        if (kontorow != null) {
            return kontorow.get(field);
        }
        LOG.info(addTagUser(format("Kontonummer %s nicht gefunden", kontonummer), legitimationInfo));
        return null;
    }

    /**
     * Prüft ob auf die Konten in der übergebenen Liste gebucht werden darf
     *
     * @param kontonummer      Auftrags-Kontonummer
     * @param kontenListe      Liste der Empfänger-Konten (IBANs)
     * @param transactionType  Transaktionstyp (sct,sdd,b2b)
     * @param legitimationInfo legitimationInfo
     * @return gibt bei erlaubnis true zurück
     */
    public ReturnDataObject checkEmpfaengerKonten(String kontonummer, HashMap<String, String> kontenListe, tranactionTypes transactionType, LegitimationInfo legitimationInfo) {

        Map<String, String> kontorow = getKontoRow(kontonummer, legitimationInfo);
        if (kontorow != null) {
            ReturnDataObject r = new ReturnDataObject(true, "");
            String preKonto = kontorow.get("pre_konto");
            String preBlz = kontorow.get("pre_blz");
            String preIban = kontorow.get("pre_iban");

            if (preIban == null || preIban.equals("")) {
                if (preKonto.length() <= 10 && preBlz.length() == 8) {
                    preIban = convertKnrBlzToIBAN(preKonto, preBlz);
                } else if (preKonto.length() > 10) {
                    preIban = preKonto;
                } else {
                    preIban = null;
                }
            }
            if (preIban != null) {
                for (String iban : kontenListe.keySet()) {
                    if (!iban.equals(preIban)) {
                        LOG.warn(addTagUser(format("Zielkonto (%s) != Gegenkonto (%s)", iban, preIban), legitimationInfo));
                        r = new ReturnDataObject(false, format("Kontoverbindung (%s) ist für dieses Auftragskonto nicht zugelassen", iban));
                        break;
                    }
                }
            } else {
                for (String iban : kontenListe.keySet()) {
                    String bic = kontenListe.get(iban);
                    ReturnDataObject bicCheck = (config(properties.bicCheckEnabled, "true").equals("true")) ? checkBic(bic, transactionType, legitimationInfo) : new ReturnDataObject(true, "");
                    if (!bicCheck.isSuccess()) {
                        LOG.warn(addTagUser(bicCheck.getMessage(), legitimationInfo));
                        r = bicCheck;
                        break;
                    }
                }
                if (getCkonto().isActive()) {
                    for (String iban : kontenListe.keySet()) {
                        String bic = kontenListe.get(iban);
                        ReturnDataObject cKontoCheck = getCkonto().checkSepa(iban, bic, "true".equals(config(properties.cKontoSondercheckDe, "")));
                        if (cKontoCheck != null && !cKontoCheck.isSuccess()) {
                            LOG.warn(addTagUser(cKontoCheck.getMessage(), legitimationInfo));
                            r = cKontoCheck;
                            break;
                        }
                    }
                }
            }
            return r;
        }
        LOG.info(addTagUser(format("Kontonummer: %s nicht gefunden", kontonummer), legitimationInfo));
        return new ReturnDataObject(false, format("Kontonummer: %s nicht gefunden", kontonummer), "9310");
    }

    private CkontoLocal getCkonto() {
        if (this.ckonto == null) {
            this.ckonto = new CkontoLocal(
                    config(properties.cKontoLocalKey, ""),
                    config(properties.cKontoLocalPath, ""),
                    config(properties.cKontoLocalCodes, "1,9")
            );
        }
        return this.ckonto;
    }

    private Date getMinDate() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, 2000);
        c.set(Calendar.MONTH, Calendar.JANUARY);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTime();
    }

    /**
     * Holt die gebuchten Umsätze aus der MT940-Tabelle
     *
     * @param kontonummer      kontonummer
     * @param vonDatum         min-Datum
     * @param bisDatum         max-Datum
     * @param legitimationInfo Legitimationsdatum
     * @return Umsätze als Byte-Array
     */
    public byte[] getGebuchteUmsaetze(String kontonummer, java.util.Date vonDatum, java.util.Date bisDatum, LegitimationInfo legitimationInfo) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        String kontoid = getFieldByKontonummer(kontonummer, "kontoid", legitimationInfo);
        LOG.info(addTagUser(format("Konto-ID gefunden: %s", kontoid), legitimationInfo));
        String customerIdByConfig = getCustomerIdByConfig(legitimationInfo);
        Double lastKontostand = getLastKontostandByBewegungen(kontoid, customerIdByConfig, legitimationInfo);
        if (lastKontostand == null) {
            lastKontostand = 0.00d;
        }
        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.applyPattern("yyyy-MM-dd");
        Connection connection = null;
        boolean found = false;
        try {
            if (vonDatum == null) {
                vonDatum = getMinDate();
            }
            if (bisDatum == null) {
                bisDatum = new java.util.Date();
            }
            connection = getConnection();
            LOG.info(addTagUser(format("vonDatum: %s", sdf.format(vonDatum)), legitimationInfo));
            LOG.info(addTagUser(format("bisDatum: %s", sdf.format(bisDatum)), legitimationInfo));
            PreparedStatement pS = connection.prepareStatement(sql.mt940.select_by_id_date);
            pS.setString(1, kontoid);
            pS.setString(2, sdf.format(vonDatum));
            pS.setString(3, sdf.format(bisDatum));
            ResultSet resultSet = pS.executeQuery();
            found = isFound(byteArrayOutputStream, resultSet, legitimationInfo);
            new DbHelper().closeQuietly(resultSet);

            if (!found) {
                LOG.info(addTagUser(format("Keine Umsätze für das Konto: %s gefunden", kontonummer), legitimationInfo));
            }
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der MT940-Sätze: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        try {
            byteArrayOutputStream.flush();
        } catch (IOException e) {
            LOG.warn(addTagUser(format("Fehler beim Flushen: %s", e.getMessage()), legitimationInfo), e);
        }
        if (found) {
            return byteArrayOutputStream.toByteArray();
        } else {
            byte[] lastMt940 = getLastMt940(kontoid, legitimationInfo);
            if (lastMt940 != null) {
                return lastMt940;
            } else {
                Date lastBuchungsdatumByBewegung = getLastBuchungsdatumByBewegung(kontoid, customerIdByConfig, legitimationInfo);
                if (lastBuchungsdatumByBewegung != null) {
                    vonDatum = onDayBefore(lastBuchungsdatumByBewegung);
                } else {
                    Date lastBuchungsdatumByVorab = getLastBuchungsdatumByVorab(kontoid, legitimationInfo);
                    if (lastBuchungsdatumByVorab != null) {
                        vonDatum = onDayBefore(lastBuchungsdatumByVorab);
                    }
                }
            }
        }
        return getEmptyMt940(kontonummer, lastKontostand, vonDatum).getBytes();
    }

    private boolean isFound(ByteArrayOutputStream byteArrayOutputStream, ResultSet resultSet, LegitimationInfo legitimationInfo) throws SQLException {
        Blob blob;
        boolean found = false;
        while (resultSet.next()) {
            blob = resultSet.getBlob("mt940");
            try {
                byteArrayOutputStream.write(blob.getBytes(1, (int) blob.length()));
                found = true;
            } catch (IOException e) {
                LOG.info(addTagUser(format("Unbekannter Fehler: %s", e.getMessage()), legitimationInfo), e);
            }
        }
        return found;
    }

    private Date onDayBefore(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(Calendar.DATE, -1);
        return c.getTime();
    }

    private String getBankCode() {
        return config(properties.bankCode, "12345678");
    }

    private String getEmptyMt940(String kontonummer, Double lastKontostand, java.util.Date startDatum) {
        Konto auftraggeberkonto = new Konto();
        auftraggeberkonto.setWaehrung("EUR");
        auftraggeberkonto.setNummer(kontonummer);
        auftraggeberkonto.setBankleitzahl(getBankCode());
        Writer empty = new Writer(auftraggeberkonto, lastKontostand, 0, 0);
        return empty.writeEmpty(startDatum);
    }

    private byte[] getLastMt940(String kontoid, LegitimationInfo legitimationInfo) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Connection connection = null;
        boolean found = false;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.mt940.select_last);
            pS.setString(1, kontoid);
            ResultSet resultSet = pS.executeQuery();
            found = isFound(byteArrayOutputStream, resultSet, legitimationInfo);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen des letzten Umsatzes: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return ((found) ? byteArrayOutputStream.toByteArray() : null);
    }

    /**
     * @param legitimationInfo         die LegitimationInfo
     * @param auftraggeberKonto        Kontonummer
     * @param empfaengerKonto          Empf&auml;nger-Kontonummer, wenn nicht vorhanden dann ""
     * @param empfaengerBlz            Empf&auml;nger-Bankleitzahl, wenn nicht vorhanden dann ""
     * @param empfaengerName           Empf&auml;nger-Name
     * @param betrag                   Betrag ( > 0 wenn Gutschrift, <0 wenn Lastschrift)
     * @param textSchluessel           Siehe SQL-Tablle
     * @param verwendungszweck         Text
     * @param TAN                      Verwendete TAN
     * @param IBAN                     Empf&auml;nger-IBAN
     * @param textSchluesselErgaenzung Text-Schl&uuml;ssel-Erg&auml;nzung
     * @param BIC                      Empf&auml;nger-BIC
     * @param counter                  Counter (pro Login)
     * @param lsArt                    unknown
     * @param lsSequenz                unknown
     * @param lsDate                   unknown
     * @param endToEnd                 endToEndID
     * @param purposeCode              purposeCode
     * @return gibt zur&uuml;ck, ob die Eintragung erfolgreich war
     */
    public Payments.Result inDtaus(
            LegitimationInfo legitimationInfo,
            String auftraggeberKonto,
            String empfaengerKonto,
            String empfaengerBlz,
            String empfaengerName,
            Double betrag,
            String textSchluessel,
            String verwendungszweck,
            String TAN, String IBAN,
            int textSchluesselErgaenzung,
            String BIC, Integer counter,
            String lsArt, String lsSequenz, Date lsDate,
            String mandatsreferenz, String glaeubigerid, Date sign_date,
            String currentTime,
            String endToEnd, String purposeCode) {
        if (lsDate == null) lsDate = new java.util.Date();
        if (sign_date == null) sign_date = new java.util.Date();
        if (currentTime == null) currentTime = new SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        Connection connection = null;
        String auftraggeberName = getFieldByCustomerId("name", legitimationInfo);
        String auftraggeberKontoInhaber = getFieldByKontonummer(auftraggeberKonto, "kontoinhaber", legitimationInfo);
        String pseudoLogin = getPseudoLogin(legitimationInfo);
        String buchungstypid = getBuchungstypid(textSchluessel, "0", legitimationInfo);
        Gegenkonto gegenkonto = new Gegenkonto(empfaengerKonto, empfaengerBlz, IBAN, BIC);
        empfaengerKonto = gegenkonto.getKontonummer();
        empfaengerBlz = gegenkonto.getBankleitzahl();
        IBAN = gegenkonto.getIBAN();
        BIC = gegenkonto.getBIC();

        Payments.Result r;
        try {
            connection = getConnection();
            PreparedStatement pSi = connection.prepareStatement(sql.dtaus.insert, Statement.RETURN_GENERATED_KEYS);
            pSi.setString(1, replaceNonSepaChars(empfaengerName));
            pSi.setString(2, empfaengerKonto);
            pSi.setString(3, empfaengerBlz);
            pSi.setDouble(4, betrag);
            pSi.setString(5, replaceNonSepaChars(verwendungszweck));
            pSi.setString(6, replaceNonSepaChars(auftraggeberKontoInhaber));
            pSi.setString(7, auftraggeberKonto);
            pSi.setString(8, replaceNonSepaChars(auftraggeberName));
            pSi.setString(9, pseudoLogin);
            pSi.setString(10, derfNullSein(TAN));
            pSi.setString(11, "0");
            pSi.setString(12, buchungstypid);
            pSi.setDate(13, new java.sql.Date(new java.util.Date().getTime()));
            pSi.setString(14, currentTime);
            pSi.setInt(15, counter);
            pSi.setInt(16, textSchluesselErgaenzung);
            pSi.setString(17, IBAN);
            pSi.setString(18, BIC);
            pSi.setString(19, lsArt);
            pSi.setString(20, lsSequenz);
            pSi.setDate(21, new java.sql.Date(lsDate.getTime()));
            pSi.setString(22, mandatsreferenz);
            pSi.setString(23, String.valueOf(unixTimeStamp(sign_date)));
            pSi.setString(24, glaeubigerid);
            pSi.setString(25, endToEnd);
            pSi.setString(26, purposeCode);
            boolean execute = pSi.execute();
            LOG.info(addTagUser(format("inDTAUS: %s", (execute ? "true" : "false")), legitimationInfo));
            r = new Payments.Result(true).readPrepared(pSi);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim anlegen einer Buchung: %s", e.getMessage()), legitimationInfo), e);
            r = new Payments.Result(false).setError(e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return r;
    }

    public Payments.Result inDauerauftraege(LegitimationInfo legitimationInfo, String auftraggeberKonto, String empfaengerKonto,
                                            String empfaengerBlz, String empfaengerName, Double betrag, String textSchluessel, String verwendungszweck,
                                            String TAN, String IBAN, String BIC, Integer counter, int turnus,
                                            Date executionTime,
                                            String currentTime, String endToEnd, String purposeCode) {
        if (currentTime == null) currentTime = new SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String benutzerkennung = legitimationInfo.getUserId();
        Connection connection = null;
        String kundenName = getFieldByCustomerId("name", legitimationInfo);
        String auftraggeberName = getFieldByKontonummer(auftraggeberKonto, "kontoinhaber", legitimationInfo);
        String kontoid = getFieldByKontonummer(auftraggeberKonto, "kontoid", legitimationInfo);
        String buchungstypid = getBuchungstypid(textSchluessel, "0", legitimationInfo);
        Gegenkonto gegenkonto = new Gegenkonto(empfaengerKonto, empfaengerBlz, IBAN, BIC);
        empfaengerKonto = gegenkonto.getKontonummer();
        empfaengerBlz = gegenkonto.getBankleitzahl();
        IBAN = gegenkonto.getIBAN();
        BIC = gegenkonto.getBIC();
        Payments.Result r;
        try {
            connection = getConnection();
            PreparedStatement pSi = connection.prepareStatement(sql.dauerauftraege.insert, Statement.RETURN_GENERATED_KEYS);
            pSi.setString(1, replaceNonSepaChars(empfaengerName));
            pSi.setString(2, empfaengerKonto);
            pSi.setString(3, empfaengerBlz);
            pSi.setDouble(4, betrag);
            pSi.setString(5, replaceNonSepaChars(verwendungszweck));
            pSi.setString(6, replaceNonSepaChars(auftraggeberName));
            pSi.setString(7, auftraggeberKonto);
            pSi.setString(8, replaceNonSepaChars(kundenName));
            pSi.setString(9, benutzerkennung);
            pSi.setString(10, "0");    //	bearbeitet
            pSi.setString(11, buchungstypid);
            pSi.setDate(12, new java.sql.Date(new java.util.Date().getTime()));
            pSi.setString(13, currentTime);
            pSi.setDate(14, new java.sql.Date(executionTime.getTime()));
            pSi.setInt(15, turnus);
            pSi.setDate(16, null);// LastAction
            pSi.setString(17, (counter == null ? null : String.valueOf(counter))); // Sammler
            pSi.setString(18, kontoid);
            pSi.setInt(19, 0);    //	Loginid
            pSi.setString(20, TAN);    //	TAN
            pSi.setString(21, IBAN);
            pSi.setString(22, BIC);
            pSi.setString(23, endToEnd);
            pSi.setString(24, purposeCode);
            boolean execute = pSi.execute();
            LOG.info(addTagUser(format("inDauerauftraege: %s", (execute ? "true" : "false")), legitimationInfo));
            r = new Payments.Result(true).readPrepared(pSi);
            new DbHelper().closeQuietly(pSi);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim anlegen eines Dauerauftrags: %s", e.getMessage()), legitimationInfo), e);
            r = new Payments.Result(false).setError(e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return r;
    }


    /**
     * Trägt die Buchung in die Vorabumsätze ein
     *
     * @param kontonummer      kontonummer
     * @param legitimationInfo legitimationInfo
     * @param amount           Betrag
     * @param isZulasten       Überweisung = true, Lastschrift = false
     * @param vwz              Verwendungszweck
     * @param creditorAgent    EmpfängerName
     * @param creditorIBAN     EmpfängerIban
     * @param creditorName     EmpfängerBIC     @return gibt zurück ob es erfolgreich war
     */
    public Payments.Result inVorabumsaetze(String kontonummer, LegitimationInfo legitimationInfo, double amount, boolean isZulasten, String vwz, String creditorAgent, String creditorIBAN, String creditorName) {
        if (!configCreateVorabumsaetze) {
            LOG.info(addTagUser("Anlage von Vorabumsätze bei Einreichung deaktiviert", legitimationInfo));
            return new Payments.Result(false);
        }
        Connection connection = null;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM");
        if (isZulasten) {
            amount *= (-1);
        }
        Payments.Result r;
        try {
            connection = getConnection();
            String kontoid = getFieldByKontonummer(kontonummer, "kontoid", legitimationInfo);
            PreparedStatement pS = connection.prepareStatement(sql.vorabumsaetze.insert, Statement.RETURN_GENERATED_KEYS);
            pS.setString(1, kontoid);
            pS.setDate(2, new java.sql.Date(new java.util.Date().getTime()));
            pS.setString(3, replaceNonSepaChars(vwz));
            pS.setDouble(4, amount);
            pS.setString(5, sdf.format(new java.util.Date()));
            pS.setString(6, replaceNonSepaChars(creditorName));
            pS.setString(7, creditorIBAN);
            pS.setString(8, creditorAgent);
            boolean execute = pS.execute();
            LOG.info(addTagUser(format("inVorab: %s", (execute ? "true" : "false")), legitimationInfo));
            r = new Payments.Result(true).readPrepared(pS);
            new DbHelper().closeQuietly(pS);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Anlage von Vorabumsatz fehlgeschlagen: %s", e.getMessage()), legitimationInfo), e);
            r = new Payments.Result(false).setError(e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return r;
    }

    /**
     * Aktualisiert die Kontostände
     *
     * @param betrag           Betrag (absolute)
     * @param isZulasten       Ist der Betrag zu lasten? (z.B. Überweisung: true)
     * @param kontonummer      Kontonummer
     * @param legitimationInfo LegitimationInfo
     * @return gibt zurück, ob die Aktualisierung funktioniert hat
     */
    public boolean updateSalden(Double betrag, Boolean isZulasten, String kontonummer, LegitimationInfo legitimationInfo) {
        Boolean success = false;
        Connection connection = null;
        try {
            betrag = (isZulasten) ? betrag * (-1) : betrag;
            Double umsatz = (betrag * (-1));
            connection = getConnection();
            PreparedStatement pSk = connection.prepareStatement(sql.konten.update_saldo);
            pSk.setDouble(1, umsatz);
            pSk.setDouble(2, betrag);
            pSk.setString(3, kontonummer);
            pSk.execute();
            PreparedStatement pSlp = connection.prepareStatement(sql.login.permission.update_by_kennung);
            pSlp.setDouble(1, umsatz);
            pSlp.setString(2, legitimationInfo.getUserId());
            pSlp.execute();
            success = true;
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Update der Kontostände: %s", e.getMessage()), legitimationInfo), e);
            success = false;
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return success;
    }

    public Double getLpTageslimit(LegitimationInfo legitimationInfo) {
        Double returnDouble = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.login.permission.select);
            pS.setString(1, legitimationInfo.getUserId());
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnDouble = resultSet.getDouble("tageslimit") - resultSet.getDouble("tagesumsatz");
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Holen der Login_Permission für Kennung: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnDouble;
    }

    /**
     * Holt die Spalte aus Login_Permission
     *
     * @param legitimationInfo LegitimationInfo
     * @param column           spaltenName
     * @return null wenn Spalte oder User nicht existiert
     */
    private String getLpPermission(LegitimationInfo legitimationInfo, String column) {
        String returnString = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.login.permission.select);
            pS.setString(1, legitimationInfo.getUserId());
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnString = toMap(resultSet).get(column);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim abholen der login_permission: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    /**
     * Prüft die eingegebene TAN vom Beuntzer
     *
     * @param dialogId         Dialog-ID
     * @param tan              eingebene TAN
     * @param legitimationInfo LegitimationInfo
     * @return gibt zurück, ob die eingegeben TAN richtig ist
     */
    public boolean checkTan(String dialogId, String tan, LegitimationInfo legitimationInfo) {
        LOG.info(addTagUser("dialogid:" + dialogId, legitimationInfo));
        String tanIdString = getTanId(dialogId, legitimationInfo);
        if (tanIdString != null) {
            int tanId = parseInt(tanIdString);
            String tanIdName = (tanId > 0) ? "TAN-ID" : "mTAN-ID";
            String dbTan = (tanId > 0) ? getTanById(tanIdString, legitimationInfo) : getTanByIdMobile(tanIdString.replace("-", ""), legitimationInfo);
            if (dbTan != null && AESUtil.aesEncrypt(tan.getBytes()).equals(dbTan)) {
                LOG.info(addTagUser(format("%s: %s korrekt", tanIdName, tanIdString.replace("-", "")), legitimationInfo));
                return true;
            } else {
                LOG.debug(addTagUser(format("(%s/%s)", dbTan, AESUtil.aesEncrypt((tan.getBytes())) + ")"), legitimationInfo));
                LOG.info(addTagUser(format("%s: %s falsch", tanIdName, tanIdString.replace("-", "")), legitimationInfo));
            }
        } else {
            LOG.warn(addTagUser(format("TAN-ID-Referenz zur Dialog-ID (%s) nicht gefunden", dialogId), legitimationInfo));
        }
        return false;
    }

    /**
     * Überprüft die Eingegeben Benutzer-BIN
     *
     * @param userId Kennung
     * @param pin    PIN
     * @return gibt zurück ob die PIN richtig ist
     */
    public boolean checkUserPin(String userId, String pin) {
        Connection connection = null;
        Boolean returnBoolean = false;
        LOG.info("creating db connection for pin check");
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.kennungen.select);
            pS.setString(1, userId);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                String dbPinEncoded = resultSet.getString("pin");
                LOG.debug(addTagUser(format("(%s/%s)", AESUtil.aesEncrypt((pin.getBytes())), dbPinEncoded), userId, ""));
                switch (dbPinEncoded.substring(0, 1)) {
                    case "#":
                        returnBoolean = false;
                        break;
                    case "!":
                        returnBoolean = false;
                        break;
                    case "*":
                        returnBoolean = false;
                        break;
                    default:
                        returnBoolean = AESUtil.aesEncrypt((pin.getBytes())).equals(dbPinEncoded);
                        break;
                }
                if (!returnBoolean) {
                    LOG.info(addTagUser(format("PIN falsch: %s", AESUtil.aesEncrypt((pin.getBytes()))), userId, ""));
                }
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der PIN für Kennung: %s", e.getMessage()), userId, ""), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }

        LOG.info("db check result {}", returnBoolean);
        return returnBoolean;
    }

    /**
     * Holt die aktuelle BPD-Version aus der Datenbank
     *
     * @return die BPD-Version, 0 wenn nicht gefunden
     */
    public int getCurrentBpdVersion() {
        int bpdVersion = 0;
        Map<String, String> bpd = getBpd();
        if (bpd != null) {
            bpdVersion = parseInt(bpd.get("version"));
            LOG.info(format("gefundene BPD-Version: %s", bpdVersion));
        } else {
            LOG.warn("Fehler beim holen der bpd.version: tabelle leer?");
        }
        return bpdVersion;
    }


    /**
     * Entschlüsselt PIN/TAN
     *
     * @param strToDecode PIN/TAN
     * @return Entschlüsselte PIN/TAN
     */
    public String decodeCA(String strToDecode) {
        String decodedPwd = "";
        try {
            int actualPwdIndex = 0;
            for (int i = 0; i <= strToDecode.length() - 3; i += 3) {
                // Get the chunk
                int chunk = Integer.parseInt(strToDecode.substring(i, i + 3));
                chunk -= Integer.parseInt(seed.substring(actualPwdIndex++, actualPwdIndex));
                decodedPwd += (char) chunk;
            }
        } catch (Exception ex) {
            decodedPwd = "";
        }
        return decodedPwd;
    }

    /**
     * Holt alle Konten für den Benutzer aus der Datenbank
     *
     * @param legitimationInfo legitimationInfo
     * @return Resultset|null wenn keine Konten vorhanden
     */
    public List<Map<String, String>> getKonten(LegitimationInfo legitimationInfo) {
        List<Map<String, String>> returnMapList = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.konten.select_by_kundenid);
            pS.setString(1, getCustomerIdByConfig(legitimationInfo));
            ResultSet resultSet = pS.executeQuery();
            returnMapList = toMapList(resultSet);
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim holen der Konten: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnMapList;
    }

    /**
     * Speichert die neue PIN des Benutzers ab
     *
     * @param legitimationInfo LegitimationInfo
     * @param newPin           Neue PIN
     * @return gibt den Erfolg des Speicherns zurück
     */
    public boolean saveNewPin(LegitimationInfo legitimationInfo, String newPin) {
        boolean returnBoolean = false;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.kennungen.update_pin);
            pS.setString(1, AESUtil.aesEncrypt((newPin.getBytes())));
            pS.setString(2, legitimationInfo.getUserId());
            pS.execute();
            LOG.info(addTagUser("PIN erfolgreich geändert", legitimationInfo));
            addLocalMessage(legitimationInfo.getUserId(), "Neue PIN", "Ihre PIN wurde erfolgreich geändert");
            returnBoolean = true;
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Es trat ein Fehler beim Ändern der PIN auf: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnBoolean;
    }

    /**
     * Holt den letzten Kontostand aus konten_bewegungen
     *
     * @param kontoid          Konto-ID
     * @param customerId       Kunden-ID
     * @param legitimationInfo LegitimationInfo
     * @return Kontostand, null wenn nicht vorhanden (z.B. wenn Konto neu)
     */
    public Double getLastKontostandByBewegungen(String kontoid, String customerId, LegitimationInfo legitimationInfo) {
        String gebuchterSaldoString = getFieldByBewegungen(customerId, kontoid, "kontostand", legitimationInfo);
        return (gebuchterSaldoString == null) ? null : parseDouble(gebuchterSaldoString);
    }

    private Date getLastBuchungsdatumByBewegung(String kontoid, String customerId, LegitimationInfo legitimationInfo) {
        String letztesBuchungsdatum = getFieldByBewegungen(customerId, kontoid, "buchungsdatum", legitimationInfo);
        try {
            return (letztesBuchungsdatum == null) ? null : sdf_sql.parse(letztesBuchungsdatum);
        } catch (ParseException e) {
            LOG.warn(addTagUser(format("Fehler beim Parsen des Datums %s: %s", letztesBuchungsdatum, e.getMessage()), legitimationInfo), e);
        }
        return null;
    }

    private Date getLastBuchungsdatumByVorab(String kontoid, LegitimationInfo legitimationInfo) {
        String letztesBuchungsdatum = getFieldByVorab(kontoid, "buchungsdatum", legitimationInfo);
        try {
            return (letztesBuchungsdatum == null) ? null : sdf_sql.parse(letztesBuchungsdatum);
        } catch (ParseException e) {
            LOG.warn(addTagUser(format("Fehler beim Parsen des Datums %s: %s", letztesBuchungsdatum, e.getMessage()), legitimationInfo), e);
        }
        return null;
    }

    private String getFieldByVorab(String kontoid, String field, LegitimationInfo legitimationInfo) {
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pSkb = connection.prepareStatement(sql.vorabumsaetze.select_last);
            pSkb.setString(1, kontoid);
            ResultSet resultSetKb = pSkb.executeQuery();
            if (resultSetKb.next()) {
                return resultSetKb.getString(field);
            }
            resultSetKb.close();
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Abholen der letzten Konto-Bewegung: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return null;
    }

    private String getFieldByBewegungen(String customerId, String kontoid, String field, LegitimationInfo legitimationInfo) {
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pSkb = connection.prepareStatement(sql.konten.bewegungen.select_by_kontoid);
            pSkb.setString(1, kontoid);
            pSkb.setString(2, customerId);
            ResultSet resultSetKb = pSkb.executeQuery();
            if (resultSetKb.next()) {
                return resultSetKb.getString(field);
            }
            resultSetKb.close();
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Abholen der letzten Konto-Bewegung: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return null;
    }

    /**
     * Holt eine Kontenzeile passend zur Kontonummer und Kunden-ID
     *
     * @param kontonummer      Kontonummer
     * @param legitimationInfo LegitimationInfo
     * @return SQL-Resultset, null wenn es die Kombination nicht gibt
     */
    public Map<String, String> getKontoRow(String kontonummer, LegitimationInfo legitimationInfo) {
        String customerId = getCustomerIdByConfig(legitimationInfo);
        Map<String, String> returnMap = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.konten.select);
            pS.setString(1, kontonummer);
            pS.setString(2, customerId);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                returnMap = toMap(resultSet);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Abholen der Kontoinformationen: Kontonummer: %s, Meldung: %s", kontonummer, e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnMap;

    }


    /**
	 * @desc function handling the fetch of the sum of the prebooked revenues
     * @param kontonummer
     * @return
     */
    public double getPreBookedRevenuesSum(String kontonummer) {
        double sumPreBooked = 0;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.vorabumsaetze.sum_prebooked);
            pS.setString(1, kontonummer);
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                sumPreBooked = resultSet.getDouble(1);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn("failed to fetch the pre booked sum of revenues for account: {}", kontonummer, e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return sumPreBooked;
    }

    /**
     * Holt die SQL-Zeile aus der Datenbank
     *
     * @return falls null, fehlt die Zeile in der Datenbank
     */
    public Map<String, String> getBpd() {
        Map<String, String> returnMap = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement ps = connection.prepareStatement(sql.bpd.select);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                returnMap = toMap(resultSet);
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException sqle) {
            LOG.warn(format("Fehler beim holen der Bankparameter: %s", sqle.getMessage()), sqle);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnMap;
    }

    /**
     * Holt alle Geschäftsvorfälle aus der Datenbank
     *
     * @param bpdVersion  BPD-Version
     * @param hbciversion HBCI-Version
     * @return SQL-Result der Geschäftsvorfälle
     */
    public List<Map<String, String>> getGeschaeftsvorfaelle(String bpdVersion, String hbciversion) {
        List<Map<String, String>> returnMapList = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.geschaeftsvorfaelle.select_by_version_bpd);
            pS.setString(1, bpdVersion);
            pS.setString(2, hbciversion);
            ResultSet resultSet = pS.executeQuery();
            returnMapList = toMapList(resultSet);
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(format("Fehler beim Holen der GlobalParameter (HBCI: %s, BPD: %s): %s", hbciversion, bpdVersion, e.getMessage()), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnMapList;
    }


    /**
     * gibt zurück ob iTAN per SMS für den Benutzer verfügbar ist
     *
     * @param legitimationInfo Kennung
     * @return falls smsSystemId und Handynummer hinterlegt ist, true
     */
    public boolean iTanSmsIsAvaible(LegitimationInfo legitimationInfo) {
        return (parseInt(config(properties.smsSystemId, "0")) > 0 && getHandyNummer(legitimationInfo) != null);
    }

    /**
     * Prüft mTAN für den Benutzer verfügbar ist
     *
     * @param legitimationInfo LegitimationInfo
     * @return falls alles richtig eingerichtet ist (Handynummer, smsSystemId & tanliste_mobil-Tabelle), gibt es hier ein true zurück
     */
    public boolean mTanIsAvaible(LegitimationInfo legitimationInfo) {
        boolean returnBoolean = false;
        if (iTanSmsIsAvaible(legitimationInfo)) {
            Connection connection = null;
            try {
                connection = getConnection();
                PreparedStatement pS = connection.prepareStatement(sql.tanliste.mobil.select);
                pS.setString(1, "0");
                pS.execute();
                returnBoolean = true;
            } catch (SQLException e) {
                LOG.info(addTagUser("mtan nicht verfügbar", legitimationInfo));
            } finally {
                new DbHelper().closeQuietly(connection);
            }
        }
        return returnBoolean;
    }

    /**
     * Holt die Handynummer aus der Login-Tabelle
     * ref_benutzerkennung muss existieren und hinterlegt sein in der logins-Tabelle
     *
     * @param legitimationInfo LegitimationInfo
     * @return gibt null zurück, falls die Handynummer leer ist oder nicht existiert
     */
    private String getHandyNummer(LegitimationInfo legitimationInfo) {
        String returnString = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.login.select_by_kennung);
            pS.setString(1, legitimationInfo.getUserId());
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                if (!resultSet.getString("handy").equals("")) {
                    returnString = resultSet.getString("handy");
                } else {
                    LOG.info(addTagUser("handynummer leer", legitimationInfo));
                }
            } else {
                LOG.info(addTagUser("Handynummer-Suche: 'handy' für ref_benutzerkennung nicht gefunden", legitimationInfo));
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Es ist ein Fehler beim holen der Handynummer aufgetreten: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    /**
     * Selectiert die nächste iTAN und gibt das als Result zurück
     * Falls die tanPerSMS true, wird diese auch gleich als versendet markiert
     *
     * @param legitimationInfo legitimationInfo
     * @param tanPerSMS        wird die TAN per SMS verschickt?
     * @return Selectierte TAN als SQL-Resultset
     */
    public Map<String, String> iTanGenerate(LegitimationInfo legitimationInfo, boolean tanPerSMS) {
        String userId = legitimationInfo.getUserId();
        Map<String, String> tlActiveRs = getActiveTanListe(legitimationInfo);
        if (tlActiveRs == null) {
            LOG.info(addTagUser("keine aktive TAN-Liste gefunden für Kennung", legitimationInfo));
            tlActiveRs = activateAndGetNextTanListe(legitimationInfo);
            if (tlActiveRs != null && tlActiveRs.containsKey("tanlistenid")) {
                addLocalMessage(userId, "Folge-TAN-Liste aktiviert", "Es wurde Ihre neue TAN-Liste mit der Nummer " + tlActiveRs.get("tanlistenid") + " aktiviert");
                LOG.info(addTagUser("nächste TAN-Liste erfolgreich aktiviert", legitimationInfo));
            } else {
                LOG.info(addTagUser("keine neue TAN-Liste gefunden für Kennung", legitimationInfo));
            }
        }
        if (tlActiveRs != null && tlActiveRs.containsKey("tanlistenid")) {
            LOG.info(addTagUser("aktive TAN-Liste gefunden für Kennung", legitimationInfo));
            String tanlistennr = tlActiveRs.get("tanlistenid");
            int cursor = 0;
            List<Map<String, String>> tanliste_rS = getNextFreeTanFromlist(tanlistennr, legitimationInfo);
            if (tanliste_rS.size() > 1) {
                cursor = random.nextInt(tanliste_rS.size() - 1);
                LOG.info(addTagUser("gehe zur Zufall:" + cursor, legitimationInfo));
            }
            if (tanPerSMS) {
                markTanSMS(tanliste_rS.get(cursor).get("tanid"), legitimationInfo);
            }
            return tanliste_rS.get(cursor);
        } else {
            return null;
        }
    }

    public String replaceNonSepaChars(String string) {
        return derfNullSein(string)
                .replaceAll("\u00fc", "u")    //	Alle kleinen ü
                .replaceAll("\u00f6", "o")    //	Alle kleinen ü
                .replaceAll("\u00e4", "a")    //	Alle kleinen ä
                .replaceAll("\u00df", "s")    //	Alle ß
                .replaceAll("\u00dc", "U")    //	Alle großen Ü
                .replaceAll("\u00d6", "O")    //	Alle großen Ö
                .replaceAll("\u00c4", "A")    //	Alle großen Ä
                .replace("/[^a-zA-Z0-9\\. \\/\\+\\-\\?:\\(\\)$,]/", ".");
    }

    /**
     * Markiert eine iTAN als versendet per SMS
     *
     * @param tanid            iTAN-ID
     * @param legitimationInfo LegitimationInfo
     * @return gibt erfolg zurück
     */
    private boolean markTanSMS(String tanid, LegitimationInfo legitimationInfo) {
        boolean returnBoolean = false;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.tanliste.update_sms);
            pS.setString(1, String.valueOf(unixTimeStamp()));
            pS.setString(2, tanid);
            pS.execute();
            returnBoolean = true;
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim markieren der TAN per SMS: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnBoolean;
    }

    /**
     * Generiert eine mTAN
     *
     * @param legitimationInfo LegitimationInfo
     * @return mTAN-ID
     */
    public Long mTanGenerate(LegitimationInfo legitimationInfo) {
        Long returnLong = null;
        Connection connection = null;
        int len = parseInt(config(properties.tanLen, "6"));
        try {

            // FIXME: set fixed mTan
            //String mTan = generateTan(len);
            String mTan = "123456";

            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.tanliste.mobil.insert, Statement.RETURN_GENERATED_KEYS);
            pS.setString(1, AESUtil.aesEncrypt((mTan.getBytes())));
            pS.setString(2, legitimationInfo.getUserId());
            pS.setString(3, String.valueOf(unixTimeStamp()));
            pS.execute();
            ResultSet resultSet = pS.getGeneratedKeys();
            if (resultSet.next()) {
                // FIXME: sending sms disabled
                return resultSet.getLong(1);
//                sms.status smsStatus = sendTan(legitimationInfo, "Ihre mTAN für Ihren HBCI-Auftrag lautet wie folgt:" + mTan);
//                switch (smsStatus) {
//                    case okay:
//                    case queued:
//                        LOG.info(addTagUser("mtanid:" + resultSet.getLong(1), legitimationInfo));
//                        returnLong = resultSet.getLong(1);
//                        break;
//                    case fail:
//                        LOG.info(addTagUser("mTan konnte nicht gesendet werden", legitimationInfo));
//                        break;
//                }
            } else {
                LOG.warn(addTagUser("Fehler beim anlegen einer Mobile-TAN", legitimationInfo), new Throwable());
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim generieren der mTAN: %s", e.getMessage()), legitimationInfo), e);
        } catch (Exception e) {
            LOG.warn(addTagUser(format("Fehler beim versenden der SMS: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnLong;
    }

    /**
     * Holt den Bankname aus der bpd-Tabelle
     *
     * @return Bankname
     */
    private String getBankName() {
        Map<String, String> bpd = getBpd();
        return (bpd != null) ? bpd.get("bankname") : "";
    }

    /**
     * Sendet eine Nachrichtan den benutzer
     *
     * @param legitimationInfo LegitimationInfo
     * @param message          Nachrichten-Text
     * @return gibt zurück, ob die SMS erfolgreich versendet wurde
     */
    public sms.status sendTan(LegitimationInfo legitimationInfo, String message) {
        String smsSystemId = config(properties.smsSystemId, "0");
        String configGatewayUrl = config(properties.smsGatewayUrl, "http://yoursmsgateway.url/yousmsgateway.php");
        String handynummer = getHandyNummer(legitimationInfo);
        String absender = abbreviate(capitalize(getBankName()).replace(" ", ""), 11);

        if (parseInt(smsSystemId) > 0 && handynummer != null) {
            sms gateway = new sms(smsSystemId, configGatewayUrl);
            gateway.setConfigSubSystemId("_" + legitimationInfo);
            try {
                return gateway.doSendSMS(handynummer, message, absender);
            } catch (Exception e) {
                LOG.warn(addTagUser("Fehler beim Versenden der SMS", legitimationInfo), e);
            }
        } else if (!(parseInt(smsSystemId) > 0)) {
            LOG.warn(addTagUser("mTanSystemId nicht gesetzt", legitimationInfo));
        } else {
            LOG.info(addTagUser("Handynummer leer?", legitimationInfo));
        }
        return sms.status.fail;
    }

    /**
     * Generiert eine PIN/TAN mit der geforderten Länge
     *
     * @param len Länge der PIN/TAN
     * @return PIN/TAN mit gewünschter Länge
     */
    private String generateTan(int len) {
        int min = parseInt(rightPad("8", len, "9"));
        int add = parseInt(rightPad("1", len, "0"));
        return String.valueOf(random.nextInt(min) + add);
    }

    /**
     * generiert einen Unix-Timestamp
     *
     * @return php like time()
     */
    private long unixTimeStamp() {
        return System.currentTimeMillis() / 1000;
    }

    private long unixTimeStamp(Date signDate) {
        long millis = signDate.getTime();
        if (millis > 0) {
            return millis / 1000;
        }
        return 0;
    }

    /**
     * Speichert den Schlüssel von einem Benutzer/Bank
     *
     * @param userId         Kennung
     * @param publicKey      Schlüssel als Base64
     * @param art            Schlüsselart
     * @param version        Schlüsselnummer
     * @param nummer         Schlüsselversion (RDH10/9)
     * @param modulo         Modulus
     * @param exponent       Exponent
     * @param freigeschaltet ist der Schlüssel gleich freigeschalten? (sollte nur beim Bankschlüssel sein)
     * @return gibt den Erfolg des speicherns zurück
     */
    public boolean saveKey(String userId, String publicKey, int art, int version, int nummer, String modulo, String exponent, boolean freigeschaltet) {
        boolean returnBoolean = false;
        Connection connection = null;
        try {
            LOG.info(addTagUser("key.base64:" + publicKey, userId, ""));
            connection = getConnection();
            Blob blob = connection.createBlob();
            blob.setBytes(1, Base64.decode(publicKey));
            PreparedStatement pS = connection.prepareStatement(sql.publickeys3.insert);
            pS.setString(1, userId);
            pS.setBlob(2, blob);
            pS.setString(3, String.valueOf(art));
            pS.setString(4, String.valueOf(nummer));
            pS.setString(5, String.valueOf(version));
            pS.setString(6, (freigeschaltet ? "1" : "0"));    //	freigeschaltet
            pS.setString(7, modulo);
            pS.setString(8, exponent);
            pS.execute();
            returnBoolean = true;
            LOG.info(addTagUser("Schlüssel erfolgreich in Datenbank geschrieben für Kennung", userId, ""));
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Speichern des PublicKeys Kennung/Art/Version/Nummer: %s/%s/%s", art, version, nummer), userId, ""), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnBoolean;
    }

    /**
     * Prüft ob ein Benutzer/Bank-Schlüssel existiert, freigeschalten spiel keine rolle
     *
     * @param userId            Kennung
     * @param schluesselArt     Schlüsselart
     * @param schluesselNummer  Schlüsselnummer (RDH10/9)
     * @param schluesselVersion Schlüsselversion
     * @return gibt zurück ob der angefragte Schlüssel hinterlegt ist
     */
    public boolean isKey(String userId, int schluesselArt, int schluesselNummer, int schluesselVersion) {
        boolean returnBoolean = false;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.publickeys3.select_wo);
            pS.setString(1, userId);
            pS.setString(2, String.valueOf(schluesselArt));
            pS.setString(3, String.valueOf(schluesselNummer));
            pS.setString(4, String.valueOf(schluesselVersion));
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                LOG.info(addTagUser("Schlüssel (Anonym) gefunden für Kennung", userId, ""));
                returnBoolean = true;
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Allgemeiner Fehler beim Holen eines PublicKeys: %s", e.getMessage()), userId, ""), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnBoolean;
    }

    /**
     * Holt den Benutzer/Bank-Schlüssel aus der Datenbank
     *
     * @param userId            kennung
     * @param schluesselArt     Schlüsselart
     * @param schluesselNummer  Schlüsselnummer (RDH10/RDH9 etc)
     * @param schluesselVersion Schlüsselversion
     * @param freigeschaltet    Muss der Schlüssel freigeschaltet sein?
     * @return Schlüssel als Base64
     */
    public String getKey(String userId, int schluesselArt, int schluesselNummer, int schluesselVersion, boolean freigeschaltet) {
        String returnString = null;
        Connection connection = null;
        try {
            String isfree = (freigeschaltet) ? "1" : "0";
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement((schluesselVersion < 0) ? sql.publickeys3.select_wo_version : sql.publickeys3.select);
            pS.setString(1, userId);
            pS.setString(2, String.valueOf(schluesselArt));
            if (schluesselVersion > 0) {
                pS.setString(3, String.valueOf(schluesselNummer));
                pS.setString(4, String.valueOf(schluesselVersion));
                pS.setString(5, isfree);
            } else {
                pS.setString(3, String.valueOf(schluesselNummer));
                pS.setString(4, isfree);
            }
            ResultSet resultSet = pS.executeQuery();
            if (resultSet.next()) {
                Blob pubkey = resultSet.getBlob("pubkey");
                byte[] returnBytes = pubkey.getBytes(1, (int) pubkey.length());
                LOG.info(addTagUser(format("key.base64: %s", new String(Base64.encode(returnBytes))), userId, ""));
                returnString = new String(Base64.encode(returnBytes));
                LOG.info(addTagUser("Schlüssel gefunden für Kennung", userId, ""));
            } else {
                LOG.info(addTagUser(format("Angefragten Schlüssel nicht gefunden für Art/Version/Nummer: %s/%s/%s", schluesselArt, schluesselVersion, schluesselNummer), userId, ""));
            }
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Allgemeiner Fehler beim Holen eines PublicKeys: %s", e.getMessage()), userId, ""), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnString;
    }

    /**
     * Holt alle Bank-Nachrichten aus der Datenbank
     *
     * @param legitimationInfo LegitimationInfo
     * @return Liste aller Bank-Nachrichten
     */
    public List<Map<String, String>> getBankMessages(LegitimationInfo legitimationInfo) {
        List<Map<String, String>> returnMapList = null;
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.messages.select_unread);
            pS.setString(1, legitimationInfo.getUserId());
            ResultSet resultSet = pS.executeQuery();
            returnMapList = toMapList(resultSet);
            new DbHelper().closeQuietly(resultSet);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Unerwarteter Fehler bei holen der ungelesenen Nachrichten, existiert Spalte BenutzeruserId in messages_read? %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnMapList;
    }

    /**
     * Setzt alle Nachrichten für den Benutzer als Gelesen
     *
     * @param messageIds       Liste der Nachrichten-IDs
     * @param legitimationInfo LegitimationInfo
     * @return gibt erfolg zurück
     */
    public boolean setBankMessagesRead(List<String> messageIds, LegitimationInfo legitimationInfo) {
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.messages.read.insert);
            for (String messageId : messageIds) {
                pS.setString(1, messageId);
                pS.setString(2, legitimationInfo.getUserId());
                pS.execute();
            }
            pS.close();
            return true;
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim gelesen-markieren von Nachrichten: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return false;
    }

    /**
     * Setzt alle Lokalen Nachrichten für den Benutzer zurück
     *
     * @param userId Kennung
     */
    public void resetLocalMessageList(String userId) {
        localMessageList.remove(userId);
        localUserReference.remove(userId);
    }

    /**
     * Fügt eine Nachricht für den Benutzer hinzu
     *
     * @param userId            Kennung
     * @param bankMessageObject Nachricht
     */
    private void addLocalMessage(String userId, BankMessageObject bankMessageObject) {
        localMessageList.putIfAbsent(userId, new ArrayList<>());
        localMessageList.get(userId).add(bankMessageObject);
    }

    public boolean isMailAvaible() {
        if (mailAvaible == null) {
            configLog(properties.mailEmpfaenger);
            configLog(properties.mailSubject);
            configLog(properties.mailSmtpHost);
            configLog(properties.mailSmtpUserName);
            configLog(properties.mailSmtpPassword);
            mailAvaible = (
                    !config(properties.mailEmpfaenger, "").isEmpty() &&
                            !config(properties.mailSubject, "").isEmpty() &&
                            !config(properties.mailSmtpHost, "").isEmpty() &&
                            !config(properties.mailSmtpUserName, "").isEmpty() &&
                            !config(properties.mailSmtpPassword, "").isEmpty()
            );
        }
        return mailAvaible;
    }

    private int parseIntNull(String intvalue, int defaultIntValue) {
        if (intvalue == null || intvalue.equals("")) return defaultIntValue;
        return parseInt(intvalue);
    }

    private int parseIntNull(String intvalue) {
        return parseIntNull(intvalue, 0);
    }

    public int getLatestUpd(LegitimationInfo legitimationInfo) {
        int returnInt = 1;
        int lastnn = parseIntNull(getFieldByUserId("lastnn", legitimationInfo));
        LOG.debug(addTagUser(format("upd:lastnn: %s", lastnn), legitimationInfo));
        Connection connection = null;
        int lastupdversion = parseIntNull(getFieldByCustomerId("updversion", legitimationInfo));
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.login.konten.permission.calc_checksum);
            pS.setString(1, legitimationInfo.getUserId());
            ResultSet resultSet = pS.executeQuery();
            int newLastNN = 1;

            if (resultSet.next()) {
                newLastNN = parseIntNull(resultSet.getString("checksum"));
            }
            LOG.debug(addTagUser(format("upd:calcNN: %s", newLastNN), legitimationInfo));
            if (lastnn != newLastNN || lastupdversion == 0) {
                LOG.info(addTagUser(format("UPD(%s)-Update erforderlich: %s > %s", lastupdversion, lastnn, newLastNN), legitimationInfo));
                PreparedStatement pSu = connection.prepareStatement(sql.kennungen.update_lastnn_checksum);
                pSu.setString(1, String.valueOf(newLastNN));
                pSu.setString(2, legitimationInfo.getUserId());
                pSu.execute();
                PreparedStatement pSc = connection.prepareStatement(sql.kunden.update_upd);
                pSc.setString(1, String.valueOf(lastupdversion + 1));
                pSc.setString(2, getCustomerIdByConfig(legitimationInfo));
                pSc.execute();
            }
            returnInt = parseIntNull(getFieldByCustomerId("updversion", legitimationInfo));
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim generieren der Latest UPD: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return returnInt;
    }

    public boolean isUpdVersionCurrent(LegitimationInfo legitimationInfo, int updVersion) {
        LOG.debug(addTagUser(format("kundenid: %s", getCustomerIdByConfig(legitimationInfo)), legitimationInfo));
        LOG.debug(addTagUser(format("angefragte UPD-Version: %s", updVersion), legitimationInfo));
        if (legitimationInfo.getCustomerId().equals("9999999999")) {
            return false;
        }
        int latestUpd = parseIntNull(getFieldByCustomerId("updversion", legitimationInfo), 1);
        return (latestUpd == updVersion);
    }

    public void addLocalMessage(String userId, String subject, String message) {
        addLocalMessage(userId, new BankMessageObject(subject, message));
    }

    /**
     * Holt alle Nachrichten für die Kennung
     *
     * @param userId Kennung
     * @return List aller Lokaler Nachrichten
     */
    public List<BankMessageObject> getLocalMessageList(String userId) {
        if (localMessageList.get(userId) == null) {
            return new ArrayList<>();
        }
        return localMessageList.get(userId);
    }

    @SuppressWarnings("UnusedParameters")
    public LinkedList<Document> getTerminzahlungen(LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, boolean isSammler, Payments.versions version) {
        LinkedList<Document> documents = new LinkedList<>();
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(((isSammler) ? sql.dauerauftraege.select.termin_sammel : sql.dauerauftraege.select.termin_single));
            pS.setString(1, legitimationInfo.getUserId());
            ResultSet resultSet = pS.executeQuery();
            LinkedList<Payments.PaymentEntry> paymentEntries = new LinkedList<>();
            Payments.AuftragsId auftragsIdLast = new Payments.AuftragsId("");
            String bic = config(properties.bankBic, "").trim().replace(" ", "");
            String blz = config(properties.bankCode, "").trim().replace(" ", "");
            while (resultSet.next()) {
                Payments.AuftragsId auftragsIdNew = Payments.AuftragsId.generate(isSammler, legitimationInfo.getUserId(), resultSet.getString("auftragsid"), resultSet.getString("sammler"));
                if (!auftragsIdNew.equals(auftragsIdLast) && paymentEntries.size() > 0) {
                    documents.add(Payments.paymentsToSepa(paymentEntries, auftragsIdLast.toString(), (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")).format(new Date()) + auftragsIdLast, version));
                    paymentEntries = new LinkedList<>();
                }
                paymentEntries.add(Payments.PaymentEntry.fromDauerautrag(resultSet, bic, blz));
                auftragsIdLast = auftragsIdNew;
            }
            if (paymentEntries.size() > 0) {
                documents.add(Payments.paymentsToSepa(paymentEntries, auftragsIdLast.toString(), (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")).format(new Date()) + auftragsIdLast, version));
            }
            new DbHelper().closeQuietly(pS);
            new DbHelper().closeQuietly(resultSet);
            LOG.info(addTagUser(format("isSammler: %s", isSammler), legitimationInfo));
            LOG.info(addTagUser(format("Terminzahlungen gefunden: %s", documents.size()), legitimationInfo));
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Holen der Daueraufträge: %s", e.getMessage()), legitimationInfo), e);
            documents = null;
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return documents;
    }

    @SuppressWarnings("UnusedParameters")
    public Payments.Result deleteTerminzahlung(LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, String auftragsidentifikation, boolean isSammler) {
        Connection connection = null;
        Payments.Result payRes = null;
        try {
            connection = getConnection();
            String start = (isSammler) ? Payments.AuftragsId.markSammler : Payments.AuftragsId.markEinzel;
            String[] parts = auftragsidentifikation.split(Payments.AuftragsId.separator);
            if (parts.length != 3 || !parts[0].equals(start) || !parts[1].equals(legitimationInfo.getUserId())) {
                return new Payments.Result(false);
            }
            String auftragsidOrSammler = parts[2];
            PreparedStatement pS = connection.prepareStatement((isSammler) ? sql.dauerauftraege.select.sammel : sql.dauerauftraege.select.single);
            pS.setString(1, legitimationInfo.getUserId());
            pS.setString(2, auftragsidOrSammler);
            ResultSet rs = pS.executeQuery();
            if (rs.next()) {
                String sqlDelete = (isSammler) ? sql.dauerauftraege.delete_sammel : sql.dauerauftraege.delete_single;
                PreparedStatement pSdelete = connection.prepareStatement(sqlDelete);
                pSdelete.setString(1, legitimationInfo.getUserId());
                pSdelete.setString(2, auftragsidOrSammler);
                pSdelete.execute();
                new DbHelper().closeQuietly(pS);
                new DbHelper().closeQuietly(rs);
                payRes = new Payments.Result(true);
            } else {
                payRes = new Payments.Result(false);
            }
        } catch (SQLException e) {
            payRes = new Payments.Result(false).setError(e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return payRes;
    }

    private ReturnDataObject checkBic(String bic, tranactionTypes transactionType, LegitimationInfo legitimationInfo) {
        Connection connection = null;
        ReturnDataObject r = new ReturnDataObject(true, "");
        if (bic.length() != 8 && bic.length() != 11) {
            return new ReturnDataObject(false, format("BIC (%s) hat eine ungültige Länge", bic), "9930");
        }
        String bic_8 = (bic.length() == 8) ? bic : StringUtils.mid(bic, 0, 8);
        String bic_11 = (bic.length() == 11) ? bic : null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.sepa.select);
            String allowed = null;
            if (bic_11 != null) {
                pS.setString(1, bic_11);
                ResultSet rs_11 = pS.executeQuery();
                if (rs_11.next()) {
                    allowed = rs_11.getString(transactionType.toString());
                }
                new DbHelper().closeQuietly(rs_11);
            }
            if (allowed == null) {
                pS.setString(1, bic_8);
                ResultSet rs_8 = pS.executeQuery();
                if (rs_8.next()) {
                    allowed = rs_8.getString(transactionType.toString());
                }
                new DbHelper().closeQuietly(rs_8);
            }
            if (allowed == null) {
                r = new ReturnDataObject(false, format("BIC (%s) wird nicht unterstützt", bic), "9930");
            } else if (!allowed.equals("1")) {
                r = new ReturnDataObject(false, format("BIC (%s) unterstützt nicht: %s", bic, transactionType.toString().toUpperCase()), "9930");
            }
            new DbHelper().closeQuietly(pS);
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Prüfen der BIC (%s): %s", bic, e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return r;
    }

    public ReturnDataObject checkPurposeCode(String purposeCode, LegitimationInfo legitimationInfo) {
        ReturnDataObject r = new ReturnDataObject(true, "");
        if (Objects.equals(purposeCode, "")) {
            return r;
        }
        if (purposeCode.length() != 4) {
            return new ReturnDataObject(false, format("PurposeCode '%s' ist inhaltlich ungültig", purposeCode), "9930");
        }
        Connection connection = null;
        try {
            connection = getConnection();
            PreparedStatement pS = connection.prepareStatement(sql.purposecodes.select);
            pS.setString(1, purposeCode);
            ResultSet rs = pS.executeQuery();
            Boolean allowed = rs.next();
            new DbHelper().closeQuietly(rs);
            if (!allowed) {
                r = new ReturnDataObject(false, format("PurposeCode '%s' ist nicht zugelassen", purposeCode), "9930");
            }
        } catch (SQLException e) {
            LOG.warn(addTagUser(format("Fehler beim Prüfen der BIC: %s", e.getMessage()), legitimationInfo), e);
        } finally {
            new DbHelper().closeQuietly(connection);
        }
        return r;
    }


    public static String addTagUser(String message, LegitimationInfo legitimationInfo) {
        return legitimationInfo != null ? addTagUser(message, legitimationInfo.getUserId(), legitimationInfo.getCustomerId()) : message;
    }

    public static String addTagUser(String message, String userId, String customerId) {
        return format("[%s:%s] %s", userId, customerId, message);
    }

    /**
     * Entspricht den Spalten-Namen in der SEPA-Tabelle
     */
    public enum tranactionTypes {
        sct, sdd, b2b
    }

    private static final class sql {
        private static final class bpd {
            private static final String select = "SELECT version AS version, blz AS blz, bankname AS bankname, anzgesch AS anzgesch, lang AS lang, hbciversion AS hbciversion,maxsize AS maxsize FROM bpd";
        }

        private static final class buchungstypen {
            private static final String select = "SELECT buchungstypid AS buchungstypid FROM buchungstypen WHERE buchungstypid=?";
        }

        private static final class dtaus {
            private static final String insert = "INSERT INTO dtaus (empfaenger,empfaengerkonto,empfaengerblz,betrag,verwendungszweck,auftraggeber,auftraggeberkonto,kunde,login,tan,bearbeitet,buchungstypid,datum,uhrzeit,counter,textschluesselergaenzung,iban,bic,ls_art,ls_sequenz,ls_duedate,mandatsreferenz,date_sign,glaeubigerid,end_to_end,purposecode) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            private static final String select_counter = "SELECT ifnull(max(counter),0) AS counter FROM dtaus WHERE login=?";

        }

        private static final class dauerauftraege {
            private static final String insert = "INSERT INTO dauerauftraege (empfaenger, empfaengerkonto, empfaengerblz, betrag, verwendungszweck, auftraggeber, auftraggeberkonto, kunde, benutzerkennung, bearbeitet, buchungstypid, datum, uhrzeit, ausfuehrungsdatum, turnus, lastaction, sammler, kontoid, loginid, tan, iban, bic, end_to_end, purposecode) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            private static final String delete_single = "DELETE FROM dauerauftraege WHERE benutzerkennung=? AND auftragsid=?";
            private static final String delete_sammel = "DELETE FROM dauerauftraege WHERE benutzerkennung=? AND sammler=?";

            private static class select {
                private static final String counter = "SELECT ifnull(max(sammler),0) as counter FROM dauerauftraege";
                private static final String termin_single = "SELECT * FROM dauerauftraege WHERE benutzerkennung=? AND turnus='6' AND ifnull(sammler,0) = 0 ORDER BY sammler";
                private static final String termin_sammel = "SELECT * FROM dauerauftraege WHERE benutzerkennung=? AND turnus='6' AND ifnull(sammler,0) > 0 ORDER BY auftragsid";
                private static final String single = "SELECT * FROM dauerauftraege WHERE benutzerkennung=? AND auftragsid=?";
                private static final String sammel = "SELECT * FROM dauerauftraege WHERE benutzerkennung=? AND sammler=?";
            }

        }

        private static final class geschaeftsvorfaelle {
            private static final String select_by_version_bpd = "SELECT bezeichnung AS segmentname,segmentversion AS segmentversion,maxanzahl AS maximaleanzahlauftraege,param1 AS anzahlsignaturenmindestens,param5 AS sicherheitsklasse,param3 AS fints_3_parameter,param4 AS fints_4_parameter FROM geschaeftsvorfaelle WHERE bpdversion=? AND hbciversion=?";
            private static final String insert = "INSERT INTO geschaeftsvorfaelle (bezeichnung,segmentversion,maxanzahl,param1,param5,param3,param4,bpdversion,hbciversion) VALUES (?,?,?,?,?,?,?,?,?)";
            private static final String delete = "DELETE FROM geschaeftsvorfaelle WHERE bpdversion=? AND hbciversion=?";

        }

        private static final class tempclientstuff3 {
            private static final String select = "SELECT dialogid AS dialogid,kundensystemid AS kundensystemid,tanid as tanid, kontrollref as kontrollref FROM tempclientstuff3 WHERE benutzerkennung=? AND kundenid=? AND produkt=? AND produktversion=?";
            private static final String select_kid = "SELECT dialogid AS dialogid, tanid as tanid, kontrollref as kontrollref FROM tempclientstuff3 WHERE benutzerkennung=? AND kundenid=? AND produkt=? AND produktversion=? and kundensystemid=?";
            private static final String select_tanid_by_dialogid = "SELECT tanid AS tanid FROM tempclientstuff3 WHERE dialogid=?";
            private static final String insert = "INSERT tempclientstuff3 (benutzerkennung,kundenid,produkt,produktversion,kundensystemid,dialogid) VALUES (?,?,?,?,?,?)";
            private static final String update_dialogid = "UPDATE tempclientstuff3 SET dialogid=? WHERE benutzerkennung=? AND kundenid=? AND produkt=? AND produktversion=?";
            private static final String update_srvref_by_dialogId = "UPDATE tempclientstuff3 SET kontrollref=? WHERE dialogid=?";
            private static final String update_tanid_by_kid = "UPDATE tempclientstuff3 SET tanid=?, dialogid=? WHERE benutzerkennung=? and kundenid=? and produkt=? and produktversion=? and kundensystemid=?";
            private static final String col_tanid = "tanid";
        }

        private static final class kennungen {
            private static final String select = "SELECT pin AS pin,lastnn AS lastnn FROM benutzerkennungen WHERE benutzerkennung=?";
            private static final String update_pin = "UPDATE benutzerkennungen SET pin=? WHERE benutzerkennung=?";
            private static final String update_lastnn_checksum = "UPDATE benutzerkennungen SET lastnn=? WHERE benutzerkennung=?";
        }

        private static final class konten {
            private static final String select = "SELECT k.kontoid AS kontoid, k.kontoinhaber AS kontoinhaber, k.kreditlinie_aktiv AS kl_aktiv,k.kreditlinie AS vb, k.kontostand AS kontostand,k.virtkontostand AS virtkontostand, k.ueberziehen AS ueberziehen,k.kontoart AS kontoart,k.tageslimit AS tageslimit,k.tagesumsatz AS tagesumsatz,k.konto_pre AS pre_konto, k.blz_pre AS pre_blz, k.iban_pre as pre_iban, k.empfaenger_pre AS pre_name FROM konten k,kunden_konten kk WHERE kk.kontoid=k.kontoid AND k.kontonummer=? AND kk.kundenid=?";
            private static final String select_by_kundenid = "SELECT k.kontoid AS kontoid, k.kontonummer AS kontonummer, k.kontoinhaber AS kontoinhaber,k.kontoart AS kontoart, k.tageslimit AS tageslimit, k.tagesumsatz AS tagesumsatz FROM kunden_konten kk, konten k WHERE kk.kundenid=? AND kk.kontoid=k.kontoid";    //s
            private static final String update_saldo = "UPDATE konten SET tagesumsatz=tagesumsatz+?, virtkontostand=virtkontostand+? WHERE kontonummer=?";

            private static final class bewegungen {
                private static final String select_by_kontoid = "SELECT kb.kontostand AS kontostand, buchungsdatum as buchungsdatum FROM konten_bewegungen kb, kunden_konten kk WHERE kk.kontoid=kb.kontoid AND kk.kontoid=? AND kk.kundenid=? ORDER BY bewegungsid DESC LIMIT 1";
            }

        }

        private static final class kunden {
            private static final String select = "SELECT name AS name, updversion AS updversion, stammnummer AS stammnummer,sachbearbeiter AS sachbearbeiter FROM kunden WHERE kundenid=?";
            private static final String update_upd = "UPDATE kunden SET updversion=? WHERE kundenid=?";

            private static final class kennungen {
                private static final String select = "SELECT kundenid AS kundenid FROM kunden_benutzerkennungen WHERE benutzerkennung=?";        //s
            }
        }

        private static final class login {
            private static final String select_by_kennung = "SELECT handy AS handy FROM logins WHERE ref_benutzerkennung=?";

            private static final class permission {
                private static final String select = "SELECT tageslimit AS tageslimit, tagesumsatz AS tagesumsatz,lastschrift AS lastschrift, ueberweisung AS ueberweisung FROM login_permission WHERE benutzerkennung=?";
                private static final String update_by_kennung = "UPDATE login_permission SET tagesumsatz=tagesumsatz+? WHERE benutzerkennung=?";
            }

            private static final class konten {
                private static final class permission {
                    private static final String select = "SELECT lkp.ueberweisung AS ueberweisung, lkp.sdd AS sdd, lkp.ade AS ade, lkp.azv AS azv, lkp.lastschrift as lastschrift FROM login_konten_permission lkp, konten k WHERE k.kontoid=lkp.kontoid AND lkp.benutzerkennung=? AND k.kontonummer=?";    //s
                    private static final String calc_checksum = "SELECT sum(ueberweisung+(lastschrift*10)+(abbuchung*100)+(dtaus*1000)+(sdd*10000)) AS checksum FROM login_konten_permission WHERE benutzerkennung=?";
                }
            }
        }

        private static final class messages {
            private static final String select_unread = "SELECT messageid AS messageid,subject AS subject,text AS message FROM messages WHERE messageid NOT IN (SELECT messageid FROM messages_read WHERE benutzerkennung=?)";

            private static final class read {
                private static final String insert = "INSERT INTO messages_read (messageid,loginid,benutzerkennung) VALUES (?,0,?)";
            }
        }

        private static final class mt940 {
            private static final String select_by_id_date = "SELECT mt940 AS mt940, mt940id AS id FROM mt940 WHERE kontoid=? AND buchungstag >=? AND buchungstag<=? order by mt940id";
            private static final String select_last = "SELECT mt940 AS mt940, mt940id AS id FROM mt940 WHERE kontoid=? order by mt940id desc limit 1";
        }

        private static final class publickeys3 {
            private static final String insert = "INSERT INTO publickeys3 (benutzerkennung,pubkey,schluesselart,schluesselnummer,schluesselversion,freigeschaltet, modulo,exponent) VALUES (?,?,?,?,?,?,?,?)";
            private static final String select = "SELECT pubkey AS pubkey FROM publickeys3 WHERE benutzerkennung=? AND schluesselart=? AND schluesselnummer=?  AND schluesselversion=? AND freigeschaltet=?";
            private static final String select_wo_version = "SELECT pubkey AS pubkey FROM publickeys3 WHERE benutzerkennung=? AND schluesselart=? AND schluesselnummer=? AND freigeschaltet=? ORDER BY schluesselnummer DESC";
            private static final String select_wo = "SELECT pubkey AS pubkey FROM publickeys3 WHERE benutzerkennung=? AND schluesselart=? AND schluesselnummer=?  AND schluesselversion=?";

        }

        private static final class purposecodes {
            private static final String select = "Select purposecode from purposecodes where purposecode=?";
        }

        private static final class tanliste {
            private static final String update = "UPDATE tanliste SET used=1, used_at=? WHERE tanid=?";
            private static final String select_by_tanid = "SELECT tan AS tan FROM tanliste WHERE tanid=?";
            private static final String update_sms = "UPDATE tanliste SET tan_per_sms=? WHERE tanid=?";
            private static final String select_unused_by_tanlistenid_kennung = "SELECT tanid AS tanid, tan AS tan, reihenfolge AS reihenfolge, ref_tanlistenlogid AS tanlistenid FROM tanliste WHERE ref_tanlistenlogid=? AND benutzerkennung=? AND used='0' AND (tan_per_sms IS null OR tan_per_sms ='0')";

            private static final class mobil {
                private static final String insert = "INSERT INTO tanliste_mobil (mtan,used,loginid,benutzerkennung,request) VALUES (?,0,0,?,?)";
                private static final String select = "SELECT mtan AS tan FROM tanliste_mobil WHERE mtanid=?";
                private static final String update = "UPDATE tanliste_mobil SET used=? WHERE mtanid=?";

            }

            private static final class nlog {
                private static final String update_by_tanlistenid = "UPDATE tanlistenlog SET freigeschaltet=1 WHERE tanlistenlogid=?";
                private static final String select_status = "SELECT tl.tanlistenlogid AS tanlistenid,sum(if(used='0',0,1)) AS free,sum(if(used='0',1,0)) AS used, tl.freigeschaltet AS active FROM tanliste t,tanlistenlog tl WHERE t.ref_tanlistenlogid = tl.tanlistenlogid AND t.benutzerkennung = tl.ref_benutzerkennung AND benutzerkennung=? AND tl.freigeschaltet=? GROUP BY  tl.tanlistenlogid HAVING sum(if(used='0',1,0)) > 0 ORDER BY tl.tanlistenlogid";
            }
        }

        private static final class vorabumsaetze {
            private static final String insert = "INSERT INTO vorabumsaetze (kontoid,buchungsdatum,primanota,verwendungszweck,betrag,kontostand,wert,refimportid,gegenkontoname,gegenkontonr,gegenkontoblz) VALUES (?,?,'000',?,?,0,?,0,?,?,?)";
            private static final String select = "SELECT buchungsdatum AS buchungsdatum,verwendungszweck AS vwz, betrag AS betrag, gegenkontoname AS gegenname, gegenkontonr AS gegenkonto, gegenkontoblz AS gegenblz FROM vorabumsaetze WHERE kontoid=? and buchungsdatum >=? and buchungsdatum <=?";
            private static final String select_last = "SELECT buchungsdatum AS buchungsdatum,verwendungszweck AS vwz, betrag AS betrag, gegenkontoname AS gegenname, gegenkontonr AS gegenkonto, gegenkontoblz AS gegenblz FROM vorabumsaetze WHERE kontoid=? order by bewegungsid desc limit 1";
            private static final String sum_prebooked = "select SUM(v.BETRAG) from vorabumsaetze v join konten k ON v.KONTOID = k.KONTOID where k.KONTONUMMER=? order by BEWEGUNGSID desc ;";
        }

        private static final class sepa {
            private static final String select = "SELECT swiftcode,sct,sdd,b2b FROM sepa WHERE swiftcode=?";
        }
    }


    /**
     * Mögliche Bank Parameter
     */
    @SuppressWarnings("WeakerAccess")
    public class properties {
        public static final String pinSignatureCount = "pinSignatureCount";
        public static final String pinMaxEntry = "pinMaxEntry";
        public static final String pinClass = "pinClass";
        public static final String pinMin = "pinMin";
        public static final String pinMax = "pinMax";
        public static final String columnSct = "columnSct";
        public static final String columnSdd = "columnSdd";
        public static final String smsSystemId = "smsSystemId";
        public static final String smsGatewayUrl = "smsGatewayUrl";
        public static final String tanLen = "tanLen";
        public static final String bankCode = "bankCode";
        public static final String createVorabumsaetze = "createVorabumsaetze";
        public static final String communikationUrls = "communikationUrls";
        public static final String mailSender = "mail.sender";
        public static final String mailEmpfaenger = "mail.empfaenger";
        public static final String mailSubject = "mail.betreff";
        public static final String mailSmtpHost = "mail.smtp.host";
        @SuppressWarnings("unused")
        public static final String mailSmtpPort = "mail.smtp.port";
        public static final String mailSmtpUserName = "mail.smtp.username";
        public static final String mailSmtpPassword = "mail.smtp.password";
        public static final String bankBic = "bankBic";
        public static final String cutOffHour = "sepa.sdd.cutOff.hour";
        public static final String cutOffMinute = "sepa.sdd.cutOff.minute";
        public static final String daysToAdd = "sepa.sdd.cuttOff.daystoadd";
        public static final String sddDueDateAdd = "sepa.sdd.duedateadd";
        public static final String allowedB2B = "sepa.sdd.b2ballowed";
        public static final String cKontoLocalPath = "ckonto.local.path";
        public static final String cKontoLocalKey = "ckonto.local.key";
        public static final String cKontoLocalCodes = "ckonto.local.codes";
        public static final String checkSonderzeichen = "check.sonderzeichen";
        public static final String cKontoSondercheckDe = "ckonto.sondercheck.de";
        public static final String avActive = "av.active";
        public static final String bicCheckEnabled = "sepa.bic.check.enabled";
        public static final String bicSaveEnabled = "sepa.bic.save.enabled";
        public static final String checkPurposeCodeSCT = "sepa.purpose.check.sct.enabled";
        public static final String checkPurposeCodeSDD = "sepa.purpose.check.sdd.enabled";
        public static final String checkCore1 = "sepa.cor1.enabled";
    }

    private class Gegenkonto {
        private String Kontonummer;
        private String Bankleitzahl;
        private String IBAN;
        private String BIC;

        Gegenkonto(String Kontonummer, String Bankleitzahl, String IBAN, String BIC) {
            this.Kontonummer = (Kontonummer != null) ? Kontonummer : "";
            this.Bankleitzahl = (Bankleitzahl != null) ? Bankleitzahl : "";
            this.IBAN = (IBAN != null) ? IBAN : "";
            this.BIC = BIC != null ? BIC : "";
            if (!this.IBAN.isEmpty()) {
                this.Bankleitzahl = "";
                this.Kontonummer = "";
            } else {
                this.IBAN = "";
                this.BIC = "";
            }
        }

        String getKontonummer() {
            return Kontonummer;
        }

        String getBankleitzahl() {
            return Bankleitzahl;
        }

        String getIBAN() {
            return IBAN;
        }

        String getBIC() {
            return (config(properties.bicSaveEnabled, "true").equals("true") ? BIC : "");
        }
    }


}
