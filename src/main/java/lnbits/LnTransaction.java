package lnbits;

import com.prowidesoftware.swift.model.field.Field61;
import com.prowidesoftware.swift.model.field.Field86;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import java.text.SimpleDateFormat;
import java.util.Date;


@JsonIgnoreProperties(ignoreUnknown = true)
public class LnTransaction {
    public LnTransaction() { super(); }

    public Field61 toField61() {
        // id: max 16 chars
        String id = StringUtils.left(checkingId, 7) + "..." + StringUtils.right(checkingId, 6);
        return new Field61()
                .setValueDate(new SimpleDateFormat("yyMMdd").format(getTime()))
                .setDebitCreditMark(this.amount >= 0 ? "C" : "D")
                .setAmount(Math.abs(this.amount+this.fee)/1000L)
                .setTransactionType("NMSC")
                .setIdentificationCode(id);
    }

    public Field86 toField86(){
        return new Field86().setComponent1(this.memo);
    }

    public Long getAmount() {
        return amount;
    }

    public Long getFee() {
        return fee;
    }

    public Date getTime() {
        return new Date(this.time*1000);
    }

    public Boolean getPending() { return pending; }

    @JsonProperty("amount")
    private Long amount;
    @JsonProperty("fee")
    private Long fee;

    @JsonProperty("memo")
    private String memo;

    // time in seconds since epoch
    @JsonProperty("time")
    private Long time;

    @JsonProperty("checking_id")
    private String checkingId;

    @JsonProperty("pending")
    private Boolean pending;

}
