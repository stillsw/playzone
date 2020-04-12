package io;

import model.*;

import java.io.*;
import java.io.File;
import java.util.*;

import jxl.*;
import jxl.read.biff.*;

public class DbLoader {
	private static DbLoader instance = new DbLoader();
	
	private DbLoader() {}
	
	public static DbLoader getInstance() {
		return instance;
	}
	
	public void loadDb(ModelLoader modelLoader) throws Exception
	{
		try {
			// load db
			String fileSeparator = System.getProperty("file.separator");
			String runDir = System.getProperty("user.home");
			String dataDirectory = runDir + fileSeparator + "bulkEmailer";
			String filename = dataDirectory + fileSeparator + "mailing_list.xls";
			WorkbookSettings ws = new WorkbookSettings();
			ws.setLocale(new Locale("en", "EN"));
	
			File xlsFile = new File(filename);
			if (xlsFile.exists()) {
				Workbook workbook = Workbook.getWorkbook(xlsFile,ws);
				
				Sheet recipientsSheet = workbook.getSheet(0);
				modelLoader.loadRecipients(readRecipientsSheet(recipientsSheet));
				Sheet categoriesSheet = workbook.getSheet(1);
				modelLoader.loadCategories(readCategoriesSheet(categoriesSheet));
				Sheet emailSettingsSheet = workbook.getSheet(2);
				modelLoader.loadEmailSettings(readEmailSettingsSheet(emailSettingsSheet));
				workbook.close();      
			}
			else {
				Logger.getInstance().append("\nCould not find file: "+filename);
				throw new Exception("Could not find file: "+filename);
			}				
	    }
	    catch (IOException e)
	    {
	      e.printStackTrace();
	    }
	    catch (BiffException e)
	    {
	      e.printStackTrace();
	    }
	    
	}

	  public ArrayList<Recipient> readRecipientsSheet(Sheet s)
	  {
//		  System.out.println("reading sheet name = "+s.getName());
//		  System.out.println("has " + s.getColumns() + " columns and " + s.getRows() + " rows");

		  ArrayList<Recipient> recipients = new ArrayList<Recipient>();
		  
		  int rows = s.getRows();
		  int NAME = 0, EMAILADDRESS = 1, MONICONTACT = 2, TOMASCONTACT = 3, ACTIVE = 4, CATEGORY = 5;
		  for (int i = 1; i < rows; i++) {
			  if (s.getCell(ACTIVE, i).getContents().equalsIgnoreCase("true"))
				   recipients.add(new Recipient(s.getCell(NAME, i).getContents()
	                       ,s.getCell(EMAILADDRESS, i).getContents()
	                       ,s.getCell(MONICONTACT, i).getContents().equalsIgnoreCase("true")
	                       ,s.getCell(TOMASCONTACT, i).getContents().equalsIgnoreCase("true")
	                       ,s.getCell(ACTIVE, i).getContents().equalsIgnoreCase("true")
	                       ,s.getCell(CATEGORY, i).getContents()));
		  }
		  return recipients;
		  
	  }

	  public ArrayList<Category> readCategoriesSheet(Sheet s)
	  {
//		  System.out.println("reading sheet name = "+s.getName());
//		  System.out.println("has " + s.getColumns() + " columns and " + s.getRows() + " rows");

		  ArrayList<Category> categories = new ArrayList<Category>();
		  
		  int rows = s.getRows();
		  int CATEGORY = 0, PARENT_CATEGORY = 1;
		  // this sheet just has 2 columns, category and parent category
		  for (int i = 1; i < rows; i++) {
			  categories.add(new Category(s.getCell(CATEGORY, i).getContents(), s.getCell(PARENT_CATEGORY, i).getContents()));
		  }
		  return categories;
		  
	  }
	  
	  public EmailSettings readEmailSettingsSheet(Sheet s)
	  {
//		  System.out.println("reading sheet name = "+s.getName());
//		  System.out.println("has " + s.getColumns() + " columns and " + s.getRows() + " rows");

		  int smtp_server = 0, from_email = 1, reply_to = 2, send_to = 3, max_in_batch = 4, delay_between_emails = 5, authenticate = 6, username = 7;

		  return new EmailSettings(s.getCell(smtp_server, 1).getContents()
	                              , s.getCell(from_email, 1).getContents()
	                              , s.getCell(reply_to, 1).getContents()
	                              , s.getCell(send_to, 1).getContents()
	                              , Integer.parseInt(s.getCell(max_in_batch, 1).getContents())
	                              , Float.parseFloat(s.getCell(delay_between_emails, 1).getContents())
	                              , s.getCell(authenticate, 1).getContents().equalsIgnoreCase("true")
	                              , s.getCell(username, 1).getContents()
	                              );
		  
	  }
}