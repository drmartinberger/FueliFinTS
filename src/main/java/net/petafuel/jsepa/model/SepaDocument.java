package net.petafuel.jsepa.model;

public class SepaDocument extends Document {
    private CCTInitiation cctInitiation;
    private DDInitiation ddInitiation;
    public CCTInitiation getCctInitiation() {
        return cctInitiation;
    }

    public DDInitiation getDdInitiation() {
        return ddInitiation;
    }
}
