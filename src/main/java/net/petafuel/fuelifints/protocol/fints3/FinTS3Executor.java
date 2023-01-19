package net.petafuel.fuelifints.protocol.fints3;

import net.petafuel.fuelifints.cryptography.SignatureHelper;
import net.petafuel.fuelifints.cryptography.SignatureHelperFactory;
import net.petafuel.fuelifints.exceptions.DependencyResolveException;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IDependentElement;
import net.petafuel.fuelifints.model.IExecutableElement;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.model.Message;
import net.petafuel.fuelifints.model.client.LegitimationInfo;
import net.petafuel.fuelifints.protocol.IFinTSExecutor;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.HIISA;
import net.petafuel.fuelifints.protocol.fints3.segments.HIRMG;
import net.petafuel.fuelifints.protocol.fints3.segments.HIRMS;
import net.petafuel.fuelifints.protocol.fints3.segments.HNSHA;
import net.petafuel.fuelifints.protocol.fints3.segments.HNSHK;
import net.petafuel.fuelifints.protocol.fints3.segments.Segment;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Rueckmeldung;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Schluesselname;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Segmentkopf;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.SicherheitsdatumUndUhrzeit;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Sicherheitsidentifikationsdetails;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Sicherheitsprofil;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Signaturalgorithmus;
import net.petafuel.fuelifints.support.SegmentComparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

public class FinTS3Executor implements IFinTSExecutor {

    private static final Logger LOG = LogManager.getLogger(FinTS3Executor.class);

    private Dialog dialog;

    @Override
    public Message execute() {
        //iterate through last requests' segments to find gvs and their dependencies:

        Message msg = this.dialog.getCurrentMessage();
        List<IMessageElement> messageElements = msg.getMessageElements();

        //AntwortSegmente
        List<IMessageElement> replyElements = msg.getReplyElements();

        boolean successful = false;
        boolean warning = false;
        boolean error = false;

        /* Prüfe, dass bisher kein Fehler aufgetreten ist.
         * Fehler können an dieser Stelle bereits beim Entschlüsseln, der Signaturprüfung und beim Parsen der Segmente aufgetreten sein
         */
        if (dialog.getErrorInfo() != null && !dialog.getErrorInfo().isErrorOccured()) {
            for (IMessageElement currentElement : messageElements) {
                if (currentElement instanceof IDependentElement) {// This segment has dependencies
                    try {
                        FinTS3Controller.getInstance().injectDependencies(currentElement, this.dialog);
                    } catch (DependencyResolveException e) {
                        LOG.error("DependencyResolveException", e);
                        HIRMS hirms = new HIRMS(new byte[0]);
                        hirms.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HIRMS.class).setSegmentVersion(2).setBezugssegment(((Segment) currentElement).getSegmentkopf().getSegmentNummer()).build());
                        Rueckmeldung rueckmeldung = new Rueckmeldung(new byte[0]);
                        rueckmeldung.setRueckmeldungscode("9000");
                        rueckmeldung.setRueckmeldungstext("Verarbeitung nicht möglich");
                        hirms.addRueckmeldung(rueckmeldung);
                        if (e.getErrorCode() != null && !e.getErrorCode().equals("")) {
                            Rueckmeldung exceptionMeldung = new Rueckmeldung(new byte[0]);
                            exceptionMeldung.setRueckmeldungscode(e.getErrorCode());
                            exceptionMeldung.setRueckmeldungstext(e.getMessage());
                            hirms.addRueckmeldung(exceptionMeldung);
                        }
                        replyElements.add(hirms);
                        error = true;
                        successful = false;
                        break;
                    }
                }
                /**
                 * Gehe über alle Auftrag-Segmente und führe sie aus.
                 * Jedes ausführbare Segment liefert einen StatusCode zurück der angibt,
                 * ob die Ausführung erfolgreich, Warnungen enthielt oder fehlerhaft war.
                 *
                 * Falls ein Fehler aufgetreten ist, dann wird abgebrochen, bei Warnungen kann
                 * weiter gearbeitet werden.
                 */
                if (currentElement instanceof IExecutableElement) {

                    IExecutableElement.StatusCode statusCode = ((IExecutableElement) currentElement).execute(dialog);
                    List<IMessageElement> segmentReplyElements = ((IExecutableElement) currentElement).getReplyMessageElements();
                    LOG.info("adding new reply element for element " + currentElement.getClass().getSimpleName() + " replyElement " + segmentReplyElements);
                    if (segmentReplyElements != null && segmentReplyElements.size() > 0) {
                        replyElements.addAll(segmentReplyElements);
                    }
                    switch (statusCode) {
                        case OK:
                            successful = true;
                            break;
                        case INFO:
                            //INFO kann wie WARNING behandelt werden..
                        case WARNING:
                            warning = true;
                            break;
                        case ERROR:
                            LOG.error("could not execute: {}", currentElement);
                            if (currentElement != null && ((IExecutableElement) currentElement).getStatusElement() != null)
                                LOG.error("error message: {}", new String(((IExecutableElement) currentElement).getStatusElement().getBytes()));
                            error = true;
                            break;
                    }
                    //füge alle Statuselemente hinzu, sie werden später sortiert.
                    IMessageElement statusElement = ((IExecutableElement) currentElement).getStatusElement();
                    if (statusElement != null && ((HIRMS) statusElement).getRueckmeldung() != null) {
                        replyElements.add(statusElement);
                    }
                    if (error) {
                        //Abbruch?
                        break;
                    }
                }
            }
        } else {
            error = true;
            if (dialog.getErrorInfo().getErrorSegment() != null) {
                replyElements.add(dialog.getErrorInfo().getErrorSegment());
            }
        }
        //erstelle HIRMG anhand der bisherigen Rückmeldungen
        HIRMG hirmg = buildHIRMG(successful, warning, error);
        replyElements.add(0, hirmg);


        return new Message(msg.getTaskId(), replyElements, msg.getMessageId());
    }

    @Override
    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void run() {
        Message message = this.execute();

        //HIRMS Segmente müssen zu Beginn der Nachricht stehen (jedoch nach HIRMG)
        List<IMessageElement> replySegments = message.getMessageElements();
        Collections.sort(replySegments, new SegmentComparator());

        boolean containsHiisa = false;
        for (IMessageElement messageElement : replySegments) {
            if (messageElement instanceof HIISA) {
                containsHiisa = true;
                break;
            }
        }

        if (!containsHiisa && dialog.getLegitimationsInfo().isAnonymousAccount() && dialog.getLegitimationsInfo().getSecurityMethod() != SecurityMethod.PIN_1 && dialog.getLegitimationsInfo().getSecurityMethod() != SecurityMethod.PIN_2) {
            //keine Bankschlüssel und Anonymer Dialog
            //es wird keine Signatur benötigt
            setSegmentNumbers(replySegments, 2);

            message.setSegmentCount(replySegments.size() + 1);
            FinTS3Controller.getInstance().finishedExecuting(message);
            return;
        }

        LegitimationInfo legitimationInfo = dialog.getLegitimationsInfo();
        if (legitimationInfo.getCustomerId() != null && legitimationInfo.isAnonymousAccount() && (legitimationInfo.getSecurityMethod() == SecurityMethod.PIN_1 || legitimationInfo.getSecurityMethod() == SecurityMethod.PIN_2) || (dialog.getErrorInfo() != null && dialog.getErrorInfo().isErrorOccured())) {
            //es wird keine Signatur benötigt, alle Segmente beginnen bei Nummer 2 (anonym PIN/TAN)
            setSegmentNumbers(replySegments, 2);
        } else {
            //es wird eine Signatur benötigt, alle Segmente beginnen bei Nummer 3
            setSegmentNumbers(replySegments, 3);
            createSignature(replySegments);
        }
        message.setSegmentCount(replySegments.size() + 1);
        FinTS3Controller.getInstance().finishedExecuting(message);
    }

    private void setSegmentNumbers(List<IMessageElement> replySegments, int startNumber) {
        for (IMessageElement iMessageElement : replySegments) {
            Field[] fields = iMessageElement.getClass().getDeclaredFields();
            Field segmentkopfField = null;
            for (Field f : fields) {
                f.setAccessible(true);
                if (f.getType().equals(Segmentkopf.class)) {
                    segmentkopfField = f;
                }
            }
            if (segmentkopfField == null) {
                throw new RuntimeException("could not set Segmentnumber.");
            } else {
                try {
                    Segmentkopf segmentkopf = (Segmentkopf) segmentkopfField.get(iMessageElement);

                    segmentkopf.setSegmentNummer(startNumber++);
                } catch (IllegalAccessException e) {
                    //setAccessible(true)
                }
            }
        }
    }

    private HIRMG buildHIRMG(boolean successful, boolean warning, boolean error) {
        HIRMG hirmg = new HIRMG(new byte[0]);
        Segmentkopf segmentkopf;// = new Segmentkopf("HIRMG:0:2".getBytes());
        segmentkopf = Segmentkopf.Builder.newInstance().setSegmentKennung(HIRMG.class).setSegmentVersion(2).build();
        hirmg.setSegmentkopf(segmentkopf);
        try {
            Rueckmeldung rueckmeldung;
            if (successful && warning) {
                //3000er HIRMG
                rueckmeldung = new Rueckmeldung("3060::Teilweise liegen Warnungen oder Hinweise vor.".getBytes("ISO-8859-1"));
            } else if (successful && error) {
                //9000er HIRMG
                rueckmeldung = new Rueckmeldung("9910::Die Nachricht konnte nicht ausgeführt werden.".getBytes("ISO-8859-1"));
            } else if (!successful && !warning && error) {
                //9000er HIRMG
                rueckmeldung = new Rueckmeldung("9910::Die Nachricht konnte nicht ausgeführt werden.".getBytes("ISO-8859-1"));
            } else if (!successful && (warning | error)) {
                //3000er HIRMG
                rueckmeldung = new Rueckmeldung("3920::Es liegen Warnungen oder Fehler vor.".getBytes("ISO-8859-1"));
            } else {
                //0000er HIRMG
                rueckmeldung = new Rueckmeldung("0010::Auftrag erfolgreich entgegengenommen.".getBytes("ISO-8859-1"));
            }
            hirmg.setRueckmeldung(rueckmeldung);
        } catch (UnsupportedEncodingException e) {
            //ISO-8859-1 is supported
        }
        return hirmg;
    }

    //TODO generieren von Segmente besser anpassen
    private void createSignature(List<IMessageElement> messageElements) {
        Segmentkopf segmentkopf;
        /*
        HIRMG hirmg = new HIRMG(new byte[0]);
        segmentkopf = new Segmentkopf("HIRMG:3:2".getBytes());
        Rueckmeldung rueckmeldung = new Rueckmeldung(new byte[0]);
        rueckmeldung.setRueckmeldungscode("0010");
        rueckmeldung.setRueckmeldungstext("Nachricht fehlerfrei entgegengenommen.");
        hirmg.setSegmentkopf(segmentkopf);
        hirmg.setRueckmeldung(rueckmeldung);
        messageElements.add(0, hirmg);
        */
        SecurityMethod securityMethod = dialog.getLegitimationsInfo().getSecurityMethod();
        LOG.debug(dialog.getLegitimationsInfo().getSecurityMethod());
        if (securityMethod == null) {
            securityMethod = SecurityMethod.RDH_10;
        }
        SignatureHelper signatureHelper = SignatureHelperFactory.getSignatureHelper(securityMethod);
        dialog.setSicherheitsKontrollReferenz(String.valueOf((int) Math.abs(Math.random() * Integer.MAX_VALUE)));
        String sicherheitskontrollreferenz = dialog.getSicherheitsKontrollReferenz();
        HNSHK hnshk = new HNSHK(new byte[0]);
        //segmentkopf = new Segmentkopf("HNSHK:2:4".getBytes());
        segmentkopf = Segmentkopf.Builder.newInstance().setSegmentKennung(HNSHK.class).setSegmentNumber(2).setSegmentVersion(4).build();
        hnshk.setSegmentkopf(segmentkopf);
        Sicherheitsprofil sicherheitsprofil = new Sicherheitsprofil(securityMethod.getHbciDEG());
        hnshk.setSicherheitsprofil(sicherheitsprofil);
        hnshk.setSicherheitsfunktion(dialog.getLegitimationsInfo().getSicherheitsfunktion());
        hnshk.setSicherheitskontrollreferenz(sicherheitskontrollreferenz);
        hnshk.setSicherheitsapplikationskontrollbereich("1");
        hnshk.setSicherheitslieferantenrolle("1");
        Sicherheitsidentifikationsdetails sicherheitsidentifikationsdetails = new Sicherheitsidentifikationsdetails("2".getBytes());
        hnshk.setSicherheitsidentifikationsdetails(sicherheitsidentifikationsdetails);
        hnshk.setSicherheitsreferenznummer(1000);
        GregorianCalendar gregorianCalendar = (GregorianCalendar) GregorianCalendar.getInstance();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd:HHmmss");
        SicherheitsdatumUndUhrzeit sicherheitsdatumUndUhrzeit = new SicherheitsdatumUndUhrzeit(("1:" + simpleDateFormat.format(gregorianCalendar.getTime())).getBytes());
        hnshk.setSicherheitsdatumUndUhrzeit(sicherheitsdatumUndUhrzeit);

        if (securityMethod == SecurityMethod.PIN_1 || securityMethod == SecurityMethod.PIN_2) {
            hnshk.setHashalgorithmus(dialog.getLegitimationsInfo().getHashalgorithmus());
            Schluesselname schluesselname = new Schluesselname(("280:" + dialog.getBankId() + ":100:S:0:0").getBytes()); //Default Schlüsselname für PIN/TAN Kommunikation
            hnshk.setSchluesselname(schluesselname);
            hnshk.setSignaturalgorithmus(new Signaturalgorithmus("6:10:16".getBytes()));
        } else {
            hnshk.setHashalgorithmus(dialog.getLegitimationsInfo().getHashalgorithmus());
            Schluesselname schluesselname = new Schluesselname(("280:" + dialog.getBankId() + ":0:S:1:1").getBytes()); //TODO Schlüsselname aus der facade
            hnshk.setSchluesselname(schluesselname);
            hnshk.setSignaturalgorithmus(new Signaturalgorithmus("6:10:19".getBytes()));
        }

        int segmentcount = 3;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write(hnshk.getHbciEncoded());
            byteArrayOutputStream.write('\'');
            for (IMessageElement iMessageElement : messageElements) {
                if (iMessageElement != null) {
                    segmentcount++;
                    byteArrayOutputStream.write(((Segment) iMessageElement).getHbciEncoded());
                    byteArrayOutputStream.write('\'');
                }
            }
            byteArrayOutputStream.flush();
        } catch (IOException ex) {

        }

        byte[] toSign = byteArrayOutputStream.toByteArray();

        try {
            LOG.debug("toSign: {}", new String(toSign, "ISO-8859-1"));
        } catch (UnsupportedEncodingException e) {
            LOG.error("UnsupportedEncodingException", e);
        }

        HNSHA hnsha = new HNSHA(new byte[0]);
        //segmentkopf = new Segmentkopf(("HNSHA:" + (segmentcount) + ":2").getBytes());
        segmentkopf = Segmentkopf.Builder.newInstance().setSegmentKennung(HNSHA.class).setSegmentNumber(segmentcount).setSegmentVersion(2).build();
        hnsha.setSegmentkopf(segmentkopf);
        hnsha.setSicherheitskontrollreferenz(sicherheitskontrollreferenz);
        byte[] signature = signatureHelper.sign(toSign, hnshk);
        if (signature != null) {
            byteArrayOutputStream.reset();
            try {
                byteArrayOutputStream.write('@');
                for (char s : Integer.toString(signature.length).toCharArray()) {
                    byteArrayOutputStream.write(s);
                }
                byteArrayOutputStream.write('@');
                byteArrayOutputStream.write(signature);
                byteArrayOutputStream.flush();
            } catch (IOException ex) {
            }
            hnsha.setValidierungsresultat(byteArrayOutputStream.toByteArray());
        }
        messageElements.add(0, hnshk);
        messageElements.add(hnsha);
    }
}
