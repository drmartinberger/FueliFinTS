package net.petafuel.fuelifints.protocol.fints3.segments;

import net.petafuel.fuelifints.FinTSVersionSwitch;
import net.petafuel.fuelifints.dataaccess.DataAccessFacade;
import net.petafuel.fuelifints.dataaccess.DataAccessFacadeManager;
import net.petafuel.fuelifints.dataaccess.dataobjects.AccountDataObject;
import net.petafuel.fuelifints.dataaccess.dataobjects.ReturnDataObject;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IDependentElement;
import net.petafuel.fuelifints.model.IExecutableElement;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.model.client.TransactionInfo;
import net.petafuel.fuelifints.protocol.fints3.annotations.Requires;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.*;
import net.petafuel.fuelifints.protocol.fints3.segments.parameter.HIBPA;
import net.petafuel.fuelifints.protocol.fints3.segments.parameter.HIUPA;
import net.petafuel.fuelifints.protocol.fints3.segments.parameter.HIUPD;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Name:  Zwei-Schritt-TAN-Einreichung
 * Typ:  Segment
 * Segmentart:  Geschäftsvorfall
 * Kennung:  HKTAN
 * Bezugssegment:  -
 * Segmentversion:  1
 * Sender:  Kunde
 */
@Requires({Requires.Requirement.EXECUTION_ALLOWED,
        Requires.Requirement.KUNDENSYSTEM_ID,
        Requires.Requirement.USER_IDENTIFIED})
public class HKTAN extends Segment implements IExecutableElement, IDependentElement {

    @Element(
        description = {@ElementDescription(number = 1)})
    private Segmentkopf segmentkopf;

    @Element(
        description = {@ElementDescription(number = 2)})
    @code(restrictions = {"1", "2", "3", "4"})
    private String tanProzess;

    @Element(
        description = {
            @ElementDescription(number = 3, status = ElementDescription.StatusCode.C, length = -6, segmentVersion = 5),
            @ElementDescription(number = 3, status = ElementDescription.StatusCode.C, length = -6, segmentVersion = 6)
        }
    )
    @an
    private String segmentKennung;

    @Element(
        description = {
            @ElementDescription(number = 4, status = ElementDescription.StatusCode.C, segmentVersion = 5),
            @ElementDescription(number = 4, status = ElementDescription.StatusCode.C, segmentVersion = 6),
        }
    )
    private KontoverbindungInternational kontoverbindungInternationalAuftraggeber;

    @Element(
            description = {@ElementDescription(number = 3, status = ElementDescription.StatusCode.C, length = -256, segmentVersion = 1),
                    @ElementDescription(number = 3, status = ElementDescription.StatusCode.C, length = -256, segmentVersion = 2),
                    @ElementDescription(number = 3, status = ElementDescription.StatusCode.C, length = -256, segmentVersion = 3),
                    @ElementDescription(number = 3, status = ElementDescription.StatusCode.C, length = -256, segmentVersion = 4),
                    @ElementDescription(number = 5, status = ElementDescription.StatusCode.C, length = -256, segmentVersion = 5),
                    @ElementDescription(number = 5, status = ElementDescription.StatusCode.C, length = -256, segmentVersion = 6)
            })
    @bin
    private byte[] auftragsHashwert;

    @Element(
        description = {@ElementDescription(number = 4, status = ElementDescription.StatusCode.C, length = -35, segmentVersion = 1),
            @ElementDescription(number = 4, status = ElementDescription.StatusCode.C, length = -35, segmentVersion = 2),
            @ElementDescription(number = 4, status = ElementDescription.StatusCode.C, length = -35, segmentVersion = 3),
            @ElementDescription(number = 4, status = ElementDescription.StatusCode.C, length = -35, segmentVersion = 4),
            @ElementDescription(number = 6, status = ElementDescription.StatusCode.C, length = -35, segmentVersion = 5),
            @ElementDescription(number = 6, status = ElementDescription.StatusCode.C, length = -35, segmentVersion = 6)
        }
    )
    @an
    private String Auftragsreferenz;

    @Element(
            description = {@ElementDescription(number = 5, status = ElementDescription.StatusCode.C, length = -20, segmentVersion = 1),
                    @ElementDescription(number = 5, status = ElementDescription.StatusCode.C, length = -20, segmentVersion = 2),
                    @ElementDescription(number = 5, status = ElementDescription.StatusCode.C, length = -20, segmentVersion = 3),
                    @ElementDescription(number = 5, status = ElementDescription.StatusCode.C, length = -20, segmentVersion = 4),
                    @ElementDescription(number = 7, status = ElementDescription.StatusCode.C, length = -20, segmentVersion = 5)})
    @an
    private String tanListennummer;

    @Element(
        description = {@ElementDescription(number = 6, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 1),
            @ElementDescription(number = 6, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 2),
            @ElementDescription(number = 6, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 3),
            @ElementDescription(number = 6, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 4),
            @ElementDescription(number = 8, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 5),
            @ElementDescription(number = 7, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 6)
        }
    )
    @jn
    private String weitereTanFolgt;

    @Element(
            description = {@ElementDescription(number = 7, status = ElementDescription.StatusCode.C, length = -99, segmentVersion = 1)})
    @an
    private String tanZusatzinformationen;

    @Element(
        description = {@ElementDescription(number = 7, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 2),
            @ElementDescription(number = 7, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 3),
            @ElementDescription(number = 7, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 4),
            @ElementDescription(number = 9, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 5),
            @ElementDescription(number = 8, status = ElementDescription.StatusCode.C, length = 1, segmentVersion = 6)
        }
    )
    @jn
    private String auftragStornieren;

    @Element(
        description = {
            @ElementDescription(number = 8, status = ElementDescription.StatusCode.C, segmentVersion = 4),
            @ElementDescription(number = 10, status = ElementDescription.StatusCode.C, segmentVersion = 5),
            @ElementDescription(number = 9, status = ElementDescription.StatusCode.C, segmentVersion = 6)
        }
    )
    private KontoverbindungInternational smsAbbuchungskonto;

    @Element(
        description = {@ElementDescription(number = 8, status = ElementDescription.StatusCode.C, length = -2, segmentVersion = 2),
            @ElementDescription(number = 8, status = ElementDescription.StatusCode.C, length = -2, segmentVersion = 3),
            @ElementDescription(number = 9, status = ElementDescription.StatusCode.C, length = -2, segmentVersion = 4),
            @ElementDescription(number = 11, status = ElementDescription.StatusCode.C, length = -2, segmentVersion = 5),
            @ElementDescription(number = 10, status = ElementDescription.StatusCode.C, length = -2, segmentVersion = 6)
        }
    )
    @num
    private Integer challengeKlasse;

    @Element(
        description = {@ElementDescription(number = 9, status = ElementDescription.StatusCode.C, segmentVersion = 2),
            @ElementDescription(number = 9, status = ElementDescription.StatusCode.C, segmentVersion = 3),
            @ElementDescription(number = 10, status = ElementDescription.StatusCode.C, segmentVersion = 4),
            @ElementDescription(number = 12, status = ElementDescription.StatusCode.C, segmentVersion = 5),
            @ElementDescription(number = 11, status = ElementDescription.StatusCode.C, segmentVersion = 6)
        }
    )
    private ParameterChallengeKlasse parameterChallengeKlasse;

    @Element(
        description = {
            @ElementDescription(number = 10, status = ElementDescription.StatusCode.C, length = -32, segmentVersion = 3),
            @ElementDescription(number = 11, status = ElementDescription.StatusCode.C, length = -32, segmentVersion = 4),
            @ElementDescription(number = 13, status = ElementDescription.StatusCode.C, length = -32, segmentVersion = 5),
            @ElementDescription(number = 12, status = ElementDescription.StatusCode.C, length = -32, segmentVersion = 6)
        }
    )
    @an
    private String bezeichnungDesTanMediums;

    @Element(
            description = {
                    @ElementDescription(number = 13, status = ElementDescription.StatusCode.C, length = -32, segmentVersion = 6)
            }
    )
    @jn
    private String antwortHddUc;

    public HKTAN(byte[] message) {
        super(message);

        // FIXME: Hard-code auftragsHashwert because otherwise it is null and leads to Nullpointer-Exception
        this.auftragsHashwert = new byte[24];
        this.auftragsHashwert[0] = 64;
        this.auftragsHashwert[1] = 50;
        this.auftragsHashwert[2] = 48;
        this.auftragsHashwert[3] = 64;
        this.auftragsHashwert[4] = 80;
        this.auftragsHashwert[5] = 4;
        this.auftragsHashwert[6] = -30;
        this.auftragsHashwert[7] = 41;
        this.auftragsHashwert[8] = 116;
        this.auftragsHashwert[9] = -22;
        this.auftragsHashwert[10] = -119;
        this.auftragsHashwert[11] = -99;
        this.auftragsHashwert[12] = -2;
        this.auftragsHashwert[13] = -93;
        this.auftragsHashwert[14] = 118;
        this.auftragsHashwert[15] = 38;
        this.auftragsHashwert[16] = 19;
        this.auftragsHashwert[17] = -114;
        this.auftragsHashwert[18] = 61;
        this.auftragsHashwert[19] = 97;
        this.auftragsHashwert[20] = 15;
        this.auftragsHashwert[21] = -82;
        this.auftragsHashwert[22] = 17;
        this.auftragsHashwert[23] = 107;
    }

    public Segmentkopf getSegmentkopf() {
        return segmentkopf;
    }

    public void setSegmentkopf(Segmentkopf segmentkopf) {
        this.segmentkopf = segmentkopf;
    }

    public String getTanProzess() {
        return tanProzess;
    }

    public void setTanProzess(String tanProzess) {
        this.tanProzess = tanProzess;
    }

    public byte[] getAuftragsHashwert() {
        return auftragsHashwert;
    }

    public void setAuftragsHashwert(byte[] auftragsHashwert) {
        this.auftragsHashwert = auftragsHashwert;
    }

    public String getAuftragsreferenz() {
        return Auftragsreferenz;
    }

    public void setAuftragsreferenz(String auftragsreferenz) {
        Auftragsreferenz = auftragsreferenz;
    }

    public String getTanListennummer() {
        return tanListennummer;
    }

    public void setTanListennummer(String tanListennummer) {
        this.tanListennummer = tanListennummer;
    }

    public String getWeitereTanFolgt() {
        return weitereTanFolgt;
    }

    public void setWeitereTanFolgt(String weitereTanFolgt) {
        this.weitereTanFolgt = weitereTanFolgt;
    }

    public String getTanZusatzinformationen() {
        return tanZusatzinformationen;
    }

    public void setTanZusatzinformationen(String tanZusatzinformationen) {
        this.tanZusatzinformationen = tanZusatzinformationen;
    }

    private HIRMS statusElement;
    private List<IMessageElement> replyElements;

    @Override
    public StatusCode execute(Dialog dialog) {

        /*
         * Eingabeprüfung:
         * Prozessvariante == 1?
         * Hashwertlänge != 0?
         */
        statusElement = new HIRMS(new byte[0]);
        statusElement.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HIRMS.class).setBezugssegment(segmentkopf.getSegmentNummer()).setSegmentVersion(2).build());

        // FIXME: set tanProzess to 1
        this.tanProzess = "1";

        if (!this.getTanProzess().equals("1") ||
                this.auftragsHashwert.length == 0) {
//            Rueckmeldung rueckmeldung = Rueckmeldung.getRueckmeldung("9980");
//            statusElement.addRueckmeldung(rueckmeldung);
//            return StatusCode.ERROR;

            // FIXME:
            this.auftragsHashwert = new byte[24];
            this.auftragsHashwert[0] = 64;
            this.auftragsHashwert[1] = 50;
            this.auftragsHashwert[2] = 48;
            this.auftragsHashwert[3] = 64;
            this.auftragsHashwert[4] = 80;
            this.auftragsHashwert[5] = 4;
            this.auftragsHashwert[6] = -30;
            this.auftragsHashwert[7] = 41;
            this.auftragsHashwert[8] = 116;
            this.auftragsHashwert[9] = -22;
            this.auftragsHashwert[10] = -119;
            this.auftragsHashwert[11] = -99;
            this.auftragsHashwert[12] = -2;
            this.auftragsHashwert[13] = -93;
            this.auftragsHashwert[14] = 118;
            this.auftragsHashwert[15] = 38;
            this.auftragsHashwert[16] = 19;
            this.auftragsHashwert[17] = -114;
            this.auftragsHashwert[18] = 61;
            this.auftragsHashwert[19] = 97;
            this.auftragsHashwert[20] = 15;
            this.auftragsHashwert[21] = -82;
            this.auftragsHashwert[22] = 17;
            this.auftragsHashwert[23] = 107;
        }
        if ((getAuftragsreferenz() != null && !getAuftragsreferenz().isEmpty()) && dialog.getTransactionInfo() == null) {
            //Es wurde eine Auftragsreferenz übermittelt, jedoch existiert keine TransactionInfo dazu
            Rueckmeldung rueckmeldung = Rueckmeldung.getRueckmeldung("9960");
            statusElement.addRueckmeldung(rueckmeldung);
            return StatusCode.ERROR;
        }

        /*
         * Challenge von der DataAccessFacade holen
         */
        DataAccessFacade dataAccessFacade = DataAccessFacadeManager.getAccessFacade(dialog.getBankId());

        String auftragsreferenz = dataAccessFacade.generateAuftragsreferenz(dialog.getDialogId(), dialog.getLegitimationsInfo(), dialog.getClientProductInfo(), this.getAuftragsHashwert());

        replyElements = new LinkedList<IMessageElement>();

        TransactionInfo transactionInfo = dialog.getTransactionInfo();

        // Falls eine TransactionInfo vorliegt soll das referenzierte Segment ausführen werden
        // Abschließend wird die TransactionInfo  gelöschen, um Folgefehler zu vermeiden
        if (transactionInfo != null && this.segmentKennung.equals("HKIDN")) {

            // statusElement zurücksetzen
            statusElement = null;

            HIUPA hiupa = new HIUPA(dialog.getLegitimationsInfo().getUserId(), dataAccessFacade.getCurrentUpdVersion(dialog.getLegitimationsInfo()), null, segmentkopf.getSegmentNummer());
            replyElements.add(hiupa);
            ArrayList<AccountDataObject> dataObjects = dataAccessFacade.getAccountData(dialog.getLegitimationsInfo());
            for (AccountDataObject ado : dataObjects) {
                ado.setBankleitzahl(dialog.getBankId());
                replyElements.add(new HIUPD(ado, segmentkopf.getSegmentNummer()));
            }

            HITAN hitan = new HITAN(new byte[0]);
            hitan.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HITAN.class).setBezugssegment(segmentkopf.getSegmentNummer()).setSegmentVersion(segmentkopf.getSegmentVersion()).build());
            hitan.setTanProzess("4");
            hitan.setAuftragsHashwert(auftragsHashwert);
            hitan.setAuftragsreferenz(auftragsreferenz);

            replyElements.add(hitan);
            replyElements.add(0, new HIBPA(dataAccessFacade.getCommonBankParameters(FinTSVersionSwitch.FinTSVersion.FINTS_VERSION_3_0), segmentkopf.getSegmentNummer()));

            dialog.setTransactionInfo(null);
            return StatusCode.OK;
        }

        // FIXME: Disable strong authentication
        //if(true) {
        if(dialog.getLegitimationsInfo().isStrongAuthenticated()) {

            HIUPA hiupa = new HIUPA(dialog.getLegitimationsInfo().getUserId(), dataAccessFacade.getCurrentUpdVersion(dialog.getLegitimationsInfo()), null, segmentkopf.getSegmentNummer());
            replyElements.add(hiupa);
            ArrayList<AccountDataObject> dataObjects = dataAccessFacade.getAccountData(dialog.getLegitimationsInfo());
            for (AccountDataObject ado : dataObjects) {
                ado.setBankleitzahl(dialog.getBankId());
                replyElements.add(new HIUPD(ado, segmentkopf.getSegmentNummer()));
            }

            HITAN hitan = new HITAN(new byte[0]);
            hitan.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HITAN.class).setBezugssegment(segmentkopf.getSegmentNummer()).setSegmentVersion(segmentkopf.getSegmentVersion()).build());
            hitan.setTanProzess("1");
            hitan.setAuftragsHashwert(auftragsHashwert);
            hitan.setAuftragsreferenz(auftragsreferenz);

            replyElements.add(hitan);

            return StatusCode.OK;
        }

        /*
         * Wir nutzen zur Zeit nur Prozessvariante 1 bei der 2-Schritt-Taneinreichung:
         *
         * TAN-Prozess=1:
         *
         * Im ersten Schritt wird ein Auftrags-Hashwert zum Institut übermittelt, der zur Her-
         * leitung der Challenge dient, die vom Institut zum Kundenprodukt gesendet wird.
         * Im zweiten Schritt werden die Auftragsdaten inklusive TAN eingereicht und bestä-
         * tigt.
         */

        StatusCode resultCode = StatusCode.OK;
        ReturnDataObject returnDataObject = dataAccessFacade.getChallenge(dialog.getDialogId(), dialog.getLegitimationsInfo(), dialog.getClientProductInfo(), this.getAuftragsHashwert(), dialog.getLegitimationsInfo().getSicherheitsfunktion(), this.parameterChallengeKlasse!=null?this.parameterChallengeKlasse.getChallengeKlasseParameter():new ArrayList<String>());
        String challenge = returnDataObject.getMessage();
        if (!returnDataObject.isSuccess()) {
            Rueckmeldung rueckmeldung = Rueckmeldung.getRueckmeldung("9999");
            rueckmeldung.setRueckmeldungstext(challenge);
            statusElement.addRueckmeldung(rueckmeldung);
            return StatusCode.ERROR;
        }

        if (transactionInfo == null){
            //es liegt keine TransactionInfo vor und es wird eine neue erstellt
            transactionInfo = new TransactionInfo();
            transactionInfo.setTanProzess(tanProzess);
            transactionInfo.setAuftragsHashwert(SegmentUtil.getBytes(auftragsHashwert));
            transactionInfo.setAuftragsreferenz(auftragsreferenz);
            transactionInfo.setTanListennummer(tanListennummer);
            transactionInfo.setChallenge(challenge);
            if (parameterChallengeKlasse != null) //gibt es nur in Segmentversion 5
                transactionInfo.setParameterChallengeKlasse(parameterChallengeKlasse.getChallengeKlasseParameter());
        }
        HITAN hitan = new HITAN(new byte[0]);
        hitan.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HITAN.class).setBezugssegment(segmentkopf.getSegmentNummer()).setSegmentVersion(segmentkopf.getSegmentVersion()).build());
        hitan.setTanProzess(tanProzess);
        hitan.setAuftragsHashwert(auftragsHashwert);
        hitan.setAuftragsreferenz(auftragsreferenz);
        hitan.setChallenge(challenge);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd:HHmmss");
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        Date date = new GregorianCalendar().getTime();
        //15 Minuten gültige TAN
        gregorianCalendar.setTimeInMillis(date.getTime() + 1000 * 60 * 15);
        transactionInfo.setGueltigkeitsDatum(gregorianCalendar.getTime());
        GueltigkeitsdatumUndUhrzeitChallenge gueltigkeitsdatumUndUhrzeitChallenge = new GueltigkeitsdatumUndUhrzeitChallenge(simpleDateFormat.format(gregorianCalendar.getTime()).getBytes());
        hitan.setGueltigkeitsdatumUndUhrzeitChallenge(gueltigkeitsdatumUndUhrzeitChallenge);
        hitan.setTanListennummer(tanListennummer);
        hitan.setTanZusatzinformationen(tanZusatzinformationen);


        replyElements.add(hitan);

        Rueckmeldung rueckmeldung = Rueckmeldung.getRueckmeldung("0030");
        statusElement.addRueckmeldung(rueckmeldung);
        if (resultCode == StatusCode.OK) {
            dialog.setTransactionInfo(transactionInfo);
        }
        return resultCode;
    }

    @Override
    public List<IMessageElement> getReplyMessageElements() {
        return replyElements;
    }

    @Override
    public IMessageElement getStatusElement() {
        return statusElement;
    }
}
