package ui;

import model.*;
import io.Logger;
import java.util.*;
import java.io.IOException;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.event.MailEvent;
import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import email.EmailFile;
import email.SendMailFailedException;
import email.WebUtil;


public class EmailSender { // implements TransportListener {
	
	private int totalNumberOfEmails = 0;
	private int totalNumberOfDelays = 0;
	private float percTimePerEmail = 0.0f;
	private float percTimePerDelay = 0.0f;
	private boolean batchComplete = false;
	private int errors = 0;
	
	private Shell shell;
	private ProgressBar progressBar;
	private Button sendBtn;
	
	private ArrayList<Recipient> recipients;
	
	private EmailFile email;
	
	public EmailSender(Shell shell, ProgressBar progressBar, Button sendBtn, ArrayList<Recipient> recipients, EmailFile email) {
		this.shell = shell;
		this.recipients = recipients;
		this.progressBar = progressBar;
		this.sendBtn = sendBtn;
		this.email = email;
	}
	
	public boolean setUpSendAndConfirm() throws Exception {
		StringBuffer emails = new StringBuffer();
		StringBuffer errors = new StringBuffer();

		// build the email string
		for (Iterator<Recipient> recsIt = recipients.iterator(); recsIt.hasNext(); ) {
			Recipient recipient = recsIt.next();
			String email = recipient.getEmail();
    		if (!recipient.emailAddressIsError()) {
    			emails.append(email);
    			emails.append("\n");
    		}
    		else {
    			// in theory this should never happen because the recs with error emails aren't added to the list
    			errors.append("\n"+recipient.getName()+" \""+email+"\"");
    		}
		}
		if (errors.length() > 0) {
			MessageDialog.showMessageDialog(shell, "Info", "Send email failed, found invalid email addresses :" + errors);
			return false;
		}

		// confirm that the emails should be sent
		String emailList = emails.toString();
		boolean confirmContinue = MessageDialog.confirmMessageDialog(shell, "Confirm Send", "Are you sure you want to send the email \" " + email.getSubject() + " \" to :\n" + (emailList.length() < 100 ? emailList : emailList.substring(0, 97) + "..."));
		if (!confirmContinue) {
			MessageDialog.showMessageDialog(shell, "Info", "Send email cancelled");
		}
		return confirmContinue;
		
	}
	
	private synchronized void setBatchComplete() {
		batchComplete = true;
	}
	
	public void sendEmail(final Text resultsLabel) throws Exception {
		final EmailSettings emailSettings = ModelLoader.getInstance().getEmailSettings();			
		final int totalRecipients = recipients.size();
		final int delay = (int) (emailSettings.getDelayBetweenEmails() * 1000);
		
		// var to hold changing value for thread that changes the progress bar
		final int[] progressVal = new int[1];
				
		final Date startTime = new Date();
        // init errors
        this.errors = 0;
		
		shell.open();
		progressBar.setMaximum(totalRecipients);

		Thread mainThread = new Thread() {
			{ this.setDaemon(true); } 
			public void run() {
				int maxInBatch = emailSettings.getMaxInBatch();

				try {
					Logger log = Logger.getInstance();
					log.append("\n\nsettings: "+emailSettings.toString());
					log.append("\n\nsubject: "+email.getSubject());
					log.append("\nhtml body: "+email.summariseContents());
					// call method which calculates the timings, but also returns a string to output to log
					String timingsEstimate = calculateTimings(emailSettings, totalRecipients);
					log.append("\n\nSending to " + totalRecipients + " recipients, estimated time to complete" + timingsEstimate);
					log.append("\nTotal emails " + totalNumberOfEmails + ", total delays, " + totalNumberOfDelays + ", percentage time per email " + percTimePerEmail + ", percentage time per delay " + percTimePerDelay);
				} catch (Exception e) {
					e.printStackTrace();
				}

				logAndScreen(resultsLabel, "\nBreaking into batches of "+maxInBatch);

			    // Create properties, get Session
			    Properties props = new Properties();
		        props.put("mail.transport.protocol", "smtp");
		        String from = emailSettings.getFromEmail();
		        props.put("mail.from", from);
		        // To see what is going on behind the scene
		        if (emailSettings.isDebug())
		        	props.put("mail.debug", "true");
		
		        props.put("mail.smtp.host", emailSettings.getSmtpServer());
		
		        if (emailSettings.isAuthenticate()) {
		        	props.setProperty("mail.smtp.auth", "true");
		        }		
				
				int fromIndex = 0;
				int toIndex = Math.min(recipients.size(), maxInBatch);
				Date lastTiming = startTime;
				Date newTiming;
		
				while (toIndex <= recipients.size() && fromIndex < toIndex) {
					// get a sub-list to send to
					List<Recipient> batchOfRecs = recipients.subList(fromIndex, toIndex);

					newTiming = new Date();
					logAndScreen(resultsLabel, "\n\t batch of "+batchOfRecs.size()+" from "+fromIndex+" to "+toIndex+" ("+batchOfRecs.get(0)+" to "+batchOfRecs.get(batchOfRecs.size()-1)+")");
					
					try {
						batchComplete = false;
						sendBatch(emailSettings, from, toIndex, props, batchOfRecs, resultsLabel);
						while (!batchComplete) {
							// wait in 1/2 sec intervals for batch to finish
							try { sleep(100); } catch (InterruptedException e) {}
						}

						logAndScreen(resultsLabel, "\n\t batch done, time elapsed "+calculateTimeDifference(lastTiming, newTiming)+"\n");
						
					} catch (Exception e) {
						logAndScreen(resultsLabel, "\nErrors returned for this batch (see log):" + e.getMessage());
					}

					lastTiming = newTiming;
					
					fromIndex = toIndex;
					toIndex += maxInBatch;
					if (toIndex > recipients.size()) {
						toIndex = recipients.size();
					}
					else {
						// update the progress bar
						if (shell.getDisplay().isDisposed()) return;
						progressVal[0] = toIndex;
						shell.getDisplay().asyncExec(new Runnable() {
							public void run() {
								if (progressBar.isDisposed ()) return;
								progressBar.setSelection(totalRecipients);
							}});
					}
				}
				
				logAndScreen(resultsLabel, "\n\nFinished with "+errors+" errors, total time elapsed = " + calculateTimeDifference(startTime, new Date()));
				
				shell.getDisplay().asyncExec(new Runnable() {
					public void run() {
						sendBtn.setEnabled(true);
					}});
			}
		};
		
		mainThread.start();
	}

	private void logAndScreen(final Text resultsLabel, String msg) {
		String logRes = "";
		try {
			Logger.getInstance().append(msg);
		} catch (IOException e) {
			logRes = " (Failed to write this msg to log)";
		}
		
		final String resMsg = msg + logRes;
		
		// update the results on the shell
		shell.getDisplay().asyncExec(new Runnable() {
			public void run() {
				resultsLabel.append(resMsg);
			}});
	}
	
	private void sendBatch(final EmailSettings emailSettings, String from, final int delay, Properties props, final List<Recipient> batchList, final Text resultsLabel) throws Exception, Exception {
        final Session session;
        if (emailSettings.isAuthenticate()) {
        	Authenticator auth = new Authenticator() {
        		protected PasswordAuthentication getPasswordAuthentication() {
        			return new PasswordAuthentication(emailSettings.getUsername(), emailSettings.getPassword());
        		}
        	};
        	session = Session.getDefaultInstance(props, auth);
        }
        else
        	session = Session.getInstance(props);

        // reopen the email file with these settings
        email.reopenFile(session);
		email.setFrom(new InternetAddress(from));
		if (emailSettings.getReplyTo() != null && !emailSettings.getReplyTo().isEmpty())
			email.setReplyTo(new InternetAddress(emailSettings.getReplyTo()));
  		
		// kick off a thread which runs the emailing and also then updates the progress bar
		new Thread() {
			public void run() {
				try {
			  		// main business
		            // Use session properties to get default protocol
		            Transport bus = session.getTransport();
		            bus.connect();
		            // catch transport/session errors
		            try {
						boolean sending = true;
	  					// get an iterator over this batch
						for (Iterator<Recipient> it = batchList.iterator(); it.hasNext(); ) {
							Recipient recipient = it.next();
							
							if (sending) {		
		  						// trap hanging email with thread that wakes up unless it's interrupted first
		  						TaskThread taskThread = new TaskThread(resultsLabel); // think "this" should work, before had -> (Thread.currentThread(), log);
		  						try {
		  							// send the email
		  							email.setSendDate();
					            	email.setSendTo(recipient.getEmail(), recipient.getName());
	
	  					            MimeMessage msg = email.getEmlMessage();
//	  					            bus.sendMessage(msg, msg.getAllRecipients());
System.out.println("send msg commented out for safety");	  					            
			  						// kill the thread that would throw error otherwise
				  					if (taskThread.isAlive()) {
			  							taskThread.interrupt();
			  						}
				  					
				  					logAndScreen(resultsLabel, "\n\t\t Send OK to : "+recipient.getName()+" ("+recipient.getEmail()+")");
			  					} 
			  					catch (Exception e) {
				  					if (taskThread.isAlive()) {
			  							taskThread.interrupt();
			  						}
				  					errors++;
				  					logAndScreen(resultsLabel, "\n\t\t #############FAILED to : "+recipient.getName()+" ("+recipient.getEmail()+") "+e.getMessage());
			  					}// send try
						
								if (!bus.isConnected()) { // in case got closed unexpectedly
									sending = false;
								}
							}
							else { // not sending (ie. there was an error already
								errors++;
								logAndScreen(resultsLabel, "\n\t\t #############FAILED to : "+recipient.getName()+" ("+recipient.getEmail()+") because of previously lost connecction");								
							}
						} // send loop
					}
		            finally {
		            	if (bus.isConnected())
		            		try { bus.close(); } catch (Exception e) {}
		            } // connect try
		            
				} // catch transport/session errors
				catch (final Exception e) {
					logAndScreen(resultsLabel, "\n\t\t ############# looks like the whole batch failed because couldn't get a connection "+e.getMessage());
				}
				finally {
					// delay between batches
					try { Thread.sleep(delay); } catch (InterruptedException e) {} 
					// callback to let main thread know this one finished
					setBatchComplete();
				}
			}
		}.start();
	}
	
	private static String calculateTimeDifference(Date startTime, Date endTime) {
		return "" + ((endTime.getTime() - startTime.getTime()) / 1000.0f) + " seconds";
	}
	
	private String calculateTimings(EmailSettings settings, int totalRecipients) {
		
		float delayBetweenBatches = settings.getDelayBetweenEmails();
		float maxInBatch = settings.getMaxInBatch();
		float totalTime = 0.0f;
		// guesswork
		float timeToSendEachEmail = 1.7f;
		
		// for sending individually maths is easy. nb each email is a batch and includes a delay
		totalTime = (totalRecipients * timeToSendEachEmail) + ((totalRecipients - 1) * delayBetweenBatches);
		
		// set the class properties used for monitoring progress
		totalNumberOfEmails = totalRecipients;

		// set the class properties used for monitoring progress
		totalNumberOfDelays = totalNumberOfEmails - 1;
		percTimePerEmail = (timeToSendEachEmail / totalTime) * 100.0f;
		percTimePerDelay = (delayBetweenBatches / totalTime) * 100.0f;
		
		return " "+totalTime+" seconds";
	}
	
	private class TaskThread extends Thread {

		int SLEEP_TIME = 15000;
		Text resultsLabel;
		
		TaskThread(Text resultsLabel) {
			super("Task Thread");
			this.resultsLabel = resultsLabel;
		}
	
		public void start() {
			super.start();
		}
		
		public void run() {
			try {
				synchronized (this) {
					sleep(SLEEP_TIME);
					logAndScreen(resultsLabel, "\n\nAttempt to send email appears to be hanging, abort abnormally to stop");
				}
			} catch (InterruptedException e ) {
				// TaskThread: exiting normally
			}
		}
	}
}
