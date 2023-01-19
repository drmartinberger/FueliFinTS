package net.petafuel.fuelifints.protocol.fints3.segments;

import net.petafuel.fuelifints.FinTSVersionSwitch;
import net.petafuel.fuelifints.dataaccess.DataAccessFacade;
import net.petafuel.fuelifints.dataaccess.DataAccessFacadeManager;
import net.petafuel.fuelifints.dataaccess.dataobjects.AccountDataObject;
import net.petafuel.fuelifints.dataaccess.dataobjects.BankMessageObject;
import net.petafuel.fuelifints.dataaccess.dataobjects.ParameterDataObject;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IExecutableElement;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.model.client.ClientProductInfo;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Rueckmeldung;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Segmentkopf;
import net.petafuel.fuelifints.protocol.fints3.segments.parameter.*;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.an;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.num;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Name: Verarbeitungsvorbereitung
 * Typ: Segment
 * Segmentart: Administration
 * Kennung: HKVVB
 * Sender: Kunde
 */

public class HKVVB extends Segment implements IExecutableElement {

    private static final Logger LOG = LogManager.getLogger(HKVVB.class);

    @Element(
            description = {@ElementDescription(number = 1)})
    private Segmentkopf segmentkopf;

    @Element(
            description = {@ElementDescription(number = 2,
                    length = -3)})
    @num
    private Integer bpd_version;

    @Element(
            description = {@ElementDescription(number = 3,
                    length = -3)})
    @num
    private Integer upd_Version;

    @Element(
            description = {@ElementDescription(number = 4,
                    length = -3)})
    @num
    private Integer dialogsprache;

    @Element(
            description = {@ElementDescription(number = 5,
                    length = -25)})
    @an
    private String produktbezeichnung;

    @Element(
            description = {@ElementDescription(number = 6,
                    length = -5)})
    @an
    private String produktversion;

    private List<IMessageElement> replyElements;
    private HIRMS statusElement;

    public HKVVB(byte[] message) {
        super(message);
    }

    @Override
    public String toString() {
        return "HKVVB{" +
                "segmentkopf=" + segmentkopf +
                ", bpd_version=" + bpd_version +
                ", upd_Version=" + upd_Version +
                ", dialogsprache=" + dialogsprache +
                ", produktbezeichnung='" + produktbezeichnung + '\'' +
                ", produktversion='" + produktversion + '\'' +
                '}';
    }

    @Override
    public StatusCode execute(Dialog dialog) {
        StatusCode resultCode = StatusCode.OK;
        replyElements = new LinkedList<IMessageElement>();
        ClientProductInfo clientProductInfo = dialog.getClientProductInfo();
        clientProductInfo.setCurrentClientBPDVersion(bpd_version);
        clientProductInfo.setCurrentClientUPDVersion(upd_Version);
        clientProductInfo.setDialogLanguage(dialogsprache);
        clientProductInfo.setClientProductName(produktbezeichnung);
        clientProductInfo.setClientProductVersion(produktversion);

        DataAccessFacade dataAccessFacade = DataAccessFacadeManager.getAccessFacade(dialog.getLegitimationsInfo().getBankId());


        LOG.debug("Getting Bankparameter....");
        if (!dataAccessFacade.isBpdVersionCurrent(bpd_version)) {
            //BPD nicht aktuell, schicke komplette Parameter:

            /*
            HIBPA  - M
            HIKOM  - O
            HISHV  - O
            HIKPV  - O
            Parameterdaten - O
             */
            HIKOM hikom = null;
            try {
                hikom = new HIKOM(segmentkopf.getSegmentVersion(), segmentkopf.getSegmentNummer(), dataAccessFacade.getKommunikationsParameterData(dialog.getBankId()));
            } catch (Exception ex) {
                //optionales feld muss nicht implementiert werden
            }

            ArrayList<ParameterDataObject> dataObjects = dataAccessFacade.getParameterData(FinTSVersionSwitch.FinTSVersion.FINTS_VERSION_3_0);
            LOG.debug("Anzahl dataObjects: {}", dataObjects.size());

            for (ParameterDataObject pdo : dataObjects) {
                replyElements.add(0, new ParameterSegment(pdo, segmentkopf.getSegmentNummer()));
            }
            replyElements.add(0, new HISHV(segmentkopf.getSegmentNummer()));
            replyElements.add(new HIPINS(dataAccessFacade.getPinParameter(), segmentkopf.getSegmentNummer()));
            if (hikom != null) {
                replyElements.add(0, hikom);
            }
            replyElements.add(0, new HIBPA(dataAccessFacade.getCommonBankParameters(FinTSVersionSwitch.FinTSVersion.FINTS_VERSION_3_0), segmentkopf.getSegmentNummer()));
        }
        LOG.debug("Getting Userparameter....");
        if (dialog.getLegitimationsInfo() != null && !dialog.getLegitimationsInfo().isAnonymousAccount()) {
            if (dialog.getLegitimationsInfo().isStrongAuthenticated()) {
                HIUPA hiupa = new HIUPA(dialog.getLegitimationsInfo().getUserId(), dataAccessFacade.getCurrentUpdVersion(dialog.getLegitimationsInfo()), null, segmentkopf.getSegmentNummer());
                replyElements.add(hiupa);
                ArrayList<AccountDataObject> dataObjects = dataAccessFacade.getAccountData(dialog.getLegitimationsInfo());
                LOG.debug("Found {} dataobjects!", dataObjects.size());
                for (AccountDataObject ado : dataObjects) {
                    ado.setBankleitzahl(dialog.getBankId());
                    replyElements.add(new HIUPD(ado, segmentkopf.getSegmentNummer()));
                }
            }

            if (statusElement == null/* && !dialog.getLegitimationsInfo().isAnonymousAccount()*/ && (dialog.getLegitimationsInfo().getSecurityMethod() == SecurityMethod.PIN_1 || dialog.getLegitimationsInfo().getSecurityMethod() == SecurityMethod.PIN_2)) {
                statusElement = new HIRMS(new byte[0]);
                statusElement.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HIRMS.class).setBezugssegment(segmentkopf.getSegmentNummer()).setSegmentVersion(2).build());
                List<String> zugelasseneTanVerfahren = dataAccessFacade.getZugelasseneTanVerfahren(dialog.getLegitimationsInfo());

                Rueckmeldung r = new Rueckmeldung(new byte[0]);
                r.setRueckmeldungscode("0020");
                r.setRueckmeldungstext("Dialoginitialisierung erfolgreich");

                Rueckmeldung rueckmeldung = new Rueckmeldung(new byte[0]);
                rueckmeldung.setRueckmeldungscode("3920");
                rueckmeldung.setRueckmeldungstext("Zugelassene TAN-Verfahren fuer den Benutzer");
                rueckmeldung.setRueckmeldungsparamter(zugelasseneTanVerfahren);
                statusElement.addRueckmeldung(r);
                statusElement.addRueckmeldung(rueckmeldung);
                resultCode = StatusCode.WARNING;
            }
            //Server-Nachrichten mitliefern
            List<BankMessageObject> bankMessageObjects = dataAccessFacade.getBankMessages(dialog.getLegitimationsInfo(), dialog.getClientProductInfo());
            for (BankMessageObject messageObject : bankMessageObjects) {
                if (messageObject != null) {
                    HIKIM hikim = new HIKIM(messageObject.getSubject(), messageObject.getMessage());
                    replyElements.add(hikim);
                }
            }
        } else {
            LOG.debug("no legitimationinfo or anonymous request");
        }
        LOG.debug("....HKVVB executing finished!");
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

    public String getProduktbezeichnung() {
        return produktbezeichnung;
    }
}
