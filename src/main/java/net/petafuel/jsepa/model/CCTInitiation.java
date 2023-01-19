package net.petafuel.jsepa.model;

import java.util.List;
import java.util.Vector;

public class CCTInitiation {
    private GroupHeader groupHeader;
    private Vector<PaymentInstructionInformation> pmtInfos;

    public void setPmtInfos(Vector<PaymentInstructionInformation> pmtInfos) {
        this.pmtInfos = pmtInfos;
    }

    public void setGrpHeader(GroupHeader groupHeader) {
        this.groupHeader = groupHeader;
    }

    public GroupHeader getGrpHeader() {
        return groupHeader;
    }

    public List<PaymentInstructionInformation> getPmtInfos() {
        return pmtInfos;
    }
}
