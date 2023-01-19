package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;

public class CryptographyFactory {
    public static Cryptography getCryptography(SecurityMethod securityMethod) {
        switch (securityMethod) {
            case PIN_1:
            case PIN_2:
                return new PinTanCryptography();
            case RDH_10:
                return new RDH10Cryptography();
            case RAH_10:
                return new RAH10Cryptography();
            case RDH_9:
                return new RDH9Cryptography();
            case RAH_9:
                return new RAH9Cryptography();
        }
        return null;
    }
}
