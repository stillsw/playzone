package email;

public class SendMailFailedException extends RuntimeException
{
	public SendMailFailedException(String msg) {
		super(msg);
	}
}