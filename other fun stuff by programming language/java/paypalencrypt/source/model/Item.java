package model;

import java.util.*;
import java.text.*;
import org.w3c.dom.Element;


public class Item {

	public static final String ITEM = "item";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	private String name;
	private Element descriptionElement;
	public static final String BASE_PRICE = "basePrice";
	private Float basePrice;
	public static final String CURRENCY = "currency";
	private String itemBaseCurrency;
	private Float shipping;
	public static final String SHIPPING = "shipping";
	public static final String SHOPPING_STYLE = "shoppingStyle";
	public static final String SHOPPING_STYLE_BOTH = "both";
	public static final String SHOPPING_STYLE_INSTANT = "instant";
	public static final String SHOPPING_STYLE_CART = "cart";
	public static final String SHOPPING_STYLE_VIEWCART = "viewcart";
	public static final String SHOPPING_STYLE_DONATION = "donation";
	public static final String AVAILABLE = "available";
	private boolean isDonation;
	private boolean isViewCart;
	private boolean isAvailable;
	private boolean makeInstantButton;
	private boolean makeCartButton;
	private HashMap<String, HashMap> currencyButtons = new HashMap<String, HashMap>();
	private HashMap<String, String[]> currencyCalculations = new HashMap<String, String[]>();
	private static final String EXPLANATION_ITEM_CURRENCY_MARKER = "$ITEM_CURRENCY";
	private static final String EXPLANATION_BUY_CURRENCY_MARKER = "$CURRENCY";
	
	public Item(String name, Float basePrice, String currency, Float shipping, boolean isDonation, boolean isViewCart, Element descriptionElement, String shoppingStyle, boolean isAvailable) {
		this.name = name;
		this.basePrice = basePrice;
		this.itemBaseCurrency = currency;
		this.shipping = shipping;
		this.isDonation = isDonation;
		this.isViewCart = isViewCart;
		this.isAvailable = isAvailable;
		this.descriptionElement = descriptionElement;
		this.makeInstantButton = (SHOPPING_STYLE_INSTANT.equalsIgnoreCase(shoppingStyle) 
				  || SHOPPING_STYLE_BOTH.equalsIgnoreCase(shoppingStyle));
		this.makeCartButton = (SHOPPING_STYLE_CART.equalsIgnoreCase(shoppingStyle) 
				  || SHOPPING_STYLE_BOTH.equalsIgnoreCase(shoppingStyle));
	}

	public Float getBasePrice() {
		return basePrice;
	}
	
	public String getItemBaseCurrency() {
		return itemBaseCurrency;
	}

	public String getName() {
		return name;
	}

	public Element getDescriptionElement() {
		return descriptionElement;
	}

	public Float getShipping() {
		return shipping;
	}

	public boolean isDonation() {
		return isDonation;
	}

	public boolean isViewCart() {
		return isViewCart;
	}

	public boolean isMakeCartButton() {
		return makeCartButton;
	}

	public boolean isMakeInstantButton() {
		return makeInstantButton;
	}

	public boolean isAvailable() {
		return isAvailable;
	}

	public HashMap<String, String[]> getCurrencyCalculations() {
		return currencyCalculations;
	}

	public void addCurrencyCalculation(String currencyCode, String[] calculations) {
		this.currencyCalculations.put(currencyCode, calculations);
	}

	public HashMap<String, HashMap> getCurrencyButtons() {
		return currencyButtons;
	}

	public void addCurrencyButton(Config config, String currencyCode, String command, String encryptedText, String actionUrl, String writeShoppingStyle) {
		
		// have a look for the currency hashmap first
		HashMap<String, Button> shoppingStyleButtons = (HashMap) currencyButtons.get(currencyCode);
		if (shoppingStyleButtons == null) {
			shoppingStyleButtons = new HashMap<String, Button>();
			currencyButtons.put(currencyCode, shoppingStyleButtons);
		}
		
		String imageKey = writeShoppingStyle;
		if (this.isDonation)
			imageKey = Config.BUTTON_IMAGE_DONATE;
		else if (this.isViewCart)
			imageKey = Config.BUTTON_IMAGE_VIEWCART;
			
		String imageName = config.getButtonImages().get(imageKey);
		Button button = new Button(command, encryptedText, imageName, actionUrl);
		shoppingStyleButtons.put(writeShoppingStyle, button);
	}
	
	public static String adjustCurrencyExplanation(String baseCurrencyCode, String currencyCode, String originalText) {

		StringBuffer contents = new StringBuffer(originalText);
		do {
			int inx = contents.indexOf(EXPLANATION_ITEM_CURRENCY_MARKER);
			contents.replace(inx, inx + EXPLANATION_ITEM_CURRENCY_MARKER.length(), baseCurrencyCode);
		}
		while (contents.indexOf(EXPLANATION_ITEM_CURRENCY_MARKER) != -1);

		do {
			int inx = contents.indexOf(EXPLANATION_BUY_CURRENCY_MARKER);
			contents.replace(inx, inx + EXPLANATION_BUY_CURRENCY_MARKER.length(), currencyCode);
		}
		while (contents.indexOf(EXPLANATION_BUY_CURRENCY_MARKER) != -1);

		return contents.toString();
	}
}
