package net.petafuel.fuelifints.protocol.fints3.segments;

import net.petafuel.fuelifints.dataaccess.DataAccessFacade;
import net.petafuel.fuelifints.dataaccess.DataAccessFacadeManager;
import net.petafuel.fuelifints.dataaccess.dataobjects.ReturnDataObject;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IDependentElement;
import net.petafuel.fuelifints.model.IExecutableElement;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.protocol.fints3.annotations.Requires;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Rueckmeldung;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Segmentkopf;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.an;

import java.util.List;

/**
 * Name: PIN ändern
 * Typ:Segment
 * Segmentart:  Geschäftsvorfall
 * Kennung:  HKPAE
 * Sender:  Kunde
 */
@Requires({Requires.Requirement.EXECUTION_ALLOWED,
        Requires.Requirement.KUNDENSYSTEM_ID,
        Requires.Requirement.USER_IDENTIFIED,
        Requires.Requirement.TAN})
public class HKPAE extends Segment implements IExecutableElement, IDependentElement {

    @Element(
            description = {@ElementDescription(number = 1)})
    private Segmentkopf segmentkopf;

    @Element(
            description = {@ElementDescription(number = 2,
                    length = -99)})
    @an
    private String pin;

    private HIRMS statusElement;

    public HKPAE(byte[] message) {
        super(message);
    }

    @Override
    public StatusCode execute(Dialog dialog) {
        statusElement = new HIRMS(new byte[0]);
        statusElement.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HIRMS.class).setSegmentVersion(2).setBezugssegment(segmentkopf.getSegmentNummer()).build());

        DataAccessFacade dataAccessFacade = DataAccessFacadeManager.getAccessFacade(dialog.getBankId());


        //TAN und Hashwert ok

        ReturnDataObject transactionSubmitted = dataAccessFacade.submitChangePin(dialog.getLegitimationsInfo(), this.pin);
        if (transactionSubmitted.isSuccess()) {
            //die Pinänderung wurde angenommen
            Rueckmeldung rueckmeldung = Rueckmeldung.getRueckmeldung("0020");
            statusElement.addRueckmeldung(rueckmeldung);
            //entferne TansactionInfo object vom Dialog
            rueckmeldung = Rueckmeldung.getRueckmeldung("0090");
            statusElement.addRueckmeldung(rueckmeldung);
            dialog.setTransactionInfo(null);
            return StatusCode.OK;
        } else {
            //die Pinänderung wurde abgelehnt

            Rueckmeldung rueckmeldung = Rueckmeldung.getRueckmeldung("9942");
            statusElement.addRueckmeldung(rueckmeldung);
            //TAN hingegen war ok
            rueckmeldung = Rueckmeldung.getRueckmeldung("0090");
            statusElement.addRueckmeldung(rueckmeldung);
            //entferne TansactionInfo object vom Dialog
            dialog.setTransactionInfo(null);

            return StatusCode.ERROR;
        }
    }

    @Override
    public List<IMessageElement> getReplyMessageElements() {
        return null;
    }

    @Override
    public IMessageElement getStatusElement() {
        return statusElement;
    }
}
