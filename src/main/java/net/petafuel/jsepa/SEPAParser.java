package net.petafuel.jsepa;

import net.petafuel.jsepa.exception.SEPAParsingException;
import net.petafuel.jsepa.model.SepaDocument;
import org.apache.commons.lang3.NotImplementedException;

public class SEPAParser {
    private SepaDocument sepaDocument;
    public SEPAParser(String sepaFile) {
    }

    public void parseSEPA() throws SEPAParsingException {
        throw new NotImplementedException("not implemented");
    }

    public SepaDocument getSepaDocument() {
        return sepaDocument;
    }
}
