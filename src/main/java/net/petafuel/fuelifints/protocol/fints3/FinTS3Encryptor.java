package net.petafuel.fuelifints.protocol.fints3;

import net.petafuel.fuelifints.cryptography.Cryptography;
import net.petafuel.fuelifints.cryptography.CryptographyFactory;
import net.petafuel.fuelifints.dataaccess.DataAccessFacade;
import net.petafuel.fuelifints.dataaccess.DataAccessFacadeManager;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.model.Message;
import net.petafuel.fuelifints.model.client.ClientProductInfo;
import net.petafuel.fuelifints.model.client.LegitimationInfo;
import net.petafuel.fuelifints.protocol.IFinTSEncryptor;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.AbstractElement;
import net.petafuel.fuelifints.protocol.fints3.segments.HIRMG;
import net.petafuel.fuelifints.protocol.fints3.segments.HIRMS;
import net.petafuel.fuelifints.protocol.fints3.segments.HNVSD;
import net.petafuel.fuelifints.protocol.fints3.segments.HNVSK;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Rueckmeldung;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Schluesselname;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Segmentkopf;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.SicherheitsdatumUndUhrzeit;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Sicherheitsidentifikationsdetails;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Sicherheitsprofil;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Verschluesselungsalgorithmus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

/**
 * Verschlüsselt die Antwortnachrichten.
 * Fügt die Segmente HNVSK und HNVSD dazu und füllt diese entsprechend.
 */
public class FinTS3Encryptor implements IFinTSEncryptor {
    private static final Logger LOG = LogManager.getLogger(FinTS3Encryptor.class);

    private Dialog dialog;

    @Override
    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void run() {
        if (dialog.getLegitimationsInfo().isAnonymousAccount()/* || dialog.getLegitimationsInfo().userKeysSubmitted()*/) {
            /*
             * Anonyme Nachricht erhalten, keine verschlüsselung nötig.
             */
            dialog.getLegitimationsInfo().setUserKeysSubmitted(false);
            FinTS3Controller.getInstance().finishedEncryption(dialog.getCurrentMessage());
            return;
        }

        SecurityMethod securityMethod = dialog.getLegitimationsInfo().getSecurityMethod();
        LOG.debug("Encrypting with securitymethod: {}", securityMethod);
        Cryptography cryptography = CryptographyFactory.getCryptography(securityMethod);

        Message message = dialog.getCurrentMessage();
        byte[] encryptionKey = cryptography.generateKey();

        LOG.debug("EncryptionKey length: {}", encryptionKey.length);

        byte[] toEncrypt = message.getMessageBytes();
        LOG.debug("ToEncryptLength: {}, ToEncrypt: {}", toEncrypt.length, new String(toEncrypt));
        byte[] encryptedMessage = cryptography.encryptMessage(toEncrypt, encryptionKey, dialog.getBankId());
        LOG.debug("encryptedMessageLength: {}", encryptedMessage.length);
        byte[] encryptedKey = cryptography.encryptEncryptionKey(encryptionKey, dialog.getBankId(), dialog.getUserId());
        if (encryptedKey.length == 0) {
            //Fehler
            List<IMessageElement> elements = new LinkedList<IMessageElement>();

            HIRMG hirmg = new HIRMG(new byte[0]);
            hirmg.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HIRMG.class).setSegmentNumber(2).setSegmentVersion(2).build());
            hirmg.setRueckmeldung(Rueckmeldung.getRueckmeldung("9999"));
            elements.add(hirmg);
            HIRMS hirms = new HIRMS(new byte[0]);
            hirms.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentVersion(2).setSegmentNumber(3).setSegmentKennung(HIRMS.class).build());
            Rueckmeldung rueckmeldung = new Rueckmeldung(new byte[0]);
            rueckmeldung.setRueckmeldungscode("9999");
            rueckmeldung.setRueckmeldungstext("Bei der Verarbeitung ist ein Fehler aufgetreten.");
            hirms.addRueckmeldung(rueckmeldung);
            elements.add(hirms);

            Message message1 = new Message(message.getTaskId(), elements, dialog.getDialogId());
            message1.setSegmentCount(message.getSegmentCount());
            FinTS3Controller.getInstance().finishedEncryption(message1);
            return;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write('@');
            byteArrayOutputStream.write(Integer.toString(encryptedMessage.length).getBytes("ISO-8859-1"));
            byteArrayOutputStream.write('@');
            byteArrayOutputStream.write(encryptedMessage);
        } catch (IOException ex) {
        }
        HNVSD hnvsd = new HNVSD(new byte[0]);
        //hnvsd.setSegmentkopf(new Segmentkopf("HNVSD:999:1".getBytes()));
        hnvsd.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HNVSD.class).setSegmentNumber(999).setSegmentVersion(1).build());
        hnvsd.setVerschluesselteDaten(byteArrayOutputStream.toByteArray());
        List<IMessageElement> elements = new LinkedList<IMessageElement>();
        elements.add(hnvsd);

        HNVSK hnvsk = new HNVSK(new byte[0]);
        //hnvsk.setSegmentkopf(new Segmentkopf("HNVSK:998:3".getBytes()));
        hnvsk.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HNVSK.class).setSegmentNumber(998).setSegmentVersion(3).build());

        Sicherheitsprofil sicherheitsprofil = new Sicherheitsprofil(securityMethod.getHbciDEG());
        hnvsk.setSicherheitsprofil(sicherheitsprofil);
        if (securityMethod == SecurityMethod.PIN_1 || securityMethod == SecurityMethod.PIN_2)
            hnvsk.setSicherheitsfunktion("998");
        else
            hnvsk.setSicherheitsfunktion("4");
        hnvsk.setSicherheitslieferant("1");
        DataAccessFacade dataAccessFacade = DataAccessFacadeManager.getAccessFacade(dialog.getBankId());
        ClientProductInfo clientProductInfo = dialog.getClientProductInfo();
        LegitimationInfo legitimationInfo = dialog.getLegitimationsInfo();

        String userSystemId = null;
        try {
            userSystemId = dataAccessFacade == null ? "0" : dataAccessFacade.getUserSystemId(dialog.getDialogId(), legitimationInfo, clientProductInfo);
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        Sicherheitsidentifikationsdetails sicherheitsidentifikationsdetails = new Sicherheitsidentifikationsdetails(("2::" + (userSystemId == null ? 0 : userSystemId)).getBytes());
        hnvsk.setSicherheitsidentifikationsdetails(sicherheitsidentifikationsdetails);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd:HHmmss");
        GregorianCalendar gregorianCalendar = (GregorianCalendar) GregorianCalendar.getInstance();
        SicherheitsdatumUndUhrzeit sicherheitsdatumUndUhrzeit = new SicherheitsdatumUndUhrzeit(("1:" + simpleDateFormat.format(gregorianCalendar.getTime())).getBytes());
        hnvsk.setSicherheitsdatumUndUhrzeit(sicherheitsdatumUndUhrzeit);
        byteArrayOutputStream.reset();
        try {
            byteArrayOutputStream.write('2');
            byteArrayOutputStream.write(':');
            byteArrayOutputStream.write(getHbciFormat(securityMethod.getOperationsModusVerschluesselung()));
            byteArrayOutputStream.write(':');
            byteArrayOutputStream.write(getHbciFormat(securityMethod.getVerschluesselungsAlgorithmus()));
            byteArrayOutputStream.write(':');
            byteArrayOutputStream.write('@');
            byteArrayOutputStream.write(Integer.toString(encryptedKey.length).getBytes());
            byteArrayOutputStream.write('@');
            byteArrayOutputStream.write(encryptedKey);
            byteArrayOutputStream.write(":6:1".getBytes());
            byteArrayOutputStream.flush();
        } catch (IOException ex) {
        }
        Verschluesselungsalgorithmus verschluesselungsalgorithmus = new Verschluesselungsalgorithmus(byteArrayOutputStream.toByteArray());
        hnvsk.setVerschluesselungsalgorithmus(verschluesselungsalgorithmus);
        String userId = dialog.getLegitimationsInfo().getUserId();
        if (userId != null) {
            if (userId.isEmpty() && !dialog.getUserId().isEmpty()) {
                userId = dialog.getUserId();
            }
        } else {
            userId = "0";
        }

        Schluesselname schluesselname = new Schluesselname(("280:" + dialog.getBankId() + ":" + AbstractElement.generateEscapedVersion(userId) + ":V:" + securityMethod.getVersionNumber() + ":1").getBytes());

        hnvsk.setSchluesselname(schluesselname);
        hnvsk.setKomprimierungsfunktion("0");

        elements.add(0, hnvsk);
        Message message1 = new Message(message.getTaskId(), elements, dialog.getDialogId());
        message1.setSegmentCount(message.getSegmentCount());
        FinTS3Controller.getInstance().finishedEncryption(message1);
    }

    private byte[] getHbciFormat(int hbciCode) {
        return Integer.toString(hbciCode).getBytes(StandardCharsets.ISO_8859_1);
    }
}
