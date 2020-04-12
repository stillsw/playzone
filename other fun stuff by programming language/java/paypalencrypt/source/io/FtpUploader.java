package io;

import java.io.*;
import java.util.*;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;

public class FtpUploader {

	public static final String FTP_SETTINGS = "ftpSettings";
	public static final String FTP_SERVER = "server";
	public static final String FTP_USER_NAME = "userName";
	public static final String FTP_REMOTE_DIR = "remoteDir";
	private String server;
	private String userName;
	private String remoteDir;
	
	public FtpUploader(String server, String userName, String remoteDir) {
		this.server = server;
		this.userName = userName;
		this.remoteDir = remoteDir;
	}
	
	public static boolean isValidFile(String fileName) {
		if (fileName == null || fileName.length() == 0)
			return false;
		
		File file = new File(fileName);
		return file.exists() && file.isFile();
	}
	
	public ArrayList<String> uploadFile(String password, String fileName) throws Exception {
		
		ArrayList<String> results = new ArrayList<String>();
		
password = "innerspace";
		if (password == null || password.length() == 0) 
			throw new Exception("Error: FtpUploader.uploadFile password empty");

		if (!isValidFile(fileName))
			throw new Exception("Error: FtpUploader.uploadFile fileName must be a valid file");

		File file = new File(fileName);
		String directory = file.getParent();
		String fileNameOnly = file.getName();
		results.add("------------------------------------------\nFtp "+fileNameOnly+" from "+directory);
		
		results.add("Attempting to open connection to "+userName+"@"+server);
		
		FTPClient ftp = new FTPClient();
        ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));

        boolean connectOkProceed = true;
        try {
            int reply;
            ftp.connect(server);
            results.add("Connected to " + server);

            reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                results.add("FTP server refused connection");
                connectOkProceed = false;
            }
        }
        catch (IOException e) {
            connectOkProceed = false;
            e.printStackTrace();
            results.add("Failed to connect to " + server + " error = "+e.getMessage());
            if (ftp.isConnected()) {
                try {
                    results.add("Disconnect from " + server);
                    ftp.disconnect();
                }
                catch (IOException f) {
                    results.add("Failed to disconnect from " + server);
                }
            }
        }
     
        if (connectOkProceed) {
	        try {
	            if (!ftp.login(userName, password)) {
	                ftp.logout();
		            results.add("Unable to login with userName/password");
	            }
	            else {
		            results.add("Remote system is " + ftp.getSystemName());
		
		            // Use passive mode as default
		            //ftp.enterLocalPassiveMode();

		            ftp.changeWorkingDirectory(remoteDir);
		            ftp.printWorkingDirectory();
//		            String[] files = ftp.listNames();
//		            for (int i = 0; i < files.length; i++)
//		            	System.out.println("file : "+files[i]);
		            // try to write file
		            results.add("Upload file to remote directory "+remoteDir);
	                InputStream input = new FileInputStream(fileName);
	                ftp.storeFile(fileNameOnly, input);
	                input.close();
		
		            results.add("File uploaded, log out");
		            ftp.logout();
	            }
	        }
	        catch (FTPConnectionClosedException e) {
	            results.add("Server closed connection");
	            e.printStackTrace();
	        }
	        catch (IOException e) {
	            results.add("IO error: "+e.getMessage());
	            e.printStackTrace();
	        }
	        finally {
	            if (ftp.isConnected()) {
	                try {
	                    results.add("Disconnect from " + server);
	                    ftp.disconnect();
	                }
	                catch (IOException f) {
	                    results.add("Failed to disconnect from " + server);
	                }
	            }
	        }
        }
        
		return results;
	}
	
	public String getRemoteDir() {
		return remoteDir;
	}

	public String getServer() {
		return server;
	}

	public String getUserName() {
		return userName;
	}
	
	
}


class PrintCommandListener implements ProtocolCommandListener
{
    private PrintWriter __writer;

    public PrintCommandListener(PrintWriter writer)
    {
        __writer = writer;
    }

    public void protocolCommandSent(ProtocolCommandEvent event)
    {
        __writer.print(event.getMessage());
        __writer.flush();
    }

    public void protocolReplyReceived(ProtocolCommandEvent event)
    {
        __writer.print(event.getMessage());
        __writer.flush();
    }
}

