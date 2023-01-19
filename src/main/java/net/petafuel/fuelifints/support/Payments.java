package net.petafuel.fuelifints.support;

import net.petafuel.jsepa.model.CCTInitiation;
import net.petafuel.jsepa.model.CategoryPurpose;
import net.petafuel.jsepa.model.CreditTransferTransactionInformation;
import net.petafuel.jsepa.model.Debitor;
import net.petafuel.jsepa.model.DebitorAccount;
import net.petafuel.jsepa.model.DebitorAgent;
import net.petafuel.jsepa.model.Document;
import net.petafuel.jsepa.model.GroupHeader;
import net.petafuel.jsepa.model.PAIN00100203Document;
import net.petafuel.jsepa.model.PAIN00100303Document;
import net.petafuel.jsepa.model.PaymentInstructionInformation;
import net.petafuel.jsepa.model.PaymentTypeInformation;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public class Payments {
	public static SimpleDateFormat creationFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	public static SimpleDateFormat executionFormat = new SimpleDateFormat("yyyy-MM-dd");

	public enum versions {
		pain00100203("sepade.pain.001.002.03.xsd", 0),
		pain00100303("sepade.pain.001.003.03.xsd", 1);

		private final String descriptor;
		private int wert = 0;

		versions(String descriptor, int wert) {
			this.descriptor = descriptor;
			this.wert = wert;
		}

		public String getDescriptor() {
			return descriptor;
		}

		public int getWert() {
			return wert;
		}
	}

	public static versions getBestVersion(List<String> unterstuetzteSepaVersionen) {
		versions max = null;
		versions maxFound = null;
		for (versions version : versions.values()) {
			if (unterstuetzteSepaVersionen != null && unterstuetzteSepaVersionen.size() > 0) {
				for (String unterstuetzt : unterstuetzteSepaVersionen) {
					if (unterstuetzt.contains(version.getDescriptor()) && (maxFound == null || (maxFound.getWert() < version.getWert()))) {
						maxFound = version;
						break;
					}
				}
			}
			if (max == null || max.getWert() < version.getWert()) {
				max = version;
			}
		}
		return (maxFound == null) ? max : maxFound;

	}

	public static Document paymentsToSepa(LinkedList<PaymentEntry> paymentEntries, String auftragsId, String MessageId, versions version) {
		Document d = null;
		switch (version) {
			case pain00100203:
				d = new PAIN00100203Document();
				break;
			case pain00100303:
				d = new PAIN00100303Document();
				break;
		}
		Vector<PaymentInstructionInformation> pmtInfos = new Vector<>();
		PaymentInstructionInformation payment = new PaymentInstructionInformation();
		GroupHeader groupHeader = new GroupHeader();


		payment.setChargeBearer("SLEV");
		List<CreditTransferTransactionInformation> creditTransferTransactionInformationVector = new ArrayList<>();
		boolean isDebitorSet = false;
		Double sum = 0d;
		for (PaymentEntry paymentEntry : paymentEntries) {
			if (!isDebitorSet) {
				Debitor debitor = new Debitor();
				debitor.setName(paymentEntry.getAbsenderName());
				payment.setDebitor(debitor);
				DebitorAccount debitorAccount = new DebitorAccount();
				debitorAccount.setIban(paymentEntry.getAbsenderIban());
				payment.setDebitorAccount(debitorAccount);
				DebitorAgent debitorAgent = new DebitorAgent();
				debitorAgent.setBic(paymentEntry.getAbsenderBIC());
				payment.setDebitorAgent(debitorAgent);
				payment.setRequestedExecutionDate(executionFormat.format(paymentEntry.getAusfuehrungsdatum()));
				groupHeader.setInitiatingPartyName(paymentEntry.getAbsenderName());
				groupHeader.setCreationTime((creationFormat.format(paymentEntry.getAnlagedatum())));
				isDebitorSet = true;
			}
			CreditTransferTransactionInformation cTTI = new CreditTransferTransactionInformation();
			cTTI.setAmount(paymentEntry.getAmount());
			cTTI.setCreditorIBAN(paymentEntry.getEmpfaengerIBAN());
			cTTI.setCreditorAgent(paymentEntry.getEmpfaengerBIC());
			cTTI.setCreditorName(paymentEntry.getEmpfaengerName());
			cTTI.setVwz(paymentEntry.getVerwendungszweck());
			cTTI.setEndToEndID("NOTPROVIDED");
			creditTransferTransactionInformationVector.add(cTTI);
			sum += paymentEntry.getAmount();
		}
		groupHeader.setNoOfTransactions(paymentEntries.size());
		groupHeader.setControlSum(sum);
		groupHeader.setMessageId(MessageId.replace("_", "-"));
		payment.setCreditTransferTransactionInformationVector(creditTransferTransactionInformationVector);
		payment.setPaymentMethod("TRF");
		payment.setPmtInfId(auftragsId);

		payment.setCtrlSum(sum);
		payment.setNoTxns(paymentEntries.size());
		PaymentTypeInformation paymentTypeInformation = new PaymentTypeInformation();
		CategoryPurpose categoryPurpose = new CategoryPurpose();
		paymentTypeInformation.setCategoryPurpose(categoryPurpose);
//		paymentTypeInformation.setSequenceType("SEPA");
		payment.setPti(paymentTypeInformation);
		pmtInfos.add(payment);

		CCTInitiation cctInitiation = new CCTInitiation();
		cctInitiation.setPmtInfos(pmtInfos);
		cctInitiation.setGrpHeader(groupHeader);
		d.setCctInitiation(cctInitiation);
		return d;
	}

	public static class PaymentEntry {
		private String empfaengerName = "";
		private String empfaengerIBAN = "";
		private String empfaengerBIC = "";
		private Double amount = 0d;
		private String absenderName = "";
		private String absenderIban = "";
		private String absenderBIC = "";
		private String verwendungszweck = "";
		private Date ausfuehrungsdatum = null;
		private Date anlagedatum = null;

		public static PaymentEntry fromDauerautrag(ResultSet resultSet, String bic, String blz) throws SQLException {
			PaymentEntry pE = new PaymentEntry();
			pE.empfaengerName = resultSet.getString("empfaenger");
			pE.empfaengerIBAN = resultSet.getString("iban");
			pE.empfaengerBIC = resultSet.getString("bic");
			pE.amount = resultSet.getDouble("betrag");
			pE.absenderName = resultSet.getString("auftraggeber");
			pE.absenderIban = convertKnrBlzToIBAN(resultSet.getString("auftraggeberkonto"), blz);
			pE.absenderBIC = bic;
			pE.verwendungszweck = resultSet.getString("verwendungszweck");
			pE.ausfuehrungsdatum = resultSet.getDate("ausfuehrungsdatum");
			pE.anlagedatum = resultSet.getDate("datum");
			return pE;
		}

		public String getEmpfaengerName() {
			return empfaengerName;
		}

		public String getEmpfaengerIBAN() {
			return empfaengerIBAN;
		}

		public String getEmpfaengerBIC() {
			return empfaengerBIC;
		}

		public Double getAmount() {
			return amount;
		}

		public String getAbsenderName() {
			return absenderName;
		}

		public String getAbsenderIban() {
			return absenderIban;
		}

		public String getAbsenderBIC() {
			return absenderBIC;
		}

		public String getVerwendungszweck() {
			return verwendungszweck;
		}

		public Date getAusfuehrungsdatum() {
			return ausfuehrungsdatum;
		}

		public Date getAnlagedatum() {
			return anlagedatum;
		}
	}

	public static class Result {
		private boolean success = false;
		private final LinkedList<String> ids = new LinkedList<>();
		private Throwable error = null;

		public Result(boolean success) {
			this.success = success;
		}

		public Result readPrepared(PreparedStatement pS) {
			try {
				ResultSet rs = pS.getGeneratedKeys();
				while (rs.next()) {
					ids.add(rs.getString(1));
				}
			} catch (SQLException ignored) {
			}
			return this;
		}

		public boolean isSuccess() {
			return success;
		}

		public LinkedList<String> getIds() {
			return ids;
		}

		public Result add(String id) {
			this.ids.add(id);
			return this;
		}

		public Result setError(Throwable e) {
			this.error = e;
			return this;
		}

		public Throwable getError() {
			return error;
		}
	}

	public static class AuftragsId {
		public static String separator = "-";
		public static String markSammler = "S";
		public static String markEinzel = "E";
		private final String auftragsid;

		public AuftragsId(String id) {
			this.auftragsid = id;
		}

		@Override
		public String toString() {
			return auftragsid;
		}

		public static AuftragsId generate(boolean isSammler, String userid, String idSingle, String idSammler) {
			return new Payments.AuftragsId(((isSammler) ? markSammler : markEinzel) + separator + userid + separator + (isSammler ? idSammler : idSingle));
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof AuftragsId && o.toString().equals(auftragsid);
		}
	}

	/**
	 * @param knr Kontonummer (max 10 Stellen)
	 * @param blz Bankleitzahl (max 8 Stellen)
	 * @return Neue Kontonummer
	 */
	public static String convertKnrBlzToIBAN(String knr, String blz) {
		// zehnstellige Kontonummer
		int anz = 10 - knr.length();
		for (int i = 0; i < anz; i++) {
			knr = "0" + knr;
		}
		// Pruefziffer
		// die 1314 steht fuer DE und die 00 fuer die fehlenden Prueffziffern
		String checkIBAN = blz + knr + "131400";
		// String in eine Zahl konvertieren
		BigInteger checkIBANSum = new BigInteger(checkIBAN);
		// Modulo rechnen
		BigInteger faktor = new BigInteger("97");
		long div = checkIBANSum.remainder(faktor).longValue();
		// Differenz zu 98
		long pZiffer = 98 - div;
		// IBAN Regeln einhalten (22 Stellen)
		return "DE" + (pZiffer < 10 ? "0" : "") + pZiffer + blz + knr;
	}

	/**
	 * Anscheinend darf der Verwendungszweck im Sepa-Format fehler [0..1] sein, darum bekommen wir bei getVwz() ein null
	 * Allerdings brauchen wir ein String.
	 *
	 * @param item String|null
	 * @return String
	 */
	public static String derfNullSein(String item) {
		return (item != null) ? item : "";
	}
}
