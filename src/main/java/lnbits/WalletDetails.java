package lnbits;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletDetails {
    public WalletDetails() { super(); }

    public Long getBalance() {
        return balance;
    }

    @JsonProperty("balance")
    private Long balance;
}
