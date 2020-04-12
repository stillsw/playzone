package model;

import java.util.*;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Business {

	public static Business instance;
	public static final String BUSINESS = "business";
	public static final String PAYPAL_BUSINESS_NAME = "paypalName";
	private String paypalBusinessName;
	public static final String SANDBOX = "sandbox";
	private boolean sandbox;
	public static final String PAYPAL_CERTIFICATE_ID = "paypalCertificateId";
	private String paypalCertificateId;
	public static final String BASE_CURRENCY = "baseCurrency";
	private String baseCurrency;
	public static final String FLAT_FEE = "flatFee";
	public static final String PAYPAL_CONVERSION_SPREAD_PERCENT = "paypalConversionSpreadPercent";
	private float paypalConversionSpreadPercent;
	public static final String COMMISSION_PERCENT = "commissionPercent";
	public static final String CONVERSION = "conversion";
	public static final String CONVERSION_CURRENCY = "currency";
	private HashMap<String, PaypalCurrency> commissionCurrencies;
	public static final String CURRENCY_EXPLANATION = "currencyExplanation";
	private String currencyExplanation;
	private ArrayList<Item> items = new ArrayList<Item>();
	public static final String CONFIRMED_URL = "confirmedUrl";
	public static final String CANCELLED_URL = "cancelledUrl";
	private String confirmedUrl;
	private String cancelledUrl;
	public static final String CONVERSION_MODEL = "model";
	
	public Business(String paypalBusinessName, boolean sandbox, String paypalCertificateId, String baseCurrency, HashMap<String, PaypalCurrency> currencies, String currencyExplanation, float paypalConversionSpreadPercent, String confirmedUrl, String cancelledUrl) {
		instance = this;
		this.paypalBusinessName = paypalBusinessName;
		this.sandbox = sandbox;
		this.paypalCertificateId = paypalCertificateId;
		this.baseCurrency = baseCurrency;
		this.commissionCurrencies = currencies;
		this.currencyExplanation = currencyExplanation;
		this.paypalConversionSpreadPercent = paypalConversionSpreadPercent;
		this.confirmedUrl = confirmedUrl;
		this.cancelledUrl = cancelledUrl;
	}

	public static Business getInstance() {
		return instance;
	}
	
	public String getBaseCurrency() {
		return baseCurrency;
	}

	public String getCurrencyExplanation() {
		return currencyExplanation;
	}

	public HashMap<String, PaypalCurrency> getCommissionCurrencies() {
		return commissionCurrencies;
	}
	
	public PaypalCurrency getCurrency(String currencyCode) {
		return commissionCurrencies.get(currencyCode);
	}

	public String getPaypalBusinessName() {
		return paypalBusinessName;
	}

	public String getPaypalCertificateId() {
		return paypalCertificateId;
	}

	public float getPaypalConversionSpreadPercent() {
		return paypalConversionSpreadPercent;
	}

	public boolean isSandbox() {
		return sandbox;
	}

	public String getCancelledUrl() {
		return cancelledUrl;
	}

	public String getConfirmedUrl() {
		return confirmedUrl;
	}

	public ArrayList<Item> getItems() {
		return items;
	}

	public void addItem(Item item) {
		items.add(item);
	}

	public void setRates(float usd, float gbp, float eur) {
		PaypalCurrency paypalCurrency = commissionCurrencies.get("CAD");
		paypalCurrency.setCurrentExchangeRate(1.0f);
		paypalCurrency = commissionCurrencies.get("USD");
		paypalCurrency.setCurrentExchangeRate(usd);
		paypalCurrency = commissionCurrencies.get("GBP");
		paypalCurrency.setCurrentExchangeRate(gbp);
		paypalCurrency = commissionCurrencies.get("EUR");
		paypalCurrency.setCurrentExchangeRate(eur);
	}
	
}
