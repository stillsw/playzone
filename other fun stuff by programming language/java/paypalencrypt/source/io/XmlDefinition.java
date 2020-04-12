package io;

import model.*;

import java.io.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlDefinition {

	private static final String FILE_SEPARATOR = System.getProperty("file.separator");
	private static final String DOCUMENT_ELEMENT = "definitions";
	
	private String encoding;
	
	// keep the directory file for all new file operations
	private static File dir; 
	
	private static XmlDefinition instance = new XmlDefinition();
	
	/**
	 * prevent creation of > 1 of these
	 *
	 */
	private XmlDefinition() {}
	
	/**
	 * on the first call initialize the dir
	 * @return
	 */
	public static XmlDefinition getInstance(String rootFolder) throws Exception {

		dir = new File(rootFolder);
		if (!dir.isDirectory()) {
			throw new Exception("Error: this is not a folder: "+rootFolder);
		}
		
		return instance;
	}
	
	public Config loadDefinitions(boolean isSandbox) throws Exception {

		String folder = null;
		if (isSandbox)
			folder = KeyDetailsLoader.SANDBOX_FOLDER;
		else
			folder = KeyDetailsLoader.LIVE_FOLDER;

		Config config = null;
		try {
			DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document dom = parser.parse(findXmlDefintionsFile(new File(dir, folder)));
			encoding = dom.getInputEncoding();
		
			// get the root element
			Element docEle = dom.getDocumentElement();

			String outputFileName = readOutputFileName(docEle);
			HashMap<String, String> buttonImages = readButtonImages(docEle);
			FtpUploader ftpUploader = readFtpSettings(docEle);
			
			// read the business 
			Business business = readBusiness(docEle.getElementsByTagName(Business.BUSINESS));
			readBusinessItems(business, docEle.getElementsByTagName(Item.ITEM));
			
			KeyDetailsLoader keyDetailsLoader = new KeyDetailsLoader(dir, isSandbox);
			
			config = new Config(buttonImages, outputFileName, business, keyDetailsLoader, ftpUploader);
			
			return config;
		}
		catch (Exception e) {
			e.printStackTrace(); 
			throw e;
		}
	}

	private File findXmlDefintionsFile(File folderFile) throws Exception {
		
		if (!folderFile.isDirectory())
			throw new Exception("Error: folder not found - "+folderFile.getAbsolutePath());
		
		String[] fileNameList = folderFile.list();

		for (int i = 0; i < fileNameList.length; i++)
			if (fileNameList[i].startsWith("paypalDefinitions") && fileNameList[i].endsWith(".xml"))
				return new File(folderFile, fileNameList[i]);
		
		throw new Exception("Error: could not find file named paypalDefinitions*.xml in "+folderFile.getAbsolutePath());
	}
	
	private String readOutputFileName(Element docEle) {

		String fileName = getTextValue(docEle, Config.OUTPUT_FILE);
		NodeList nl = docEle.getElementsByTagName(Config.OUTPUT_FILE);
		
		if(nl != null && nl.getLength() > 0) {
			Element el = (Element)nl.item(0);
			if (!Boolean.parseBoolean(el.getAttribute(Config.CONTAINS_FULL_PATH)))
				fileName = dir.getAbsolutePath() + FILE_SEPARATOR + fileName;
		}
		
		return fileName;
	}

	private HashMap<String, String> readButtonImages(Element docEle) {

		NodeList nl = docEle.getElementsByTagName(Config.BUTTON_IMAGE);
		HashMap<String, String> buttonImages = new HashMap<String, String>();
		
		if(nl != null) {
			
			for(int i = 0; i < nl.getLength(); i++) {
				if (nl.item(i) instanceof Element) {
					Element el = (Element) nl.item(i);
					buttonImages.put(el.getAttribute(Config.BUTTON_IMAGE_NAME), el.getFirstChild().getNodeValue());
				}
			}
		}
		
		return buttonImages;
	}

	private FtpUploader readFtpSettings(Element docEle) {

		FtpUploader ftpUploader = null;
		NodeList nl = docEle.getElementsByTagName(FtpUploader.FTP_SETTINGS);		
		if(nl != null && nl.getLength() > 0) {
			Element el = (Element) nl.item(0);
			String server = getTextValue(el, FtpUploader.FTP_SERVER);
			String userName = getTextValue(el, FtpUploader.FTP_USER_NAME);
			String remoteDir = getTextValue(el, FtpUploader.FTP_REMOTE_DIR);

			ftpUploader = new FtpUploader(server, userName, remoteDir);
		}
		
		return ftpUploader;
	}

	private Business readBusiness(NodeList nl) throws Exception {

		Business business = null;
		
		if(nl != null && nl.getLength() > 0) {
			Element el = (Element)nl.item(0);
			String paypalBusinessName = el.getAttribute(Business.PAYPAL_BUSINESS_NAME);
			boolean sandbox = Boolean.parseBoolean(el.getAttribute(Business.SANDBOX));
			String paypalCertificateId = el.getAttribute(Business.PAYPAL_CERTIFICATE_ID);
			String baseCurrency = el.getAttribute(Business.BASE_CURRENCY);
			String currencyExplanation = getTextValue(el, business.CURRENCY_EXPLANATION);
			String confirmedUrl = getTextValue(el, business.CONFIRMED_URL);
			String cancelledUrl = getTextValue(el, business.CANCELLED_URL);
			float paypalConversionSpreadPercent = getFloatValue(el.getAttribute(Business.PAYPAL_CONVERSION_SPREAD_PERCENT));
			HashMap<String, PaypalCurrency> conversionCurrencies = new HashMap<String, PaypalCurrency>();
			NodeList currs = el.getElementsByTagName(Business.CONVERSION);
			if (currs != null) {
				for(int i = 0; i < currs.getLength(); i++) {
					if (currs.item(i) instanceof Element) {
						Element conv = (Element) currs.item(i);
						String currency = conv.getAttribute(Business.CONVERSION_CURRENCY);
						String model = conv.getAttribute(Business.CONVERSION_MODEL);
						float commissionPercent = getFloatValue(conv.getAttribute(Business.COMMISSION_PERCENT));
						float flatFee = getFloatValue(conv.getAttribute(Business.FLAT_FEE));
						PaypalCurrency paypalCurrency = new PaypalCurrency(currency, commissionPercent, flatFee, model);
						conversionCurrencies.put(currency, paypalCurrency);
					}
				}
			}
			business = new Business(paypalBusinessName, sandbox, paypalCertificateId, baseCurrency, conversionCurrencies, currencyExplanation, paypalConversionSpreadPercent, confirmedUrl, cancelledUrl);

		}
		
		return business;

	}
	
	private void readBusinessItems(Business business, NodeList nl) {

		if(business != null && nl != null) 
			for(int i = 0; i < nl.getLength(); i++) {
				if (nl.item(i) instanceof Element) {
					Element el = (Element) nl.item(i);
					// mandatory
					String name = getTextValue(el, Item.NAME);
					String currency = getTextValue(el, Item.CURRENCY);
					NodeList descList = el.getElementsByTagName(Item.DESCRIPTION);
					Element descriptionElement = null;
					if(descList != null && descList.getLength() > 0) {
						descriptionElement = (Element)((Element)descList.item(0)).cloneNode(true);
					}
					String shoppingStyle = getTextValue(el, Item.SHOPPING_STYLE);
					boolean isDonation = Item.SHOPPING_STYLE_DONATION.equals(shoppingStyle);
					boolean isViewCart = Item.SHOPPING_STYLE_VIEWCART.equals(shoppingStyle);
					boolean isAvailable = Boolean.parseBoolean(getTextValue(el, Item.AVAILABLE));
					// optional
					Float basePrice = getFloatObject(getTextValue(el, Item.BASE_PRICE));
					Float shipping = getFloatObject(getTextValue(el, Item.SHIPPING));
					
					Item item = new Item(name, basePrice, currency, shipping, isDonation, isViewCart, descriptionElement, shoppingStyle, isAvailable);
					business.addItem(item);
				}
			}

	}

	private static String getDefaultTextValue(String value, String defaulted) {
		return (value == null || value.length() == 0 ? defaulted : value);
	}
	
	private static String getTextValue(Element ele, String tagName) {
		String textVal = null;
		NodeList nl = ele.getElementsByTagName(tagName);
		if(nl != null && nl.getLength() > 0) {
			Element el = (Element)nl.item(0);
			textVal = el.getFirstChild().getNodeValue();
		}

		return textVal;
	}

	/**
	 * Calls getTextValue and returns a int value
	 */
	private static float getFloatValue(String string) {
		//in production application you would catch the exception
		if (string == null)
			return (float) 0.0;
		else
			return Float.parseFloat(string);
	}

	private static Float getFloatObject(String string) {
		//in production application you would catch the exception
		if (string == null)
			return null;
		else
			return Float.valueOf(string);
	}
	
}