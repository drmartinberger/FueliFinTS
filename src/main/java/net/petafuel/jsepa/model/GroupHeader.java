package net.petafuel.jsepa.model;

public class GroupHeader {
    private Double controlSum;

    private int noOfTransactions;

    private String creationTime;

    private String initiatingPartyName;

    private String messageId;

    public double getControlSum() {
        return controlSum;
    }

    public void setControlSum(Double sum) {
        this.controlSum = sum;
    }

    public int getNoOfTransactions() {
        return noOfTransactions;
    }

    public void setNoOfTransactions(int size) {
        this.noOfTransactions = size;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(String creationTime) {
        this.creationTime = creationTime;
    }

    public String getInitiatingPartyName() {
        return initiatingPartyName;
    }

    public void setInitiatingPartyName(String initiatingPartyName) {
        this.initiatingPartyName = initiatingPartyName;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
