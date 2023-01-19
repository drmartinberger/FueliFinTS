package net.petafuel.jsepa.model;

public class DirectDebitTransactionInformation {
    private Purpose purpose;
    private String debitorIban;

    private String debitorName;

    private String debitorAgent;

    private String remittanceInfo;

    private String mandateId;

    private String dateOfSignature;

    private String endToEndId;

    private double amount;

    private CreditorSchemeId creditorSchemeId;

    public Purpose getPurpose() {
        return purpose;
    }

    public String getDebitorIBAN() {
        return debitorIban;
    }

    public String getDebitorAgent() {
        return debitorAgent;
    }

    public String getDebitorName() {
        return debitorName;
    }

    public String getVwz() {
        return remittanceInfo;
    }

    public CreditorSchemeId getCreditorSchemeId() {
        return creditorSchemeId;
    }

    public double getAmount() {
        return amount;
    }

    public String getMandateID() {
        return mandateId;
    }

    public String getDtOfSgntr() {
        return dateOfSignature;
    }

    public String getEndToEndID() {
        return endToEndId;
    }
}
