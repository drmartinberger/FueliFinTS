package net.petafuel.jsepa.model;

public class CreditTransferTransactionInformation {
    private Purpose purpose;
    private Double amount;

    private String endToEndId;

    private String creditorName;

    private String remittanceInfo;

    private String creditorAgent;

    private String creditorIban;

    public Purpose getPurpose() {
        return purpose;
    }

    public String getEndToEndID() {
        return endToEndId;
    }

    public void setEndToEndID(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public void setCreditorName(String creditorName) {
        this.creditorName = creditorName;
    }

    public String getVwz() {
        return remittanceInfo;
    }

    public void setVwz(String remittanceInfo) {
        this.remittanceInfo = remittanceInfo;
    }

    public String getCreditorIBAN() {
        return creditorIban;
    }

    public void setCreditorIBAN(String creditorIban) {
        this.creditorIban = creditorIban;
    }

    public String getCreditorAgent() {
        return creditorAgent;
    }

    public void setCreditorAgent(String creditorAgent) {
        this.creditorAgent = creditorAgent;
    }
}
