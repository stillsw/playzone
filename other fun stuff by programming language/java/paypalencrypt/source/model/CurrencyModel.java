package model;

/**
 * Based on the following rules
 * 			1	Foreign currencies are charged at a higher percentage rather than by cross-border fee
 *			2	This way would mean we can predict the results because we know the currency being used.
 *
 */
public class CurrencyModel extends AbstractCurrencyModel implements ICurrencyConversion {

	public CurrencyModel(PaypalCurrency currency) {
		super(currency);
	}

	public float getCalculatedAmount(Business business, Item item) {
		float grossUpTotal = 0.0f;
		
		// keep up with what is calculated for the html calculation text too
		int calcsSize = ((item.getShipping() == null) ? 6 : 8);
		String[] calcs = new String[calcsSize];
		int i = 0;
		Float basePrice = item.getBasePrice();
		
		if (basePrice != null) {
			String itemName = item.getName();
			Float shipping = item.getShipping();
			String itemBaseCurrency = item.getItemBaseCurrency();
			System.out.println("itemName="+itemName+" basePrice="+basePrice);
			float cost = getCurrencyConversion(business, itemBaseCurrency, basePrice.floatValue());
			calcs[i++] = "Cost";
			calcs[i++] = getDisplayAmount(cost);
			
			if (shipping != null) {
				System.out.println("itemName="+itemName+" shipping="+shipping);
				float shippingAmt = getCurrencyConversion(business, itemBaseCurrency, shipping.floatValue());
				cost += shippingAmt;
				calcs[i++] = "Shipping";
				calcs[i++] = getDisplayAmount(shippingAmt);
			}
			
			float payPalFlatFee = currency.getFlatFee();
			System.out.println("got payPal flat fee="+payPalFlatFee);
			float payPalRate = currency.getCommissionRate();
			System.out.println("got payPal rate="+payPalRate);
			float grossUpFlatFee = getGrossFromNet(payPalFlatFee, payPalRate);
			System.out.println("grossed up payPal fee="+grossUpFlatFee);
			float grossUpAmt = getGrossFromNet(cost, payPalRate);
			System.out.println("grossed up cost="+grossUpAmt);
			grossUpTotal = grossUpAmt + grossUpFlatFee;
			System.out.println("grossed up total="+grossUpTotal);
			float payPalTotal = grossUpTotal - cost;
			System.out.println("Paypal total ="+payPalTotal);
			payPalTotal = Math.round(payPalTotal * 100.0f) / 100.0f;
			System.out.println("rounded Paypal total ="+payPalTotal);
			String explainCurrency = ""; // don't detail it "(" + payPalRate + "% + " + getDisplayAmount(payPalFlatFee) + ")";
			
			calcs[i++] = "PayPal "+explainCurrency;
			calcs[i++] = getDisplayAmount(payPalTotal);
			calcs[i++] = "Total";		
			System.out.println("TOTAL for "+itemName+"="+grossUpTotal);
			grossUpTotal = Math.round(grossUpTotal * 100.0f) / 100.0f;
			System.out.println("TOTAL rounded for "+itemName+"="+grossUpTotal);
			calcs[i++] = getDisplayAmount(grossUpTotal);

			item.addCurrencyCalculation(currencyCode, calcs);
			
		}
		return grossUpTotal;
	}

}
