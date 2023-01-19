package net.petafuel.fuelifints.protocol.fints3.segments.model;

public enum SecurityMethod {

    PIN_1(1, 10, 16, 2, 6, 13, 2),
    PIN_2(2, 10, 16, 2, 6, 13, 2),
    RDH_9(9, 10, 19, 6, 6, 13, 18),
    RDH_10(10, 10, 19, 6, 6, 13, 2),
    RAH_9(9, 10, 19, 6, 6, 14, 18),
    RAH_10(10, 10, 19, 6, 6, 14, 2);

    private final int number;

    private int signaturAlgorithmus;
    private int operationsModusSignatur;
    private int verwendungSignaturAlgorithmus;
    private int hashAlgorithmus;
    private int verschluesselungsAlgorithmus;
    private int operationsModusVerschluesselung;

    private SecurityMethod(int number, int signaturAlgorithmus, int operationsModusSignatur, int verwendungSignaturAlgorithmus, int hashAlgorithmus, int verschluesselungsAlgorithmus, int operationsModusVerschluesselung) {
        this.number = number;
        this.signaturAlgorithmus = signaturAlgorithmus;
        this.operationsModusSignatur = operationsModusSignatur;
        this.verwendungSignaturAlgorithmus = verwendungSignaturAlgorithmus;
        this.hashAlgorithmus = hashAlgorithmus;
        this.verschluesselungsAlgorithmus = verschluesselungsAlgorithmus;
        this.operationsModusVerschluesselung = operationsModusVerschluesselung;
    }

    public byte[] getHbciDEG() {
        String asString = this.toString();
        return asString.replace('_', ':').getBytes();
    }


    public int getVersionNumber() {
        return number;
    }

    public int getSignaturAlgorithmus() {
        return signaturAlgorithmus;
    }

    public int getOperationsModusSignatur() {
        return operationsModusSignatur;
    }

    public int getVerwendungSignaturAlgorithmus() {
        return verwendungSignaturAlgorithmus;
    }

    public int getHashAlgorithmus() {
        return hashAlgorithmus;
    }

    public int getVerschluesselungsAlgorithmus() {
        return verschluesselungsAlgorithmus;
    }

    public int getOperationsModusVerschluesselung() {
        return operationsModusVerschluesselung;
    }
      
    public boolean isPIN() {
        return this.number == PIN_1.number || this.number == PIN_2.number;
    }

}
