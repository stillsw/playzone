package encrypt;

import ui.View;
import io.*;
import model.*;

import java.io.*;
import java.util.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertStoreException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Iterator;

import org.bouncycastle.cms.CMSException;



/**
*/
public class ButtonEncryption {
	public static boolean noEncryption;
	public static final String NOENCRYPT = "NOENCRYPT";
	private static final String CMD_START_INSTANT = "cmd=_xclick,business=";
	private static final String CMD_START_CART = "cmd=_cart,business=";
	private static final String CMD_START_DONATION = "cmd=_donations,business=";
	private static final String CMD_AMOUNT = ",amount=";
	private static final String CMD_CURRENCY = ",currency_code=";
	private static final String CMD_ITEM = ",item_name=";
	private static final String CMD_NOTE = ",no_note=0";
	private static final String CMD_CERT_ID = ",cert_id=";
	private static final String CMD_RETURN_CONFIRMED = ",return=";
	private static final String CMD_RETURN_CANCELLED = ",cancel_return=";
	private static final String CMD_ADD = ",add=1";
	private static final String CMD_QUANTITY = ",undefined_quantity=1";
	private static final String CMD_INSTRUCTIONS_SHIPPING = ",no_shipping=2";
	private static final String CMD_INSTRUCTIONS_NOT_SHIPPING = ",no_shipping=1";
	private static final String CMD_TAX = ",tax=0";
	private static final String CMD_DISPLAY = ",display=1";
	private static final String CMD_BN_INSTANT = ",bn=PP-BuyNowBF";
	private static final String CMD_BN_CART = ",bn=PP-ShopCartBF";
	private static final String CMD_BN_DONATION = ",bn=PP-DonationsBF";
	private static final String COMMAND = "_s-xclick";
	
	private String keyPath = null;
	private String certPath = null;
	private String paypalCertPath = null;
	private String keyPass = "password";
	private String actionUrl;
	private Business business;
	private Encryptor encryptEngine;
	private float usdRate;
	private float gbpRate;
	private float eurRate;
	private Config config;
	private String returnConfirmed;
	private String returnCancelled;

	public ButtonEncryption(Config config, String keyPass, float usdRate, float gbpRate, float eurRate) throws Exception {

		if (config == null || keyPass.length() == 0)
			throw new Exception("ButtonEncryption Error: something missing, one of Config or Password");

		if (usdRate == 0.0f || gbpRate == 0.0f || eurRate == 0.0)
			throw new Exception("ButtonEncryption Error: no rate can be 0.0 (usd="+usdRate+", gbp="+gbpRate+", eur="+eurRate+")");
		
		this.config = config;
		this.business = config.getBusiness();
		this.returnConfirmed = business.getConfirmedUrl();
		this.returnCancelled = business.getCancelledUrl();
		this.keyPass = keyPass;
		this.usdRate = usdRate;
		this.gbpRate = gbpRate;
		this.eurRate = eurRate;
		
		KeyDetailsLoader keyDetailsLoader = config.getKeyDetailsLoader();
		String filesFolder = keyDetailsLoader.getFullPathToFiles();
		certPath = filesFolder + keyDetailsLoader.getPublicCertificateFileName();
		keyPath = filesFolder + keyDetailsLoader.getPrivateKeyP12FileName();
		paypalCertPath = filesFolder + keyDetailsLoader.getPaypalCertificateFileName();
		
		encryptEngine = new Encryptor(keyPath, certPath, paypalCertPath, keyPass);
		
		if (business.isSandbox())
			actionUrl = "https://www." + Business.SANDBOX + ".paypal.com/cgi-bin/webscr";		
		else
			actionUrl = "https://www.paypal.com/cgi-bin/webscr";		
			
	}

	private void getButtonEncryptString(StringBuffer buffer, String businessName, String itemName, String certificateId, float calculatedAmount, String currencyCode, boolean isDonation, boolean isViewCart, String writeShoppingStyle, boolean isShipping) 
		throws Exception {
		
		String cmdStart = CMD_START_INSTANT;
		if (isDonation)
			cmdStart = CMD_START_DONATION;
		else if (isViewCart || writeShoppingStyle.equals(XmlButtonWriter.SHOPPING_STYLE_CART))
			cmdStart = CMD_START_CART;

		buffer.setLength(0);
		buffer.append(cmdStart);
		buffer.append(businessName);

		if (isViewCart) {
			buffer.append(CMD_DISPLAY);
		}
		else {
			buffer.append(CMD_ITEM);
			buffer.append(itemName);
			buffer.append(CMD_CURRENCY);
			buffer.append(currencyCode);
			buffer.append(CMD_RETURN_CONFIRMED);
			buffer.append(returnConfirmed);
			buffer.append(CMD_RETURN_CANCELLED);
			buffer.append(returnCancelled);

		    if (isDonation) {
				buffer.append(CMD_TAX);
				buffer.append(CMD_BN_DONATION);
		    }
		    else {
		    	if (calculatedAmount != 0.0f) {
					buffer.append(CMD_AMOUNT);
					buffer.append(calculatedAmount);
		    	}
		    	else if (writeShoppingStyle.equals(XmlButtonWriter.SHOPPING_STYLE_CART)) {
		    		throw new Exception("Error: cannot create cart style button for item "+itemName+", Amount=0.0 or not specified");
		    	}
		    	else // want them to put in a note (this isn't enforced by pp though)
					buffer.append(CMD_NOTE);
		    		
		        if (isShipping)
					buffer.append(CMD_INSTRUCTIONS_SHIPPING);
		        else
					buffer.append(CMD_INSTRUCTIONS_NOT_SHIPPING);
		    
			    if (writeShoppingStyle.equals(XmlButtonWriter.SHOPPING_STYLE_CART)) {
					buffer.append(CMD_ADD);
					buffer.append(CMD_BN_CART);
		    	}
			    else {
					buffer.append(CMD_QUANTITY);
					buffer.append(CMD_BN_INSTANT);
			    }
		    }
		}
		
		buffer.append(CMD_CERT_ID);
		buffer.append(certificateId);
	}
	
	public ArrayList<String> encryptButtonItems() throws Exception {
		
		ArrayList<String> results = new ArrayList<String>();
		StringBuffer buffer = new StringBuffer();
		try {
			String businessName = business.getPaypalBusinessName();
			String certificateId = business.getPaypalCertificateId();
			business.getBaseCurrency();
			HashMap<String, PaypalCurrency> currencies = business.getCommissionCurrencies();
			//noEncryption = true;
			for (Iterator itCur = currencies.keySet().iterator(); itCur.hasNext(); ) {
				String currencyCode = (String) itCur.next();
				results.add("Encryption:" + (noEncryption ? "Off" : "On") + " ---------------------------------------------\nItems for Currency="+currencyCode);
				PaypalCurrency currency = currencies.get(currencyCode);
				ICurrencyConversion currencyModel = currency.getConversionModel();
				for (Iterator it = business.getItems().iterator(); it.hasNext(); ) {
					Item item = (Item) it.next();
					String itemName = item.getName();
					boolean isDonation = item.isDonation();
					boolean isViewCart = item.isViewCart();
					boolean isShipping = (item.getShipping() != null);
					
					if (isViewCart || isDonation || item.getBasePrice() == null) { // no amount
						results.add("non-amount item="+itemName+" viewcart="+isViewCart+" donation="+isDonation);
						if (isViewCart) {
							getButtonEncryptString(buffer, businessName, itemName, certificateId, 0.0f, currencyCode, isDonation, isViewCart, XmlButtonWriter.SHOPPING_STYLE_CART, isShipping);
							String encryptResult = encryptEngine.getButtonEncryptionValue(noEncryption, buffer.toString(), keyPath, certPath, paypalCertPath, keyPass);
							item.addCurrencyButton(config, currencyCode, COMMAND, encryptResult, actionUrl, XmlButtonWriter.SHOPPING_STYLE_CART);
						}
						else { // either donation or 0.0 amount
							getButtonEncryptString(buffer, businessName, itemName, certificateId, 0.0f, currencyCode, isDonation, isViewCart, XmlButtonWriter.SHOPPING_STYLE_INSTANT, isShipping);
							String encryptResult = encryptEngine.getButtonEncryptionValue(noEncryption, buffer.toString(), keyPath, certPath, paypalCertPath, keyPass);
							item.addCurrencyButton(config, currencyCode, COMMAND, encryptResult, actionUrl, XmlButtonWriter.SHOPPING_STYLE_INSTANT);
						}
					}
					else if (!item.isAvailable()) {
						results.add("amount item="+itemName+" not available for buying");
						item.addCurrencyButton(config, currencyCode, null, null, null, XmlButtonWriter.SHOPPING_STYLE_INSTANT);
					}
					else {
						float calculatedAmount = currencyModel.getCalculatedAmount(business, item);
						results.add("amount item="+itemName+" basePrice="+item.getBasePrice()+" shipping="+item.getShipping()+" calculatedAmount="+calculatedAmount);
						if (item.isMakeInstantButton()) {
							getButtonEncryptString(buffer, businessName, itemName, certificateId, calculatedAmount, currencyCode, isDonation, isViewCart, XmlButtonWriter.SHOPPING_STYLE_INSTANT, isShipping);
							String encryptResult = encryptEngine.getButtonEncryptionValue(noEncryption, buffer.toString(), keyPath, certPath, paypalCertPath, keyPass);
							item.addCurrencyButton(config, currencyCode, COMMAND, encryptResult, actionUrl, XmlButtonWriter.SHOPPING_STYLE_INSTANT);
						}
						if (item.isMakeCartButton()) {
							getButtonEncryptString(buffer, businessName, itemName, certificateId, calculatedAmount, currencyCode, isDonation, isViewCart, XmlButtonWriter.SHOPPING_STYLE_CART, isShipping);
							String encryptResult = encryptEngine.getButtonEncryptionValue(noEncryption, buffer.toString(), keyPath, certPath, paypalCertPath, keyPass);
							item.addCurrencyButton(config, currencyCode, COMMAND, encryptResult, actionUrl, XmlButtonWriter.SHOPPING_STYLE_CART);
						}
					}
				}
			}
			
		} 
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		} 
		results.add("---------------------------------------------");
		return results;
	}
	
}
