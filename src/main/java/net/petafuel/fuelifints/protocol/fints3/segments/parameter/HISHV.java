package net.petafuel.fuelifints.protocol.fints3.segments.parameter;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.Element;
import net.petafuel.fuelifints.protocol.fints3.segments.ElementDescription;
import net.petafuel.fuelifints.protocol.fints3.segments.Segment;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Segmentkopf;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.UnterstuetzesSicherheitsverfahren;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.jn;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Name: Sicherheitsverfahren
 * Typ: Segment
 * Segmentart: Administration
 * Version: 3
 */
public class HISHV extends Segment {
    @Element(
            description = {@ElementDescription(number = 1)})
    private Segmentkopf segmentkopf;

    @Element(
            description = {@ElementDescription(number = 2)})
    @jn
    private String mischung_zulaessig;

    @Element(
            description = {@ElementDescription(number = 3)})
    private List<UnterstuetzesSicherheitsverfahren> unterstuetzte_sicherheitsverfahren = new LinkedList<>();

    public HISHV(int bezugsSegment) {
        super(new byte[0]);
        /*
        this.segmentkopf = new Segmentkopf(
                ("HISHV:0:3:" +
                        bezugsSegment).getBytes()
        );
        */
        this.segmentkopf = Segmentkopf.Builder.newInstance().setSegmentKennung(HISHV.class).setSegmentVersion(3).setBezugssegment(bezugsSegment).build();

        this.mischung_zulaessig = "N";

        String hbciSecurityProtocols = System.getProperties().getProperty("hbci.security.protocols");
        List<String> enabledSecurityProtocols = Arrays.asList(hbciSecurityProtocols.split(";"));
        for (SecurityMethod method : SecurityMethod.values()) {
            if (enabledSecurityProtocols.contains(method.toString())) {
                UnterstuetzesSicherheitsverfahren unterstuetzeSicherheitsverfahren = new UnterstuetzesSicherheitsverfahren(method.getHbciDEG());
                unterstuetzte_sicherheitsverfahren.add(unterstuetzeSicherheitsverfahren);
            }
        }
    }

    public Segmentkopf getSegmentkopf() {
        return segmentkopf;
    }

    public void setSegmentkopf(Segmentkopf segmentkopf) {
        this.segmentkopf = segmentkopf;
    }

    public String getMischung_zulaessig() {
        return mischung_zulaessig;
    }

    public void setMischung_zulaessig(String mischung_zulaessig) {
        this.mischung_zulaessig = mischung_zulaessig;
    }

    public List<UnterstuetzesSicherheitsverfahren> getUnterstuetzte_sicherheitsverfahren() {
        return unterstuetzte_sicherheitsverfahren;
    }

    public void setUnterstuetzte_sicherheitsverfahren(List<UnterstuetzesSicherheitsverfahren> unterstuetzte_sicherheitsverfahren) {
        this.unterstuetzte_sicherheitsverfahren = unterstuetzte_sicherheitsverfahren;
    }
}
