package model;

import java.text.DecimalFormat;
import java.util.HashMap;

public class AbstractCurrencyModel {

	protected static final DecimalFormat moneyFormat = new DecimalFormat("0.00");
	protected static HashMap<String, String> currencySymbols = new HashMap<String, String>();
	static {
		currencySymbols.put("CAD", "$");
		currencySymbols.put("EUR", "€");
		currencySymbols.put("GBP", "£");
		currencySymbols.put("USD", "$");
	}
	protected String currencyCode;
	protected PaypalCurrency currency;

	protected static float getGrossFromNet(float net, float perc) {
		float rev = 100.0f - perc;
		float revPerc = rev / 100.0f;
		float grossAmt = (net / revPerc);// * 100.0f;
		return grossAmt;
	}

	protected String getDisplayAmount(float amount) {
		return currencySymbols.get(currencyCode) + moneyFormat.format((double) amount);
	}

	protected AbstractCurrencyModel(PaypalCurrency currency) {
		this.currency = currency;
		this.currencyCode = currency.getCurrencyCode();
//		System.out.println("Using "+this.getClass().getName()+" for "+currencyCode);
	}

	public float getCurrencyConversion(Business business, String itemBaseCurrency, float amount) {
		
		System.out.println("convert "+amount+" from "+itemBaseCurrency+" to "+currencyCode);

		// don't convert if currency is the base for the item
		if (itemBaseCurrency.equalsIgnoreCase(currencyCode)) {
			System.out.println("no conversion required");
			return amount;
		}
		
		float itemCadAmt = amount;
		if (!itemBaseCurrency.equalsIgnoreCase(business.getBaseCurrency())) {// it's not a CAD item, convert to CAD first
			float rate = business.getCurrency(itemBaseCurrency).getCurrentExchangeRate();
			System.out.println("get the rate for "+itemBaseCurrency+ "="+rate);
			itemCadAmt = amount * rate;
			System.out.println("CAD Amount="+itemCadAmt);
		}
		
		System.out.println("so now, convert "+itemCadAmt+" from CAD to "+currencyCode);
		float rate = currency.getCurrentExchangeRate();
		System.out.println("got the rate for "+currencyCode+ "="+rate);
		float itemCurrAmt = itemCadAmt / rate;
		System.out.println("converted to " + currencyCode + " Amount="+itemCurrAmt+" now add spread costs");
		
		// add the spread
		float spreadPercent = business.getPaypalConversionSpreadPercent();
		rate -= rate * (spreadPercent / 100.0f);
		System.out.println("got the spread rate for "+currencyCode+ "="+rate+" spread="+spreadPercent+"%");
		itemCurrAmt = itemCadAmt / rate;
		System.out.println("spreaded value " + currencyCode + " Amount="+itemCurrAmt);
		
		return itemCurrAmt;
	}

/*	
	protected float getPayPalRate(Business business, String itemBaseCurrency, String itemCurrency) {

		if (itemBaseCurrency.equals(this.baseCurrency)) // the item is priced as CAD
			if (itemBaseCurrency.equals(itemCurrency)) {// and it's CAD, return standard rate
				PaypalCurrency paypalCurrency = commissionCurrencies.get(itemCurrency);
				return paypalCurrency.getPaypalCommissionRate();
			}
			else { // it's a conversion from a CAD item to another currency
				PaypalCurrency paypalCurrency = commissionCurrencies.get(itemCurrency);
				return paypalCurrency.getPaypalCommissionRate();
			}
		else { //TODO: it's a conversion from a non-CAD item to another currency... asfik this is the same... check
			PaypalCurrency paypalCurrency = commissionCurrencies.get(itemCurrency);
			return paypalCurrency.getPaypalCommissionRate();
		}
	}
	
	protected static float getPayPalFlatFee(Business business, String itemBaseCurrency, String itemCurrency) {

		if (itemBaseCurrency.equals(this.baseCurrency)) // the item is priced as CAD
			if (itemBaseCurrency.equals(itemCurrency)) {// and it's CAD, return standard rate
				PaypalCurrency paypalCurrency = commissionCurrencies.get(itemCurrency);
				return paypalCurrency.getPaypalCommissionFee();
			}
			else { // it's a conversion from a CAD item to another currency
				PaypalCurrency paypalCurrency = commissionCurrencies.get(itemCurrency);
				return paypalCurrency.getPaypalCommissionFee();
			}
		else { //TODO: it's a conversion from a non-CAD item to another currency... asfik this is the same... check
			PaypalCurrency paypalCurrency = commissionCurrencies.get(itemCurrency);
			return paypalCurrency.getPaypalCommissionFee();
		}
				

	}
*/	

}