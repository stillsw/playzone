package email;

import java.io.*;
import javax.mail.MessagingException;
import javax.mail.internet.MimeUtility;

/**
 * rather than expose the sub packages
 * @author Tomas
 *
 */
public class MimeDecoder {

	private MimeDecoder() {
	}

	public static InputStreamReader getDecodedReader(InputStream is, String htmlEncoding, String readerEncoding) {

		try {
			return new InputStreamReader(MimeUtility.decode(is, htmlEncoding), readerEncoding);

		}
		catch (Exception e) {
			return new InputStreamReader(is);
		}
	}

}
