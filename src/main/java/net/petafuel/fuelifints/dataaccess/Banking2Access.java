package net.petafuel.fuelifints.dataaccess;

import net.petafuel.fuelifints.FinTSVersionSwitch;
import net.petafuel.fuelifints.cryptography.aesencryption.AESUtil;
import net.petafuel.fuelifints.dataaccess.dataobjects.*;
import net.petafuel.fuelifints.dataaccess.dataobjects.database.banking2;
import net.petafuel.fuelifints.dataaccess.dataobjects.gateway.email;
import net.petafuel.fuelifints.dataaccess.dataobjects.gateway.sms;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.model.client.ClientProductInfo;
import net.petafuel.fuelifints.model.client.LegitimationInfo;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Betrag;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.KontoverbindungInternational;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.KontoverbindungNational;
import net.petafuel.fuelifints.support.Payments;
import net.petafuel.jsepa.SEPAParser;
import net.petafuel.jsepa.exception.SEPAParsingException;
import net.petafuel.jsepa.model.*;
import net.petafuel.jsepa.util.BankDateCalculator;
import net.petafuel.jsepa.util.SepaUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.mail.MessagingException;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static net.petafuel.fuelifints.dataaccess.dataobjects.PermissionDataObject.FINTS30;
import static net.petafuel.fuelifints.dataaccess.dataobjects.PermissionDataObject.getGeschaeftsvorfall;
import static net.petafuel.fuelifints.dataaccess.dataobjects.database.banking2.addTagUser;
import static net.petafuel.fuelifints.support.Payments.derfNullSein;


@SuppressWarnings("unused")
public class Banking2Access implements DataAccessFacade {
    private static final Logger LOG = LogManager.getLogger(Banking2Access.class);
    private final String bankCode;
    private String configColumnSct = "ueberweisung";
    private String configColumnSdd = "sdd";
    private final banking2 database;

	/**
	 * @param configFile Pfad zum Konfigurationsfile
	 * @throws IOException wenn Config nicht gefunden wird
	 */
	public Banking2Access(String configFile) throws IOException {
		Properties configuration = new Properties();
		configuration.load(new FileInputStream(configFile));
		database = new banking2(configuration);
		bankCode = config(banking2.properties.bankCode, "12345678");
	}

	/**
	 * Prüft ob ein Schlüsselpar für die Bank existiert
	 *
	 * @param securityMethod Sicherheitsmethode
	 * @param keyType        SchlüsselTyp
	 * @return true wenn exisitert
	 */
	@Override
	public boolean hasKeyPair(SecurityMethod securityMethod, String keyType) {
		return database.isKey("0", getSchluesselArtByKeyType(keyType), 1, 1);
	}

	/**
	 * Holt eine Schl&uuml;sselart anhand des Schl&uuml;sseltyp
	 *
	 * @param keyType Schl&uuml;sseltyp
	 * @return Schl&uuml;sselart
	 */
	private int getSchluesselArtByKeyType(String keyType) {
		switch (keyType) {
			case "V":
				return 1;
			case "S":
				return 2;
			default:
				return 3;
		}
	}

	/**
	 * Speichert einen neuen Bankschlüssel
	 *
	 * @param securityMethod Sicherheitsmethode
	 * @param privateKey     privater Schlüssel?
	 * @param publicKey      öffentlicher Schlüssel
	 * @param keyType        SchlüsselTyp
	 * @param modulus        Modulos
	 * @param publicExponent Exponent
	 */
	@Override
	public void updateKeyPair(SecurityMethod securityMethod, String privateKey, String publicKey, String keyType, BigInteger modulus, BigInteger publicExponent) {
		database.saveKey("0", publicKey, getSchluesselArtByKeyType(keyType), 1, 1, modulus.toString(16), publicExponent.toString(16), true);
	}

	/**
	 * ?? keystore!
	 *
	 * @param securityMethod Sicherheitsmethode
	 * @param keyType        SchlüsselTyp
	 * @return ??
	 */
	@Override
	public String getPrivateKey(SecurityMethod securityMethod, String keyType) {
		return null;
	}

	@Override
	public String getPublicKey(SecurityMethod securityMethod, String keyType) {
		return database.getKey("0", getSchluesselArtByKeyType(keyType), 1, 1, true);
	}

	/**
	 * Speichert einen Benutzer-Schlüssel in die Datenbank
	 *
	 * @param userId         Kennung
	 * @param customerId     Kunden-ID
	 * @param securityMethod Sicherheitsmethode
	 * @param pubKey         öffentlicher Schlüssel
	 * @param keyType        SchlüsselNummer -> Schlüsselart
	 * @param keyVersion     SchlüsselVersion
	 * @param keyNumber      Schlüsselnummer
	 * @param modulus        Modulos
	 * @param publicExponent Exponent
	 */
	@Override
	public boolean addUserPublicKey(String userId, String customerId, SecurityMethod securityMethod, String pubKey, String keyType, int keyVersion, int keyNumber, BigInteger modulus, BigInteger publicExponent) {
		return database.saveKey(userId, pubKey, getSchluesselArtByKeyType(keyType), keyVersion, keyNumber, modulus.toString(16), publicExponent.toString(16), getSchluesselArtByKeyType(keyType) == 1);
	}

	/**
	 * Holt den Benutzer-Schlüssel aus der Datenbank
	 *
	 * @param userId         Kennung
	 * @param customerId     Kunden-ID
	 * @param securityMethod Sicherheitsmethode
	 * @param keyType        SchlüsselNummer -> Schlüsselart
	 * @param keyVersion     SchlüsselVersion
	 * @param keyNumber      Schlüsselnummer
	 * @return null, wenn der Schlüssel nicht existiert
	 */
	@Override
	public String getUserPublicKey(String userId, String customerId, SecurityMethod securityMethod, String keyType, int keyVersion, int keyNumber) {
		return database.getKey(userId, getSchluesselArtByKeyType(keyType), keyNumber, keyVersion, true);
	}

	/**
	 * Prüft ob ein Benutzer-Schlüssel exisitert
	 *
	 * @param userId         Kennung
	 * @param customerId     Kunden-ID
	 * @param securityMethod Sicherheitsmethode
	 * @param keyType        SchlüsselNummer -> Schlüsselart
	 * @param keyVersion     SchlüsselVersion
	 * @param keyNumber      Schlüsselnummer
	 * @return true wenn existiert
	 */
	@Override
	public boolean existsUserKey(String userId, String customerId, SecurityMethod securityMethod, String keyType, int keyVersion, int keyNumber) {
		return database.isKey(userId, getSchluesselArtByKeyType(keyType), keyNumber, keyVersion);
	}

	/**
	 * gibt die aktuelle Kunden-System-ID zurück
	 *
	 * @param dialogid          Dialog-ID
	 * @param legitimationInfo  LegitimationInfo
	 * @param clientProductInfo ClientProductInfo
	 * @return Kunden-System-ID
	 */
	@Override
	public String getUserSystemId(String dialogid, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo) {
		return getOrGenerateUserSystemId(dialogid, legitimationInfo, clientProductInfo);
	}

	/**
	 * Generiert eine Kunden-System-ID
	 *
	 * @param dialogId          Dialog-ID
	 * @param legitimationInfo  LegitimationInfo
	 * @param clientProductInfo ClientProductInfo
	 * @return Kunden-System-ID
	 */
	@Override
	public String getOrGenerateUserSystemId(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo) {
		return database.getOrGenerateUserSystemId(dialogId, legitimationInfo, clientProductInfo);
	}

	/**
	 * Ermitteln der TAN-Verfahren für den Benutzer
	 *
	 * @param legitimationInfo die LegitimationInfo
	 * @return Liste der verfahren
	 */
	@Override
	public List<String> getZugelasseneTanVerfahren(LegitimationInfo legitimationInfo) {
		LinkedList<String> result = new LinkedList<>();
		if (database.mTanIsAvaible(legitimationInfo)) {
			result.add(PermissionDataObject.verfahren_mTan);
		}
		return result;
	}

	/**
	 * Funktion zum TAN-Entwerten
	 *
	 * @param dialogId          die DialogID
	 * @param legitimationInfo  die Legitimationsinfo
	 * @param clientProductInfo die Client
	 * @param TAN               die zu Entwertende TAN
	 */
	@Override
	public void devalueTan(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, String TAN) {
		database.devalueTan(dialogId, legitimationInfo);
	}

	/**
	 * Keine Ahnung wofür die ist
	 *
	 * @param bankId Bankleizahl
	 * @return KommunikationsParameterData, null wenn nicht in Config gesetzt
	 */
	@Override
	public KommunikationsParameterData getKommunikationsParameterData(String bankId) {
		String communikationUrlListString = config(banking2.properties.communikationUrls, "");
		if (!communikationUrlListString.equals("")) {
			KommunikationsParameterData kommunikationsParameterData = new KommunikationsParameterData();
			List<String> communikationUrlList = com.mysql.cj.util.StringUtils.split(communikationUrlListString, ",", true);
			communikationUrlList.forEach(kommunikationsParameterData::addKommunikationszugang);
			kommunikationsParameterData.setBankId(bankId);
			return kommunikationsParameterData;
		}
		return null;
	}

	/**
	 * Gibt alle Banknachrichten zurück
	 *
	 * @param legitimationInfo  die Legitimationsinfo
	 * @param clientProductInfo die ClientProductInfo
	 * @return Liste alle Banknachrichten
	 */
	@Override
	public List<BankMessageObject> getBankMessages(LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo) {
		List<BankMessageObject> messageObjectList = database.getLocalMessageList(legitimationInfo.getUserId());
		List<String> messagesFound = new ArrayList<>();
		List<Map<String, String>> bankMessages = database.getBankMessages(legitimationInfo);
		for (Map<String, String> bankMessage : bankMessages) {
			messageObjectList.add(new BankMessageObject(bankMessage.get("subject"), bankMessage.get("message")));
			messagesFound.add(bankMessage.get("messageid"));
		}
		database.setBankMessagesRead(messagesFound, legitimationInfo);
		database.resetLocalMessageList(legitimationInfo.getUserId());
		return messageObjectList;
	}

	/**
	 * Liefert den Bestand an an Sepa Einzellastschriften zurück.
	 * In diesem Fall muss im DirectDebitDataObject die SepaPainMessage und der SepaDescriptor gesetzt werden
	 *
	 * @param legitimationInfo die Legitmationsinfo
	 * @return für jede Einzellastschrift ein DirectDebitDataObject in einer Liste
	 */
	@Override
	public List<DirectDebitDataObject> getEinzelLastschriftBestand(LegitimationInfo legitimationInfo) {
		/*
		 * todo
         */
		return null;
	}

	/**
	 * Liefert den Bestand an an Sepa Sammellastschriften zurück.
	 *
	 * @param legitimationInfo die Legitmationsinfo
	 * @return für jede Einzellastschrift ein DirectDebitDataObject in einer Liste
	 */
	@Override
	public List<DirectDebitDataObject> getSammelLastschriftBestand(LegitimationInfo legitimationInfo) {
		/*
		 * todo
         */
		return null;
	}

	@Override
	public boolean userMessageSubmitted(LegitimationInfo legitimationsInfo, KontoverbindungNational kontoverbindungAuftraggeber, String freitextmeldung, String betreff, String empfaengerangaben) {
		/*
		 * todo
         * Sachbearbeiter, Stammnummer & Kunden-Name heraussuchen
         */
		if (kontoverbindungAuftraggeber != null) {
			LOG.debug(addTagUser(format("Kontonummer: %s", kontoverbindungAuftraggeber.getKontonummer()), legitimationsInfo));
			LOG.debug(addTagUser(format("Kreditinstitutkennung: %s", kontoverbindungAuftraggeber.getKreditinstitutskennung()), legitimationsInfo));
			LOG.debug(addTagUser(format("Unterkontomerkmal: %s", kontoverbindungAuftraggeber.getUnterkontomerkmal()), legitimationsInfo));
		}
		LOG.debug(addTagUser(format("Betreff: %s", betreff), legitimationsInfo));
		LOG.debug(addTagUser(format("Empfaengerangaben: %s", empfaengerangaben), legitimationsInfo));
		String absender = config(banking2.properties.mailSender, "banking@yourbank.url");
		String empfaenger = config(banking2.properties.mailEmpfaenger, "");
		String subject = config(banking2.properties.mailSubject, "");
		email mail = new email(config(banking2.properties.mailSmtpHost, ""), config(banking2.properties.mailSmtpUserName, ""), config(banking2.properties.mailSmtpPassword, ""));
		try {
			mail.sendMail(absender, empfaenger, subject, freitextmeldung.replaceAll("<.*?>", ""));
			return true;
		} catch (MessagingException e) {
			LOG.warn(addTagUser(format("Fehler beim Senden der Mail: %s", e.getMessage()), legitimationsInfo), e);
		}
		return false;
	}

	@Override
	public String getBic() {
		return config(banking2.properties.bankBic, "NOOODE00BIC");
	}

	/**
	 * Holt die aktuelle BPD-Version
	 *
	 * @return BPD-Version
	 */
	@Override
	public int getCurrentBpdVersion() {
		return database.getCurrentBpdVersion();
	}

	/**
	 * Prüft ob die bpdVersion übereinstimmt
	 *
	 * @param bpdVersion die BPD-Version des Kunden
	 * @return übereinstimmung
	 */
	@Override
	public boolean isBpdVersionCurrent(int bpdVersion) {
		return (getCurrentBpdVersion() == bpdVersion);
	}

	/**
	 * @param legitimationInfo LegitimationInfo, für die die Version geholt werden soll
	 * @return aktuelle UPD-Version
	 */
	@Override
	public int getCurrentUpdVersion(LegitimationInfo legitimationInfo) {
		// FIXME: customerId is not always set
		return 1;
//		String customerId = legitimationInfo.getCustomerId();
//		if (customerId != null && customerId.equals("9999999999")) {
//			return 1;
//		}
//		return database.getLatestUpd(legitimationInfo);
	}

	/**
	 * @param legitimationInfo Benutzerkennung
	 * @param updVersion       updVersion
	 * @return übereinstimmung
	 */
	@Override
	public boolean isUpdVersionCurrent(LegitimationInfo legitimationInfo, int updVersion) {
		return database.isUpdVersionCurrent(legitimationInfo, updVersion);
	}

	/**
	 * Gibt alle Parameter-Daten für die FINTS-Version zurück
	 *
	 * @param fintsVersion Gibt die FinTS Version an, für die Parameterdaten gefordert wurden
	 * @return Liste mit ParameterDataObject
	 */
	@Override
    public ArrayList<ParameterDataObject> getParameterData(FinTSVersionSwitch.FinTSVersion fintsVersion) {
		String hbciversion = PermissionDataObject.getHBCIversion(fintsVersion);
		String bpdVersion = String.valueOf(getCurrentBpdVersion());
		ArrayList<ParameterDataObject> retObjects = new ArrayList<>();
		List<Map<String, String>> geschaeftsvorfaelle = database.getGeschaeftsvorfaelle(bpdVersion, hbciversion);
		List<PermissionDataObject.GlobalParameter> bankParameter = PermissionDataObject.getBankParameters();
		if (geschaeftsvorfaelle.size() < bankParameter.size()) {
			database.resetGeschaeftsvorfaelle(bpdVersion, hbciversion);
			for (PermissionDataObject.GlobalParameter globalParameter : bankParameter) {
				database.insertGeschaeftsvorfall(globalParameter, bpdVersion, hbciversion);
			}
			geschaeftsvorfaelle = database.getGeschaeftsvorfaelle(bpdVersion, hbciversion);
		}
		for (Map<String, String> geschaeftsvorfall : geschaeftsvorfaelle) {
			retObjects.add(
					new ParameterDataObject(geschaeftsvorfall.get("segmentname"),
							database.getInt(geschaeftsvorfall.get("segmentversion")),
							database.getInt(geschaeftsvorfall.get("maximaleanzahlauftraege")),
							database.getInt(geschaeftsvorfall.get("anzahlsignaturenmindestens")),
							geschaeftsvorfall.get("sicherheitsklasse"),
							geschaeftsvorfall.get("fints_3_parameter"))
			);
		}
		return retObjects;
	}

	/**
	 * Holt alle Komunikationsparameter ab
	 *
	 * @param fintsVersion Gibt die FinTS Version an, für die Parameterdaten gefordert wurden
	 * @return CommonBankParameterDataObject
	 */
	@Override
    public CommonBankParameterDataObject getCommonBankParameters(FinTSVersionSwitch.FinTSVersion fintsVersion) {
		String bankName = "Unbekannte Bank";
		String bankLang = "1";
		String bankHbciVersion = PermissionDataObject.getHBCIversion(fintsVersion);
		Map<String, String> bpd = database.getBpd();
		if (bpd != null) {
			//		bankCode = bpd.get("blz");
			bankName = bpd.get("bankname");
			bankLang = bpd.get("lang");
//			bankHbciVersion = bpd.get("hbciversion");
		} else {
			LOG.warn(format("Fehler beim holen der Bankparameter: tabelle bpd leer? (HBCI: %s)", fintsVersion));
		}
		return new CommonBankParameterDataObject(getCurrentBpdVersion(), bankCode, bankName, bankLang, bankHbciVersion);
	}

	/**
	 * Gibt eine Liste aller Konten zurück
	 *
	 * @param legitimationInfo Legitimationsinfo zum Benutzer
	 * @return Liste mit AccountDataObjects
	 */
	@Override
	public ArrayList<AccountDataObject> getAccountData(LegitimationInfo legitimationInfo) {
		String userId = legitimationInfo.getUserId();
		LOG.info(addTagUser("Konten holen", legitimationInfo));
		ArrayList<AccountDataObject> retObjects = new ArrayList<>();
		List<Map<String, String>> konten = database.getKonten(legitimationInfo);
		String kontonummer;
		PermissionDataObject permissionDataObject;
        boolean pinUsable = database.isPinUsable(legitimationInfo);
        boolean mailAvaible = database.isMailAvaible();
		for (Map<String, String> konto : konten) {
			kontonummer = konto.get("kontonummer");
			LOG.info(addTagUser(format("Konto gefunden: %s", kontonummer), legitimationInfo));
			AccountDataObject currentData =
					new AccountDataObject(kontonummer,
							bankCode,
							konto.get("kontoinhaber"),
							konto.get("kontoart"));
			// An der stelle muss immer die Angefragte Kunden-ID zurück gegeben werden, nicht die tatsächliche!
			currentData.setKunden_id(legitimationInfo.getCustomerId());
			currentData.setKontoart("1");

            /*
             * @todo setKontolimit mit KontolimitObject
             */
			currentData.setKontoart(konto.get("kontoart"));
            /*
             * saldo	inland kontoverbindung sepa-single sepa-sammel umsätze tan
             * HKSAL:1+HKUEB:1+HKSPA:1+HKCCS:1+HKCCM:1+HKKAZ:1+HKTAN:1
             */
			permissionDataObject = new PermissionDataObject();
            Map<String, Boolean> permittedOperations = database.getPermittedOperations(legitimationInfo, kontonummer);
            configColumnSct = config(banking2.properties.columnSct, configColumnSct);
            configColumnSdd = config(banking2.properties.columnSdd, configColumnSdd);
			for (FINTS30 type : PermissionDataObject.getAllowedFINTS30()) {
                boolean allowed = false;
                switch (type) {
                    case SaldoAbfrage:                              //	Saldo
                    case UmsatzAbfrage:                             //	Umsätze & Vorabumsätze
                    case Vormerkumsaetze:                           //	Vorabumsätze extra
                    case PinTanVerfahren:                           //	TAN-Eingabe
                    case PinTanZweiSchrittParameter:                //  PIN-/TAN-Zweischritt-Parameter
                    case SepaKontoVerbindungen:                     //	SEPA-Parameter
                    case SepaUeberweisungEinzelTerminBestand:       //  SEPA-Einzel-Überweisung-Termin Bestand
                    case SepaUeberweisungSammelTerminBestand:       //  SEPA-Sammel-Überweisung-Termin Bestand
                        allowed = true;
                        break;
                    case PinAendern:    //	PIN-Eingabe
                        allowed = pinUsable;
                        break;
                    case SepaUeberweisungEinzelEinreichen:            //	SEPA-Einzel-Überweisung Einreichen
                    case SepaUeberweisungEinzelTerminEinreichen:    //  SEPA-Einzel-Überweisung-Termin Einreichen
                    case SepaUeberweisungEinzelTerminLoeschen:        //	SEPA-Einzel-Überweisung-Termin Löschen
                    case SepaUeberweisungSammelEinreichen:            //	SEPA-Sammel-Überweisung Einreichen
                    case SepaUeberweisungSammelTerminEinreichen:    //	SEPA-Sammel-Überweisung-Termin Einreichen
                    case SepaUeberweisungSammelTerminLoeschen:        //	SEPA-Sammel-Überweisung-Termin Löschen
                        allowed = permittedOperations.get(configColumnSct) && permittedOperations.get("ade");
                        break;
                    case SepaLastschriftEinzelEinreichenB2B:
                    case SepaLastschriftEinzelBestandB2B:
                    case SepaLastschriftEinzelLoeschenB2B:
                    case SepaLastschriftSammelEinreichenB2B:
                    case SepaLastschriftSammelBestandB2B:
                    case SepaLastschriftSammelLoeschenB2B:
                        if (!config(banking2.properties.allowedB2B, "0").equals("1")) {
                            allowed = false;
                            break;
                        }
                    case SepaLastschriftEinzelBestand:              //  SEPA-Einzel-Lastschriften Bestand
                    case SepaLastschriftSammelBestand:              //  SEPA-Sammel-Lastschriften Bestand
                    case SepaLastschriftEinzelEinreichen:           //  SEPA-Einzel-Lastschrift Einreichen
                    case SepaLastschriftEinzelEinreichenCore1:      //  SEPA-Einzel-Lastschrift-COR1 Einreichen
                    case SepaLastschriftEinzelAendern:              //  SEPA-Einzel-Lastschriften Ändern
                    case SepaLastschriftEinzelLoeschen:             //  SEPA-Einzel-Lastschrift Löschen
                    case SepaLastschriftSammelEinreichen:           //  SEPA-Sammel-Lastschrift Einreichen
                    case SepaLastschriftSammelEinreichenCore1:      //  SEPA-Sammel-Lastschrift-COR1 Einreichen
                    case SepaLastschriftSammelLoeschen:             //  SEPA-Sammel-Lastschrift Löschen
                        allowed = permittedOperations.get(configColumnSdd) && permittedOperations.get("ade");
                        break;
                    case KundenBankNachricht:
                        allowed = mailAvaible;
                        break;
                    default:
                        allowed = false;
                        break;
                }
                if (allowed) {
					permissionDataObject.addPermission(type);
				}
			}
			currentData.setErlaubteGeschaeftsvorfaelle(permissionDataObject.toString());
			retObjects.add(currentData);
		}
		return retObjects;
	}

	/**
	 * Generiert einen PinParameterObject, gibt an für welchen Vorgang eine TAN benötigt wird
	 *
	 * @return PinParameterObject
	 */
	@Override
	public PinParameterObject getPinParameter() {
		PinParameterObject pinParameterObject = new PinParameterObject();
		pinParameterObject.setAnzahlSignaturenMindestens(parseInt(config(banking2.properties.pinSignatureCount, "1")));
		pinParameterObject.setMaximaleAnzahlAuftraege(parseInt(config(banking2.properties.pinMaxEntry, "1")));
		pinParameterObject.setSicherheitsklasse(parseInt(config(banking2.properties.pinClass, "0")));
		pinParameterObject.setMinPinLaenge(parseInt(config(banking2.properties.pinMin, "5")));
		pinParameterObject.setMaxPinLaenge(parseInt(config(banking2.properties.pinMax, "20")));
		pinParameterObject.setMaxTanLaenge(parseInt(config(banking2.properties.tanLen, "6")));
		pinParameterObject.setTextBelegungBenutzerkennung(config("textKennung", "Benutzerkennung"));
		pinParameterObject.setTextBelegungKundenId(config("textKundenId", "Kunden-ID"));
		HashMap<FINTS30, Boolean> pinRequired = PermissionDataObject.getPinRequired();
		List<FINTS30> allowed = PermissionDataObject.getAllowedFINTS30();
		pinRequired
				.entrySet()
				.stream()
				.filter(entry -> allowed.contains(entry.getKey()))
				.forEach(entry -> pinParameterObject.addSegment(getGeschaeftsvorfall(entry.getKey()), entry.getValue()));
		return pinParameterObject;
	}

	/**
	 * Fragt ob der Vorgang erlaubt ist
	 *
	 * @param legitimationInfo LegitimationInfo
	 * @param accountId        die Kontonummer
	 * @param aClass           @return
	 * @return true wenn erlaubt, false wenn nicht erlaubt oder nicht definiert
	 */
	@Override
	public boolean operationAllowedForAccount(LegitimationInfo legitimationInfo, String accountId, Class<? extends IMessageElement> aClass) {
		return operationAllowedForAccount(legitimationInfo, accountId, aClass.getSimpleName());
	}

	/**
	 * Liefert ob ein Auftrag (aClass repräsentiert das Segment als Klasse) für den
	 * angegebenen Account gültig ist.
	 *
	 * @param legitimationInfo LegitimationInfo
	 * @param aClass           @return
	 */
	@Override
	public boolean operationAllowedForAccount(LegitimationInfo legitimationInfo, Class<? extends IMessageElement> aClass) {
		return operationAllowedForAccount(legitimationInfo, null, aClass.getSimpleName());
	}

	/**
	 * Interne Abfrage ob die Operation erlaubt ist
	 *
	 * @param legitimationInfo  LegitimationInfo
	 * @param kontonummer       Kontonummer
	 * @param geschaeftsvorfall Geschäftsvorfall
	 * @return true, wenn erlaubt, false wenn nicht erlaubt oder nicht definiert
	 */
	private boolean operationAllowedForAccount(LegitimationInfo legitimationInfo, String kontonummer, String geschaeftsvorfall) {
		boolean returnBoolean;
		configColumnSct = config(banking2.properties.columnSct, configColumnSct);
		configColumnSdd = config(banking2.properties.columnSdd, configColumnSdd);
		LOG.info(addTagUser(format("Abfrage für Geschäftsvorfall: %s", geschaeftsvorfall), legitimationInfo));
		FINTS30 geschaeftsvorfallEnum = getGeschaeftsvorfall(geschaeftsvorfall);
		if (geschaeftsvorfallEnum == null) {
			returnBoolean = false;
		} else {
			switch (geschaeftsvorfallEnum) {
				case Identifikation:							//  Identifikation via HKIDN
				case SaldoAbfrage:                              //	Saldo
				case UmsatzAbfrage:                             //	Umsätze & Vorabumsätze
				case Vormerkumsaetze:                           //	Vorabumsätze extra
				case PinTanVerfahren:                           //	TAN-Eingabe
				case PinTanZweiSchrittParameter:                //  PIN-/TAN-Zweischritt-Parameter
				case SepaKontoVerbindungen:                     //	SEPA-Parameter
				case SepaUeberweisungEinzelTerminBestand:       //  SEPA-Einzel-Überweisung-Termin Bestand
				case SepaUeberweisungSammelTerminBestand:       //  SEPA-Sammel-Überweisung-Termin Bestand
					returnBoolean = true;
					break;
				case PinAendern:    //	PIN-Eingabe
					returnBoolean = database.isPinUsable(legitimationInfo);
					break;
				case SepaUeberweisungEinzelEinreichen:            //	SEPA-Einzel-Überweisung Einreichen
				case SepaUeberweisungEinzelTerminEinreichen:    //  SEPA-Einzel-Überweisung-Termin Einreichen
				case SepaUeberweisungEinzelTerminLoeschen:        //	SEPA-Einzel-Überweisung-Termin Löschen
				case SepaUeberweisungSammelEinreichen:            //	SEPA-Sammel-Überweisung Einreichen
				case SepaUeberweisungSammelTerminEinreichen:    //	SEPA-Sammel-Überweisung-Termin Einreichen
				case SepaUeberweisungSammelTerminLoeschen:        //	SEPA-Sammel-Überweisung-Termin Löschen
					returnBoolean = kontonummer == null || database.isOperationPermit(legitimationInfo, kontonummer, configColumnSct);
					break;
				case SepaLastschriftEinzelEinreichenB2B:
				case SepaLastschriftEinzelBestandB2B:
				case SepaLastschriftEinzelLoeschenB2B:
				case SepaLastschriftSammelEinreichenB2B:
				case SepaLastschriftSammelBestandB2B:
				case SepaLastschriftSammelLoeschenB2B:
					if (!config(banking2.properties.allowedB2B, "0").equals("1")) {
						returnBoolean = false;
						break;
					}
				case SepaLastschriftEinzelBestand:              //  SEPA-Einzel-Lastschriften Bestand
				case SepaLastschriftSammelBestand:              //  SEPA-Sammel-Lastschriften Bestand
				case SepaLastschriftEinzelEinreichen:           //  SEPA-Einzel-Lastschrift Einreichen
				case SepaLastschriftEinzelEinreichenCore1:      //  SEPA-Einzel-Lastschrift-COR1 Einreichen
				case SepaLastschriftEinzelAendern:              //  SEPA-Einzel-Lastschriften Ändern
				case SepaLastschriftEinzelLoeschen:             //  SEPA-Einzel-Lastschrift Löschen
				case SepaLastschriftSammelEinreichen:           //  SEPA-Sammel-Lastschrift Einreichen
				case SepaLastschriftSammelEinreichenCore1:      //  SEPA-Sammel-Lastschrift-COR1 Einreichen
				case SepaLastschriftSammelLoeschen:             //  SEPA-Sammel-Lastschrift Löschen
					returnBoolean = kontonummer == null || database.isOperationPermit(legitimationInfo, kontonummer, configColumnSdd);
					break;
				case KundenBankNachricht:
					returnBoolean = database.isMailAvaible();
					break;
				default:
					returnBoolean = false;
					break;
			}
		}
		LOG.info(addTagUser(format("Abfrage für Geschäftsvorfall: %s > %s", geschaeftsvorfall, (returnBoolean ? "true" : "false")), legitimationInfo));
		return returnBoolean;
	}

	/**
	 * Generiert ein SaldoDataObject anhand der Kontonummer und Benutzerdaten
	 *
	 * @param kontonummer       Kontonummer
	 * @param legitimationsInfo LegitimationInfo
	 * @return SaldoDataObject
	 */
	@Override
	public SaldoDataObject getSaldo(String kontonummer, LegitimationInfo legitimationsInfo) {

		/**
		 * vars used: vorgemerkter saldo = prebooked revenues (sum of it)[sum all entries in vorabumsaetze for the konto in hand]
		 * var used:  gebuchterSaldo = booked saldo kontoStand from last entry of konten_bewegungen
		 * var used:  verfuegbarerBetrag = real available amount of account from konten column virtkontoStand
		 */
		SaldoDataObject sDO = new SaldoDataObject();
		String customerIdByConfig = database.getCustomerIdByConfig(legitimationsInfo);
		Map<String, String> kontoRow = database.getKontoRow(kontonummer, legitimationsInfo);
		if (kontoRow != null) {
			String kontoid = kontoRow.get("kontoid");
			Double userLimit = database.getLpTageslimit(legitimationsInfo);
			double verfuegbar_tageslimit = (userLimit != null) ? userLimit : parseDouble(kontoRow.get("tageslimit")) - parseDouble(kontoRow.get("tagesumsatz"));
			double gebuchterSaldo = parseDouble(kontoRow.get("kontostand"));
			Double gebuchterSaldoBewegung = database.getLastKontostandByBewegungen(kontoid, customerIdByConfig, legitimationsInfo);
			if (gebuchterSaldoBewegung != null) {
				gebuchterSaldo = gebuchterSaldoBewegung;
			}
			double vorgemerkterSaldo = parseDouble(kontoRow.get("virtkontostand"));
			Double vorgemerkterBetrag = (vorgemerkterSaldo - gebuchterSaldo);
			sDO.setGebuchterSaldo(gebuchterSaldo);
			boolean kl_aktiv = kontoRow.get("kl_aktiv").equals("1");
			boolean ueberziehen = kontoRow.get("ueberziehen").equals("1");
			sDO.setOverdrawAllowed(ueberziehen);
			sDO.setVorgemerkterSaldo(vorgemerkterSaldo);
			if (config(banking2.properties.avActive, "false").equals("true")) {
				double preBookedSum = database.getPreBookedRevenuesSum(kontonummer);//in case of av active fetch the sum of the prebooked revenues from the database.
				sDO.setVorgemerkterSaldo(preBookedSum);
				sDO.setVerfuegbarerBetrag(vorgemerkterSaldo);
			} else {
				if (!kl_aktiv) {
					if (!ueberziehen) {
						Double verfuegbarerBetrag = (vorgemerkterSaldo > 0d) ? vorgemerkterSaldo : 0d;
						sDO.setKreditlinie(0d);
						sDO.setVerfuegbarerBetrag(verfuegbarerBetrag);
					}
				} else    //	kreditlinie aktiv
				{
					Double verfuegbarerBetrag = parseDouble(kontoRow.get("vb")) + vorgemerkterBetrag;
					verfuegbarerBetrag = (verfuegbarerBetrag > 0d) ? verfuegbarerBetrag : 0d;
					Double kreditlinie = gebuchterSaldo + verfuegbarerBetrag;
					kreditlinie = (ueberziehen && kreditlinie > 0d) ? kreditlinie : 0d;
					sDO.setKreditlinie(kreditlinie);
					if (ueberziehen) {
						sDO.setVerfuegbarerBetrag(verfuegbarerBetrag);
					} else if (vorgemerkterSaldo <= 0d) {
						sDO.setVerfuegbarerBetrag(0d);
					} else if (vorgemerkterSaldo > verfuegbarerBetrag) {
						sDO.setVerfuegbarerBetrag(verfuegbarerBetrag);
					} else {
						sDO.setVerfuegbarerBetrag(vorgemerkterSaldo);
					}
				}
				if (sDO.getVerfuegbarerBetrag() > verfuegbar_tageslimit) {
					sDO.setVerfuegbarerBetrag(verfuegbar_tageslimit);
				}
			}
		} else {
			LOG.info(addTagUser(format("Kann Konto %s nicht finden", kontonummer), legitimationsInfo));
		}
		return sDO;
	}

	/**
	 * Überprüft einen Benutzer-PIN
	 *
	 * @param userId die ID des Nutzers.
	 * @param pin    eingegebene PIN
	 * @param dialog Dialog
	 * @return true, wenn PIN korrekt
	 */
	@Override
	public ReturnDataObject checkUserPin(String userId, String pin, Dialog dialog) {
		return new ReturnDataObject(database.checkUserPin(userId, pin), "");
	}

	/**
	 * 1.
	 *
	 * @param dialogId          die Dialog-ID
	 * @param legitimationInfo  die Benutzerdaten
	 * @param clientProductInfo die Produktinformationen
	 * @param auftragsHashwert  der Auftrags-Hashwert  @return Auftragsreferenz
	 */
	@Override
	public String generateAuftragsreferenz(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, byte[] auftragsHashwert) {
		return database.generateAuftragsreferenz(dialogId, auftragsHashwert, legitimationInfo);
	}

	/**
	 * @param dialogId          DialogId
	 * @param legitimationInfo  BenutzerDaten
	 * @param clientProductInfo KundenProdukt
	 * @param auftragsHashwert  der Auftrags-Hashwert
	 * @param methode           TAN-Methode
	 * @return ReturnDataObject
	 */
	@Override
	public ReturnDataObject getChallenge(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, byte[] auftragsHashwert, String methode, List<String> parameterChallengeKlasse) {
		LOG.info(addTagUser(format("TAN-Methode: %s, Dialog-ID: %s", methode, dialogId), legitimationInfo));
		if (parameterChallengeKlasse != null) {
			for (String parameter : parameterChallengeKlasse) {
				LOG.info(addTagUser(format("challange-parameter: %s", parameter), legitimationInfo));
			}
		}
		switch (methode) {
			case PermissionDataObject.verfahren_iTan:
				LOG.info(addTagUser("iTAN Abfrage", legitimationInfo));
				return getChallenge_iTAN(dialogId, legitimationInfo, clientProductInfo, auftragsHashwert, parameterChallengeKlasse, false);
			case PermissionDataObject.verfahren_iTanSms:
				LOG.info(addTagUser("iTAN-SMS Abfrage", legitimationInfo));
				return getChallenge_iTAN(dialogId, legitimationInfo, clientProductInfo, auftragsHashwert, parameterChallengeKlasse, true);
			case PermissionDataObject.verfahren_mTan:
				LOG.info(addTagUser("mTAN Abfrage", legitimationInfo));
				return getChallenge_mTAN(dialogId, legitimationInfo, clientProductInfo, auftragsHashwert, parameterChallengeKlasse);
			default:
				LOG.info(addTagUser(format("Unbekannte TAN-Methode: %s", methode), legitimationInfo));
				return new ReturnDataObject(false, "unbekanntes TAN-Verfahren", "9999");
		}

	}

	/**
	 * Holt die Challange für mTAN
	 *
	 * @param dialogId                 Dialog-ID
	 * @param legitimationInfo         LegitimationInfo
	 * @param clientProductInfo        ClientProductInfo
	 * @param auftragsHashwert         Auftragshashwert
	 * @param parameterChallengeKlasse ChallangeParameter
	 * @return ReturnDataObject
	 */
	@SuppressWarnings("UnusedParameters")
	private ReturnDataObject getChallenge_mTAN(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, byte[] auftragsHashwert, List<String> parameterChallengeKlasse) {
		Long tanid = database.mTanGenerate(legitimationInfo);
		if (tanid != null) {
			tanid = tanid * (-1);
			database.setTanId(dialogId, String.valueOf(tanid), legitimationInfo, clientProductInfo);
			return new ReturnDataObject(true, "Bitte geben Sie die so eben versandte mTAN ein");
		}
		return new ReturnDataObject(false, "Fehler bei der Generierung Ihrer mTAN", "9999");  //To change body of created methods use File | Settings | File Templates.
	}

	/**
	 * Holt die Challange für iTAN
	 *
	 * @param dialogId                 Dialog-ID
	 * @param legitimationInfo         LegitimationInfo
	 * @param clientProductInfo        ClientProductInfo
	 * @param auftragsHashwert         Auftragshashwert
	 * @param parameterChallengeKlasse ChallangeParameter
	 * @param tanPerSMS                wird die TAN per SMS verschickt?
	 * @return ReturnDataObject
	 */
	@SuppressWarnings("UnusedParameters")
	private ReturnDataObject getChallenge_iTAN(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, byte[] auftragsHashwert, List<String> parameterChallengeKlasse, boolean tanPerSMS) {
		Map<String, String> tanliste = database.iTanGenerate(legitimationInfo, tanPerSMS);
		if (tanliste != null) {
			String reihenfolge = tanliste.get("reihenfolge");
			String tanlistennr = tanliste.get("tanlistenid");
			LOG.info(addTagUser(format("tanliste/tannr: %s/%s", tanlistennr, reihenfolge), legitimationInfo));
			String tanId = tanliste.get("tanid");
			String dbTan = new String((AESUtil.aesDecrypt(tanliste.get("tan"))));
			if (database.setTanId(dialogId, tanId, legitimationInfo, clientProductInfo)) {
				LOG.info(addTagUser(format("TAN-ID: %s", tanId), legitimationInfo));
				if (tanPerSMS) {
					sms.status smsStatus = database.sendTan(legitimationInfo, format("Ihre iTAN per SMS für Ihren HBCI-Auftrag lautet wie folgt: %s", dbTan));
					switch (smsStatus) {
						case okay:
							return new ReturnDataObject(true, "Bitte geben Sie die so eben versandte iTAN ein");
						case queued:
							return new ReturnDataObject(true, "TAN-Versand wurde von Ihrem Mobilfunk-Anbieter verzögert. Bitte geben Sie die so eben versandte iTAN ein");
						case fail:
							return new ReturnDataObject(false, "Es ist ein Fehler beim versenden der SMS aufgetreten, bitte probieren Sie es später erneut", "9999");
					}
				} else {
					return new ReturnDataObject(true, format("Bitte geben Sie aus der TAN-Liste %s die TAN-Nr %s ein", tanlistennr, reihenfolge));
				}
			} else {
				LOG.warn(addTagUser("Fehler beim auswählen der TAN", legitimationInfo));
				return new ReturnDataObject(false, "Fehler auswählen der TAN", "9998");
			}
			return new ReturnDataObject(false, "Fehler auswählen der TAN", "9997");
		} else {
			return new ReturnDataObject(false, "Es wurde keine aktive TAN-Liste gefunden", "9999");
		}
	}

	/**
	 * 3.
	 *
	 * @param dialogId          DialogId
	 * @param legitimationInfo  die Kunden-ID
	 * @param clientProductInfo ClientProductInfo
	 * @param tan               die eingegebenen TAN
	 * @param auftragsHashWert  der Auftrags-Hashwert   @return ob TAN-Eingabe stimmt
	 */
	@Override
	public ReturnDataObject checkTan(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, String tan, byte[] auftragsHashWert) {
		boolean check = database.checkTan(dialogId, tan, legitimationInfo);
		if (check) {
			devalueTan(dialogId, legitimationInfo, clientProductInfo, tan);
			return new ReturnDataObject(true, "TAN wurde verbraucht.", "3913");
		}
		return new ReturnDataObject(false, "");
	}

	/**
	 * IZV?
	 *
	 * @param legitimationInfo            die LegitimationInfo
	 * @param kontoverbindungAuftraggeber die Kontoverbindung des Auftragsgebers
	 * @param kontoverbindungEmpfaenger   die Kontoverbindung des Empfänger
	 * @param nameEmpfaenger1             der Name des Empfängers 1
	 * @param nameEmpfaenger2             der Name des Empfängers 2
	 * @param textschluessel              der Textschlüssel der Überweisung
	 * @param textschluesselErgaenzung    die Textschlüsselergänzung
	 * @param verwendungszweckzeilen      die Verwendungszweckzeilen
	 * @param betrag                      der Betrag
	 * @param endToEnd                    die endToEnd-ID
	 * @param purposeCode                 der Purposecode
	 * @return ReturnDataObject
	 */
	@Override
	public ReturnDataObject submitNewTransaction(LegitimationInfo legitimationInfo, KontoverbindungNational kontoverbindungAuftraggeber, KontoverbindungNational kontoverbindungEmpfaenger, String nameEmpfaenger1, String nameEmpfaenger2, String textschluessel, String textschluesselErgaenzung, List<String> verwendungszweckzeilen, Betrag betrag, String endToEnd, String purposeCode) {
		int tk = parseInt(textschluessel);
		String kontonummer = kontoverbindungAuftraggeber.getKontonummer();
		double betragBuchungen = parseDouble(betrag.getWert());
		if ((tk == 4 || tk == 5)) {
			if (!database.isOperationPermit(legitimationInfo, kontonummer, configColumnSdd)) {
				return new ReturnDataObject(false, "Konto darf Lastschriften nicht ausführen", "9935");
			}
		} else {
			if (!database.isOperationPermit(legitimationInfo, kontonummer, configColumnSct)) {
				return new ReturnDataObject(false, "Konto darf Buchung nicht ausführen", "9935");
			} else {
				SaldoDataObject saldoDataObject = getSaldo(kontonummer, legitimationInfo);
				if (saldoDataObject.getVerfuegbarerBetrag() < betragBuchungen) {
					return checkBuchungenBetrag(betragBuchungen, saldoDataObject, legitimationInfo);
				}
			}
		}
		Payments.Result r = database.inDtaus(
				legitimationInfo, kontonummer,
				kontoverbindungEmpfaenger.getKontonummer(),
				kontoverbindungEmpfaenger.getKreditinstitutskennung().getKreditinstitutscode(),
				nameEmpfaenger1 + nameEmpfaenger2, parseDouble(betrag.getWert()),
				textschluessel, StringUtils.join(verwendungszweckzeilen, ""), "", "",
				parseInt(textschluesselErgaenzung), "", database.getNextCounterDTAUS(legitimationInfo, true), "", "", null,
				"", "", null, null, endToEnd, purposeCode);
		if (r.isSuccess()) {
			return new ReturnDataObject(true, "Auftrag Entgegengenommen", "0010");
		}
		return new ReturnDataObject(false, "Fehler bei der Einreichung", "9030");
	}

	private ReturnDataObject checkBuchungenBetrag(double betragBuchungen, SaldoDataObject saldoDataObject, LegitimationInfo legitimationInfo) {
		if (saldoDataObject.getVerfuegbarTag() != null && saldoDataObject.getVerfuegbarTag() < betragBuchungen) {
			LOG.info(addTagUser("Vorgang wuerde Tageslimit für Konto ueberschreiten", legitimationInfo));
			return new ReturnDataObject(false, "Vorgang wuerde Tageslimit für Konto ueberschreiten", "9935");
		} else {
			LOG.info(addTagUser("Vorgang wuerde Verfügbaren Betrag ueberschreiten", legitimationInfo));
			return new ReturnDataObject(false, "Vorgang wuerde Verfügbaren Betrag ueberschreiten", "9935");
		}
	}

	/**
	 * Fügt eine neue Buchung hinzu (bisher nur SEPA-SCT/-SDD)
	 *
	 * @param legitimationInfo LegitimationInfo
	 * @param sepaDescriptor   Sepa-Bezeichner
	 * @param sepaPainMessage  die SEPA-Pain-Message  @return true falls Auftrag angenommen, ansonsten false
	 * @param isSammler        gibt an, ob es ein Sammel-Auftrag ist
	 * @return ReturnDataObject
	 */
	@Override
	public ReturnDataObject submitNewTransaction(LegitimationInfo legitimationInfo, String sepaDescriptor, byte[] sepaPainMessage, boolean isSammler) {
		LOG.info(addTagUser(sepaDescriptor, legitimationInfo));
		if (sepaDescriptor.contains("pain.001.00")) {
			return submitSCT(legitimationInfo, sepaPainMessage, isSammler, false);
		} else if (sepaDescriptor.contains("pain.008.00")) {
			return submitSDD(legitimationInfo, sepaPainMessage, isSammler);
		} else {
			LOG.warn(addTagUser(format("folgender Sepa-Descriptor wurde nicht erkannt: %s", sepaDescriptor), legitimationInfo));
			LOG.info(addTagUser(new String(sepaPainMessage), legitimationInfo));
		}
		return new ReturnDataObject(false, "SEPA-Auftrag kann nicht verarbeitet werden", "9999");
	}

	/**
	 * Reicht die Pinänderung ein
	 *
	 * @param legitimationInfo LegitimationInfo
	 * @param newPin           die Neue PIN, die für den Nutzer hinterlegt werden soll
	 * @return gibt zurück, ob die PIN erfolgreich geändert wurde
	 */
	@Override
	public ReturnDataObject submitChangePin(LegitimationInfo legitimationInfo, String newPin) {
		if (database.saveNewPin(legitimationInfo, newPin)) {
			return new ReturnDataObject(true, "PIN erfolgreich geändert", "0020");
		} else {
			return new ReturnDataObject(false, "Fehler beim Ändern der PIN", "9942");
		}
	}

	/**
	 * Verarbeitet eine SEPA-SDD
	 *
	 * @param legitimationInfo LegitimationInfo
	 * @param sepaPainMessage  XML-Inhalt
	 * @param isSammler        ist es ein Sammler?
	 * @return ReturnDataObject
	 */
	private ReturnDataObject submitSDD(LegitimationInfo legitimationInfo, byte[] sepaPainMessage, boolean isSammler) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		SEPAParser sddParser;
		DDInitiation ddInitiation;
		PaymentInstructionInformation paymentInfo;
		DirectDebitTransactionInformation buchung;
		String sepaFile = new String(sepaPainMessage);
		sepaFile = sepaFile.replaceAll("pain.008.001.01", "CstmrDrctDbtInitn").replaceAll("OthrId", "Othr");
		sddParser = new SEPAParser(sepaFile);
		try {
			sddParser.parseSEPA();
			ddInitiation = sddParser.getSepaDocument().getDdInitiation();
			if (ddInitiation.getPmtInfos() == null || ddInitiation.getPmtInfos().size() != 1) {
				LOG.warn(addTagUser(format("Nur ein PmtInfo-Segment unterstuetzt: %s", ((ddInitiation.getPmtInfos() != null) ? ddInitiation.getPmtInfos().size() : "null")), legitimationInfo));
				return new ReturnDataObject(false, "Ungültige Anzahl an PmtInfo-Segmente gefunden", "9930");
			}
			List<DirectDebitTransactionInformation> vectorBuchungen = ddInitiation.getPmtInfos().get(0).getDirectDebitTransactionInformationVector();
			if (vectorBuchungen == null || (isSammler && vectorBuchungen.size() < 1) || (!isSammler && vectorBuchungen.size() > 1)) {
				LOG.warn(addTagUser("Ungültige Menge an DDInfosegmente gefunden", legitimationInfo));
				return new ReturnDataObject(false, "Ungültige Menge an DDInfosegmente gefunden", "9930");
			}
			int anzahlBuchungen = vectorBuchungen.size();
			// Zahlungsinfos:
			paymentInfo = ddInitiation.getPmtInfos().get(0);
			//	Gesamtbetrag!
//			double betragBuchungen = paymentInfo.getCtrlSum();
			String kontonummer = new SepaUtils().getAccountNoFromDeIban(paymentInfo.getCreditorAccountIBAN());
			String kontoid = database.getFieldByKontonummer(kontonummer, "kontoid", legitimationInfo);
			if (kontoid == null) {
				LOG.info(addTagUser(format("Konto nicht existent: %s", kontonummer), legitimationInfo));
				return new ReturnDataObject(false, "Konto nicht existent", "9935");
			} else if (!database.isOperationPermit(legitimationInfo, kontonummer, configColumnSdd)) {
				LOG.info(addTagUser(format("Konto darf Lastschriften nicht ausführen: %s", kontonummer), legitimationInfo));
				return new ReturnDataObject(false, "Konto darf Lastschriften nicht ausführen", "9935");
			}
			HashMap<String, String> ibanBicListe = new HashMap<>();
			Boolean purposeCodeFound = false;
			for (int bId = 0; bId < anzahlBuchungen; bId++) {
				buchung = paymentInfo.getDirectDebitTransactionInformationVector().get(bId);
				ibanBicListe.put(buchung.getDebitorIBAN(), buchung.getDebitorAgent());
				if (config(banking2.properties.checkSonderzeichen, "false").equals("true")) {
					if (!database.replaceNonSepaChars(buchung.getDebitorName()).equals(buchung.getDebitorName())) {
						return new ReturnDataObject(false, format("Name enthält ungültige Zeichen: %s", buchung.getDebitorName()), "9935");
					}
					if (!database.replaceNonSepaChars(buchung.getVwz()).equals(buchung.getVwz())) {
						return new ReturnDataObject(false, format("Verwendungszweck enthält ungültige Zeichen: %s", buchung.getVwz()), "9935");
					}
				}
				if (!getPurposeCode(buchung).equals("")) {
					purposeCodeFound = true;
				}
			}
			if (config(banking2.properties.checkPurposeCodeSDD, "true").equals("true") && purposeCodeFound) {
				database.addLocalMessage(legitimationInfo.getUserId(), "Zahlungsarten (PurposeCodes) werden bei Lastschriften nicht übernommen.",
						"Sie haben SEPA-Lastschriften mit Zahlungsarten (Purpose Code) eingereicht. " +
								"Diese Zahlungsarten (PurposeCodes) werden von uns verworfen, damit wir die Lastschriften problemlos ausführen können."
				);
			}
			ReturnDataObject kontenCheck = database.checkEmpfaengerKonten(kontonummer, ibanBicListe, getTransactionTypeSDD(paymentInfo), legitimationInfo);
			if (!kontenCheck.isSuccess()) {
				return kontenCheck;
			}
			try {
				Date execute = sdf.parse(paymentInfo.getRequestedCollectionDate());
				Payments.Result inDtausSuccess;
				ReturnDataObject typeCheck = isValidDDType(paymentInfo);
				if (!typeCheck.isSuccess()) {
					LOG.warn(addTagUser(typeCheck.getMessage(), legitimationInfo));
					return typeCheck;
				}
				ReturnDataObject executeCheck = isValidTarget2ExecutionDate(paymentInfo);
				if (!executeCheck.isSuccess()) {
					LOG.info(addTagUser(executeCheck.getMessage(), legitimationInfo));
					return executeCheck;
				}
				int counter = database.getNextCounterDTAUS(legitimationInfo, true);
				String glaubigerid = (paymentInfo.getCreditorSchemeId() != null) ? paymentInfo.getCreditorSchemeId().getCdtrSchmeId() : null;
				for (int bId = 0; bId < anzahlBuchungen; bId++) {
					buchung = paymentInfo.getDirectDebitTransactionInformationVector().get(bId);
					if (glaubigerid == null && buchung.getCreditorSchemeId() != null) {
						glaubigerid = buchung.getCreditorSchemeId().getCdtrSchmeId();
					} else if (glaubigerid == null) {
						return new ReturnDataObject(false, "SEPA-Lastschrift entspricht nicht dem Standard", "9037");
					}
					String currentTime = new SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
					inDtausSuccess = database.inDtaus(
							legitimationInfo, kontonummer, "", "",
							buchung.getDebitorName(), buchung.getAmount(), "7",
							buchung.getVwz(), "",
							buchung.getDebitorIBAN(), 0,
							buchung.getDebitorAgent(), counter,
							paymentInfo.getDirectDebitType(), paymentInfo.getSequenceType(),
							execute, buchung.getMandateID(), glaubigerid,
							sdf.parse(buchung.getDtOfSgntr()), currentTime,
							derfNullSein(buchung.getEndToEndID()), ""
					);
					if (inDtausSuccess.isSuccess()) {
						database.updateSalden(buchung.getAmount(), false, kontonummer, legitimationInfo);
						database.inVorabumsaetze(kontonummer, legitimationInfo, buchung.getAmount(), false, buchung.getVwz(), buchung.getDebitorAgent(), buchung.getDebitorIBAN(), buchung.getDebitorName());
						LOG.info(addTagUser(format("Buchung ausgeführt: %s / %s / %s", (bId + 1), anzahlBuchungen, buchung.getAmount()), legitimationInfo));
					} else if (anzahlBuchungen == 1) {
						LOG.info(addTagUser("Es trat ein Fehler bei der Übergabe auf", legitimationInfo));
						return new ReturnDataObject(false, "Buchung konnte nicht ausgeführt werden", "9035");
					} else {
						LOG.info(addTagUser("Es trat ein Fehler bei der Übergabe auf", legitimationInfo));
						return new ReturnDataObject(false, "Eine oder mehrere Lastschriften konnten nicht ausgeführt werden", "9035");
					}
				}

				return new ReturnDataObject(true, "Auftrag Entgegengenommen", "0010");

			} catch (ParseException e) {
				LOG.error(addTagUser(format("Fehler beim auslesen des Fälligkeit-Datums: %s", e.getMessage()), legitimationInfo), e);
				return new ReturnDataObject(false, "Fehler beim auslesen des Fälligkeit-Datums (YYYY-MM-DD)", "9935");
			}
		} catch (SEPAParsingException e) {
			return new ReturnDataObject(false, "SEPA Datei kann nicht gelesen werden", "9930");
		}
	}

	/**
	 * Verarbeitet eine SEPA-SCT
	 *
	 * @param legitimationInfo LegitimationInfo
	 * @param sepaPainMessage  XML-Inhalt
	 * @param isSammler        handelt es sich um einen Sammler
	 * @return ReturnDataObject
	 */
	private ReturnDataObject submitSCT(LegitimationInfo legitimationInfo, byte[] sepaPainMessage, boolean isSammler, boolean isTermin) {
		Payments.AuftragsId auftragsidentifikation;
		LOG.info(addTagUser(format("isSammler: %s", isSammler), legitimationInfo));
		LOG.info(addTagUser(format("isTermin: %s", isTermin), legitimationInfo));
		SEPAParser sctParser;
		CCTInitiation cctInitiation;
		PaymentInstructionInformation paymentInfo;
		CreditTransferTransactionInformation buchung;
		String sepaFile = new String(sepaPainMessage);
		sctParser = new SEPAParser(sepaFile);
		try {
			sctParser.parseSEPA();
			cctInitiation = sctParser.getSepaDocument().getCctInitiation();
			if (cctInitiation.getPmtInfos() == null || cctInitiation.getPmtInfos().size() != 1) {
				LOG.warn(addTagUser(format("Nur ein PmtInfo-Segment unterstuetzt: %s", ((cctInitiation.getPmtInfos() != null) ? cctInitiation.getPmtInfos().size() : "null")), legitimationInfo));
				return new ReturnDataObject(false, "Ungültige Anzahl an PmtInfo-Segmente gefunden", "9930");
			}
			List<CreditTransferTransactionInformation> vectorBuchungen = cctInitiation.getPmtInfos().get(0).getCreditTransferTransactionInformationVector();
			if (vectorBuchungen == null || (isSammler && vectorBuchungen.size() < 1) || (!isSammler && vectorBuchungen.size() > 1)) {
				int count = (vectorBuchungen == null) ? 0 : vectorBuchungen.size();
				String singleSammel = (isSammler) ? "s" : "m";
				String message = format("Ungültige Menge an CTTInfosegmente gefunden: %s (%s)", count, singleSammel);
				LOG.warn(addTagUser(message, legitimationInfo));
				return new ReturnDataObject(false, message, "9930");
			}
			int anzahlBuchungen = vectorBuchungen.size();
			// Zahlungsinfos:
			paymentInfo = cctInitiation.getPmtInfos().get(0);
			//	Gesamtbetrag!
			//double betragBuchungen = paymentInfo.getCtrlSum();//control sum is not available in the body
			double betragBuchungen = cctInitiation.getGrpHeader().getControlSum();//Getting the control sum from the header
			//	Kontonummer
			String kontonummer = new SepaUtils().getAccountNoFromDeIban(paymentInfo.getDebtorAccountIBAN());
			String kontoid = database.getFieldByKontonummer(kontonummer, "kontoid", legitimationInfo);
			if (kontoid == null) {
				LOG.info(addTagUser(format("Konto nicht existent: %s", kontonummer), legitimationInfo));
				return new ReturnDataObject(false, "Konto nicht existent", "9935");
			} else if (!database.isOperationPermit(legitimationInfo, kontonummer, configColumnSct)) {
				LOG.info(addTagUser(format("Konto darf Buchung nicht ausführen: %s", kontonummer), legitimationInfo));
				return new ReturnDataObject(false, "Konto darf Buchung nicht ausführen", "9935");
			}
			SaldoDataObject saldoDataObject = getSaldo(kontonummer, legitimationInfo);
			HashMap<String, String> ibanBicListe = new HashMap<>();
			for (int bId = 0; bId < anzahlBuchungen; bId++) {
				buchung = paymentInfo.getCreditTransferTransactionInformationVector().get(bId);
				ibanBicListe.put(buchung.getCreditorIBAN(), buchung.getCreditorAgent());
				if (config(banking2.properties.checkSonderzeichen, "false").equals("true")) {
					if (!database.replaceNonSepaChars(buchung.getCreditorName()).equals(buchung.getCreditorName())) {
						return new ReturnDataObject(false, format("Name enthält ungültige Zeichen: %s", buchung.getCreditorName()), "9935");
					}
					if (!database.replaceNonSepaChars(buchung.getVwz()).equals(buchung.getVwz())) {
						return new ReturnDataObject(false, format("Verwendungszweck enthält ungültige Zeichen: %s", buchung.getVwz()), "9935");
					}
				}
				if (config(banking2.properties.checkPurposeCodeSCT, "true").equals("true")) {
					ReturnDataObject purposeCheck = database.checkPurposeCode(getPurposeCode(buchung), legitimationInfo);
					if (!purposeCheck.isSuccess()) {
						return purposeCheck;
					}
				}
			}
			ReturnDataObject kontenCheck = database.checkEmpfaengerKonten(kontonummer, ibanBicListe, banking2.tranactionTypes.sct, legitimationInfo);
			if (!kontenCheck.isSuccess()) {
				return kontenCheck;
			}
			Date executionTime = null;
			if (isTermin) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				String regExDate = paymentInfo.getRequestedExecutionDate();
				LOG.info(addTagUser(regExDate, legitimationInfo));
				if (regExDate != null && !regExDate.equals("1999-01-01")) {
					try {
						executionTime = sdf.parse(regExDate);
						long dd = daysDiff(new Date(), executionTime);
						if (dd < 1) {
							LOG.warn(addTagUser(format("Datum liegt nicht in der Zukunft, Tage: %s", dd), legitimationInfo));
							return new ReturnDataObject(false, "Datum für die Ausführung muss mindestens ein Tag in der Zukunft liegen", "9935");
						}
						LOG.info(addTagUser(format("Tage-Diff: %s", dd), legitimationInfo));
					} catch (ParseException e) {
						LOG.error(addTagUser(format("Datum für die Ausführung fehlt oder ist Fehlerhaft: %s", e.getMessage()), legitimationInfo), e);
						return new ReturnDataObject(false, "Datum für die Ausführung fehlt oder ist Fehlerhaft", "9935");
					}
				}
			}
			LOG.info(format("Prüfe Überziehung: Terminüberweisung: %b verfügbarer Betrag: %f CtrlSum %f", isTermin, saldoDataObject.getVerfuegbarerBetrag(), betragBuchungen));
			if (!isTermin && !saldoDataObject.isOverdrawAllowed() && saldoDataObject.getVerfuegbarerBetrag() < betragBuchungen) {
				return checkBuchungenBetrag(betragBuchungen, saldoDataObject, legitimationInfo);
			} else {
				Payments.Result inDXXX;
				String wording_1 = (isTermin) ? "Dauerauftrag" : "Buchung";
				String wording_X = (isTermin) ? "Daueraufträge" : "Buchungen";
				String wording_do = (isTermin) ? " angelegt" : " ausgeführt";
				Integer counter;
				if (isTermin) {
					counter = anzahlBuchungen == 1 ? null : database.getNextCounterDauerauftraege(legitimationInfo, true);
				} else {
					counter = database.getNextCounterDTAUS(legitimationInfo, true);
				}
				String lastInsertId = "";
				String currentTime = new SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
				String tan = "";
				if(legitimationInfo.getSecurityMethod().equals(SecurityMethod.PIN_1) || legitimationInfo.getSecurityMethod().equals(SecurityMethod.PIN_2))
				{
					tan = legitimationInfo.getTanResponse();
				}
				else
				{
					tan = "0";
				}

				for (int bId = 0; bId < anzahlBuchungen; bId++) {
					buchung = paymentInfo.getCreditTransferTransactionInformationVector().get(bId);
					if (isTermin) {
						inDXXX = database.inDauerauftraege(
								legitimationInfo, kontonummer, "", "", buchung.getCreditorName(), buchung.getAmount(), "51",
								buchung.getVwz(), tan,
								buchung.getCreditorIBAN(),
								buchung.getCreditorAgent(), counter, 6,
								executionTime, currentTime,
								derfNullSein(buchung.getEndToEndID()), derfNullSein(getPurposeCode(buchung))
						);
					} else {
						inDXXX = database.inDtaus(
								legitimationInfo, kontonummer, "", "",
								buchung.getCreditorName(), buchung.getAmount(), "51",
								derfNullSein(buchung.getVwz()), legitimationInfo.getTanResponse(),
								buchung.getCreditorIBAN(), 0,
								buchung.getCreditorAgent(), counter, "", "", null,
								"", "", null, currentTime,
								derfNullSein(buchung.getEndToEndID()), derfNullSein(getPurposeCode(buchung))
						);
					}
					if (inDXXX.isSuccess()) {
						lastInsertId = inDXXX.getIds().getFirst();
						LOG.info(addTagUser(wording_1 + wording_do + ": " + (bId + 1) + "/" + anzahlBuchungen + "/" + (buchung.getAmount() * (-1)), legitimationInfo));
						if (!isTermin) {
							database.updateSalden(buchung.getAmount(), true, kontonummer, legitimationInfo);
							database.inVorabumsaetze(kontonummer, legitimationInfo, buchung.getAmount(), true, derfNullSein(buchung.getVwz()), buchung.getCreditorAgent(), buchung.getCreditorIBAN(), buchung.getCreditorName());
						}
					} else {
						LOG.warn(addTagUser("Es trat ein Fehler bei der Übergabe auf", legitimationInfo), inDXXX.getError());
						if (anzahlBuchungen == 1) {
							return new ReturnDataObject(false, format("%s konnte nicht %s werden", wording_1, wording_do), "9035");
						} else {
							return new ReturnDataObject(false, format("Eine oder mehrere %s konnten nicht %s werden", wording_X, wording_do), "9035");
						}
					}
				}
				auftragsidentifikation = Payments.AuftragsId.generate(isSammler, legitimationInfo.getUserId(), lastInsertId, String.valueOf(counter));
				return new ReturnDataObject(true, "Auftrag Entgegengenommen", "0010").setAdditionalData(auftragsidentifikation);
			}
		} catch (SEPAParsingException e) {
			LOG.warn(addTagUser(new String(sepaPainMessage), legitimationInfo), e);
			return new ReturnDataObject(false, "SEPA Datei kann nicht gelesen werden", "9930");
		}
	}

	private static String getPurposeCode(Object buchung) {
		if (buchung instanceof CreditTransferTransactionInformation) {
			if (Objects.equals(buchung, null) || Objects.equals(((CreditTransferTransactionInformation) buchung).getPurpose(), null)) {
				return "";
			} else {
				return ((CreditTransferTransactionInformation) buchung).getPurpose().getCd();
			}
		} else if (buchung instanceof DirectDebitTransactionInformation) {
			if (Objects.equals(buchung, null) || Objects.equals(((DirectDebitTransactionInformation) buchung).getPurpose(), null)) {
				return "";
			} else {
				return ((DirectDebitTransactionInformation) buchung).getPurpose().getCd();
			}
		}
		return "";
	}

	/**
	 * Berechnet den Tagesunterschied von zwei Date's
	 *
	 * @param from start-Datum (i.d.R. kleiner)
	 * @param to   end-Datum (i.d.R. größer
	 * @return Tage dazwischen
	 */
	private static long daysDiff(Date from, Date to) {
		return daysDiff(from.getTime() - (from.getTime() % 86400000L), to.getTime());
	}

	/**
	 * Berechnet den Tages-Unterschied von 2 Timestamps
	 *
	 * @param from start-Datum (i.d.R. kleiner)
	 * @param to   end-Datum (i.d.R. größer
	 * @return Tage dazwischen
	 */
	private static long daysDiff(long from, long to) {
		return Math.round((to - from) / 86400000D); // 1000 * 60 * 60 * 24
	}

	/**
	 * Holt die gebuchten Umsätze
	 *
	 * @param kontonummer       die Kontonummer
	 * @param vonDatum          ab Datum
	 * @param bisDatum          bis Datum
	 * @param legitimationsInfo LegitimationInfo
	 * @return Byte-Array
	 */
	@Override
	public byte[] getGebuchteUmsaetze(String kontonummer, Date vonDatum, Date bisDatum, LegitimationInfo legitimationsInfo) {
		return database.getGebuchteUmsaetze(kontonummer, vonDatum, bisDatum, legitimationsInfo);
	}

	/**
	 * Holt die Vorgemerkten Umsätze
	 * soll null zurückgeben, wenn keine Vorabumsätze vorhanden sind
	 *
	 * @param kontonummer      die Kontonummer
	 * @param vonDatum         ab Datum
	 * @param bisDatum         bis Datum
	 * @param legitimationInfo Legitimationsinfo
	 * @return Byte-Array
	 */
	@Override
	public byte[] getNichtGebuchteUmsaetze(String kontonummer, java.util.Date vonDatum, java.util.Date bisDatum, LegitimationInfo legitimationInfo) {
		return database.getNichtGebuchteUmsaetze(bankCode, kontonummer, legitimationInfo, getKontowaehrung(kontonummer), vonDatum, bisDatum);
	}

	/**
	 * Holt die Kontoproduktbezeichnung aus der Datenbank
	 *
	 * @param kontonummer       Kontonummer
	 * @param legitimationsInfo LegitimationInfo
	 * @return Kontoart
	 */
	@Override
	public String getKontoproduktbezeichnung(String kontonummer, LegitimationInfo legitimationsInfo) {
		return database.getFieldByKontonummer(kontonummer, "kontoart", legitimationsInfo);
	}

	/**
	 * Gibt die Kontowährung zurück
	 *
	 * @param kontonummer Kontonummer
	 * @return eigentlich immer EUR
	 */
	@Override
	public String getKontowaehrung(String kontonummer) {
		return "EUR";
	}

	/**
	 * Prüft ob Kennung und Kunden-ID zusammen gehören
	 * Wird nicht geprüft wenn Kennung = 9999999999 = Anonyme-Anfrage
	 *
	 * @param userId     die Benutzerkennung
	 * @param customerId die Kunden-ID
	 * @return true, wenn Kennung und Kunden-ID zusammen gehören
	 */
	@Override
	public boolean isCustomerIdValid(String userId, String customerId) {
		//	Anonyme Anfrage
		if (userId != null && userId.equals("9999999999")) return true;
		String customerIdFromDb = database.getCustomerId(userId);
		//	Kundenid wird gar nicht benötigt (Konfiguration)
		if (database.isKundenIdNotRequired()) {
			//	Prüfen ob Kunde überhaupt verknüpft

			boolean userExistAndConnected = (userId != null && customerIdFromDb != null);
			//	Kundenid nicht gesetzt oder leer übergeben
			if (customerId == null || customerId.equals("") || (customerIdFromDb != null && customerIdFromDb.equals(customerId))) return userExistAndConnected;
			//	Kundenid sollte zumindestens mit der Benutzerkennung übereinstimmen
			return userExistAndConnected && userId.equals(customerId);
		}
		//	Kennung im System, mit KundenID verbunden, KundenID stimmt mit übergebener ID überein
		return customerIdFromDb != null && customerIdFromDb.equals(customerId);
	}


	private ReturnDataObject isValidDDType(PaymentInstructionInformation paymentInfo) {
		ReturnDataObject r = new ReturnDataObject(true, "");
		String type = paymentInfo.getDirectDebitType();
		if (type.equals("B2B")) {
			boolean b2ballowed = config(banking2.properties.allowedB2B, "0").equals("1");
			if (!b2ballowed) r = new ReturnDataObject(false, "B2B ist nicht erlaubt");
		} else if (!type.equals("COR1") && !type.equals("CORE")) {
			r = new ReturnDataObject(false, format("%s ist nicht erlaubt", type));
		}
		return r;
	}

	private banking2.tranactionTypes getTransactionTypeSDD(PaymentInstructionInformation paymentInfo) {
		return (paymentInfo.getDirectDebitType().equals("B2B")) ? banking2.tranactionTypes.b2b : banking2.tranactionTypes.sdd;
	}

	/**
	 * Prüft ob das Ausführungsdatum Target2-Valide ist
	 *
	 * @param paymentInfo PaymentInstructionInformation (CORE/COR1)
	 * @return falls alles passt, true
	 */
	private ReturnDataObject isValidTarget2ExecutionDate(PaymentInstructionInformation paymentInfo) {
		Date executionTime;
		try {
			executionTime = HolidaysObject.checkDueDate(
					paymentInfo.getDirectDebitType(),
					paymentInfo.getSequenceType(),
					banking2.sdf_sql.parse(paymentInfo.getRequestedCollectionDate()),
					parseInt(config(banking2.properties.cutOffHour, "23")),
					parseInt(config(banking2.properties.cutOffMinute, "59")),
					parseInt(config(banking2.properties.daysToAdd, "0")),
					parseInt(config(banking2.properties.sddDueDateAdd, "0")),
					config(banking2.properties.checkCore1, "true").equals("true")
			);
		} catch (Exception e) {
			return new ReturnDataObject(false, e.getMessage(), "9930");
		}
		Calendar executionCalendar = new GregorianCalendar();
		executionCalendar.setTime(executionTime);
		if (BankDateCalculator.isDateTarget2BankHoliday(executionCalendar)) {
			return new ReturnDataObject(false, "Ausfuehrungsdatum ist kein Banken Geschaeftstag", "9930");
		}
		return new ReturnDataObject(true, "all fine");
	}

	@SuppressWarnings("unused")
	private String replaceNonSepaChars(String string) {
		string = derfNullSein(string);
		return string.replace("/[^a-zA-Z0-9\\. \\/\\+\\-\\?:\\(\\)$,]/", " ");
	}

	/**
	 * Holt Configurationseinstellungen
	 *
	 * @param property     Einstellungs-Name
	 * @param defaultValue defaultValue
	 * @return Einstellungswert, falls nicht gesetzt die defaultValue
	 */
	private String config(String property, String defaultValue) {
		return database.config(property, defaultValue);
	}

	/**
	 * Liste aller Terminzahlungen
	 *
	 * @param legitimationsInfo legitimationsInfo
	 * @param clientProductInfo clientProductInfo
	 * @param isSammler         ob Sammler oder Einzel-Terminzahlungen zurückgegeben werden sollen
	 * @param sepaVersion       in welcher Sepa-Version die Daten zurück gegeben werden sollen
	 * @return Liste mit SEPA-Dokumente
	 */
	@Override
	public LinkedList<Document> getTerminzahlungen(LegitimationInfo legitimationsInfo, ClientProductInfo clientProductInfo, boolean isSammler, Payments.versions sepaVersion) {
		return database.getTerminzahlungen(legitimationsInfo, clientProductInfo, isSammler, sepaVersion);
	}

	/**
	 * Legt eine neue Termin Einzel/Sammel-Überweisung an
	 *
	 * @param legitimationsInfo legitimationsInfo
	 * @param sepaDescriptor    sepaDescriptor
	 * @param sepaPainMessage   sepaPainMessage
	 * @param isSammler         ob es sich um ein Sammler oder Einzel-Terminzahlung handelt
	 * @return Erfolgsobjekt
	 */
	@Override
	public ReturnDataObject submitNewTransactionSchedule(LegitimationInfo legitimationsInfo, String sepaDescriptor, byte[] sepaPainMessage, boolean isSammler) {
		return submitSCT(legitimationsInfo, sepaPainMessage, isSammler, true);
	}

	/**
	 * Löscht eine Terminzahlung anhand der Auftragsid
	 *
	 * @param legitimationsInfo      legitimationsInfo
	 * @param clientProductInfo      clientProductInfo
	 * @param auftragsidentifikation auftragsidentifikation
	 * @param isSammler              gibt an, ob es sich um einen Sammler oder Einzelbuchung handelt
	 * @return Erfolgsobjekt
	 */
	@Override
	public ReturnDataObject deleteTerminzahlung(LegitimationInfo legitimationsInfo, ClientProductInfo clientProductInfo, String auftragsidentifikation, boolean isSammler) {
		Payments.Result r = database.deleteTerminzahlung(legitimationsInfo, clientProductInfo, auftragsidentifikation, isSammler);
		if (r.isSuccess()) {
			return new ReturnDataObject(true, "Auftrag gelöscht", "0010");
		} else {
			if (r.getError() == null) {
				return new ReturnDataObject(false, "Auftrag existiert nicht bzw. wurde bereits ausgeführt", "9210");
			} else {
				LOG.warn(addTagUser(format("Fehler beim Löschen eines Terminauftrags: %s %s",auftragsidentifikation,r.getError().getMessage()), legitimationsInfo), r.getError());
				return new ReturnDataObject(false, "Es ist ein Fehler beim Löschen aufgetreten", "9930");
			}
		}
	}

	@Override
	public List<byte[]> getGebuchteCamtUmsaetze(String kontonummer, Date vonDatum, Date bisDatum, LegitimationInfo legitimationsInfo, String camtDescriptor) {
		//TODO implementieren
		return null;
	}

	@Override
	public byte[] getNichtGebuchteCamtUmsaetze(String kontonummer, Date vonDatum, Date bisDatum, LegitimationInfo legitimationsInfo) {
		//TODO implementieren
		return new byte[0];
	}

	@Override
	public List<CreditCardRevenueDataObject> getCreditCardRevenueData(String kreditkartennummer, Date von, Date bis, LegitimationInfo legitimationsInfo) {
		return null;
	}

	@Override
	public List<RecipientAccountDataObject> getEmpfaengerkontenbestand(LegitimationInfo legitimationsInfo, KontoverbindungInternational kontoverbindungInternational) {
		return null;
	}
}
