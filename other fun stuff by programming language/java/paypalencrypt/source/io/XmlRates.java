package io;

import model.*;

import java.io.*;
import java.util.*;
import java.net.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlRates {

	private static final String ITEM = "item";
	private static final String TARGET_CURRENCY = "cb:targetCurrency";
	private static final String VALUE = "cb:value";
	private static final String URI_STRING = "http://www.bankofcanada.ca/rss/fx/close/fx-close.xml";
	
	private static XmlRates instance = new XmlRates();
	
	/**
	 * prevent creation of > 1 of these
	 *
	 */
	private XmlRates() {}
	
	/**
	 * on the first call initialize the dir
	 * @return
	 */
	public static XmlRates getInstance() throws Exception {
		return instance;
	}
	
	public ArrayList<String> readRates(Config config) {

		ArrayList<String> results = new ArrayList<String>(); 
		try {
			URI uri = new URI(URI_STRING);
			DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document dom = parser.parse(uri.toString());
		
			// get the root element
			Element docEle = dom.getDocumentElement();
			results.add("read from " + URI_STRING + " document element="+docEle.getNodeName());

			HashMap<String, PaypalCurrency> currencies = config.getBusiness().getCommissionCurrencies();
			
			NodeList nl = docEle.getElementsByTagName(ITEM);
			for (int i = 0; i < nl.getLength(); i++) {
				Element el = (Element)nl.item(i);
				String targetCurrency = getTextValue(el, TARGET_CURRENCY);
				PaypalCurrency currency = currencies.get(targetCurrency);
				if (currency != null) {
					float value = 1.0f / getFloatValue(getTextValue(el, VALUE));
					results.add("targetCurrency="+targetCurrency+" value="+value);
					currency.setCurrentExchangeRate(value);
				}
			}
			return results;
		}
		catch (Exception e) {
			String error = "Error reading rates from "+URI_STRING+" : "+e;
			System.out.println(error);
			e.printStackTrace(); 
			results.add(error);
		}
		return results;
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
	
}