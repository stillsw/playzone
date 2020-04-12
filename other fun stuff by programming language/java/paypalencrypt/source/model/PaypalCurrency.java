package model;

public class PaypalCurrency {

	private static final String CURRENCY_MODEL = "currencyModel";
	private String currencyCode;
	private float commissionRate;
	private float flatFee;
	private float currentExchangeRate;
	private ICurrencyConversion conversionModel;
	
	public PaypalCurrency(String currencyCode, float paypalCommissionRate, float paypalCommissionFee, String model) throws Exception {
		this.currencyCode = currencyCode;
		this.commissionRate = paypalCommissionRate;
		this.flatFee = paypalCommissionFee;
		if (model.equals(CURRENCY_MODEL))
			conversionModel = new CurrencyModel(this);
		else
			throw new Exception("Error: model not implemented = "+model);
	}

	public float getCurrentExchangeRate() {
		return currentExchangeRate;
	}

	public void setCurrentExchangeRate(float currentExchangeRate) {
		this.currentExchangeRate = currentExchangeRate;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public float getFlatFee() {
		return flatFee;
	}

	public float getCommissionRate() {
		return commissionRate;
	}

	public ICurrencyConversion getConversionModel() {
		return conversionModel;
	}
	
}
