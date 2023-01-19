package net.petafuel.fuelifints.model.client;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Hashalgorithmus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LegitimationInfo {
    private static final Logger LOG = LogManager.getLogger(LegitimationInfo.class);
    public static final String CUSTOMER_ANONYM = "9999999999";

    private SecurityMethod securityMethod;
    private String userId;      //Benutzerkennung
    private String customerId;  //Kunden-ID
    private String bankId;

    protected String tanResponse;

    private boolean userIdentified;
    private Hashalgorithmus hashalgorithmus;

    private String sicherheitsfunktion = "2";

    private boolean anonymousAccount = false;
    private boolean userKeysSubmitted = false;
    private boolean isStrongAuthenticated = false;

    public LegitimationInfo() {

    }

    public LegitimationInfo(LegitimationInfo legitimationInfo) {
        this.securityMethod = legitimationInfo.securityMethod;
        this.userId = legitimationInfo.userId;
        this.customerId = legitimationInfo.customerId;
        this.bankId = legitimationInfo.bankId;
        this.userIdentified = legitimationInfo.userIdentified;
        this.hashalgorithmus = legitimationInfo.hashalgorithmus;
        this.sicherheitsfunktion = legitimationInfo.sicherheitsfunktion;
        this.anonymousAccount = legitimationInfo.anonymousAccount;
        this.userKeysSubmitted = legitimationInfo.userKeysSubmitted;
    }

    public void setUserId(String userId) {
        this.anonymousAccount = userId.equals(CUSTOMER_ANONYM);
        LOG.debug("Setting new userId: {}", userId);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserIdentified(boolean userIdentified) {
        this.userIdentified = userIdentified;
    }

    public boolean isUserIdentified() {
        return userIdentified;
    }

    public void setSecurityMethod(SecurityMethod securityMethod) {
        this.securityMethod = securityMethod;
    }

    public SecurityMethod getSecurityMethod() {
        return securityMethod;
    }

    public String getBankId() {
        return bankId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.anonymousAccount = customerId.equals(CUSTOMER_ANONYM);
        this.customerId = customerId;
    }

    public void setHashalgorithmus(Hashalgorithmus hashalgorithmus) {
        this.hashalgorithmus = hashalgorithmus;
    }

    public Hashalgorithmus getHashalgorithmus() {
        return hashalgorithmus;
    }

    public String getSicherheitsfunktion() {
        return sicherheitsfunktion;
    }

    public void setSicherheitsfunktion(String sicherheitsfunktion) {
        this.sicherheitsfunktion = sicherheitsfunktion;
    }

    public boolean isAnonymousAccount() {
        return anonymousAccount;
    }

    public boolean userKeysSubmitted() {
        return userKeysSubmitted;
    }

    public void setUserKeysSubmitted(boolean submitted) {
        userKeysSubmitted = submitted;
    }

    public void setTanResponse(String tanResponse) {
        this.tanResponse = tanResponse;
    }

    public String getTanResponse() {
        return tanResponse;
    }

    public boolean isStrongAuthenticated() {
        return isStrongAuthenticated;
    }

    public void setStrongAuthenticated(boolean strongAuthenticated) {
        this.isStrongAuthenticated = strongAuthenticated;
    }
}
