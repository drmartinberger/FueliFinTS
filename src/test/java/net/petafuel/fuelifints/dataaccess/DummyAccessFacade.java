package net.petafuel.fuelifints.dataaccess;

import net.petafuel.fuelifints.FinTSVersionSwitch;
import net.petafuel.fuelifints.dataaccess.dataobjects.CommonBankParameterDataObject;
import net.petafuel.fuelifints.dataaccess.dataobjects.ParameterDataObject;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;

public class DummyAccessFacade {
    public DummyAccessFacade(Object o) {
        throw new NotImplementedException("not implemented");
    }

    public ArrayList<ParameterDataObject> getParameterData(FinTSVersionSwitch.FinTSVersion fintsVersion30) {
        throw new NotImplementedException("not implemented");
    }

    public void clearUserKeys() {
        throw new NotImplementedException("not implemented");
    }

    public void clearDB() {
        throw new NotImplementedException("not implemented");
    }

    public void close() {
        throw new NotImplementedException("not implemented");
    }

    public boolean hasKeyPair(SecurityMethod rdh10, String v) {
        throw new NotImplementedException("not implemented");
    }

    public void updateKeyPair(SecurityMethod rdh10, String privatekey, String publickey, String v, Object o, Object o1) {
        throw new NotImplementedException("not implemented");
    }

    public String getPrivateKey(SecurityMethod rdh10, String v) {
        throw new NotImplementedException("not implemented");
    }

    public String getPublicKey(SecurityMethod rdh10, String v) {
        throw new NotImplementedException("not implemented");
    }

    public void addUserPublicKey(String aUserId, Object o, SecurityMethod rdh10, String uPubKey, String v, int i, int i1, Object o1, Object o2) {
        throw new NotImplementedException("not implemented");
    }

    public String getUserPublicKey(String aUserId, Object o, SecurityMethod rdh10, String v, int i, int i1) {
        throw new NotImplementedException("not implemented");
    }

    public CommonBankParameterDataObject getCommonBankParameters(FinTSVersionSwitch.FinTSVersion fintsVersion30) {
        throw new NotImplementedException("not implemented");
    }
}
