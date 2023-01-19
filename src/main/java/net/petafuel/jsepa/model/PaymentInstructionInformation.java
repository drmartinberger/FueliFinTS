package net.petafuel.jsepa.model;

import java.util.List;

public class PaymentInstructionInformation {
    private CreditorSchemeId creditorSchemeId;
    private String requestedCollectionDate;
    private String requestedExecutionDate;
    private List<DirectDebitTransactionInformation> ddTxInfos;

    private List<CreditTransferTransactionInformation> ctTxInfos;

    private Debitor debitor;

    private String sequenceType;

    private String directDebitType;

    private String pmtInfoId;
    private DebitorAccount debitorAccount;

    private CreditorAccount creditorAccount;

    public void setChargeBearer(String slev) {
    }

    public void setDebitor(Debitor debitor) {
        this.debitor = debitor;
    }

    public void setDebitorAccount(DebitorAccount debitorAccount) {
        this.debitorAccount = debitorAccount;
    }

    public void setCreditorAccount(CreditorAccount creditorAccount) {
        this.creditorAccount = creditorAccount;
    }

    public void setDebitorAgent(DebitorAgent debitorAgent) {
    }

    public void setRequestedExecutionDate(String format) {
    }

    public void setCreditTransferTransactionInformationVector(List<CreditTransferTransactionInformation> creditTransferTransactionInformationVector) {
        this.ctTxInfos = creditTransferTransactionInformationVector;
    }

    public void setPaymentMethod(String trf) {
    }

    public void setPmtInfId(String pmtInfoId) {
        this.pmtInfoId = pmtInfoId;
    }

    public void setCtrlSum(Double sum) {
    }

    public void setNoTxns(int size) {
    }

    public void setPti(PaymentTypeInformation paymentTypeInformation) {
    }

    public List<DirectDebitTransactionInformation> getDirectDebitTransactionInformationVector() {
        return ddTxInfos;
    }

    public String getRequestedCollectionDate() {
        return requestedCollectionDate;
    }

    public String getDirectDebitType() {
        return directDebitType;
    }

    public Object getCreditorAccountIBAN() {
        return creditorAccount.getIban();
    }

    public CreditorSchemeId getCreditorSchemeId() {
        return creditorSchemeId;
    }

    public Object getDebtorAccountIBAN() {
        return debitorAccount.getIban();
    }

    public List<CreditTransferTransactionInformation> getCreditTransferTransactionInformationVector() {
        return ctTxInfos;
    }

    public String getRequestedExecutionDate() {
        return requestedExecutionDate;
    }

    public String getSequenceType() {
        return sequenceType;
    }

    public String getPmtInfId() {
        return pmtInfoId;
    }
}
