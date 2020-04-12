package email;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;


public class SimpleMail
{
	public static float ESTIMATED_TIME_TO_SEND_AN_EMAIL = 0.5f;
	private String smtpServer;
	private String mailFrom;
	private String mailTo;
	private String mailCc;
	private String mailBcc;
	private String mailReplyTo;
	private String mailSubject;
	private String mailHtmlBody;
	private String mailPlainBody;
	private byte[] attachment;
	private String attachmentName;
	private Session session;
	
	/**
	 * Constructor
	 */
	public SimpleMail(String smtpServer) {
		this.smtpServer = smtpServer;
		Properties props = System.getProperties();
		props.put("mail.smtp.host", smtpServer);
		session = Session.getInstance(props,null);
	}

	public SimpleMail(String smtpServer, String subject, String htmlBody, String plainBody, String TO, String CC, String BCC, String mailFrom, String replyTo, String fileName, byte[] attachment) {
		this(smtpServer);
		setMailFrom(mailFrom);
		setMailTo(TO);
		setMailCc(CC);
		setMailBcc(BCC);
		setMailReplyTo(((replyTo == null || replyTo.isEmpty()) ? mailFrom : replyTo));
		setMailSubject(subject);
		setMailHtmlBody(htmlBody);
		setMailPlainBody(plainBody);
		setAttachmentName(fileName);
		setAttachment(attachment);
	}
	
	public String getMailBcc() {
		return mailBcc;
	}

	public void setMailBcc(String mailBcc) {
		this.mailBcc = mailBcc;
	}


	public String getMailHtmlBody() {
		return mailHtmlBody;
	}

	public void setMailHtmlBody(String mailHtmlBody) {
		this.mailHtmlBody = mailHtmlBody;
	}

	public String getMailPlainBody() {
		return mailPlainBody;
	}

	public void setMailPlainBody(String mailPlainBody) {
		this.mailPlainBody = mailPlainBody;
	}

	public String getMailFrom() {
		return mailFrom;
	}

	public String getMailReplyTo() {
		return mailReplyTo;
	}

	public String getMailSubject() {
		return mailSubject;
	}

	public String getMailTo() {
		return mailTo;
	}

	/**
	 * @return Returns the mailCc.
	 */
	public String getMailCc() {
		return mailCc;
	}

	public void setMailFrom(String string) {
		mailFrom = string;
	}

	public void setMailReplyTo(String string) {
		mailReplyTo = string;
	}

	public void setMailSubject(String string) {
		mailSubject = string;
	}

	public void setMailTo(String string) {
		mailTo = string;
	}


	/**
	 * @param mailCc The mailCc to set.
	 */
	public void setMailCc(String mailCc) {
		this.mailCc = mailCc;
	}
	/**
	 * @return Returns the attachment.
	 */
	public byte[] getAttachment() {
		return attachment;
	}
	/**
	 * @param attachment The attachment to set.
	 */
	public void setAttachment(byte[] attachment) {
		this.attachment = attachment;
	}

	/**
	 * @return Returns the attachmentName.
	 */
	public String getAttachmentName() {
		return attachmentName;
	}
	/**
	 * @param attachmentName The attachmentName to set.
	 */
	public void setAttachmentName(String attachmentName) {
		this.attachmentName = attachmentName;
	}

	// Not using the following method since it throws UnknownHostException in CCI
	public void sendJavaXMail(String htmlEncoding)
		throws SendMailFailedException
	{
		  try{
			// -- Create a new message --
			Message msg = new MimeMessage(session);
						
			if (mailReplyTo != null)
			{
				InternetAddress replyToAddr = new InternetAddress(mailReplyTo);
				Address[] addr = new Address[1];
				addr[0] = replyToAddr;
				msg.setReplyTo(addr);
			}

			// -- Set the FROM and TO fields --
			msg.setFrom(new InternetAddress(mailFrom));
			msg.setRecipients(Message.RecipientType.TO,InternetAddress.parse(mailTo, false));

			if (mailCc != null) // && !mailCc.equals(Constants.FIELD_NOT_ENTERED))
				msg.setRecipients(Message.RecipientType.CC,InternetAddress.parse(mailCc, false));

			if (mailBcc != null) // && !mailCc.equals(Constants.FIELD_NOT_ENTERED))
				msg.setRecipients(Message.RecipientType.BCC,InternetAddress.parse(mailBcc, false));

			// -- Set the subject and body text --
			msg.setSubject(mailSubject);
			
//			msg.setContent(mailBody, "text/html;charset=ISO-8859-1");
			
//			msg.addHeader("Content-Type", "multipart/alternative");
			
			Multipart mp = new MimeMultipart("alternative");

	        // Create and fill the first message part
			{
				MimeBodyPart mbp2 = new MimeBodyPart();
				mbp2.setText(mailPlainBody);// <-- set it like this NOT (html,"text/html")!!!
				mbp2.addHeaderLine("Content-Type: text/plain; charset=\"iso-8859-1\"");
				mbp2.addHeaderLine("Content-Transfer-Encoding: quoted-printable");
//				mbp2.setContent(mailBody, "text/plain;charset=ISO-8859-1");
				mp.addBodyPart(mbp2);
			}

			{
				MimeBodyPart mbp;
				mbp = new MimeBodyPart();
				String encoding = (htmlEncoding != null && htmlEncoding.length() > 0 ? htmlEncoding : "iso-8859-1");
				mbp.setText(mailHtmlBody);// <-- set it like this NOT (html,"text/html")!!!
				mbp.addHeaderLine("Content-Type: text/html; charset=\""+encoding+"\"");
				mbp.addHeaderLine("Content-Transfer-Encoding: quoted-printable");
//				mbp.setContent(mailBody, "text/html;charset=ISO-8859-1");
	            mp.addBodyPart(mbp);
			}
			
            // Add the Multipart to the message
            msg.setContent(mp);

            // Set the Date: header
            msg.setSentDate(new Date());
            
            // ensure correct headers are written
            msg.saveChanges();

			// -- Send the message --
			Transport.send(msg);

		  }catch (Exception ex){
		  	throw new SendMailFailedException(ex.getMessage());
		  }// End of try... catch...

		  /**
		   * this bit of code was used to add attachments, not currently used but keep for eg
		   */
//        // add attachment file
////      if (attachment != null) {
////         MimeBodyPart mbp = new MimeBodyPart();
//////         mbp.setHeader("Content-Type", "application/vnd.ms-excel");
////         ByteArrayDataSource ds = new ByteArrayDataSource(attachment, "APPLICATION/OCTET-STREAM");
////         mbp.setDataHandler(new DataHandler(ds));
////         mbp.setFileName(attachmentName);
////         mp.addBodyPart(mbp);
////      }
//
	
	}// End of sendMail()...

	public void mailAttachment(String subject, String htmlBody, String plainBody, String TO, String CC, String BCC, String mailFrom, String replyTo, String fileName, byte[] attachment)
	  throws SendMailFailedException
	{
	  setMailFrom(mailFrom);
	  setMailTo(TO);
	  setMailCc(CC);
	  setMailBcc(BCC);
	  setMailReplyTo((replyTo == null ? mailFrom : replyTo));
	  setMailSubject(subject);
	  setMailHtmlBody(htmlBody);
	  setMailPlainBody(plainBody);
	  setAttachmentName(fileName);
	  setAttachment(attachment);

	  sendJavaXMail(null);
	}
	
	/**
	 * Test run
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.out.println("Run SimpleMail.mailAttachment");
			SimpleMail simpleMail = new SimpleMail("shawmail.vc.shawcable.net");
			simpleMail.mailAttachment("bonjour c'est un test"
					, "<html><body color='red'>html string</body></html>"
					, "plain text string"
					, "tomas.stubbs@telus.net"
					, (String) null
					, (String) null
					, "beingstill@gmail.com"
					, (String) null
					, (String) null
					, (byte[]) null);
			System.out.print("Run Completed!");
		}
		catch (Exception e) {
			System.out.println(e);
		}
	}
}