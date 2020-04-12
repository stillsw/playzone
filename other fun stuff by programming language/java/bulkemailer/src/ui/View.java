package ui;

import model.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import email.EmailFile;
import email.MimeDecoder;
import email.WebUtil;

//import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.mail.internet.MimeMessage;
import javax.swing.tree.DefaultMutableTreeNode;

public class View
{
	private static String ALL = "";
	private static String NO_CATEGORY = "Not Placed";

	private ModelLoader modelLoader = ModelLoader.getInstance();
	// swt 
	private Shell shell = null;
	private HashMap<String, TreeItem> mailingCategories = new HashMap<String, TreeItem>();

	private ToolItem moniChk;
	private ToolItem tomasChk;
	private ToolItem exclusivelyChk;

	private TreeItem treeTopItem;
	
	private Text subjectText;
	private Text bodyText;
	
	private EmailFile email;
	
	private View() throws Exception {
	    // load database first
	    modelLoader.loadModel();		
	}

	/**
	 * recursively make an item then find its children and make items for them too
	 * @param parentItem
	 * @param parentNode
	 */
	private void createChildCategories(TreeItem parentItem, DefaultMutableTreeNode parentNode) {
		if (!parentNode.isLeaf()) { // ie. has some children
			for (int i = 0; i < parentNode.getChildCount(); i++) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) parentNode.getChildAt(i);
				String childName = node.toString();
				TreeItem childItem = new TreeItem(parentItem, SWT.NONE);
				childItem.setText(new String[] { childName, "", "" });
				// add to list for lookups from recipients
				this.mailingCategories.put(childName, childItem);
				// recurse downwards
				this.createChildCategories(childItem, node);
				childItem.setExpanded(true);
			}
		}
	}
	
	private void createNodes(Tree tree) {
		
		StringBuffer errors = new StringBuffer();
		StringBuffer warnings = new StringBuffer();
		
		// make categories first (using DefaultMutableTreeNode for convenience, it has nothing
		// to do with the ui)
		DefaultMutableTreeNode categoryTree = this.createCategoryNodes();
		treeTopItem = new TreeItem(tree, SWT.NONE);
		treeTopItem.setText(new String[] { ALL, "", "" });
		// when read recipients will need to be able to find the category for each, so add treeitem to map
		this.mailingCategories.put(ALL, treeTopItem);
		
		// now can walk the category tree to make the tree items for the ui
		this.createChildCategories(treeTopItem, categoryTree);
		treeTopItem.setExpanded(true);
	    
		// load up the recipients to the mailist list too
		Iterator<Recipient> it = modelLoader.getRecipients().iterator();
		while (it.hasNext()) {
			Recipient recipient = it.next();
//			recipient.setName(getUniqueRecipientName(recipient.getName()));
			
			// add to category if found otherwise to no category
			String recipientCategoryName = recipient.getCategory();
			TreeItem category;
			if (recipientCategoryName == null || recipientCategoryName.length() == 0)
				category = mailingCategories.get(NO_CATEGORY);
			else {
				category = mailingCategories.get(recipientCategoryName);
				if (category == null) {
					// some error where category isn't legal
					warnings.append("Non existent category for "+recipient.getName()+" category="+recipientCategoryName+"\n");
					category = mailingCategories.get(NO_CATEGORY);
				}
			}
			
			TreeItem recipientItem = new TreeItem(category, SWT.NONE);
			String contact = "Both";
			if (recipient.isOnlyMoniContact())
				contact = "Moni";
			if (recipient.isOnlyTomasContact())
				contact = "Tomas";
			recipientItem.setText(new String[] { recipient.getName(), contact, recipient.getEmail() });
			
			// put the recipient into the item
			recipientItem.setData(recipient);

			if (recipient.emailAddressIsError())
				errors.append("   "+recipient.getName()+" email="+recipient.getEmail()+"\n");
			
    	}
		if (errors.length() != 0 || warnings.length() != 0) {
			if (warnings.length() != 0) {
				warnings.insert(0, "Warnings :\n");
			}
			if (errors.length() != 0) {
				errors.insert(0, "Errors:\nThe following email addresses are invalid and will be ignored\n");
				if (warnings.length() != 0)
					errors.append("\n");	
			}
			errors.append(warnings.toString());
			
			MessageDialog.showMessageDialog(shell, "Data issues", errors.toString());
		}
	}
		
	/**
	 * use DefaultMutableTreeNode to build tree, cos don't have to have parent first this way
	 */
	private DefaultMutableTreeNode createCategoryNodes() {
		
		HashMap<String, DefaultMutableTreeNode> categoryNodes = new HashMap<String, DefaultMutableTreeNode>();

		DefaultMutableTreeNode top = new DefaultMutableTreeNode(ALL);
	    
		String CATEGORIES = "Categories";
		// add top node to the main list for future comparisons
		categoryNodes.put(CATEGORIES, top);
	
		Iterator<Category> catsIt = modelLoader.getCategories().iterator();
		while (catsIt.hasNext()) {
			Category category = catsIt.next();
			String name = category.getName();
			// find this node (could already be added through another's parent category)
			// if not found create it fresh
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) categoryNodes.get(name);
			if (node == null) 
				node = new DefaultMutableTreeNode(name);
			// if no parent category add to top of tree
			String parentCategory = category.getParentName();
			if (parentCategory == null || parentCategory.length() == 0)
				top.add(node);
			else {
				// if there is a parent find it in the previously read list and add this one as a child
				DefaultMutableTreeNode parentNode = categoryNodes.get(parentCategory);
				if (parentNode != null)
					parentNode.add(node);
				else {
					// if the parent isn't in the list yet, add it with the new child too
					parentNode = new DefaultMutableTreeNode(parentCategory);
					parentNode.add(node);
					categoryNodes.put(parentCategory, parentNode);
				}
			}
				
			// add this node to the main recipients list for future comparisons
			categoryNodes.put(name, node);
		}
		DefaultMutableTreeNode noCatNode = new DefaultMutableTreeNode(NO_CATEGORY);
		top.add(noCatNode);
		categoryNodes.put(NO_CATEGORY, noCatNode);
	
	    return top;
	}
	
	
  public static void main(String[] args){
	  
		Display display = Display.getDefault();
		try {
			View view = new View();
			view.createShell();
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
	private void createShell() {
		shell = new Shell();
		shell.setText("Mailing List Delivery");
		shell.setSize(new Point(800, 600));
		shell.setLayout(new FillLayout());
//			this.waitCursor = shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT);
//			this.defaultCursor = shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW);
		try {
			createLayout();
		} catch (Exception e) {
			String error = "Error: could not create layout because :" + e.getMessage();
			e.printStackTrace();
			System.out.println(error);
//				if (this.textArea != null)
//					this.textArea.setText(error);
		}
		
//			populateDataFields();
	}

	private Composite makeEmailSettings(Composite parent) {
		final Composite comp = new Composite(parent, SWT.NONE);
		GridData compGridData = new GridData();
		compGridData.horizontalAlignment = GridData.FILL;
		compGridData.grabExcessHorizontalSpace = true;
		compGridData.verticalAlignment = GridData.FILL;
		comp.setLayoutData(compGridData);
		GridLayout compGridLayout = new GridLayout();
		compGridLayout.numColumns = 2;
		compGridLayout.verticalSpacing = 5;
		comp.setLayout(compGridLayout);

		// make and set all the email settings data
		final EmailSettings emailSettings = this.modelLoader.getEmailSettings();
		Label fromLabel = new Label(comp, SWT.NONE);
		fromLabel.setText("From");
		final Text fromText = new Text(comp, SWT.BORDER);
		fromText.setLayoutData(compGridData);
		fromText.setText(emailSettings.getFromEmail());
		//
		Label toLabel = new Label(comp, SWT.NONE);
		toLabel.setText("Send To");
		final Text toText = new Text(comp, SWT.BORDER);
		toText.setLayoutData(compGridData);
		toText.setText(emailSettings.getSendTo());
		//
		Label smtpLabel = new Label(comp, SWT.NONE);
		smtpLabel.setText("Server");
		final Text smtpText = new Text(comp, SWT.BORDER);
		smtpText.setLayoutData(compGridData);
		smtpText.setText(emailSettings.getSmtpServer());
		//
		Label replyLabel = new Label(comp, SWT.NONE);
		replyLabel.setText("Reply To");
		final Text replyText = new Text(comp, SWT.BORDER);
		replyText.setLayoutData(compGridData);
		replyText.setText(emailSettings.getReplyTo());
		//
		Label emailDelayLabel = new Label(comp, SWT.NONE);
		emailDelayLabel.setText("Delay");
		final Text emailDelayText = new Text(comp, SWT.BORDER);
		emailDelayText.setLayoutData(compGridData);
		emailDelayText.setText(""+emailSettings.getDelayBetweenEmails());
		//
		Label maxBatchLabel = new Label(comp, SWT.NONE);
		maxBatchLabel.setText("Max Batch");
		final Text maxBatchText = new Text(comp, SWT.BORDER);
		maxBatchText.setLayoutData(compGridData);
		maxBatchText.setText(""+emailSettings.getMaxInBatch());
		// Authentication fields
		Label authenticateLabel = new Label(comp, SWT.NONE);
		authenticateLabel.setText("Use Authentication");
		final Button authChk = new Button(comp, SWT.CHECK);
		authChk.setSelection(emailSettings.isAuthenticate());
		//
		Label usernameLabel = new Label(comp, SWT.NONE);
		usernameLabel.setText("Username");
		final Text usernameText = new Text(comp, SWT.BORDER);
		usernameText.setLayoutData(compGridData);
		usernameText.setText(emailSettings.getUsername());
		//
		Label passwordLabel = new Label(comp, SWT.NONE);
		passwordLabel.setText("Password");
		final Text passwordText = new Text(comp, SWT.BORDER | SWT.PASSWORD);
		passwordText.setLayoutData(compGridData);
		//
		usernameText.setEnabled(authChk.getSelection());
		passwordText.setEnabled(authChk.getSelection());
		//
		authChk.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					usernameText.setEnabled(authChk.getSelection());
					passwordText.setEnabled(authChk.getSelection());
				}
				catch (Exception ex) {}
			}
		});	
		
		// set debugging on smtp
		Label debugLabel = new Label(comp, SWT.NONE);
		debugLabel.setText("Debug SMTP");
		final Button debugChk = new Button(comp, SWT.CHECK);

		final Button sendBtn = new Button(comp, SWT.NONE);
		sendBtn.setText("Send Now");
		GridData buttonGridData = new GridData();
		buttonGridData.horizontalAlignment = GridData.CENTER;
		buttonGridData.horizontalSpan = 2;
		sendBtn.setLayoutData(buttonGridData);

		// progress indication is invisible at first, switched on by send
	    final Label progressLabel = new Label(comp, SWT.NONE);
	    progressLabel.setText("Progress");
	    progressLabel.setVisible(false);
	    GridData progressData = new GridData(GridData.FILL_HORIZONTAL);
	    progressData.horizontalSpan = 2;
	    progressLabel.setLayoutData(progressData);
		final ProgressBar progressBar = new ProgressBar(comp, SWT.SMOOTH);
		progressBar.setLayoutData(progressData);
		progressBar.setVisible(false);
	    final Text resultsLabel = new Text(comp, SWT.V_SCROLL | SWT.MULTI | SWT.WRAP | SWT.READ_ONLY);
	    GridData resultsData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
	    resultsData.horizontalSpan = 2;
	    resultsData.grabExcessVerticalSpace = true;
	    resultsLabel.setLayoutData(resultsData);
		
		sendBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				// validate the items before sending 
				try {
					sendBtn.setEnabled(false);
					// first that we have recipients to send to
					ArrayList<Recipient> recipients = new ArrayList<Recipient>();
					grabRecipients(treeTopItem, recipients);
					if (recipients.size() == 0)
						throw new Exception("No recipients selected");
					
					// then that we have email content
					if (email == null)
						throw new Exception("Load an email file before sending");
				
					// then that the email settings are valid				
					emailSettings.setFromEmail(fromText.getText());
					emailSettings.setSendTo(toText.getText());
					emailSettings.setSmtpServer(smtpText.getText());
					emailSettings.setReplyTo(replyText.getText());
					emailSettings.setDelayBetweenBatches(emailDelayText.getText());
					emailSettings.setMaxInBatch(maxBatchText.getText());
					boolean authenticate = authChk.getSelection();
					emailSettings.setAuthenticate(authenticate);
					if (authenticate) {
						emailSettings.setUsername(usernameText.getText());
						emailSettings.setPassword(passwordText.getText());
					}
					emailSettings.setDebug(debugChk.getSelection());
					
					// got this far, should all be valid and good to go
					EmailSender sender = new EmailSender(shell, progressBar, sendBtn, recipients, email);
					if (sender.setUpSendAndConfirm()) {
					    progressLabel.setVisible(true);
						progressBar.setVisible(true);						
						sender.sendEmail(resultsLabel);
					}
				}
				catch (Exception ex) {
					MessageDialog.showMessageDialog(shell, "Error", ex.getMessage());
				}
				finally {
//					sendBtn.setEnabled(true);
				}
			}
		});	
		
		return comp;
	}

	private void grabRecipients(TreeItem treeItem, ArrayList<Recipient> recipients)
		throws Exception {

		// look for recipients that are selected and not grayed
	    TreeItem[] items = treeItem.getItems();
	    for (int i = 0; i < items.length; i++) {
	    	Object data = items[i].getData();
	    	if (data != null && data instanceof Recipient && items[i].getChecked() && !items[i].getGrayed() && !((Recipient)data).emailAddressIsError()) {
    			recipients.add((Recipient) data);
	    	}
	    	else
	    		grabRecipients(items[i], recipients);
    	}
	}	
	
	private void loadFile(Shell parent, Text subjectText, Text bodyText) {
		
		FileDialog fileDialog = new FileDialog(parent.getShell(), SWT.OPEN);
		fileDialog.setText("Open");
		//          fileDialog.setFilterPath("C:/");
		String[] filterExt = { "*.eml" };
		fileDialog.setFilterExtensions(filterExt);
		try {
		      String fileName = fileDialog.open();
		      if (fileName != null) {
		    	  // code here to open the file and display
		    	  File file = new File(fileName);
		    	  if (!(file.exists() && file.isFile() && file.canRead()))
		    		  throw new FileNotFoundException();

		    	  // new way, open the eml file
		    	  email = new EmailFile(file);
		    	  subjectText.setText(email.getSubject());
		    	  bodyText.setText(email.summariseContents());
		      }
		} 
		catch (FileNotFoundException e) {
			MessageBox messageBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
			messageBox.setMessage("Could not open file");
			messageBox.setText("Error");
			messageBox.open();
		}
		catch (IOException e) {
			MessageBox messageBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
			messageBox.setMessage("Problem reading from file: "+e.getMessage());
			messageBox.setText("Error");
			messageBox.open();
		} 
		catch (Exception e) {
			MessageBox messageBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
			messageBox.setMessage("Some other Problem reading from file: "+e.getMessage());
			messageBox.setText("Error");
			messageBox.open();
		}
		
	}
		
	private Composite makeEmailContentPane(final Composite parent) {

		Composite comp = new Composite(parent, SWT.BORDER);
		GridData compGridData = new GridData();
		compGridData.horizontalAlignment = GridData.FILL;
		compGridData.grabExcessHorizontalSpace = true;
		compGridData.verticalAlignment = GridData.FILL;
		comp.setLayoutData(compGridData);
		GridLayout compGridLayout = new GridLayout();
		compGridLayout.numColumns = 1;
		compGridLayout.verticalSpacing = 5;
		comp.setLayout(compGridLayout);

		
		Composite buttonsComp = new Composite(comp, SWT.NONE);
		GridData btnsGridData = new GridData();
		btnsGridData.grabExcessHorizontalSpace = true;
		btnsGridData.horizontalAlignment = GridData.FILL;
		buttonsComp.setLayoutData(btnsGridData);
		GridLayout buttonsGridLayout = new GridLayout();
		buttonsGridLayout.numColumns = 2;
		buttonsGridLayout.verticalSpacing = 5;
		buttonsComp.setLayout(buttonsGridLayout);
		final Button loadBtn = new Button(buttonsComp, SWT.NONE);
		loadBtn.setText("Load File");
		
		Composite subjComp = new Composite(comp, SWT.NONE);
		RowLayout rowLayout = new RowLayout();
		subjComp.setLayout(rowLayout);
		
		Label subjLabel = new Label(subjComp, SWT.NONE);
		subjLabel.setText("Subject");
		subjectText = new Text(subjComp, SWT.BORDER);
		subjectText.setLayoutData(new RowData(450, SWT.DEFAULT));
		subjectText.setEditable(false);

		// 2nd group
		Composite comp2 = new Composite(comp, SWT.NONE);
		GridData comp2GridData = new GridData();
		comp2GridData.horizontalAlignment = GridData.FILL;
		comp2GridData.grabExcessHorizontalSpace = true;
		comp2GridData.verticalAlignment = GridData.FILL;
		comp2GridData.grabExcessVerticalSpace = true;
		comp2.setLayoutData(comp2GridData);
		GridLayout comp2GridLayout = new GridLayout();
		comp2GridLayout.numColumns = 1;
		comp2GridLayout.verticalSpacing = 5;
		comp2.setLayout(comp2GridLayout);
		
		bodyText = new Text (comp2, SWT.BORDER
				| SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.WRAP 		
		);
	    bodyText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	    bodyText.setEditable(false);

	    // populate the items from file if load from file is pressed
		loadBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					loadFile(parent.getShell(), subjectText, bodyText);
				}
				catch (Exception ex) {}
			}
		});
	    
	    
	    
		return comp;
	}
	
	private void createEmailShellLayout(Shell shell) {
		
		// tabs
		final TabFolder tabFolder = new TabFolder (shell, SWT.BORDER);
		// first tab: email content
		TabItem tabItem1 = new TabItem (tabFolder, SWT.NONE);
		tabItem1.setText ("Email Content");
		tabItem1.setControl (this.makeEmailContentPane(tabFolder));
		// second tab: email settings
		TabItem tabItem2 = new TabItem (tabFolder, SWT.NONE);
		tabItem2.setText ("Send the Email");
		tabItem2.setControl (this.makeEmailSettings(tabFolder));
		
		tabFolder.pack ();
	}
	
	/**
	 * called when the toolbar check items are used, this resets the tree chk items accordingly
	 * eg. moni selected, if moni not in list, that item is disabled
	 * eg. moni and exclusively, if not only moni, that item is disabled
	 */
	private void setTreeChks() {
		boolean moniChecked = moniChk.getSelection();
		boolean tomasChecked = tomasChk.getSelection();
		boolean exclusivelyChecked = exclusivelyChk.getSelection();
		setRecipients(this.treeTopItem, moniChecked, tomasChecked, exclusivelyChecked);
	}
	private void setRecipients(TreeItem treeItem, boolean moniChecked, boolean tomasChecked, boolean exclusivelyChecked) {

	    TreeItem[] items = treeItem.getItems();
	    for (int i = 0; i < items.length; i++) {
	    	Object data = items[i].getData();
	    	if (data != null && data instanceof Recipient) {
	    		boolean enable = this.isRecipientSelectable((Recipient) data, moniChecked, tomasChecked, exclusivelyChecked);
	    		items[i].setGrayed(!enable);
	    		if (enable && treeItem.getChecked()) // is parent checked?
	    			items[i].setChecked(true);
	    	}
	    	else
	    		setRecipients(items[i], moniChecked, tomasChecked, exclusivelyChecked);
	    }
	}
	private boolean isRecipientSelectable(Recipient recipient, boolean moniChecked, boolean tomasChecked, boolean exclusivelyChecked) {
		return (((recipient.isOnlyMoniContact() && moniChecked && exclusivelyChecked)
				|| (recipient.isOnlyTomasContact() && tomasChecked && exclusivelyChecked)
				|| (recipient.isMoniContact() && moniChecked && !exclusivelyChecked)
				|| (recipient.isTomasContact() && tomasChecked && !exclusivelyChecked))
					&& !recipient.emailAddressIsError()
						);
	}
	
	private void makeToolbar(Composite parent) {
		ToolBar bar = new ToolBar (parent, SWT.NONE);
		moniChk = new ToolItem (bar, SWT.BORDER | SWT.CHECK);
		moniChk.setText ("Moni");
		moniChk.setSelection(true);
		tomasChk = new ToolItem (bar, SWT.BORDER | SWT.CHECK);
		tomasChk.setText ("Tomas");
		exclusivelyChk = new ToolItem (bar, SWT.BORDER | SWT.CHECK);
		exclusivelyChk.setText ("Exclusively");

		SelectionAdapter processChk = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					if (e.getSource() == moniChk || e.getSource() == tomasChk) {
						if (moniChk.getSelection() && tomasChk.getSelection()) {
							exclusivelyChk.setEnabled(false);
							exclusivelyChk.setSelection(false);
						}
						else
							exclusivelyChk.setEnabled(true);
					}
					setTreeChks();
				}
				catch (Exception ex) {}
			}
		};
		
		moniChk.addSelectionListener(processChk);	
		tomasChk.addSelectionListener(processChk);	
		exclusivelyChk.addSelectionListener(processChk);	
		
		final ToolItem sendEmailBtn = new ToolItem (bar, SWT.BORDER | SWT.PUSH);
		sendEmailBtn.setText("Send An Email");
		sendEmailBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					Shell dialog = new Shell (shell);
					dialog.setText ("Send an Email");
					dialog.setSize (750, 650);
					dialog.setLocation(100, 50);
					dialog.setLayout(new FillLayout());
					createEmailShellLayout(dialog);
					dialog.open ();
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		
		bar.pack ();

	}
	
	private void createLayout() {		
		
		Composite parentComp = new Composite(shell, SWT.NONE);
		GridData compGridData = new GridData();
		compGridData.horizontalAlignment = GridData.FILL;
		compGridData.grabExcessHorizontalSpace = true;
		compGridData.verticalAlignment = GridData.FILL;
		parentComp.setLayoutData(compGridData);
		GridLayout compGridLayout = new GridLayout();
		compGridLayout.numColumns = 1;
		compGridLayout.verticalSpacing = 1;
		parentComp.setLayout(compGridLayout);
		
		makeToolbar(parentComp);
		
		Composite treeComp = this.makeTree(parentComp);
		treeComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		setTreeChks();
		
	}

	// two methods to handle checkbox behaviour for the tree
	private void checkPath(TreeItem item, boolean checked, boolean grayed) {
	    if (item == null) return;
	    if (grayed) {
	        checked = true;
	    } else {
	        int index = 0;
	        TreeItem[] items = item.getItems();
	        while (index < items.length) {
	            TreeItem child = items[index];
	            if (child.getGrayed() || checked != child.getChecked()) {
	                checked = grayed = true;
	                break;
	            }
	            index++;
	        }
	    }
	    item.setChecked(checked);
	    item.setGrayed(grayed);
	    checkPath(item.getParentItem(), checked, grayed);
	}
	private void checkItems(TreeItem item, boolean checked) {
		
		// depends on toolbar selections
    	Object data = item.getData();
    	if (data != null && data instanceof Recipient) {
    		boolean enable = this.isRecipientSelectable((Recipient) data, this.moniChk.getSelection(), this.tomasChk.getSelection(), this.exclusivelyChk.getSelection());
    		item.setGrayed(!enable);
    		item.setChecked(checked); // checked is according to parent, grayed is according to toolbar
    	}
    	else {
		    item.setGrayed(false);
		    item.setChecked(checked);
		    TreeItem[] items = item.getItems();
		    for (int i = 0; i < items.length; i++) {
		        checkItems(items[i], checked);
		    }
    	}
	}

	private static boolean itemIsExpanded(boolean expandedEvent, TreeItem itemGeneratingEvent, TreeItem item) {
	    // expanded and the item is the generator reports the opposite way
	    boolean itemIsExpanded = item.getExpanded();
	    if (item == itemGeneratingEvent)
	    	itemIsExpanded = !itemIsExpanded;
	    
	    return itemIsExpanded;
	}
	
	/**
	 * used to determine the preferred size of the tree when expanded/collapsed, cos computesize
	 * isn't working
	 * seems to be to do with the fact that the listener reports on the item being expanded wrongly
	 * if expanded, recurse down and keep counting children
	 * @param item
	 */
	private static int countSpaceOccupiers(boolean expandedEvent, TreeItem itemGeneratingEvent, TreeItem item) {
	    int childrenSize = 0;
	    
	    if (itemIsExpanded(expandedEvent, itemGeneratingEvent, item)) {
		    TreeItem[] items = item.getItems();
		    for (int i = 0; i < items.length; i++) {
	        	childrenSize++;
		        childrenSize += countSpaceOccupiers(expandedEvent, itemGeneratingEvent, items[i]);
		    }
	    }
	    return childrenSize;
	}
	
	private Composite makeTree(Composite parent) {
		final ScrolledComposite sc = new ScrolledComposite (parent, SWT.VERTICAL);
		sc.setBounds (10, 10, 775, 600);
		final int clientWidth = sc.getClientArea ().width;
		// tree implements checkbox behaviour
		final Tree tree = new Tree (sc, SWT.NONE | SWT.NO_SCROLL | SWT.CHECK);
	    tree.addListener(SWT.Selection, new Listener() {
	        public void handleEvent(Event event) {
	            if (event.detail == SWT.CHECK) {
	                TreeItem item = (TreeItem) event.item;
	                boolean checked = item.getChecked();
	                checkItems(item, checked);
	                checkPath(item.getParentItem(), checked, false);
	            }
	        }
	    });
		tree.setHeaderVisible(true);
		TreeColumn column1 = new TreeColumn(tree, SWT.LEFT);
		column1.setText("Name");
		column1.setWidth(400);
		TreeColumn column2 = new TreeColumn(tree, SWT.LEFT);
		column2.setText("Who");
		column2.setWidth(60);
		TreeColumn column3 = new TreeColumn(tree, SWT.LEFT);
		column3.setText("Email");
		column3.setWidth(140);
		// populate the tree from db
		if (this.modelLoader.isModelLoaded())
			this.createNodes(tree);
		else {
			MessageDialog.showMessageDialog(shell, "Error", this.modelLoader.getLoadError());
		}
		// let's try to determine the height...
		sc.setContent (tree);
		//int prefHeight = tree.getItemHeight() * (this.modelLoader.getCategories().size() + this.modelLoader.getRecipients().size() + 3); 
		int prefHeight = tree.computeSize (SWT.DEFAULT, SWT.DEFAULT).y;
		tree.setSize (clientWidth, prefHeight);
		/*
		 * The following listener ensures that the Tree is always large
		 * enough to not need to show its own vertical scrollbar.
		 */
		tree.addTreeListener (new TreeListener () {
			public void treeExpanded (TreeEvent e) {
				// let's try to determine the number of nodes open and closed
				int numberOfSpaceOccupiers = 1 + countSpaceOccupiers(true, (TreeItem)e.item, treeTopItem);
				//int prefHeight = tree.computeSize (SWT.DEFAULT, SWT.DEFAULT).y;
				int prefHeight = tree.getItemHeight() * (numberOfSpaceOccupiers + 5); // the 5 is a hack to make extra space for header/borders
				tree.setSize (clientWidth, prefHeight);				
			}
			public void treeCollapsed (TreeEvent e) {
				// let's try to determine the number of nodes open and closed
				int numberOfSpaceOccupiers = 1 + countSpaceOccupiers(false, (TreeItem)e.item, treeTopItem);
				//int prefHeight = tree.computeSize (SWT.DEFAULT, SWT.DEFAULT).y;
				int prefHeight = tree.getItemHeight() * (numberOfSpaceOccupiers + 5); // the 5 is a hack to make extra space for header/borders
				tree.setSize (clientWidth, prefHeight);
			}
		});
		/*
		 * The following listener ensures that a newly-selected item
		 * in the Tree is always visible.
		 */
		tree.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				TreeItem [] selectedItems = tree.getSelection();
				if (selectedItems.length > 0) {
					Rectangle itemRect = selectedItems[0].getBounds();
					Rectangle area = sc.getClientArea();
					Point origin = sc.getOrigin();
					if (itemRect.x < origin.x || itemRect.y < origin.y
							|| itemRect.x + itemRect.width > origin.x + area.width
							|| itemRect.y + itemRect.height > origin.y + area.height) {
						sc.setOrigin(itemRect.x, itemRect.y);
					}
				}
			}
		});
		/*
		 * The following listener scrolls the Tree one item at a time
		 * in response to MouseWheel events.
		 */
		tree.addListener(SWT.MouseWheel, new Listener() {
			public void handleEvent(Event event) {
				Point origin = sc.getOrigin();
				if (event.count < 0) {
					origin.y = Math.min(origin.y + tree.getItemHeight(), tree.getSize().y);
				} else {
					origin.y = Math.max(origin.y - tree.getItemHeight(), 0);
				}
				sc.setOrigin(origin);
			}
		});

		
		return sc;
	}
}