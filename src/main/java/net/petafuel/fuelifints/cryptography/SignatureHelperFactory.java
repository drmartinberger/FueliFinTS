package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;

public class SignatureHelperFactory {

    public static SignatureHelper getSignatureHelper(SecurityMethod method) {
        switch (method) {
            case PIN_1:
            case PIN_2:
                return new PinTanSignatureHelper();
            case RDH_9:
            case RAH_9:
            case RAH_10:
            case RDH_10:
                return new RDH10SignatureHelper();
        }

        if (method.equals("PIN-1") || method.equals("PIN-2")) {
            return new PinTanSignatureHelper();
        } else if (method.equals("RDH-10")) {
            return new RDH10SignatureHelper();
        }
        return null;
    }
}
