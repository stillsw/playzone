package ui;


import model.Business;
import model.Config;
import model.PaypalCurrency;
import io.*;
import java.util.*;

import java.security.Security;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import encrypt.ButtonEncryption;


public class View {
		
	public static final String ID = "ppencrypt.view";
	public static final String NONE = "";
	private Text textArea;
	private static boolean DEFAULT_SANDBOX = true;
	private XmlDefinition xmlDefinition;
	private Text certText;
	private Text privKeyText;
	private Text ppKeyText;
	private Text ftpSiteText;
	private Text outputFileText;
	private Text pwdText;
	private Text ftpPwdText;
	private Text gbpRate;
	private Text eurRate;
	private Text usdRate;
	private Button sandBoxBtn;
	private Button encryptAndWriteBtn;
	private Button ftpBtn;
	private Shell shell = null;
	private Config liveConfig;
	private Config sandboxConfig;
	private Config currentConfig;
	private Cursor defaultCursor;
	private Cursor waitCursor;
	ArrayList<String> resultsFromReadingRates;
	private boolean gotExchangeRates = false;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());	
		
		int FIRST_PARAM = 0;
		int SECOND_PARAM = 1;
		int THIRD_PARAM = 2;
		String USAGE = "usage";
		String DATA_DIRECTORY = "data";
		
		if (args != null && args.length > FIRST_PARAM && args[FIRST_PARAM].equals(USAGE)) {
			System.out.println("Usage: all parameters optional");
			System.out.println("    1. usage - to get this message");
			System.out.println(" or 1. sandbox - indicates default to test env");
			System.out.println("    2. NOENCRYPT - indicates do not encrypt the buttons");
			System.out.println("    3. dataDirectory - enter full path to data directory to override default");
			System.out.println("                     - default is run directory/data/pp and /pp-sandbox");
			System.exit(0);
		}
		
//		String userdir = System.getProperty("user.dir");
//		System.out.println("userdir="+userdir);
		
		View.DEFAULT_SANDBOX = (args != null && args.length > FIRST_PARAM && args[FIRST_PARAM].equalsIgnoreCase(Business.SANDBOX));
		ButtonEncryption.noEncryption = (args != null && args.length > SECOND_PARAM && args[SECOND_PARAM].equalsIgnoreCase(ButtonEncryption.NOENCRYPT));
		String rootFolder = System.getProperty("user.dir") + System.getProperty("file.separator") + DATA_DIRECTORY;
		if (args != null && args.length > THIRD_PARAM)
			rootFolder = args[THIRD_PARAM];
		
		System.out.println("rootFolder="+rootFolder);
		
		// TODO Auto-generated method stub
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */	
		
		Display display = Display.getDefault();
		View view = new View();
		try {
			view.createShell(rootFolder);
			view.shell.open();
	
			while (!view.shell.isDisposed()) {
				if (!display.readAndDispatch())
					display.sleep();
			}
		} 
		catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			display.dispose();
		}
	}

	/**
	 * This method initializes sShell
	 */
	private void createShell(String rootFolder) {
		shell = new Shell();
		shell.setText("PayPal Button Encryptor");
		shell.setSize(new Point(800, 600));
		this.waitCursor = shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT);
		this.defaultCursor = shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW);
		try {
			createLayout();
		} catch (Exception e) {
			String error = "Error: could not create layout because :" + e.getMessage();
			e.printStackTrace();
			System.out.println(error);
			if (this.textArea != null)
				this.textArea.setText(error);
		}
		try {
			xmlDefinition = XmlDefinition.getInstance(rootFolder);
			if (DEFAULT_SANDBOX)
				this.sandboxConfig = xmlDefinition.loadDefinitions(true);
			else
				this.liveConfig = xmlDefinition.loadDefinitions(false);
			
			loadConfigData();
		}
		catch (Exception e) {
			String error = "Error: could not load default configuration because :" + e.getMessage();
			e.printStackTrace();
			System.out.println(error);
			if (this.textArea != null)
				this.textArea.setText(error);
		}
		
		populateDataFields();
	}

	private void createLayout() {
		Composite parent = new Composite(shell, SWT.NONE);
		GridData textGridData = new GridData();
		textGridData.horizontalAlignment = GridData.FILL;
		textGridData.grabExcessHorizontalSpace = true;
		textGridData.grabExcessVerticalSpace = true;
		textGridData.verticalAlignment = GridData.FILL;
		GridData buttonLayoutData = new GridData();
		buttonLayoutData.horizontalAlignment = GridData.CENTER;
		buttonLayoutData.widthHint = 150;
		buttonLayoutData.verticalAlignment = GridData.CENTER;
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.verticalSpacing = 5;
		shell.setLayout(gridLayout);
		shell.setLayoutData(textGridData);
		parent.setLayout(gridLayout);
		parent.setLayoutData(textGridData);
		Group group = new Group(parent, SWT.SHADOW_ETCHED_IN);
		group.setLayout(gridLayout);
		group.setLayoutData(textGridData);
		Composite comp = new Composite(group, SWT.BORDER);
		GridData compGridData = new GridData();
		compGridData.horizontalAlignment = GridData.FILL;
		compGridData.grabExcessHorizontalSpace = true;
		compGridData.verticalAlignment = GridData.FILL;
		comp.setLayoutData(compGridData);
		GridLayout compGridLayout = new GridLayout();
		compGridLayout.numColumns = 2;
		compGridLayout.verticalSpacing = 5;
		comp.setLayout(compGridLayout);
		GridData inputsGridData = new GridData();
		inputsGridData.horizontalAlignment = GridData.FILL;
		inputsGridData.grabExcessHorizontalSpace = true;
		Label certLabel = new Label(comp, SWT.NONE);
		certLabel.setText("Your Public Certificate");
		certText = new Text(comp, SWT.BORDER);
		certText.setLayoutData(inputsGridData);
		certText.setEnabled(false);
		Label privKeyLabel = new Label(comp, SWT.NONE);
		privKeyLabel.setText("Your private PKCS12 key");
		privKeyText = new Text(comp, SWT.BORDER);
		privKeyText.setLayoutData(inputsGridData);
		privKeyText.setEnabled(false);
		Label ppKeyLabel = new Label(comp, SWT.NONE);
		ppKeyLabel.setText("PayPal's Public Certificate");
		ppKeyText = new Text(comp, SWT.BORDER);
		ppKeyText.setLayoutData(inputsGridData);
		ppKeyText.setEnabled(false);
		Label ftpSiteLabel = new Label(comp, SWT.NONE);
		ftpSiteLabel.setText("Ftp site/user/folder");
		ftpSiteText = new Text(comp, SWT.BORDER);
		ftpSiteText.setLayoutData(inputsGridData);
		ftpSiteText.setEnabled(false);
		
		Label pwdsLabel = new Label(comp, SWT.NONE);
		pwdsLabel.setText("Passwords");
		Composite pwds = new Composite(comp, SWT.NONE);
		GridData pwdsGridData = new GridData();
		pwdsGridData.horizontalAlignment = GridData.FILL;
		pwdsGridData.grabExcessHorizontalSpace = true;
		pwdsGridData.verticalAlignment = GridData.CENTER;
		GridLayout pwdsLayout = new GridLayout();
		pwdsLayout.numColumns = 4;
		pwdsLayout.verticalSpacing = 0;
		pwdsLayout.marginHeight = 0;
		pwds.setLayout(pwdsLayout);
		pwds.setLayoutData(pwdsGridData);
		Label keyPwdLabel = new Label(pwds, SWT.NONE);
		keyPwdLabel.setText("Private key");
		pwdText = new Text(pwds, SWT.BORDER | SWT.PASSWORD);
		Label ftpPwdLabel = new Label(pwds, SWT.NONE);
		ftpPwdLabel.setText("Ftp");
		ftpPwdText = new Text(pwds, SWT.BORDER | SWT.PASSWORD);
		Label ratesLabel = new Label(comp, SWT.NONE);
		ratesLabel.setText("Currency Rates");
		Composite rates = new Composite(comp, SWT.NONE);
		GridData ratesGridData = new GridData();
		ratesGridData.horizontalAlignment = GridData.FILL;
		ratesGridData.grabExcessHorizontalSpace = true;
		ratesGridData.verticalAlignment = GridData.CENTER;
		GridLayout ratesLayout = new GridLayout();
		ratesLayout.numColumns = 6;
		ratesLayout.verticalSpacing = 0;
		ratesLayout.marginHeight = 0;
		rates.setLayout(ratesLayout);
		rates.setLayoutData(ratesGridData);
		Label usdLabel = new Label(rates, SWT.NONE);
		usdLabel.setText("USD");
		usdRate = new Text(rates, SWT.BORDER);
		Label gbpLabel = new Label(rates, SWT.NONE);
		gbpLabel.setText("GBP");
		gbpRate = new Text(rates, SWT.BORDER);
		Label eurLabel = new Label(rates, SWT.NONE);
		eurLabel.setText("EUR");
		eurRate = new Text(rates, SWT.BORDER);
		Label outputFileLabel = new Label(comp, SWT.NONE);
		outputFileLabel.setText("Output file name");
		outputFileText = new Text(comp, SWT.BORDER);
		outputFileText.setLayoutData(inputsGridData);
		Label sandboxLabel = new Label(comp, SWT.NONE);
		sandboxLabel.setText("Sandbox (test environment)");
		sandBoxBtn = new Button(comp, SWT.CHECK);
		sandBoxBtn.setSelection(DEFAULT_SANDBOX);
		sandBoxBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					loadConfigData();
					populateDataFields();
				}
				catch (Exception ex) {
					textArea.setText(ex.getLocalizedMessage());
				}
			}
		});	

		Composite buttons = new Composite(parent, SWT.NONE);
		GridData buttonsGridData = new GridData();
		buttonsGridData.horizontalAlignment = GridData.END;
		buttonsGridData.grabExcessHorizontalSpace = true;
		buttonsGridData.verticalAlignment = GridData.CENTER;
//		buttonsGridData.horizontalSpan = 2;
		GridLayout buttonsLayout = new GridLayout();
		buttonsLayout.numColumns = 2;
		buttonsLayout.verticalSpacing = 0;
		buttonsLayout.marginHeight = 0;
		buttons.setLayout(buttonsLayout);
		buttons.setLayoutData(buttonsGridData);
		
		encryptAndWriteBtn = new Button(buttons, SWT.FLAT);
		encryptAndWriteBtn.setLayoutData(buttonLayoutData);
		encryptAndWriteBtn.setText("Encrypt and Write Xml");
		shell.setDefaultButton(encryptAndWriteBtn);
		encryptAndWriteBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					shell.setCursor(waitCursor);
					
					float usd = Float.parseFloat(usdRate.getText());
					float gbp = Float.parseFloat(gbpRate.getText());
					float eur = Float.parseFloat(eurRate.getText());
					currentConfig.getBusiness().setRates(usd, gbp, eur);
					ButtonEncryption encryption = new ButtonEncryption(currentConfig, pwdText.getText(), usd, gbp, eur);
					ArrayList<String> results = encryption.encryptButtonItems();
					for (Iterator it = results.iterator(); it.hasNext(); ) {
						textArea.append("\n" + ((String) it.next()));
					}

					textArea.append("\nEncryption complete");
					
					XmlButtonWriter.getInstance().writeConfig(currentConfig);
					textArea.append("\nXML file written");
					ftpBtn.setEnabled(true);
					shell.setDefaultButton(ftpBtn);
				}
				catch (Exception ex) {
					textArea.append("\nError encountered: " + ex.getClass() + "\n" + ex.getMessage());
				}
				finally {
					shell.setCursor(defaultCursor);
				}
				
			}
		});
		
		ftpBtn = new Button(buttons, SWT.FLAT);
		ftpBtn.setLayoutData(buttonLayoutData);
		ftpBtn.setText("Upload XML File");
		ftpBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					shell.setCursor(waitCursor);
					
					ArrayList<String> results = currentConfig.getFtpUploader().uploadFile(ftpPwdText.getText(), currentConfig.getOutputFileName());
					for (Iterator it = results.iterator(); it.hasNext(); ) {
						textArea.append("\n" + ((String) it.next()));
					}
				}
				catch (Exception ex) {
					textArea.append("\nError encountered: " + ex.getClass() + "\n" + ex.getMessage());
				}
				finally {
					shell.setCursor(defaultCursor);
				}
				
			}
		});
		textArea = new Text(group, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		textArea.setLayoutData(textGridData);
		
	}

	private void populateDataFields() {
		try {
			getDefaultRates();
			
			this.outputFileText.setText(currentConfig.getOutputFileName());

			KeyDetailsLoader keyFilesLoader = currentConfig.getKeyDetailsLoader(); 
			certText.setText(keyFilesLoader.getPublicCertificateFileName());
			privKeyText.setText(keyFilesLoader.getPrivateKeyP12FileName());
			ppKeyText.setText(keyFilesLoader.getPaypalCertificateFileName());
			
			FtpUploader ftpUploader = currentConfig.getFtpUploader();
			String ftpDisplay = ftpUploader.getUserName()+"@"+ftpUploader.getServer()+" directory=/"+ftpUploader.getRemoteDir();
			ftpSiteText.setText(ftpDisplay);
			ftpBtn.setEnabled(FtpUploader.isValidFile(currentConfig.getOutputFileName()));
			
			StringBuffer buffer = new StringBuffer();
			buffer.append("Loaded files ------------------------------------------------------------------------------------------------------------");
			buffer.append("\nYour public certificate : ");
			buffer.append(keyFilesLoader.getPublicCertificateFileName());
			buffer.append("\nYour private key : ");
			buffer.append(keyFilesLoader.getPrivateKeyP12FileName());
			buffer.append("\nPayPal public key : ");
			buffer.append(keyFilesLoader.getPaypalCertificateFileName());
			buffer.append("\nFtp settings : ");
			buffer.append(ftpDisplay);
			buffer.append("\nOutput file : ");
			buffer.append(currentConfig.getOutputFileName());
			buffer.append("\nBusiness details ------------------------------------------------------------------------------------------------------------");
			Business business = currentConfig.getBusiness();
			buffer.append("\nPayPal business name : ");
			buffer.append(business.getPaypalBusinessName());
			buffer.append("\nSandbox : ");
			buffer.append(business.isSandbox());
			buffer.append("\nPayPal certificate Id : ");
			buffer.append(business.getPaypalCertificateId());
			buffer.append("\nBase currency : ");
			buffer.append(business.getBaseCurrency());
			buffer.append("\nPayPal commission rates : ");
			HashMap<String, PaypalCurrency> currencies = business.getCommissionCurrencies();
			for (Iterator it = currencies.keySet().iterator(); it.hasNext(); ) {
				String currencyCode = (String) it.next();
				PaypalCurrency paypalCurrency = currencies.get(currencyCode);
				buffer.append(currencyCode);
				buffer.append(" : ");
				buffer.append(paypalCurrency.getCommissionRate());
				buffer.append("%  flat fee : ");
				buffer.append(paypalCurrency.getFlatFee());
				buffer.append("\t\t");
			}
			buffer.append("\nExchange rates from the internet : ");
			for (Iterator it = resultsFromReadingRates.iterator(); it.hasNext(); ) {
				buffer.append("\n\t\t");
				buffer.append((String) it.next());
			}
			buffer.append("\nSale Items ------------------------------------------------------------------------------------------------------------");
			for (Iterator it = business.getItems().iterator(); it.hasNext(); ) {
				model.Item item = (model.Item) it.next();
				buffer.append("\nItem name : ");
				buffer.append(item.getName());
				buffer.append("\nPrice : ");
				buffer.append((item.getBasePrice() == null ? "NONE SET" : item.getBasePrice()));
				buffer.append("\nCurrency : ");
				buffer.append(item.getItemBaseCurrency());
				buffer.append("\nShipping : ");
				buffer.append((item.getShipping() == null ? "NONE" : item.getShipping()));
				buffer.append("\nDonation : ");
				buffer.append(item.isDonation());
				buffer.append("\n------------------------------------------------------------------------------------------------------------");
			}
			
			textArea.setText(buffer.toString());
			encryptAndWriteBtn.setEnabled(true);
		}
		catch (Exception e) {
			outputFileText.setText(NONE);
			certText.setText(NONE);
			privKeyText.setText(NONE);
			ppKeyText.setText(NONE);
			pwdText.setText(NONE);
			encryptAndWriteBtn.setEnabled(false);
			textArea.setText(e.getLocalizedMessage());
		}	
	}

	private void getDefaultRates() {
		this.usdRate.setText(Float.toString(currentConfig.getBusiness().getCurrency("USD").getCurrentExchangeRate()));
		this.gbpRate.setText(Float.toString(currentConfig.getBusiness().getCurrency("GBP").getCurrentExchangeRate()));
		this.eurRate.setText(Float.toString(currentConfig.getBusiness().getCurrency("EUR").getCurrentExchangeRate()));
	}
	
	/**
	 * 
	 * @return a string of the loadedConfig to show in the text field
	 * @throws Exception
	 */
	private void loadConfigData() throws Exception {
		
		if (this.sandBoxBtn.getSelection()) {
			if (this.sandboxConfig == null) 
				this.sandboxConfig = xmlDefinition.loadDefinitions(true);
			
			currentConfig = sandboxConfig;
		}
		else {
			if (this.liveConfig == null) 
				this.liveConfig = xmlDefinition.loadDefinitions(false);
			
			currentConfig = liveConfig;
		}
		

		try {
			XmlRates xmlRates = XmlRates.getInstance();
			resultsFromReadingRates = xmlRates.readRates(currentConfig);
			gotExchangeRates = true;
		} catch (Exception e1) {
			System.out.print("Unable read rates : "); System.out.println(e1);
			e1.printStackTrace();
		}
	}
}
