package net.petafuel.fuelifints.protocol.fints3.segments;

import net.petafuel.fuelifints.cryptography.KeyManager;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IExecutableElement;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.OeffentlicherSchluessel;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Rueckmeldung;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Schluesselname;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Segmentkopf;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Sicherheitsprofil;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Zertifikat;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.code;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.LinkedList;
import java.util.List;

/**
 * Name: Änderung eines öffentlichen Schlüssels
 * Typ:  Segment
 * Segmentart:  Administration
 * Kennung:  HKSAK
 * Bezugssegment:        -
 * Sender:  Kunde
 */
public class HKSAK extends Segment implements IExecutableElement {

    @Element(
            description = {@ElementDescription(number = 1)})
    private Segmentkopf segmentkopf;

    @Element(
            description = {@ElementDescription(number = 2, length = 1)})
    @code(restrictions = {"2"})
    private String nachrichtenbeziehungKodiert;

    @Element(
            description = {@ElementDescription(number = 3, length = -3)})
    @code(restrictions = {"112"})
    private String funktionstypBezeichner;

    @Element(
            description = {@ElementDescription(number = 4)})
    private Sicherheitsprofil sicherheitsprofil;

    @Element(
            description = {@ElementDescription(number = 5)})
    private Schluesselname schluesselname;

    @Element(
            description = {@ElementDescription(number = 6)})
    private OeffentlicherSchluessel oeffentlicherSchluessel;

    @Element(
            description = {@ElementDescription(number = 7, status = ElementDescription.StatusCode.O)})
    private Zertifikat zertifikat;

    private List<IMessageElement> replyElements;
    private HIRMS statusElement;

    public HKSAK(byte[] message) {
        super(message);
    }

    @Override
    public StatusCode execute(Dialog dialog) {
        StatusCode statusCode;
        replyElements = new LinkedList<IMessageElement>();
        statusElement = new HIRMS(new byte[0]);
        Segmentkopf sk;// = new Segmentkopf(("HIRMS:0:2:" + segmentkopf.getSegmentNummer()).getBytes());
        sk = Segmentkopf.Builder.newInstance().setSegmentKennung(HIRMS.class).setSegmentVersion(2).setBezugssegment(segmentkopf.getSegmentNummer()).build();
        Rueckmeldung rueckmeldung;
        statusElement.setSegmentkopf(sk);
        if (oeffentlicherSchluessel != null) {
            KeyManager keyManager = KeyManager.getInstance(schluesselname.getKreditsinstitutkennung().getKreditinstitutscode());
            SecurityMethod securityMethod = SecurityMethod.valueOf(sicherheitsprofil.getSicherheitsverfahren() + "_" + sicherheitsprofil.getSicherheitsverfahrensversion());
            BigInteger modulus = new BigInteger(1, SegmentUtil.getBytes(oeffentlicherSchluessel.getModulus()));
            BigInteger exponent = new BigInteger(1, SegmentUtil.getBytes(oeffentlicherSchluessel.getExponent()));
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(modulus, exponent);
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
                if (keyManager.addUserPublicKey(schluesselname.getBenutzerkennung(), securityMethod, schluesselname.getSchluesselart(), schluesselname.getSchluesselversion(), schluesselname.getSchluesselnummer(), publicKey)) {
                    if (schluesselname.getSchluesselart().equals("S")) {
                        statusElement.addRueckmeldung(new Rueckmeldung("3310::Ini-Brief erforderlich.".getBytes("ISO-8859-1")));
                        rueckmeldung = new Rueckmeldung("0010::Entgegengenommen.".getBytes("ISO-8859-1"));
                        statusCode = StatusCode.OK;
                    } else {
                        rueckmeldung = new Rueckmeldung("0010::Entgegengenommen.".getBytes("ISO-8859-1"));
                        statusCode = StatusCode.OK;
                    }
                } else {
                    //Schlüsseleintraguung in die Datenbank fehlgschlagen -> Schlüssel bereits vorhanden
                    //Fehlermeldung darf laut Doku aus Sicherheitsgründen nicht begründet werden!
                    rueckmeldung = new Rueckmeldung("9010::Auftrag abgelehnt.".getBytes("ISO-8859-1"));
                    statusCode = StatusCode.ERROR;
                }

            } catch (NoSuchAlgorithmException e) {
                //return false;
                rueckmeldung = new Rueckmeldung("9000::Kann nicht verarbeitet werden.".getBytes());
                statusCode = StatusCode.ERROR;
            } catch (InvalidKeySpecException e) {
                try {
                    rueckmeldung = new Rueckmeldung("9010::Verarbeitung nicht möglich.".getBytes("ISO-8859-1"));
                    statusCode = StatusCode.ERROR;
                } catch (UnsupportedEncodingException e1) {
                    throw new RuntimeException(e);
                }
            } catch (UnsupportedEncodingException e) {
                //ISO-8859-1 is supported
                throw new RuntimeException(e);
            }
        } else {
            rueckmeldung = new Rueckmeldung("9160:6:Fehlt.".getBytes());
            statusCode = StatusCode.ERROR;
        }
        statusElement.addRueckmeldung(rueckmeldung);
        //replyElements.add(statusElement);
        return statusCode;
    }

    @Override
    public List<IMessageElement> getReplyMessageElements() {
        return replyElements;
    }

    @Override
    public IMessageElement getStatusElement() {
        return statusElement;
    }

    public Segmentkopf getSegmentkopf() {
        return segmentkopf;
    }

    public void setSegmentkopf(Segmentkopf segmentkopf) {
        this.segmentkopf = segmentkopf;
    }

    public String getNachrichtenbeziehungKodiert() {
        return nachrichtenbeziehungKodiert;
    }

    public void setNachrichtenbeziehungKodiert(String nachrichtenbeziehungKodiert) {
        this.nachrichtenbeziehungKodiert = nachrichtenbeziehungKodiert;
    }

    public String getFunktionstypBezeichner() {
        return funktionstypBezeichner;
    }

    public void setFunktionstypBezeichner(String funktionstypBezeichner) {
        this.funktionstypBezeichner = funktionstypBezeichner;
    }

    public Sicherheitsprofil getSicherheitsprofil() {
        return sicherheitsprofil;
    }

    public void setSicherheitsprofil(Sicherheitsprofil sicherheitsprofil) {
        this.sicherheitsprofil = sicherheitsprofil;
    }

    public Schluesselname getSchluesselname() {
        return schluesselname;
    }

    public void setSchluesselname(Schluesselname schluesselname) {
        this.schluesselname = schluesselname;
    }

    public OeffentlicherSchluessel getOeffentlicherSchluessel() {
        return oeffentlicherSchluessel;
    }

    public void setOeffentlicherSchluessel(OeffentlicherSchluessel oeffentlicherSchluessel) {
        this.oeffentlicherSchluessel = oeffentlicherSchluessel;
    }

    public Zertifikat getZertifikat() {
        return zertifikat;
    }

    public void setZertifikat(Zertifikat zertifikat) {
        this.zertifikat = zertifikat;
    }
}
