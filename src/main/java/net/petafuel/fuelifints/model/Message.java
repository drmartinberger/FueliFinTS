package net.petafuel.fuelifints.model;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.HIRMS;
import net.petafuel.fuelifints.protocol.fints3.segments.Segment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Message {

    private static final Logger LOG = LogManager.getLogger(Message.class);

    private int messageNumber;
    private String messageId;
    private int taskId;
    private List<IMessageElement> messageElements;
    private SecurityMethod securityMethod;
    private String userId;
    private String bankId;
    private int segmentCount;
    private List<IMessageElement> replyElements = new ArrayList<>();

    public Message(int taskId, List<IMessageElement> messageElements, String dialogId) {
        this.taskId = taskId;
        this.messageElements = messageElements;
        this.messageId = dialogId;
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageId=" + messageId +
                ", messageNumber=" + messageNumber +
                '}';
    }

    public String getMessageId() {
        return messageId;
    }

    public void executeGVs() {

    }

    public List<IMessageElement> getMessageElements() {
        return messageElements;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public void setSecurityMethod(SecurityMethod securityMethod) {
        this.securityMethod = securityMethod;
    }

    public SecurityMethod getSecurityMethod() {
        return securityMethod;
    }

    public byte[] getMessageBytes() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        LOG.debug("IMessageElementsSize: {}", messageElements.size());
        for (int i = 0; i < messageElements.size(); i++) {
            IMessageElement messageElement = messageElements.get(i);
            if (messageElement != null) {
                try {
                    byteArrayOutputStream.write(((Segment) messageElement).getHbciEncoded());
                    byteArrayOutputStream.flush();
                } catch (IOException ex) {
                    LOG.error("IOException during creating bytes of message..", ex);
                    return new byte[0];
                }
                byteArrayOutputStream.write('\'');
                try {
                    byteArrayOutputStream.flush();
                } catch (IOException e) {
                    LOG.error("IOException during creating bytes of message..", e);
                    return new byte[0];
                }
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getBankId() {
        return bankId;
    }

    public void setSegmentCount(int segmentCount) {
        this.segmentCount = segmentCount;
    }

    public int getSegmentCount() {
        return segmentCount;
    }

    public void addReplyElement(HIRMS hirms) {
        replyElements.add(hirms);
    }

    public List<IMessageElement> getReplyElements() {
        return replyElements;
    }
}
