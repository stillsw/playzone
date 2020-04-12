package email;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import model.Recipient;

public class EmailFile {

	private MimeMessage emlMessage;
	private File emlFile;
	
	public EmailFile(File emlFile) throws Exception {
		// when loading the file to begin with don't know if the smtp server will change, don't set a session up
		this.emlMessage = openFile(emlFile, null);
		this.emlFile = emlFile;
	}
   
	public void reopenFile(Session session) throws Exception {
		// want to set a session up now
		this.emlMessage = openFile(emlFile, session);
	}
	
	public MimeMessage openFile(File emlFile, Session mailSession) throws Exception{
        InputStream source = new FileInputStream(emlFile);
        MimeMessage message = new MimeMessage(mailSession, source);

//
//        System.out.println("Subject : " + message.getSubject());
//        System.out.println("From : " + message.getFrom()[0]);
//        System.out.println("--------------");
//        System.out.println("Body : " +  message.getContent());
//        
        return message;
    }

	public String getSubject() throws MessagingException {
		return emlMessage.getSubject();
	}
	
	public void setFrom(Address from) throws MessagingException {
		emlMessage.setFrom(from);
	}
	
	public void setReplyTo(Address replyTo) throws MessagingException {
		emlMessage.setReplyTo(new Address[] {replyTo});
	}
	
	public void setSendDate() throws MessagingException {
		emlMessage.setSentDate(new Date());
	}
	
	public void setSendTo(InternetAddress sendTo) throws MessagingException {
		emlMessage.setRecipients(Message.RecipientType.TO, new Address[] {sendTo});
	}
	
	public void setSendTo(String sendTo) throws AddressException, MessagingException {
		setSendTo(new InternetAddress(sendTo));
	}
	public void setSendTo(String sendTo, String name) throws AddressException, MessagingException, UnsupportedEncodingException {
		setSendTo(new InternetAddress(sendTo, name));
	}
	
	public void setBccList(ArrayList<Recipient> recipients) throws MessagingException {
		if (recipients != null && recipients.size() > 0) {
			Address[] bcc = new Address[recipients.size()];
			Recipient[] recs = new Recipient[recipients.size()];
			recs = recipients.toArray(recs);
			for (int i = 0; i < bcc.length; i++) {
				try {
					bcc[i] = new InternetAddress(recs[i].getEmail(), recs[i].getName());
				} catch (UnsupportedEncodingException e) {
					bcc[i] = new InternetAddress(recs[i].getEmail());
				}				
			}
			emlMessage.setRecipients(Message.RecipientType.BCC, bcc);
		}
	}
	
	public MimeMessage getEmlMessage() throws MessagingException {
		// save any changes and return the message
		emlMessage.saveChanges();
		return emlMessage;
	}

	public String summariseContents() throws MessagingException, IOException {
		StringBuffer summary = new StringBuffer();
        Object o = emlMessage.getContent();
        if (o instanceof String) {
        	summary.append("**A simple (string) email**\n");
        	summary.append((String)o);
        }
        else if (o instanceof Multipart) {
        	summary.append("**A Multipart email, ");
            Multipart mp = (Multipart)o;
            int count3 = mp.getCount();
            summary.append("with " + count3 + " parts**\n");
            for (int j = 0; j < count3; j++) {
                // Part are numbered starting at 0
                BodyPart b = mp.getBodyPart(j);
                String mimeType = b.getContentType();
                summary.append( "\nPart " + (j + 1) + " is " + mimeType + "\n");

                Object o2 = b.getContent();
                if (o2 instanceof String) {
                	//summary.append((String)o2);
                }
                else if (o2 instanceof Multipart) {
                	summary.append("      It contains a nested Multipart with ");
                    Multipart mp2 = (Multipart)o2;
                    int count2 = mp2.getCount();
                    summary.append("" + count2 + " further parts in it\n");
                    for (int k = 0; k < mp2.getCount(); k++) {
                    	BodyPart b2 = mp2.getBodyPart(k);
                    	summary.append("\n          Part "+(k+1)+" is "+b2.getContentType()+"\n");
                    }
                }
                else if (o2 instanceof InputStream) {
                	//summary.append("**This is an InputStream part**\n");
                }
            } //End of for
        }
        else if (o instanceof InputStream) {
        	summary.append("**A stream email**\n");
        }
        
        return summary.toString();
		
	}
}
