package net.petafuel.fuelifints.dataaccess;

import net.petafuel.fuelifints.FinTSVersionSwitch;
import net.petafuel.fuelifints.dataaccess.dataobjects.*;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.model.client.ClientProductInfo;
import net.petafuel.fuelifints.model.client.LegitimationInfo;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Betrag;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.KontoverbindungInternational;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.KontoverbindungNational;
import net.petafuel.fuelifints.support.Payments;
import net.petafuel.jsepa.model.Document;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Facade für den Zugriff auf die Daten, die vom FueliFinTS an den Nutzer übermittelt werden können
 * <p/>
 * Die Implementierung erfolgt abhängig davon, welches Bankingsystem hinter dem HBCI Server steht
 */

public interface DataAccessFacade {

    /**
     * Prüft ob ein Schlüsselpar für die Bank existiert
     *
     * @param securityMethod Sicherheitsmethode
     * @param keyType        SchlüsselTyp
     * @return true wenn exisitert
     */
    boolean hasKeyPair(SecurityMethod securityMethod, String keyType);

    void updateKeyPair(SecurityMethod securityMethod, String privateKey, String publicKey, String keyType, BigInteger modulus, BigInteger publicExponent);

    String getPrivateKey(SecurityMethod securityMethod, String keyType);

    String getPublicKey(SecurityMethod securityMethod, String keyType);

    boolean addUserPublicKey(String userId, String customerId, SecurityMethod securityMethod, String pubKey, String keyType, int keyVersion, int keyNumber, BigInteger modulus, BigInteger publicExponent);

    String getUserPublicKey(String userId, String customerId, SecurityMethod securityMethod, String keyType, int keyVersion, int keyNumber);

    boolean existsUserKey(String userId, String customerId, SecurityMethod securityMethod, String keyType, int keyVersion, int keyNumber);

    String getUserSystemId(String dialogid, LegitimationInfo legitimationInfo, ClientProductInfo clientPoductInfo);

    String getOrGenerateUserSystemId(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo);

    /**
     * Gibt die aktuelle Version der Bankparameter Daten zurück
     *
     * @return aktuelle Version der Bankparameter Daten
     */
    int getCurrentBpdVersion();

    /**
     * Zeigt ob die übergebenen BDP Version auch die aktuelle Version ist.
     *
     * @param bpdVersion
     * @return true falls bpdVersion die aktuelle Version ist, ansonsten false.
     */
    boolean isBpdVersionCurrent(int bpdVersion);

    /**
     * Gibt die aktuelle Version der Userparameter Daten für die übergebene Benutzerkennung zurück
     *
     * @param legitimationInfo Benutzerkennung, für die die Version geholt werden soll
     * @return aktuelle Version der Userparameter Daten
     */
    int getCurrentUpdVersion(LegitimationInfo legitimationInfo);

    /**
     * Liefert ob übergebene UPD Version auch die aktuelle Version ist.
     *
     * @param legitimationInfo Benutzerkennung
     * @param updVersion       updVersion
     * @return true falls aktuell, ansonsten false.
     */
    boolean isUpdVersionCurrent(LegitimationInfo legitimationInfo, int updVersion);

    /**
     * Liefert Parameterdaten für die Segmentfolge Bankparameterdaten als Rückmledung auf die Dialoginitilisierung
     * des Kunden zurück.
     * <p/>
     * FinTS 3.0: Formals, D.1
     *
     * @param fintsVersion Gibt die FinTS Version an, für die Parameterdaten gefordert wurden
     * @return Liste mit Parameterdaten für alle erlaubten Geschäftsvorfälle
     */
    ArrayList<ParameterDataObject> getParameterData(FinTSVersionSwitch.FinTSVersion fintsVersion);

    /**
     * Liefert  Bankparameterdaten als Rückmeldung auf die Dialoginitilisierung
     * des Kunden zurück.
     * <p/>
     * FinTS 3.0: Formals, D.2 (HIBPA)
     *
     * @param fintsVersion Gibt die FinTS Version an, für die Parameterdaten gefordert wurden
     * @return BankParameterDaten für die gewählte HBCI Version
     */
    CommonBankParameterDataObject getCommonBankParameters(FinTSVersionSwitch.FinTSVersion fintsVersion);

    /**
     * Liefert allgemeine Informationen zu den Konten der übergebenen Benutzerkennung zurück
     * <p/>
     * FinTS 3.0: Formals, E.1 (HIUPD)
     *
     * @param legitimationInfo Legitimationsinfo zum Benutzer
     * @return AccountDataObject-Liste für alle passenden Konten
     */
    ArrayList<AccountDataObject> getAccountData(LegitimationInfo legitimationInfo);

    /**
     * Liefert die Parameter die für Pin/Tan benötigt werden.
     *
     * @return
     */
    PinParameterObject getPinParameter();

    /**
     * Liefert ob ein Auftrag (aClass repräsentiert das Segment als Klasse) für den
     * angegebenen Account gültig ist.
     *
     * @param legitimationInfo LegitimationInfo
     * @param accountId        die Kontonummer
     * @param aClass           @return
     */
    boolean operationAllowedForAccount(LegitimationInfo legitimationInfo, String accountId, Class<? extends IMessageElement> aClass);


    /**
     * Liefert ob ein Auftrag (aClass repräsentiert das Segment als Klasse) für den
     * angegebenen Account gültig ist.
     *
     * @param legitimationInfo LegitimationInfo
     * @param aClass           @return
     */
    boolean operationAllowedForAccount(LegitimationInfo legitimationInfo, Class<? extends IMessageElement> aClass);

    /**
     * Liefert den Saldo zu einem angegebenen Account.
     *
     * @param accountId
     * @param legitimationsInfo
     * @return
     */
    SaldoDataObject getSaldo(String accountId, LegitimationInfo legitimationsInfo);

    /**
     * Liefert die PIN eines Nutzers für das PIN/TAN verfahren.
     *
     * @param userId die ID des Nutzers.
     * @param pin
     * @param dialog
     * @return die PIN des Nutzers oder null.
     */
    ReturnDataObject checkUserPin(String userId, String pin, Dialog dialog);

    /**
     * Prüft ob eine TAN korrekt ist.
     *
     *
	 * @param dialogId
	 * @param legitimationInfo die Kunden-ID
	 * @param clientProductInfo
	 *@param tan              die eingegebenen TAN
	 * @param auftragsHashWert der Auftrags-Hashwert   @return true falls TAN korrekt, ansonsten false
     */
    ReturnDataObject checkTan(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, String tan, byte[] auftragsHashWert);

    /**
     * Generiert eine Auftragsreferenz zu einem Auftrag.
     *
     * @param dialogId          die Dialog-ID
     * @param clientProductInfo
     * @param auftragsHashwert  der Auftrags-Hashwert  @return die generierte Auftragsreferenz
     */
    String generateAuftragsreferenz(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, byte[] auftragsHashwert);

    /**
     * Liefert die Challenge zu einem Auftrag.
     *
     * @param dialogId
     * @param legitimationInfo         BenutzerDaten
     * @param clientProductInfo        KundenProdukt
     * @param auftragsHashwert         der Auftrags-Hashwert
     * @param tanVerfahren             das gewählte TAN verfahren das zur Durchführung genutzt werden soll
     * @param parameterChallengeKlasse Parameter zur Challenge wie Beispielsweise Beträge oder Kontonummern
     * @return die Challenge
     */
    ReturnDataObject getChallenge(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, byte[] auftragsHashwert, String tanVerfahren, List<String> parameterChallengeKlasse);

    /**
     * Reicht eine Inlandsüberweisung ein.
     *
     * @param kontoverbindungAuftraggeber die Kontoverbindung des Auftragsgebers
     * @param kontoverbindungEmpfaenger   die Kontoverbindung des Empfänger
     * @param nameEmpfaenger1             der Name des Empfängers 1
     * @param nameEmpfaenger2             der Name des Empfängers 2
     * @param textschluessel              der Textschlüssel der Überweisung
     * @param textschluesselErgaenzung    die Textschlüsselergänzung
     * @param verwendungszweckzeilen      die Verwendungszweckzeilen
     * @param betrag
     * @param endToEnd
     * @param purposeCode
     * @return true falls Auftrag angenommen, ansonsten false
     */
    ReturnDataObject submitNewTransaction(LegitimationInfo legitimationInfo, KontoverbindungNational kontoverbindungAuftraggeber, KontoverbindungNational kontoverbindungEmpfaenger, String nameEmpfaenger1, String nameEmpfaenger2, String textschluessel, String textschluesselErgaenzung, List<String> verwendungszweckzeilen, Betrag betrag, String endToEnd, String purposeCode);

    /**
     * Reicht eine SEPA-Überweisung ein.
     *
     * @param legitimationInfo LegitimationInfo
     * @param sepaDescriptor   Sepa-Bezeichner
     * @param sepaPainMessage  die SEPA-Pain-Message  @return true falls Auftrag angenommen, ansonsten false
     * @param isSammler
     */
    ReturnDataObject submitNewTransaction(LegitimationInfo legitimationInfo, String sepaDescriptor, byte[] sepaPainMessage, boolean isSammler);


    /**
     * Reicht die Pinänderung ein
     *
     * @param legitimationInfo LegitimationInfo
     * @param newPin           die Neue PIN, die für den Nutzer hinterlegt werden soll
     * @return
     */
    ReturnDataObject submitChangePin(LegitimationInfo legitimationInfo, String newPin);

    /**
     * Liefert die gebuchten Umsätze.
     *
     * @param kontonummer       die Kontonummer
     * @param vonDatum          ab Datum
     * @param bisDatum          bis Datum
     * @param legitimationsInfo LegitimationInfo
     * @return die gebuchten Umsätze als MT940-byte[]
     */
    byte[] getGebuchteUmsaetze(String kontonummer, Date vonDatum, Date bisDatum, LegitimationInfo legitimationsInfo);

    /**
     * Liefert die nicht-gebuchten Umsätze.
     *
     * @param kontonummer die Kontonummer
     * @param vonDatum    ab Datum
     * @param bisDatum    bis Datum
     * @return die nicht gebuchten Umsätze als MT942-byte[]
     */
    byte[] getNichtGebuchteUmsaetze(String kontonummer, Date vonDatum, Date bisDatum, LegitimationInfo legitimationInfo);

    /**
     * Liefert die Kontoproduktbezeichnung zu einer Kontonummer.
     *
     * @param accountId         die Kontonummer
     * @param legitimationsInfo
     * @return die Kontoproduktbezeichnung als String.
     */
    String getKontoproduktbezeichnung(String accountId, LegitimationInfo legitimationsInfo);

    /**
     * Liefert die genutzte Kontowährung zur Kontonummer.
     *
     * @param accountId die Kontonummer zu der die Währung abgefragt wird.
     * @return die verwendete Währung zum Konto.
     */
    String getKontowaehrung(String accountId);

    /**
     * Prüft ob eine Kunden-ID (customerId) korrekt ist und zur Benutzerkennung (userId) passt.
     *
     * @param userId     die Benutzerkennung
     * @param customerId die Kunden-ID
     * @return true falls die Kunden-ID verifiziert wurde, ansonsten false.
     */
    boolean isCustomerIdValid(String userId, String customerId);

    /**
     * Liefert alle zugelassen TAN Verfahren für den Konto.
     *
     * @param legitimationInfo die LegitimationInfo
     * @return die zugelassen TAN Verfahren für den Benutzer.
     */
    List<String> getZugelasseneTanVerfahren(LegitimationInfo legitimationInfo);

    /**
     * @param dialogId          die DialogID
     * @param legitimationInfo  die Legitimationsinfo
     * @param clientProductInfo die Client
     * @param TAN               die zu Entwertende TAN
     */
    void devalueTan(String dialogId, LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo, String TAN);

    /**
     * Liefert die Kommunikationsadressen unter der die Banken über HBCI erreichbar sind.
     *
     * @param bankId
     * @return
     */
    KommunikationsParameterData getKommunikationsParameterData(String bankId);

    /**
     * Funktion für Rückgabe der Bank-Mitteilungen
     *
     * @param legitimationInfo  die Legitimationsinfo
     * @param clientProductInfo die ClientProductInfo
     * @return Liste an Nachrichten
     */
    List<BankMessageObject> getBankMessages(LegitimationInfo legitimationInfo, ClientProductInfo clientProductInfo);

    /**
     * Liefert den Bestand an an Sepa Einzellastschriften zurück.
     * In diesem Fall muss im DirectDebitDataObject die SepaPainMessage und der SepaDescriptor gesetzt werden
     *
     * @param legitimationInfo die Legitmationsinfo
     * @return für jede Einzellastschrift ein DirectDebitDataObject in einer Liste
     */
    List<DirectDebitDataObject> getEinzelLastschriftBestand(LegitimationInfo legitimationInfo);

    /**
     * Liefert den Bestand an an Sepa Sammellastschriften zurück.
     *
     * @param legitimationInfo die Legitmationsinfo
     * @return für jede Einzellastschrift ein DirectDebitDataObject in einer Liste
     */
    List<DirectDebitDataObject> getSammelLastschriftBestand(LegitimationInfo legitimationInfo);

    /**
     * Kunden haben die Möglichkeit Nachrichten an das Kreditinstitut zu senden.
     *
     * @param legitimationsInfo
     * @param kontoverbindungAuftraggeber kann null sein
     * @param freitextmeldung
     * @param betreff                     kann null sein
     * @param empfaengerangaben           kann null sein
     * @return true falls die Nachricht angenommen wurden, false andernfalls.
     */
    boolean userMessageSubmitted(LegitimationInfo legitimationsInfo, KontoverbindungNational kontoverbindungAuftraggeber, String freitextmeldung, String betreff, String empfaengerangaben);

    /**
     * Liefert die BIC des aktuellen Bankinstituts.
     *
     * @return die BIC als String.
     */
    String getBic();

    LinkedList<Document> getTerminzahlungen(LegitimationInfo legitimationsInfo, ClientProductInfo clientProductInfo, boolean isSammler,  Payments.versions version);

	ReturnDataObject submitNewTransactionSchedule(LegitimationInfo legitimationsInfo, String sepaDescriptor, byte[] sepaFile, boolean isSammler);

	ReturnDataObject deleteTerminzahlung(LegitimationInfo legitimationsInfo, ClientProductInfo clientProductInfo, String auftragsidentifikation, boolean isSammler);

    /**
     * Liefert die gebuchten SEPA Umsätze.
     *
     *
     * @param kontonummer       die Kontonummer
     * @param vonDatum          ab Datum
     * @param bisDatum          bis Datum
     * @param legitimationsInfo LegitimationInfo
     * @param camtDescriptor    unterstütztes camt Format des Kundenprodukts
     * @return die gebuchten Umsätze als List<camt.052>
     */
    List<byte[]> getGebuchteCamtUmsaetze(String kontonummer, Date vonDatum, Date bisDatum, LegitimationInfo legitimationsInfo, String camtDescriptor);

    /**
     * Liefert die nicht-gebuchten SEPA Umsätze.
     *
     * @param kontonummer die Kontonummer
     * @param vonDatum    ab Datum
     * @param bisDatum    bis Datum
     * @return die nicht gebuchten Umsätze als camt.052
     */
    byte[] getNichtGebuchteCamtUmsaetze(String kontonummer, Date vonDatum, Date bisDatum, LegitimationInfo legitimationsInfo);

    /**
     * Liefert kreditkartenumsätze
     * @param kreditkartennummer die Nummer der Kreditkarte
     * @param von Datum ab wann Umsätze geliefert werden sollen
     * @param bis Datum bis wann Umsätze geliefert werden sollen
     * @param legitimationsInfo zur Überprüfung
     * @return Liste von CreditCardRevenueDataObject oder null falls keine Vorhanden sind.
     */
    List<CreditCardRevenueDataObject> getCreditCardRevenueData(String kreditkartennummer, Date von, Date bis, LegitimationInfo legitimationsInfo);

    /**
     * Liefert den Bestand an Empfängerkonten (für SEPA-Übertrag)
     * @param legitimationsInfo zur Überprüfung
     * @param kontoverbindungInternational Konto für das der Bestand zurückgeliefert werden soll
     * @return Liste mit den Empfängerkonten-Daten oder null falls keine Vorhanden sind.
     */
    List<RecipientAccountDataObject> getEmpfaengerkontenbestand(LegitimationInfo legitimationsInfo, KontoverbindungInternational kontoverbindungInternational);
}
