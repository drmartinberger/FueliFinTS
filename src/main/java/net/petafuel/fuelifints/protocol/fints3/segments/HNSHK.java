package net.petafuel.fuelifints.protocol.fints3.segments;

import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IExecutableElement;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.model.client.LegitimationInfo;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.*;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.an;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.code;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.num;

import java.util.List;

/**
 * Name: Signaturkopf
 * Typ: Segment
 * Segmentart: Administration
 * Kennung: HNSHK
 * Sender: Kunde/Kreditinstitut
 */
public class HNSHK extends Segment implements IExecutableElement {

    @Element(
            description = {@ElementDescription(number = 1)})
    private Segmentkopf segmentkopf;

    @Element(
            description = {@ElementDescription(number = 2)})
    private Sicherheitsprofil sicherheitsprofil;

    @Element(
            description = {@ElementDescription(number = 3, length = -3)})
//    @code(restrictions = {"1", "2", "4", "911", "900", "999"})
	@an
    private String sicherheitsfunktion;

    @Element(
            description = {@ElementDescription(number = 4, length = -14)})
    @an
    private String sicherheitskontrollreferenz;

    @Element(
            description = {@ElementDescription(number = 5, length = -3)})
    @code(restrictions = {"1"})
    private String sicherheitsapplikationskontrollbereich;

    @Element(
            description = {@ElementDescription(number = 6, length = -4)})
    @code(restrictions = {"1", "3", "4"})
    private String sicherheitslieferantenrolle;

    @Element(
            description = {@ElementDescription(number = 7)})
    private Sicherheitsidentifikationsdetails sicherheitsidentifikationsdetails;

    @Element(
            description = {@ElementDescription(number = 8, length = -16)})
    @num
    private Integer sicherheitsreferenznummer;

    @Element(
            description = {@ElementDescription(number = 9)})
    private SicherheitsdatumUndUhrzeit sicherheitsdatumUndUhrzeit;

    @Element(
            description = {@ElementDescription(number = 10)})
    private Hashalgorithmus hashalgorithmus;

    @Element(
            description = {@ElementDescription(number = 11)})
    private Signaturalgorithmus signaturalgorithmus;

    @Element(
            description = {@ElementDescription(number = 12)})
    private Schluesselname schluesselname;

    @Element(
            description = {@ElementDescription(number = 13, status = ElementDescription.StatusCode.C)})
    private Zertifikat zertifikat;           //Darf bei PIN/TAN nicht belegt werden

    public HNSHK(byte[] message) {
        super(message);
    }

    public Sicherheitsprofil getSicherheitsprofil() {
        return sicherheitsprofil;
    }

    public Sicherheitsidentifikationsdetails getSicherheitsidentifikationsdetails() {
        return sicherheitsidentifikationsdetails;
    }

    public Schluesselname getSchluesselname() {
        return schluesselname;
    }

    public Zertifikat getZertifikat() {
        return zertifikat;
    }

    public void setZertifikat(Zertifikat zertifikat) {
        this.zertifikat = zertifikat;
    }

    public Segmentkopf getSegmentkopf() {
        return segmentkopf;
    }

    public void setSegmentkopf(Segmentkopf segmentkopf) {
        this.segmentkopf = segmentkopf;
    }

    public void setSicherheitsprofil(Sicherheitsprofil sicherheitsprofil) {
        this.sicherheitsprofil = sicherheitsprofil;
    }

    public String getSicherheitsfunktion() {
        return sicherheitsfunktion;
    }

    public void setSicherheitsfunktion(String sicherheitsfunktion) {
        this.sicherheitsfunktion = sicherheitsfunktion;
    }

    public String getSicherheitskontrollreferenz() {
        return sicherheitskontrollreferenz;
    }

    public void setSicherheitskontrollreferenz(String sicherheitskontrollreferenz) {
        this.sicherheitskontrollreferenz = sicherheitskontrollreferenz;
    }

    public String getSicherheitsapplikationskontrollbereich() {
        return sicherheitsapplikationskontrollbereich;
    }

    public void setSicherheitsapplikationskontrollbereich(String sicherheitsapplikationskontrollbereich) {
        this.sicherheitsapplikationskontrollbereich = sicherheitsapplikationskontrollbereich;
    }

    public String getSicherheitslieferantenrolle() {
        return sicherheitslieferantenrolle;
    }

    public void setSicherheitslieferantenrolle(String sicherheitslieferantenrolle) {
        this.sicherheitslieferantenrolle = sicherheitslieferantenrolle;
    }

    public void setSicherheitsidentifikationsdetails(Sicherheitsidentifikationsdetails sicherheitsidentifikationsdetails) {
        this.sicherheitsidentifikationsdetails = sicherheitsidentifikationsdetails;
    }

    public Integer getSicherheitsreferenznummer() {
        return sicherheitsreferenznummer;
    }

    public void setSicherheitsreferenznummer(Integer sicherheitsreferenznummer) {
        this.sicherheitsreferenznummer = sicherheitsreferenznummer;
    }

    public SicherheitsdatumUndUhrzeit getSicherheitsdatumUndUhrzeit() {
        return sicherheitsdatumUndUhrzeit;
    }

    public void setSicherheitsdatumUndUhrzeit(SicherheitsdatumUndUhrzeit sicherheitsdatumUndUhrzeit) {
        this.sicherheitsdatumUndUhrzeit = sicherheitsdatumUndUhrzeit;
    }

    public Hashalgorithmus getHashalgorithmus() {
        return hashalgorithmus;
    }

    public void setHashalgorithmus(Hashalgorithmus hashalgorithmus) {
        this.hashalgorithmus = hashalgorithmus;
    }

    public Signaturalgorithmus getSignaturalgorithmus() {
        return signaturalgorithmus;
    }

    public void setSignaturalgorithmus(Signaturalgorithmus signaturalgorithmus) {
        this.signaturalgorithmus = signaturalgorithmus;
    }

    public void setSchluesselname(Schluesselname schluesselname) {
        this.schluesselname = schluesselname;
    }

    @Override
    public StatusCode execute(Dialog dialog) {
        LegitimationInfo legitimationInfo = dialog.getLegitimationsInfo();
        legitimationInfo.setSecurityMethod(SecurityMethod.valueOf(sicherheitsprofil.getSicherheitsverfahren() + "_" + sicherheitsprofil.getSicherheitsverfahrensversion()));
        legitimationInfo.setHashalgorithmus(getHashalgorithmus());
        legitimationInfo.setSicherheitsfunktion(getSicherheitsfunktion());
        legitimationInfo.setUserId(schluesselname.getBenutzerkennung());
        return StatusCode.OK;
    }

    @Override
    public List<IMessageElement> getReplyMessageElements() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public IMessageElement getStatusElement() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String toString() {
        return "HNSHK{" +
                "segmentkopf=" + segmentkopf +
                ", sicherheitsprofil=" + sicherheitsprofil +
                ", sicherheitsfunktion='" + sicherheitsfunktion + '\'' +
                ", sicherheitskontrollreferenz='" + sicherheitskontrollreferenz + '\'' +
                ", sicherheitsapplikationskontrollbereich='" + sicherheitsapplikationskontrollbereich + '\'' +
                ", sicherheitslieferantenrolle='" + sicherheitslieferantenrolle + '\'' +
                ", sicherheitsidentifikationsdetails=" + sicherheitsidentifikationsdetails +
                ", sicherheitsreferenznummer=" + sicherheitsreferenznummer +
                ", sicherheitsdatumUndUhrzeit=" + sicherheitsdatumUndUhrzeit +
                ", hashalgorithmus=" + hashalgorithmus +
                ", signaturalgorithmus=" + signaturalgorithmus +
                ", schluesselname=" + schluesselname +
                ", zertifikat=" + zertifikat +
                '}';
    }
}
