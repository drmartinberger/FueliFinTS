package net.petafuel.mt94x;

import com.prowidesoftware.swift.model.BIC;
import com.prowidesoftware.swift.model.field.*;
import com.prowidesoftware.swift.model.mt.mt9xx.MT940;
import lnbits.LnbitsHelper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Mt940Helper {
    public static byte[] getMT940s(String kontonummer, java.util.Date vonDatum, java.util.Date bisDatum) throws IOException {
        if(vonDatum == null) {
            vonDatum = new Date(0);
        }
        if(bisDatum == null) {
            bisDatum = new Date();
        }
        return getMT940(kontonummer, vonDatum, bisDatum).getBytes();
    }

    private static String getMT940(String kontonummer, java.util.Date vonDatum, java.util.Date bisDatum) throws IOException {
        String simpleDate = new SimpleDateFormat("yyMMdd").format(bisDatum);
        MT940 a = new MT940();
        a.setSender(new BIC("NOOODE00BIC"));
        a.setReceiver(new BIC("NOOODE00BIC"));
        a.addField(new Field20(simpleDate)); // Transaktionsnummer
        a.addField(new Field21("NONREF")); // Bezugsreferenznummer
        a.addField(new Field25(kontonummer)); // Kontobezeichnung
        a.addField(new Field28C((simpleDate))); // Auszugsnummer
        for(Field f : LnbitsHelper.getMt940Fields(vonDatum, bisDatum)) {
            a.addField(f);
        }
        return a.message();
    }
}
