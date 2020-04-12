import com.courseracapstone.serverside.integration.test.PotlatchServersideTest;


/**
 * Just a main class to easily insert a few gifts during the demo
 *
 */
public class InsertDemoGifts {

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: InsertDemoGifts <server> (<num gifts>)");
			System.exit(0);
		}
		
		// default 3 gifts
		int numberOfGifts = 3;
		
		if (args.length == 2) {
			try {
				numberOfGifts = Integer.parseInt(args[1]);
			} 
			catch (NumberFormatException e) {
				System.out.println("Usage: InsertDemoGifts <server> (<num gifts>)");
				System.exit(0);
			}
		}
		
		String server = args[0] + ":8443";
		
		PotlatchServersideTest test = new PotlatchServersideTest(server, true);
		try {
			test.testAddGetGiftImages(numberOfGifts);
		} 
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
