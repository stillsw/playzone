package io;

import model.*;

import java.io.*;
import java.util.*;
import java.text.DateFormat;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


public class XmlButtonWriter {

	private static final String FILE_SEPARATOR = System.getProperty("file.separator");
	private static final String DOCUMENT_ELEMENT = "shoppingMarket";
	private static final String ENCODING = "utf-8";
	private static final String CURRENCY = "currency";
	private static final String CURRENCY_CODE = "code";
	private static final String CURRENCY_EXPLANATION = "explanation";
	private static final String CURRENCY_CALCULATIONS = "calculations";
	private static final String BUTTON = "button";
	private static final String SHOPPING_STYLE = "shoppingStyle";
	public static final String SHOPPING_STYLE_ALWAYS = "always";
	public static final String SHOPPING_STYLE_CART = "cart";
	public static final String SHOPPING_STYLE_INSTANT = "instant";
	private static final String COMMAND = "command";
	private static final String ACTION_URL = "actionUrl";
	private static final String ENCRYPTED_TEXT = "encryptedText";
	private static final String ITEM = "item";
	private static final String NAME = "name";
	private static final String TIMESTAMP = "timestamp";
	private static final String AMOUNT = "amount";
	
	private static XmlButtonWriter instance = new XmlButtonWriter();
	
	/**
	 * prevent creation of > 1 of these
	 *
	 */
	private XmlButtonWriter() {}
	
	public static XmlButtonWriter getInstance() throws Exception {
		return instance;
	}
	
	public void writeConfig(Config config) throws Exception {

		String fileName = config.getOutputFileName();
		
		if (fileName == null)
			throw new Exception("Error: writeConfig fileName cannot be null");
		
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document dom = builder.newDocument();
			// make the root element and load the nodes
			Element docEle = dom.createElement(DOCUMENT_ELEMENT);
			docEle.setAttribute(TIMESTAMP, DateFormat.getDateTimeInstance().format(new Date()));
			dom.appendChild(docEle);
			loadItemButtons(dom, docEle, config);
			
			OutputFormat format = new OutputFormat(dom);
			format.setEncoding(ENCODING);
			format.setIndenting(true);
			format.setIndent(2);
			File file = new File(fileName);
			XMLSerializer serializer = new XMLSerializer(new FileOutputStream(file), format);

			serializer.serialize(dom);

		}
		catch (Exception e) {
			e.printStackTrace(); 
			throw e;
		}
	}

	private void loadItemButtons(Document dom, Element parentElement, Config config) {
		
		HashMap<String, String> buttonImages = config.getButtonImages();
		Business business = config.getBusiness();
		String currencyExplanation = business.getCurrencyExplanation();
		for (Iterator it = business.getItems().iterator(); it.hasNext(); ) {
			Item item = (Item) it.next();
			String itemBaseCurrencyCode = item.getItemBaseCurrency();
			boolean includesAmount = !(item.isDonation() || item.isViewCart() || item.getBasePrice() == null);
			includesAmount = (includesAmount && item.isAvailable());
			HashMap<String, String[]> currencyCalculations = item.getCurrencyCalculations();
			HashMap<String, HashMap> currencyButtons = item.getCurrencyButtons();
			
			Element itemElement = dom.createElement(ITEM);
			parentElement.appendChild(itemElement);
			itemElement.setAttribute(NAME, item.getName());
		
			Element descriptionElement = item.getDescriptionElement();
			dom.adoptNode(descriptionElement);
			itemElement.appendChild(descriptionElement);
//			Element descriptionElement = dom.createElement(DESCRIPTION);
//			itemElement.appendChild(descriptionElement);
//			descriptionElement.setTextContent(item.getDescription());
						
			for (Iterator curIt = business.getCommissionCurrencies().keySet().iterator(); curIt.hasNext(); ) {
				String currencyCode = (String) curIt.next();
				writeButtonsForCurrency(dom, itemElement, itemBaseCurrencyCode, currencyCode, currencyExplanation, currencyCalculations, currencyButtons, includesAmount, item.isAvailable());
			}
		}
	}
	
	private void writeButtonsForCurrency(Document dom, Element itemElement, String itemBaseCurrencyCode, String currencyCode, String currencyExplanation, HashMap<String, String[]> currencyCalculations, HashMap<String, HashMap> currencyButtons, boolean includesAmount, boolean isAvailable) {

		HashMap<String, Button> shoppingStyleButtons = (HashMap<String, Button>) currencyButtons.get(currencyCode);
		if (shoppingStyleButtons != null) {

			Element currencyElement = dom.createElement(CURRENCY);
			itemElement.appendChild(currencyElement);
			currencyElement.setAttribute(CURRENCY_CODE, currencyCode);
			
			if (includesAmount) {
// not using the currency explanations at the moment
//				if (!currencyCode.equals(itemBaseCurrencyCode)) {
//					Element currencyExplElement = dom.createElement(CURRENCY_EXPLANATION);
//					currencyElement.appendChild(currencyExplElement);
//					currencyExplElement.setTextContent(Item.adjustCurrencyExplanation(itemBaseCurrencyCode, currencyCode, currencyExplanation));
//				}
				
				String[] currencyCalcs = (String[]) currencyCalculations.get(currencyCode);
				if (currencyCalcs != null) {
					for (int i = 0; i < currencyCalcs.length; i++) {
						Element currencyCalcElement = dom.createElement(CURRENCY_CALCULATIONS);
						currencyElement.appendChild(currencyCalcElement);
						currencyCalcElement.setTextContent(currencyCalcs[i++]);			
						currencyCalcElement.setAttribute(AMOUNT, currencyCalcs[i]);
					}
				}
			}
			
			if (isAvailable) {
				for (Iterator it = shoppingStyleButtons.keySet().iterator(); it.hasNext(); ) {
					String shoppingStyle = (String) it.next();
						
					Button button = (Button) shoppingStyleButtons.get(shoppingStyle);
						
					if (button != null) {
						
						Element buttonElement = dom.createElement(BUTTON);
						currencyElement.appendChild(buttonElement);
						buttonElement.setAttribute(SHOPPING_STYLE, shoppingStyle);
						
						if (button.getImageName() != null) {
							Element imageElement = dom.createElement(Config.BUTTON_IMAGE);
							buttonElement.appendChild(imageElement);
							imageElement.setTextContent(button.getImageName());
						}
											
						Element actionUrl = dom.createElement(ACTION_URL);
						buttonElement.appendChild(actionUrl);
						actionUrl.setTextContent(button.getActionUrl());
						
						Element buttonCmd = dom.createElement(COMMAND);
						buttonElement.appendChild(buttonCmd);
						buttonCmd.setTextContent(button.getCommand());
						
						Element encryptedElement = dom.createElement(ENCRYPTED_TEXT);
						buttonElement.appendChild(encryptedElement);
						encryptedElement.setTextContent(button.getEncryptedText());					
					}
				}
			}
		}
	}

}