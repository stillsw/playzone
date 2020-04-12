package email;

import java.util.regex.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

public final class WebUtil {
	  
	  /**
	  * Validate the form of an email address.
	  *
	  * <P>Return <tt>true</tt> only if 
	  *<ul> 
	  * <li> <tt>aEmailAddress</tt> can successfully construct an 
	  * {@link javax.mail.internet.InternetAddress} 
	  * <li> when parsed with "@" as delimiter, <tt>aEmailAddress</tt> contains 
	  * two tokens which satisfy {@link hirondelle.web4j.util.Util#textHasContent}.
	  *</ul>
	  *
	  *<P> The second condition arises since local email addresses, simply of the form
	  * "<tt>albert</tt>", for example, are valid for 
	  * {@link javax.mail.internet.InternetAddress}, but almost always undesired.
	  */
	  public static boolean isValidEmailAddress(String aEmailAddress){
	    if (aEmailAddress == null) return false;
	    boolean result = true;
	    try {
	      InternetAddress emailAddr = new InternetAddress(aEmailAddress);
	      if ( ! hasNameAndDomain(aEmailAddress) ) {
	        result = false;
	      }
	    }
	    catch (AddressException ex){
	      result = false;
	    }
	    return result;
	  }

	  private static boolean hasNameAndDomain(String aEmailAddress){
	    String[] tokens = aEmailAddress.split("@");
	    return 
	     tokens.length == 2 &&
	     textHasContent( tokens[0] ) && 
	     textHasContent( tokens[1] ) ;
	  }
	  
	  private static boolean textHasContent(String text) {

		  if (text == null || text.length() == 0)
			  return false;
		  if (text.indexOf(' ') != -1)
			  return false;
		  
		  return true;
	  }
	  
	  /* 
	   * detect if email address is of the format that includes the actual address
	   * within < and > and just validate what's in between those
	   */
	  public static boolean isValidEmailString(String emailText) {
		  int idx = emailText.indexOf('<');
		  if (idx >= 0) {
			  int idx2 = emailText.indexOf('>');
			  if (idx2 > idx) {
				  String part = emailText.substring(idx + 1, idx2);
				  return isValidEmailAddress(part);
			  }
			  else 
				  return false;
		  }
		  else
			  return isValidEmailAddress(emailText);
	  }
	  
	  
}