package net.petafuel.fuelifints.model;

import net.petafuel.fuelifints.communication.FinTSCommunicationHandler;
import net.petafuel.fuelifints.model.client.ClientProductInfo;
import net.petafuel.fuelifints.model.client.LegitimationInfo;
import net.petafuel.fuelifints.model.client.TransactionInfo;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Hashalgorithmus;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;

public class Dialog {

    private FinTSCommunicationHandler.CommunicationChannel communicationChannel;
    private ArrayList<Message> messages = new ArrayList<Message>();
    private Message currentMessage;
    private int taskId;
    private String dialogId;
    private long timeStamp;
    private boolean lastMessage; //Gibt an, ob das serverseitig die letzte Nachricht ist (FinTS 3: HKEND)
    private SecurityMethod securityMethod;
    private String bankId;
    private String userId;
    private int keyVersion;
    private int keyNumber;
    private LegitimationInfo legitimationsInfo = new LegitimationInfo();
    private ClientProductInfo clientProductInfo;
    private Integer messageNumber = 1;
    private TransactionInfo transactionInfo;
    private ErrorInfo errorInfo;
    private String sicherheitsKontrollReferenz;

    public Dialog() {
        //generiere zufällige, neue DialogId:
        SecureRandom random = new SecureRandom();
        this.dialogId = new BigInteger(130, random).toString(32);
        //legitimationsInfo = new LegitimationInfo();
        clientProductInfo = new ClientProductInfo();
        errorInfo = new ErrorInfo();
        timeStamp = System.currentTimeMillis();
    }

    /**
     * Fügt dem Dialog eine neue Nachricht hinzu.
     * Diese Nachricht wird automatisch auch als aktuelle Nachricht (currentMessage) markiert
     *
     * @param message Nachricht, die dem Dialog hinzugefügt werden soll
     */
    public void addMessage(Message message) {
        updateTimeStamp();
        if (!this.messages.contains(message)) {
            this.messages.add(message);
            this.setCurrentMessage(message);
        }
    }

    /**
     * Liefert die Dialog Id des Dialogs zurück
     *
     * @return Dialog Id des Dialog
     */
    public String getDialogId() {
        return dialogId;
    }

    /**
     * Setzt die Dialog Id des Dialogs auf den übergebenen Wert
     *
     * @param dialogId Neue Dialog Id, die im Dialog gesetzt werden soll
     */
    public void setDialogId(String dialogId) {
        updateTimeStamp();
        this.dialogId = dialogId;
    }

    /**
     * Zwei Dialog Objekte sind gleich, wenn ihre Dialog Ids übereinstimmen
     *
     * @param o Objekt, das mit dem aktuellen Dialog verglichen werden soll
     * @return true, wenn das Vergleichobjekt auch ein Dialog ist und die Dialog Ids übereinstimmen, ansonsten false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dialog)) return false;

        Dialog dialog = (Dialog) o;

        if (dialogId != null ? !dialogId.equals(dialog.dialogId) : dialog.dialogId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return dialogId != null ? dialogId.hashCode() : 0;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
        updateTimeStamp();
    }

    public Message getCurrentMessage() {
        return currentMessage;
    }

    public void setCurrentMessage(Message currentMessage) {
        updateTimeStamp();
        this.currentMessage = currentMessage;
    }

    public String getBankId() {
        return bankId;
    }

    public String getUserId() {
        return userId;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public int getKeyNumber() {
        return keyNumber;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setBankId(String bankId) {
        if (this.bankId != null || bankId == null) {
            return;
        }
        this.bankId = bankId;
    }

    public void setLegitimationInfo(LegitimationInfo legitimationInfo) {
        this.legitimationsInfo = legitimationInfo;
    }

    public LegitimationInfo getLegitimationsInfo() {
        return legitimationsInfo;
    }

    public ClientProductInfo getClientProductInfo() {
        return clientProductInfo;
    }

    public void setClientProductInfo(ClientProductInfo clientProductInfo) {
        this.clientProductInfo = clientProductInfo;
    }

    public Integer getMessageNumber() {
        return messageNumber;
    }

    public void setMessageNumber(Integer messageNumber) {
        this.messageNumber = messageNumber;
    }

    public void increaseMessageNumber() {
        messageNumber++;
    }

    public void setTransactionInfo(TransactionInfo transactionInfo) {
        this.transactionInfo = transactionInfo;
    }

    public TransactionInfo getTransactionInfo() {
        return transactionInfo;
    }

    public ErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(ErrorInfo errorInfo) {
        this.errorInfo = errorInfo;
    }

    public FinTSCommunicationHandler.CommunicationChannel getCommunicationChannel() {
        return communicationChannel;
    }

    public void setCommunicationChannel(FinTSCommunicationHandler.CommunicationChannel communicationChannel) {
        this.communicationChannel = communicationChannel;
		switch (communicationChannel) {
			case SSL:
				legitimationsInfo.setSecurityMethod(SecurityMethod.PIN_1);
				legitimationsInfo.setHashalgorithmus(new Hashalgorithmus("1:999:1".getBytes()));
				break;
			case SOCKET:
                if (legitimationsInfo.getSecurityMethod() == null) {
                    legitimationsInfo.setSecurityMethod(SecurityMethod.RDH_10);
                }
				legitimationsInfo.setHashalgorithmus(new Hashalgorithmus("1:6:1".getBytes()));
				break;
		}
    }

    public boolean isLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(boolean lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getSicherheitsKontrollReferenz() {
        return sicherheitsKontrollReferenz;
    }

    public void setSicherheitsKontrollReferenz(String sicherheitsKontrollReferenz) {
        this.sicherheitsKontrollReferenz = sicherheitsKontrollReferenz;
    }

    private void updateTimeStamp() {
        timeStamp = System.currentTimeMillis();
    }

    public long getTimeStamp() {
        if(transactionInfo != null) {
            updateTimeStamp();
        }
        return timeStamp;
    }
}
