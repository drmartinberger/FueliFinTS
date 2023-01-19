package net.petafuel.jsepa;

import net.petafuel.jsepa.exception.SEPAWriteException;
import net.petafuel.jsepa.model.Document;
import org.apache.commons.lang3.NotImplementedException;

public class SEPAWriter {
    private Document document;
    public SEPAWriter(Document d) {
        this.document = d;
    }

    public byte[] writeSEPA() throws SEPAWriteException {
        throw new NotImplementedException("not implemented");
    }
}
