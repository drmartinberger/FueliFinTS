package net.petafuel.fuelifints.protocol.fints3;

import net.petafuel.fuelifints.ExecutorManager;
import net.petafuel.fuelifints.FinTSServer;
import net.petafuel.fuelifints.HBCIParseException;
import net.petafuel.fuelifints.IFinTSController;
import net.petafuel.fuelifints.communication.FinTSCommunicationHandler;
import net.petafuel.fuelifints.dataaccess.DataAccessFacade;
import net.petafuel.fuelifints.dataaccess.DataAccessFacadeManager;
import net.petafuel.fuelifints.dataaccess.dataobjects.PinParameterObject;
import net.petafuel.fuelifints.exceptions.DependencyResolveException;
import net.petafuel.fuelifints.management.CommunicationManagement;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.model.Message;
import net.petafuel.fuelifints.model.client.LegitimationInfo;
import net.petafuel.fuelifints.model.client.TransactionInfo;
import net.petafuel.fuelifints.protocol.FinTSPayload;
import net.petafuel.fuelifints.protocol.fints3.annotations.ApplicantAccount;
import net.petafuel.fuelifints.protocol.fints3.annotations.Requires;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.HIRMG;
import net.petafuel.fuelifints.protocol.fints3.segments.HIRMS;
import net.petafuel.fuelifints.protocol.fints3.segments.HNHBK;
import net.petafuel.fuelifints.protocol.fints3.segments.HNHBS;
import net.petafuel.fuelifints.protocol.fints3.segments.SegmentUtil;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Bezugsnachricht;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.KontoverbindungInternational;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.KontoverbindungNational;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Rueckmeldung;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Segmentkopf;
import net.petafuel.fuelifints.support.ByteSplit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.JDKMessageDigest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class FinTS3Controller implements IFinTSController {

    private final long dialogTimeout;
    private static final Logger LOG = LogManager.getLogger(FinTS3Controller.class);

    private final List<Dialog> runningDialogs = new ArrayList<Dialog>();
    private static FinTS3Controller ourInstance;
    private ExecutorManager executor = new ExecutorManager();

    private DataAccessFacade accessFacade;
    private FinTSServer finTSServer;

    private Thread dialogCleanupThread;

    /**
     * private (Singleton) Constructor
     */
    private FinTS3Controller() {
        dialogTimeout = Long.parseLong(System.getProperty(CommunicationManagement.DIALOG_TIMEOUT_MILLIS, "10000"));
        dialogCleanupThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    try {
                        Thread.sleep(2 * 60 * 1000);
                    } catch (InterruptedException e) {
                        //ignored
                    }
                    synchronized (runningDialogs) {
                        int countBefore = runningDialogs.size();
                        Iterator<Dialog> iterator = runningDialogs.iterator();
                        while (iterator.hasNext()) {
                            Dialog dialog = iterator.next();
                            if ((System.currentTimeMillis() - dialog.getTimeStamp()) > dialogTimeout) {
                                iterator.remove();
                            }
                        }
                        if (countBefore != runningDialogs.size()) {
                            LOG.debug("cleaned up {} Dialog(s)", (countBefore - runningDialogs.size()));
                        }
                    }
                }
            }
        });
        dialogCleanupThread.setDaemon(true);
        dialogCleanupThread.setName("DialogCleanupThread");
        dialogCleanupThread.start();
    }

    public static FinTS3Controller getInstance() {
        if (ourInstance == null)
            ourInstance = new FinTS3Controller();
        return ourInstance;
    }

    /**
     * Für Tests, um nach dem Test die Insatnz vom FinTSController los zu werden.
     */
    public static void removeInstance() {
        ourInstance = null;
    }

    @Override
    public boolean newRequest(final FinTSPayload payload, FinTSCommunicationHandler.CommunicationChannel channel) {
        //Dialog ID holen, neue Message erstellen, neue oder existierenden Dialog suchen
        String dialogId = "0";
        Integer nachrichtenNummer = 1;
        try {
            //Dialog ID steht in HNHBK, Element Nummer 4:
            List<byte[]> segments = ByteSplit.split(payload.getPayload(), ByteSplit.MODE_SEGMENT);
            if (segments.size() > 0) {
                HNHBK hnhbk = new HNHBK(segments.get(0));
                hnhbk.parseElement();
                dialogId = hnhbk.getDialogId();
                nachrichtenNummer = hnhbk.getNachrichtennummer();

                Message requestMessage = new Message(payload.getTaskId(), new ArrayList<IMessageElement>(), hnhbk.getDialogId());
                Dialog dialog = getNewOrExistingDialog(hnhbk.getDialogId());

                dialog.setCommunicationChannel(channel);
                dialog.addMessage(requestMessage);
                LOG.info(dialog);
                /*
                if (dialog.getMessageNumber() != 1 && (dialog.getMessageNumber() != hnhbk.getNachrichtennummer() - 1)) {
                    replyWithError(payload.getTaskId(), "9010::Unbekannter Nachrichtenaufbau");
                    return false;
                }
                */

                dialog.setMessageNumber(hnhbk.getNachrichtennummer());
                FinTS3Decryptor decryptor = new FinTS3Decryptor();
                this.executor.addToDecryptionQueue(dialog, payload, decryptor);
                return true;
            }
        } catch (Exception e) {
            LOG.error("Interner Fehler", e);
        }

        HNHBK hnhbk = new HNHBK(new byte[0]);
        hnhbk.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HNHBK.class).setSegmentVersion(3).setSegmentNumber(1).build());
        hnhbk.setDialog_id(dialogId);
        hnhbk.setNachrichtennummer(nachrichtenNummer);
        hnhbk.setBezugsnachricht(new Bezugsnachricht((dialogId + ":" + nachrichtenNummer).getBytes()));
        hnhbk.setNachrichtengroesse("012345678901");
        hnhbk.setHbci_version(300);
        HIRMG hirmg = new HIRMG(new byte[0]);
        hirmg.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentVersion(2).setSegmentKennung(HIRMG.class).setSegmentNumber(2).build());
        hirmg.setRueckmeldung(Rueckmeldung.getRueckmeldung("9800"));
        HIRMS hirms = new HIRMS(new byte[0]);
        hirms.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentVersion(2).setSegmentKennung(HIRMS.class).setSegmentNumber(3).build());
        hirms.addRueckmeldung(Rueckmeldung.getRueckmeldung("9951"));
        HNHBS hnhbs = new HNHBS(new byte[0]);
        hnhbs.setNachrichtennummer(nachrichtenNummer);
        hnhbs.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentVersion(1).setSegmentNumber(4).setSegmentKennung(HNHBS.class).build());

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write(hnhbk.getHbciEncoded());
            byteArrayOutputStream.write('\'');
            byteArrayOutputStream.write(hirmg.getHbciEncoded());
            byteArrayOutputStream.write('\'');
            byteArrayOutputStream.write(hirms.getHbciEncoded());
            byteArrayOutputStream.write('\'');
            byteArrayOutputStream.write(hnhbs.getHbciEncoded());
            byteArrayOutputStream.write('\'');
            byteArrayOutputStream.flush();
        } catch (IOException e) {
            //unrepairable error
        }

        byte[] respondBytes = byteArrayOutputStream.toByteArray();
        int length = respondBytes.length;
        byte[] lengthBytes = String.format("%012d", length).getBytes();
        System.arraycopy(lengthBytes, 0, respondBytes, 10, 12);
        String responseString = "error";
        try {
            responseString = new String(respondBytes, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            LOG.error("Exception", e);
        }

        Thread t = new Thread() {
            @Override
            public void run() {
                //Fehler beim Parsen der Dialog ID - Fehler zurückgeben, weitere Verarbeitung abbrechen
                replyWithError(payload.getTaskId(), getName());
            }
        };
        t.setName(responseString);
        t.start();

        return false;
    }

    /**
     * For testing purposes
     *
     * @param manager new Executormanager to be used
     */
    @Override
    public void setExecutor(ExecutorManager manager) {
        this.executor = manager;
    }

    private Dialog getNewOrExistingDialog(String requestId) throws HBCIParseException {
        if (requestId == null)
            throw new HBCIParseException("no dialogid given");
        if (requestId.equals("0")) {
            //Neue Nachricht, MessageId wurde bisher noch nicht gesetzt.
            Dialog newDialog = new Dialog();
            synchronized (runningDialogs) {
                runningDialogs.add(newDialog);
            }
            return newDialog;
        } else {
            synchronized (runningDialogs) {
                for (Dialog currentDialog : runningDialogs) {
                    if (currentDialog.getDialogId().equals(requestId))
                        return currentDialog;
                }
            }
        }
        throw new HBCIParseException("Unknown dialogId");
    }

    private Dialog getExistingDialog(int taskId) {
        synchronized (runningDialogs) {
            for (Dialog currentDialog : runningDialogs) {
                if (currentDialog.getTaskId() == taskId)
                    return currentDialog;
            }
        }
        return null;
    }

    private void updateDialog(Dialog dialog) {

    }

    public void updateLegitimationInfo(String requestId, LegitimationInfo legitimationInfo) {
        synchronized (runningDialogs) {
            for (Dialog currentDialog : runningDialogs) {
                if (currentDialog.getDialogId().equals(requestId))
                    currentDialog.setLegitimationInfo(legitimationInfo);
            }
        }
    }

    /**
     * Called by {@link net.petafuel.fuelifints.protocol.fints3.FinTS3Decryptor} when decrypting is done (iof necessary)
     *
     * @param payload {@link FinTSPayload} Object containing the decrypted payload
     */
    @Override
    public void finishedDecryption(FinTSPayload payload, Dialog dialog) {
        FinTS3Parser parser = new FinTS3Parser();
        executor.addToParseQueue(dialog, payload, parser);
    }

    /**
     * Called by {@link net.petafuel.fuelifints.protocol.fints3.FinTS3Parser} when it finished its parsing
     *
     * @param taskId        taskId of FinTS request
     * @param currentDialog
     */
    @Override
    public void finishedParsing(int taskId, Dialog currentDialog) {
        LOG.trace("finishedParsing called; taskId: {}", taskId);
        currentDialog.setTaskId(taskId);
        if (currentDialog.getCurrentMessage().getUserId() != null) {
            currentDialog.setUserId(currentDialog.getCurrentMessage().getUserId());
        }
        currentDialog.setBankId(currentDialog.getCurrentMessage().getBankId());
        executor.addToExecuteQueue(currentDialog, new FinTS3Executor());
    }

    @Override
    public void injectDependencies(IMessageElement elem, Dialog dialog) throws DependencyResolveException {
        LOG.debug("Injecting dependencies for {}", elem.getClass().getSimpleName());
        // Inject dependencies into elem
        if (dialog == null) {
            throw new DependencyResolveException("Dialog nicht vorhanden.");
        }
        if (dialog.getLegitimationsInfo() == null) {
            throw new DependencyResolveException("Legitimationsinfo nicht gesetzt.");
        }
        if (dialog.getClientProductInfo() == null) {
            throw new DependencyResolveException("ClientProductInfo nicht gesetzt.");
        }

        //zu Beginn die Requirement durchgehen und prüfen
        if (elem.getClass().getAnnotation(Requires.class) != null) {
            DataAccessFacade dataAccessFacade = DataAccessFacadeManager.getAccessFacade(dialog.getBankId());
            Requires.Requirement[] requirements = elem.getClass().getAnnotation(Requires.class).value();
            for (Requires.Requirement requirement : requirements) {
                switch (requirement) {
                    case EXECUTION_ALLOWED: //Prüft, ob die Ausführung des Segments für den angemeldeten Benutzer erlaubt ist
                        String accountId = "";
                        for (Field f : elem.getClass().getDeclaredFields()) {
                            //Gehe alle Felder durch, um zu schauen, ob eventuell eine Auftraggeberkontonummer gesetzt ist:
                            if (f.getAnnotation(ApplicantAccount.class) != null) {
                                f.setAccessible(true);
                                try {
                                    Object o = f.get(elem);
                                    if (o != null) {
                                        if (o instanceof KontoverbindungNational)
                                            accountId = ((KontoverbindungNational) o).getKontonummer();
                                        else if (o instanceof KontoverbindungInternational)
                                            accountId = SegmentUtil.ibanToAccountNr(((KontoverbindungInternational) o).getIban());
                                    }
                                } catch (IllegalAccessException e) {
                                    LOG.error("Problem accessing " + f.getName() + " in Element " + elem.getClass().getSimpleName());
                                    throw new DependencyResolveException("Auftrag konnte aus technischen Gründen nicht verarbeitet werden.", "9999");
                                }

                            }
                        }
                        if ((!accountId.equals("") && !dataAccessFacade.operationAllowedForAccount(dialog.getLegitimationsInfo(), accountId, elem.getClass()))
                                || !dataAccessFacade.operationAllowedForAccount(dialog.getLegitimationsInfo(), elem.getClass())) {
                            throw new DependencyResolveException("Benutzer hat keine Auftragsberechtigung", "9380");
                        }
                        break;
                    case TAN: //Prüfe, ob die Ausführung eine TAN benötigt und wenn ja, ob die transactioninfos stimmen:
                        PinParameterObject ppo = dataAccessFacade.getPinParameter();

                        // TAN ist nur erforderlich falls
                        // 1. Das Segment TAN erfordert und
                        // 2. der Kunde noch nicht stark authenifiziert wurde
                        if (ppo.isTanErforderlich(elem.getClass().getSimpleName()) && !dialog.getLegitimationsInfo().isStrongAuthenticated()) {
                            //für diesen Geschäftsvorfall wird eine TAN benötigt
                            if (dialog.getLegitimationsInfo().getSecurityMethod() == SecurityMethod.PIN_1 || dialog.getLegitimationsInfo().getSecurityMethod() == SecurityMethod.PIN_2) {

                                TransactionInfo transactionInfo = dialog.getTransactionInfo();
                                if (transactionInfo == null)
                                    throw new DependencyResolveException("Verarbeitung nicht möglich", "9010");
                                String hashalgorithmus = dialog.getLegitimationsInfo().getHashalgorithmus().getHashalgorithmus();
                                byte[] generatedHashFromSegment = new byte[0];
                                //das Abschließende Semikolon fehlt
                                byte[] segmentBytes = new byte[elem.getBytes().length + 1];
                                System.arraycopy(elem.getBytes(), 0, segmentBytes, 0, elem.getBytes().length);
                                segmentBytes[elem.getBytes().length] = '\'';
                                if (hashalgorithmus.equals("999")) {
                                    //RIPEMD-160
                                    JDKMessageDigest.RIPEMD160 digest = new JDKMessageDigest.RIPEMD160();
                                    generatedHashFromSegment = digest.digest(segmentBytes);
                                } else if (hashalgorithmus.equals("1")) {
                                    //SHA-1
                                    try {
                                        MessageDigest digest = MessageDigest.getInstance("SHA-1");
                                        generatedHashFromSegment = digest.digest(segmentBytes);
                                    } catch (NoSuchAlgorithmException e) {
                                        //never thrown SHA-1 is supported
                                    }
                                }
                                if (!Arrays.equals(transactionInfo.getAuftragsHashwert(), generatedHashFromSegment)) {
                                    LOG.error("Auftragshashwert stimmt nicht mit errechnetem Hashwert überein");
                                    LOG.error("Die Überweisung wird abgelehnt");
                                    throw new DependencyResolveException("Verarbeitung nicht möglich", "9010");
                                }
                                transactionInfo.setTanUsed(true);
                            }

                        }
                        break;
                    case KUNDENSYSTEM_ID:
                        String userSystemId = dialog.getClientProductInfo().getUserSystemId();
                        // FIXME: For some reason clientProductInfo of Dialog is not properly populated
                        //if (userSystemId == null || (userSystemId.equals("0") && !dialog.getLegitimationsInfo().isAnonymousAccount())) {
                        //    throw new DependencyResolveException("Kundensystem-Id fehlerhaft oder nicht vorhanden: '" + userSystemId + "'");
                        //}
                        break;
                    case USER_IDENTIFIED:
                        // FIXME: For some reason legitimationsInfo of Dialog is not properly populated
                        //if (!dialog.getLegitimationsInfo().isUserIdentified()) {
                        //    throw new DependencyResolveException("Nutzer nicht identifiziert.");
                        //}
                        break;

                }
            }
        }

        //setze benötigte Attribute
    }

    @Deprecated
    public void finishedInjecting(int taskId) {
        Dialog currentDialog = getExistingDialog(taskId);
        if (currentDialog == null) {
        }
        executor.addToExecuteQueue(currentDialog, new FinTS3Executor());
    }

    /**
     * Called by {@link Dialog) after execution of FinTS request is finished
     *
     * @param taskId taskId of FinTS request
     */
    @Override
    public void finishedExecuting(Message msg) {
        LOG.trace("finishedExecuting called; taskId: {}", msg.getTaskId());
        Dialog currentDialog = getExistingDialog(msg.getTaskId());
        if (currentDialog == null) {
            LOG.error("Dialog konnte nicht gefunden werden.");
            replyWithError(msg.getTaskId(), "Could not find dialog.");
        } else {
            currentDialog.setCurrentMessage(msg);
            FinTS3Encryptor finTS3Encryptor = new FinTS3Encryptor();
            finTS3Encryptor.setDialog(currentDialog);
            executor.addToEncryptionQueue(currentDialog, finTS3Encryptor);
        }
    }

    @Override
    public void finishedEncryption(Message msg) {
        LOG.trace("finishedEncryption called; taskId: {}", msg.getTaskId());
        Dialog currentDialog = getExistingDialog(msg.getTaskId());
        if (currentDialog == null) {
            LOG.error("Dialog konnte nicht gefunden werden.");
            replyWithError(msg.getTaskId(), "Could not find dialog.");
            return;
        }

        currentDialog.addMessage(msg);
        FinTS3ReplyThread replyThread = new FinTS3ReplyThread();
        executor.addToReplyQueue(currentDialog, finTSServer, replyThread);

    }

    public void replyWithError(int taskId, HIRMS hirms) {
        Dialog dialog = getExistingDialog(taskId);
        if (dialog != null && hirms != null) {
            List<IMessageElement> messageElements = new LinkedList<IMessageElement>();
            messageElements.add(hirms);
            Message errorMessage = new Message(taskId, messageElements, dialog.getDialogId());
            dialog.setCurrentMessage(errorMessage);
            dialog.addMessage(errorMessage);
            FinTS3Encryptor encryptor = new FinTS3Encryptor();
            encryptor.setDialog(dialog);
            executor.addToEncryptionQueue(dialog, encryptor);
        }
    }

    public void replyWithError(int taskid, String error) {
        if (error != null && finTSServer != null) {
            finTSServer.respond(taskid, error.getBytes(), true);
        }
    }

    @Override
    public int getRunningDialogCount() {
        synchronized (runningDialogs) {
            return runningDialogs.size();
        }
    }

    public void setAccessFacade(DataAccessFacade accessFacade) {
        this.accessFacade = accessFacade;
    }

    @Override
    public void setFinTSServer(FinTSServer finTSServer) {
        this.finTSServer = finTSServer;
    }

    @Override
    public FinTSServer getFinTSServer() {
        return this.finTSServer;
    }

    public ExecutorManager getExecutorManager() {
        return executor;
    }
}
