package com.courseracapstone.serverside.integration.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;

import retrofit.ErrorHandler;
import retrofit.RestAdapter;
import retrofit.RestAdapter.LogLevel;
import retrofit.RetrofitError;
import retrofit.client.ApacheClient;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

import com.courseracapstone.common.GiftsSectionType;
import com.courseracapstone.common.PagedCollection;
import com.courseracapstone.common.PotlatchConstants;
import com.courseracapstone.serverside.client.PotlatchSvcApi;
import com.courseracapstone.serverside.repository.Gift;
import com.courseracapstone.serverside.repository.UserNotice;
import com.courseracapstone.serversidetest.gift.TestData;

/**
 * A test for the gift service
 * 
 * @author xxx xxx
 */
public class PotlatchServersideTest {

	private class ErrorRecorder implements ErrorHandler {

		private RetrofitError error;

		@Override
		public Throwable handleError(RetrofitError cause) {
			error = cause;
			return error.getCause();
		}

		public RetrofitError getError() {
			return error;
		}
	}

	private final String TEST_URL;
	private final boolean addTimestampToRandomTitles;
	
	private final int ADMIN_USER = PotlatchConstants.ADMIN_TEST_USER_IDX;
	private int DEFAULT_USER = -1, ALT_USER = -1, ALT_USER2 = -1;
	
	// tests that need to get all rows when paging
	private final long ALL_ROWS_PAGE_SIZE = 1000000000;
	private final long ALL_ROWS_PAGE_IDX = 0;


	private final ArrayList<PotlatchSvcApi> testUsers;

	public PotlatchServersideTest() {
		this("localhost:8443", false);
	}
	
	public PotlatchServersideTest(String server, boolean isDemo) {
		TEST_URL = "https://" + server;
		addTimestampToRandomTitles = !isDemo;
		
		testUsers = new ArrayList<PotlatchSvcApi>();
		
		for (int i = 0; i < PotlatchConstants.TEST_USER_NAMES.length; i++) {
			if (i == PotlatchConstants.ADMIN_TEST_USER_IDX) {
				testUsers.add(new SecuredRestBuilder()
					.setClient(new ApacheClient(UnsafeHttpsClient.createUnsafeClient()))
					.setEndpoint(TEST_URL)
					.setLoginEndpoint(TEST_URL + PotlatchConstants.TOKEN_PATH)
					.setUsername(PotlatchConstants.TEST_USER_NAMES[i]).setPassword(PotlatchConstants.TEST_USER_PWDS[i]).setClientId(PotlatchConstants.ADMIN_CLIENT_ID)
					.build().create(PotlatchSvcApi.class));
			}
			else {
				if (DEFAULT_USER == -1) DEFAULT_USER = i;
				else if (ALT_USER == -1) ALT_USER = i;
				else if (ALT_USER2 == -1) ALT_USER2 = i;
				
				testUsers.add(new SecuredRestBuilder()
					.setClient(new ApacheClient(UnsafeHttpsClient.createUnsafeClient()))
					.setEndpoint(TEST_URL)
					.setLoginEndpoint(TEST_URL + PotlatchConstants.TOKEN_PATH)
					.setUsername(PotlatchConstants.TEST_USER_NAMES[i]).setPassword(PotlatchConstants.TEST_USER_PWDS[i]).setClientId(PotlatchConstants.NORMAL_CLIENT_ID)
					.build().create(PotlatchSvcApi.class));
			}
		}
	}

	@Test
	public void testAddUser() throws Exception {
		// can only run this once for while the server is up, 2nd time will fail with bad request
		String un = "mynewtest";
		String pwd = "passit";
		
		// special api gives access to the createUser method
		// this is a bit quick and dirty to prove it can work this way
		// on android it won't use the api to access that method
		PotlatchSvcApi userApi = new SignupSecuredRestBuilder()
		.setClient(new ApacheClient(UnsafeHttpsClient.createUnsafeClient()))
		.setEndpoint(TEST_URL)
		.setLoginEndpoint(TEST_URL + PotlatchConstants.POTLATCH_SIGN_UP_PATH)
		.setUsername(un ).setPassword(pwd ).setClientId(PotlatchConstants.NORMAL_CLIENT_ID)
		.build().create(PotlatchSvcApi.class);
		
		// try a to add the user, should fail with bad request
		// only because of the hack method of doing it... it's doing an intercept
		// which means the same request twice, but that's ok, the next test will 
		// prove it's really created and this one actually is good to test can't do it
		// twice with the same user name
		try {
			userApi.createUser(un, pwd, PotlatchConstants.NORMAL_CLIENT_ID);

			fail("Yikes, the security setup is horribly broken and let sign up with same username twice!!");

		} catch (RetrofitError e) {
			// depends on the order of tests either:
			// get a 400 bad request (when run on clean server)
			try {
				assertEquals(400, e.getResponse().getStatus());
			} 
			catch (Exception e2) {
				// or the following message (when run with other tests), 
				// both are ok - they're both erroring the create 
				System.out.println("add user error = "+e.getMessage());	// testing the message of the first error		
				assertTrue(e.getMessage().contains("Login failure: 400"));
			}
		}		
		
		// now create a proper svc api to do stuff with the created client
		PotlatchSvcApi newUserApi = new SecuredRestBuilder()
		.setClient(new ApacheClient(UnsafeHttpsClient.createUnsafeClient()))
		.setEndpoint(TEST_URL)
		.setLoginEndpoint(TEST_URL + PotlatchConstants.TOKEN_PATH)
		.setUsername(un).setPassword(pwd).setClientId(PotlatchConstants.NORMAL_CLIENT_ID)
		.build().create(PotlatchSvcApi.class);
		
		// use it to add a gift
		Gift gift = TestData.randomGift(un);
		Gift received = newUserApi.addGift(gift);
		assertEquals(gift.getTitle(), received.getTitle());
		assertTrue(received.getId() > 0);
	}
	
	
	@Test
	public void testAddGiftMetadata() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);
		Gift received = testUsers.get(DEFAULT_USER).addGift(gift);
		assertEquals(gift.getTitle(), received.getTitle());
		assertTrue(received.getId() > 0);
	}

	@Test
	public void testAddGetGift() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);
		gift = testUsers.get(DEFAULT_USER).addGift(gift);
		long giftId = gift.getId();
		Gift stored = testUsers.get(ADMIN_USER).getGiftById(giftId);
		assertTrue(stored != null);
	}

	private static final File jpgFile = new File("src/test/resources/pic0.jpg");
	private static final File pngFile = new File("src/test/resources/pic1.png");
	private static final File gifFile = new File("src/test/resources/pic2.gif");
	private static final String jpgMime = "image/jpeg", jpgFormat = "jpg";
	private static final String gifMime = "image/gif", gifFormat = "gif";
	private static final String pngMime = "image/png", pngFormat = "png";

	/**
	 * Not a standard test, an overloaded version that creates a bunch of rows
	 * Called from InsertDemoGifts (default package)
	 * @param numberOfGifts
	 * @throws Exception
	 */
	public void testAddGetGiftImages(int numberOfGifts) throws Exception {
		if (numberOfGifts == 3) {
			// do the normal one
			testAddGetGiftImages();
		}
		else {

			for (int i = 0; i < numberOfGifts; i++) {
				Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], false);
				gift = testUsers.get(DEFAULT_USER).addGift(gift);
				testUsers.get(DEFAULT_USER).setGiftImageById(gift.getId(), gifFormat, new TypedFile(gifMime, getRandomDemoGifFile()));
			}
		}
	}

	private File getRandomDemoGifFile() throws FileNotFoundException {
		final String base = "src/test/resources/demoGif";
		final int seedNum = 10;
		final int ir =  (int) ((seedNum - 1) * Math.random());
		final String filename = base + ir + ".gif";
		
		File f = new File(filename);
		if (!f.exists()) {
			throw new FileNotFoundException(filename);
		}
		
		return f;
	}

	@Test
	public void testAddGetGiftImages() throws Exception {
		// create a gift, add it and then send the image
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], addTimestampToRandomTitles);
		gift = testUsers.get(DEFAULT_USER).addGift(gift);
		gift = testUsers.get(DEFAULT_USER).setGiftImageById(gift.getId(), jpgFormat, new TypedFile(jpgMime, jpgFile));
		
		assertTrue(gift.getImagePath() != null && gift.getThumbnailPath() != null);
		
		// check the jpg returned matches the one sent
		Response response = testUsers.get(DEFAULT_USER).getGiftImageData(gift.getId());		
		InputStream imageData = response.getBody().in();
		byte[] originalFile = IOUtils.toByteArray(new FileInputStream(jpgFile));
		byte[] retrievedFile = IOUtils.toByteArray(imageData);
		assertTrue(Arrays.equals(originalFile, retrievedFile));

		response = testUsers.get(DEFAULT_USER).getGiftThumbnailData(gift.getId());
		imageData = response.getBody().in();
		retrievedFile = IOUtils.toByteArray(imageData);
		assertTrue(originalFile.length > retrievedFile.length);

		// png file
		gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], addTimestampToRandomTitles);
		gift = testUsers.get(DEFAULT_USER).addGift(gift);
		gift = testUsers.get(DEFAULT_USER).setGiftImageById(gift.getId(), pngFormat, new TypedFile(pngMime, pngFile));
		response = testUsers.get(DEFAULT_USER).getGiftImageData(gift.getId());		
		imageData = response.getBody().in();
		originalFile = IOUtils.toByteArray(new FileInputStream(pngFile));
		retrievedFile = IOUtils.toByteArray(imageData);
		assertTrue(Arrays.equals(originalFile, retrievedFile));
		
		// gif file
		gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], addTimestampToRandomTitles);
		gift = testUsers.get(DEFAULT_USER).addGift(gift);
		gift = testUsers.get(DEFAULT_USER).setGiftImageById(gift.getId(), gifFormat, new TypedFile(gifMime, gifFile));
		response = testUsers.get(DEFAULT_USER).getGiftImageData(gift.getId());		
		imageData = response.getBody().in();
		originalFile = IOUtils.toByteArray(new FileInputStream(gifFile));
		retrievedFile = IOUtils.toByteArray(imageData);
		assertTrue(Arrays.equals(originalFile, retrievedFile));
		
	}
	
	@Test
	public void testAddInappropriateGift() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);
		gift.setUserInappropriateFlag(true);
		gift = testUsers.get(DEFAULT_USER).addGift(gift);
		Gift stored = testUsers.get(DEFAULT_USER).getGiftById(gift.getId());
		assertTrue(stored.getUserInappropriateFlag());
	}

	@Test
	public void testDenyGiftAddWithoutOAuth() throws Exception {
		ErrorRecorder error = new ErrorRecorder();
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);

		// Create an insecure version of our Rest Adapter that doesn't know how
		// to use OAuth.
		PotlatchSvcApi insecureGiftService = new RestAdapter.Builder()
				.setClient(
						new ApacheClient(UnsafeHttpsClient.createUnsafeClient()))
				.setEndpoint(TEST_URL).setLogLevel(LogLevel.FULL)
				.setErrorHandler(error).build().create(PotlatchSvcApi.class);
		try {
			// This should fail because we haven't logged in!
			insecureGiftService.addGift(gift);

			fail("Yikes, the security setup is horribly broken and didn't require the user to authenticate!!");

		} catch (Exception e) {
			// Ok, our security may have worked, ensure that
			// we got a 401
			assertEquals(HttpStatus.SC_UNAUTHORIZED, error.getError()
					.getResponse().getStatus());
		}

		// We should NOT get back the gift that we added above!
		PagedCollection<Gift> page = testUsers.get(DEFAULT_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, ALL_ROWS_PAGE_IDX, ALL_ROWS_PAGE_SIZE);
		assertFalse(page.getDataList().contains(gift));
	}

	@Test
	public void testDenyRemoveGiftWithoutOAuth() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[ALT_USER]);
		gift = testUsers.get(ALT_USER).addGift(gift);
		long giftId = gift.getId();
		
		try {
			// This should fail because this user doesn't have the necessary privilege
			testUsers.get(DEFAULT_USER).removeObsceneGiftById(giftId);

			fail("Yikes, the security setup is horribly broken and didn't require the admin user!!");

		} catch (RetrofitError e) {
			// Make sure we got a 403 forbidden
			assertEquals(403, e.getResponse().getStatus());
		}

		// We should get back the gift that we added above!
		gift = testUsers.get(DEFAULT_USER).getGiftById(giftId);
		assertTrue(gift != null); // actually 404 exception if it were to fail
		
		// let the admin user remove it
		testUsers.get(ADMIN_USER).removeObsceneGiftById(giftId);

		try {
			// This should fail because the gift isn't there anymore
			gift = testUsers.get(DEFAULT_USER).getGiftById(giftId);

			fail("gift still there even though admin removed it");

		} catch (RetrofitError e) {
			// Make sure we got a 404 not found
			assertEquals(404, e.getResponse().getStatus());
		}

		// try again, this time the user that creates it will remove it, which is allowed
		gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[ALT_USER]);
		gift = testUsers.get(ALT_USER).addGift(gift);
		giftId = gift.getId();
		
		try {
			// This should succeed because this user is the gift owner
			testUsers.get(ALT_USER).removeGiftById(giftId);

		} catch (RetrofitError e) {
			// Make sure we got a 403 forbidden
			fail("User that creates a gift is allowed to remove it!");
		}
	}

	@Test
	public void testTouchOnce() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);

		// Add the gift
		Gift g = testUsers.get(DEFAULT_USER).addGift(gift);

		// Like the gift
		testUsers.get(DEFAULT_USER).touchedByGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the like count is 1
		assertTrue(g.getTouchCount() == 1);

		// Unlike the gift
		testUsers.get(DEFAULT_USER).untouchedByGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the like count is 0
		assertTrue(g.getTouchCount() == 0);
	}

	@Test
	public void testTouchTwice() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);

		// Add the gift
		Gift g = testUsers.get(DEFAULT_USER).addGift(gift);

		// Like the gift
		testUsers.get(DEFAULT_USER).touchedByGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the like count is 1
		assertTrue(g.getTouchCount() == 1);

		try {
			// Like the gift again.
			testUsers.get(DEFAULT_USER).touchedByGift(g.getId());

			fail("The server let us like a gift twice without returning a 400");
		} catch (RetrofitError e) {
			// Make sure we got a 400 Bad Request
			assertEquals(400, e.getResponse().getStatus());
		}

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the like count is still 1
		assertTrue(g.getTouchCount() == 1);
	}

	@Test
	public void testTouchNonExistantGift() throws Exception {

		try {
			// Like the gift again.
			testUsers.get(DEFAULT_USER).touchedByGift(getInvalidGiftId());

			fail("The server let us like a gift that doesn't exist without returning a 404.");
		} catch (RetrofitError e) {
			// Make sure we got a 400 Bad Request
			assertEquals(404, e.getResponse().getStatus());
		}
	}

	@Test
	public void testTwoUsersTouchGift() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);

		// Add the gift
		Gift g = testUsers.get(DEFAULT_USER).addGift(gift);

		// Like the gift with 2 users
		testUsers.get(DEFAULT_USER).touchedByGift(g.getId());
		testUsers.get(ADMIN_USER).touchedByGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the like count is 2
		assertTrue(g.getTouchCount() == 2);

		// un Like the gift again.
		testUsers.get(DEFAULT_USER).untouchedByGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the like count is now back to 1
		assertTrue(g.getTouchCount() == 1);
	}

	@Test
	public void testSetInapproriateFlag() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);

		// Add the gift
		Gift g = testUsers.get(DEFAULT_USER).addGift(gift);

		// make it bad
		testUsers.get(DEFAULT_USER).inappropriateGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the it's still bad
		assertTrue(g.getUserInappropriateFlag());

		// Unlike the gift
		testUsers.get(DEFAULT_USER).notInappropriateGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the flag is unset
		assertTrue(!g.getUserInappropriateFlag());
	}

	@Test
	public void testSetObsceneFlag() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);

		// Add the gift
		Gift g = testUsers.get(DEFAULT_USER).addGift(gift);

		// make it bad
		testUsers.get(DEFAULT_USER).obsceneGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the it's still bad
		assertTrue(g.getUserObsceneFlag());

		// Unlike the gift
		testUsers.get(DEFAULT_USER).notObsceneGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the flag is unset
		assertTrue(!g.getUserObsceneFlag());
	}

	@Test
	public void testSetInapproriateFlagBySecondUser() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);

		// Add the gift
		Gift g = testUsers.get(DEFAULT_USER).addGift(gift);

		// 2nd user make it bad
		testUsers.get(ADMIN_USER).inappropriateGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the flag is unset for first user
		assertTrue(!g.getUserInappropriateFlag());
		
		// Get the gift again as 2nd user
		g = testUsers.get(ADMIN_USER).getGiftById(g.getId());
		
		// Make sure the it's still bad for 2nd user
		assertTrue(g.getUserInappropriateFlag());
	}

	@Test
	public void testSetObsceneFlagBySecondUser() throws Exception {
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);

		// Add the gift
		Gift g = testUsers.get(DEFAULT_USER).addGift(gift);

		// 2nd user make it bad
		testUsers.get(ADMIN_USER).obsceneGift(g.getId());

		// Get the gift again
		g = testUsers.get(DEFAULT_USER).getGiftById(g.getId());

		// Make sure the flag is unset for first user
		assertTrue(!g.getUserObsceneFlag());
		
		// Get the gift again as 2nd user
		g = testUsers.get(ADMIN_USER).getGiftById(g.getId());
		
		// Make sure the it's still bad for 2nd user
		assertTrue(g.getUserObsceneFlag());
	}

	@Test
	public void testFindByName() {

		// Create the names unique for testing.
		String[] names = new String[] { "The Cat", "The Spoon", "The Plate" };

		// Create three random gifts, but use the unique names
		ArrayList<Gift> gifts = new ArrayList<Gift>();

		for (int i = 0; i < names.length; ++i) {
			gifts.add(TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], names[i]));
		}

		// Add all the gifts to the server
		for (Gift g : gifts){
			testUsers.get(DEFAULT_USER).addGift(g);
		}

		// Search for "The Cat"
		PagedCollection<Gift> page = testUsers.get(DEFAULT_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), "the cat", -1L, ALL_ROWS_PAGE_IDX, ALL_ROWS_PAGE_SIZE);
		assertTrue(page.getDataList().size() > 0);

		// Make sure all the returned gifts have "The Cat" for their title
		for (Gift v : page.getDataList()) {
			assertTrue(v.getTitle().equals(names[0]));
		}
	}

	@Test
	public void testSetFilterContent() throws Exception {

		// Set filter on, for test make sure not set by another test that failed
		boolean isOn = testUsers.get(DEFAULT_USER).isUserFilterContent();
		if (isOn) {
			testUsers.get(DEFAULT_USER).unfilterContent();
		}
		
		testUsers.get(DEFAULT_USER).filterContent();

		try {
			// try to filter again
			testUsers.get(DEFAULT_USER).filterContent();

			fail("The server let us filter content twice without returning a 400");
		} catch (RetrofitError e) {
			// Make sure we got a 400 Bad Request
			assertEquals(400, e.getResponse().getStatus());
		}

		// unfilter 
		testUsers.get(DEFAULT_USER).unfilterContent();

		try {
			// try to unfilter again
			testUsers.get(DEFAULT_USER).unfilterContent();

			fail("The server let us unfilter content twice without returning a 400");
		} catch (RetrofitError e) {
			// Make sure we got a 400 Bad Request
			assertEquals(400, e.getResponse().getStatus());
		}

	}

	@Test
	public void testFiltering() throws Exception {

		// Set filter on, for test make sure not set by another test that failed
		try {
			testUsers.get(DEFAULT_USER).unfilterContent();
		} catch (Exception e) {
		}
		
		testUsers.get(DEFAULT_USER).filterContent();

		// Create the names unique for testing.
		String[] names = new String[] { "Good stuff", "Bad stuff", "Obscene stuff" };

		// Create three random gifts with another user, but use the unique names
		Gift[] gifts = new Gift[names.length];

		for (int i = 0; i < names.length; ++i) {
			gifts[i] = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[ADMIN_USER], names[i]);
		}

		// Add all the gifts to the server
		for (int i = 0; i < gifts.length; i++){
			gifts[i] = testUsers.get(ADMIN_USER).addGift(gifts[i]);
		}

		// filtering user sees all the gifts
		ArrayList<Gift> giftsList = new ArrayList<Gift>();
		for (Gift g : gifts) {
			giftsList.add(g);
		}
		assertTrue(
				testUsers.get(DEFAULT_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, ALL_ROWS_PAGE_IDX, ALL_ROWS_PAGE_SIZE)
					.getDataList()
					.containsAll(giftsList));

		// set flags to not see some of the data
		testUsers.get(ADMIN_USER).inappropriateGift(giftsList.get(1).getId()); // Bad stuff 
		testUsers.get(ADMIN_USER).obsceneGift(giftsList.get(2).getId()); // Obscene stuff 
		
		// filtering user sees only the good gift
		Collection<Gift> col = testUsers.get(DEFAULT_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, ALL_ROWS_PAGE_IDX, ALL_ROWS_PAGE_SIZE).getDataList();
		assertTrue(col.contains(giftsList.get(0)) && !col.contains(giftsList.get(1)) && !col.contains(giftsList.get(2)));

		// other user still sees all of them
		assertTrue(testUsers.get(ADMIN_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, ALL_ROWS_PAGE_IDX, ALL_ROWS_PAGE_SIZE).getDataList().containsAll(giftsList));
		
		// filtering user search by title doesn't return a bad one
		assertTrue(testUsers.get(DEFAULT_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), names[1], -1L, ALL_ROWS_PAGE_IDX, ALL_ROWS_PAGE_SIZE).getDataList().isEmpty());
		
		// other user still does
		Collection<Gift> tc = testUsers.get(ADMIN_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), names[1], -1L, ALL_ROWS_PAGE_IDX, ALL_ROWS_PAGE_SIZE).getDataList();
		assertTrue(tc.size() > 0);
		
		// unfilter 
		testUsers.get(DEFAULT_USER).unfilterContent();
	}

	@Test
	public void testAdminGetFlaggedGifts() throws Exception {

		// Create 6 names unique for testing.
		String[] names = new String[] { "Good stuff admin", "Bad stuff admin", "Obscene stuff admin"
				, "Good stuff2 admin", "Bad stuff2 admin", "Obscene stuff2 admin"  };

		// Create three random gifts with another user, but use the unique names
		Gift[] gifts = new Gift[names.length];

		for (int i = 0; i < names.length; ++i) {
			gifts[i] = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], names[i]);
			gifts[i] = testUsers.get(DEFAULT_USER).addGift(gifts[i]);
		}

		// set 1 as inapproriate and 2 as obscene
		int[] inapprops = new int[] { 2 };
		int[] obscens = new int[] { 1, 4 };
		// these are the non flagged ones
		int[] cleans = new int[] { 0, 3, 5 };

		for (int i = 0; i < inapprops.length; i++) {
			testUsers.get(ALT_USER).inappropriateGift(gifts[inapprops[i]].getId());
		}

		for (int i = 0; i < obscens.length; i++) {
			testUsers.get(ALT_USER).obsceneGift(gifts[obscens[i]].getId());
		}

		// try to get the flagged gifts as a normal user
		long PAGE_SIZE = 100;
		try {
			// This should fail because this user doesn't have the necessary privilege
			testUsers.get(DEFAULT_USER).findFlaggedGifts("", 0L, PAGE_SIZE );

			fail("Yikes, the security setup is horribly broken and didn't require the admin user!!");

		} catch (RetrofitError e) {
			// Make sure we got a 403 forbidden
			assertEquals(403, e.getResponse().getStatus());
		}
		
		// get the list as an admin user
		PagedCollection<Gift> flaggedGifts = testUsers.get(ADMIN_USER).findFlaggedGifts("", 0L, PAGE_SIZE);

		// there should not be inappropriate gifts in there
		for (int i = 0; i < inapprops.length; i++) {
			assertTrue(!flaggedGifts.getDataList().contains(gifts[inapprops[i]]));
		}
		// all the obscene ones should be
		for (int i = 0; i < obscens.length; i++) {
			assertTrue(flaggedGifts.getDataList().contains(gifts[obscens[i]]));
		}
		// and not the clean ones
		for (int i = 0; i < cleans.length; i++) {
			assertTrue(!flaggedGifts.getDataList().contains(gifts[cleans[i]]));
		}
	}
	
	@Test
	public void testUserNotice() throws Exception {
		
		clearAllRowsForTests();

		// clear first
		testUsers.get(DEFAULT_USER).removeNotices();
		
		// Create 6 names unique for testing.
		String[] names = new String[] { "un Good stuff admin", "un Bad stuff admin", "un Obscene stuff admin"
				, "un Good stuff2 admin", "un Bad stuff2 admin", "un Obscene stuff2 admin"  };

		Gift[] gifts = new Gift[names.length];

		for (int i = 0; i < names.length; ++i) {
			gifts[i] = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], names[i]);
			gifts[i] = testUsers.get(DEFAULT_USER).addGift(gifts[i]);
		}

		// set 1 as inapproriate and 2 as obscene
		int[] inapprops = new int[] { 2 };
		int[] obscens = new int[] { 1, 4 };
		// these are the touched ones
		int[] cleans = new int[] { 0, 3, 5 };

		for (int i = 0; i < inapprops.length; i++) {
			testUsers.get(ALT_USER).inappropriateGift(gifts[inapprops[i]].getId());
		}

		for (int i = 0; i < obscens.length; i++) {
			testUsers.get(ALT_USER).obsceneGift(gifts[obscens[i]].getId());
		}

		// get a touch on 3 cleans by 2 users = 6 touches
		for (PotlatchSvcApi user : new PotlatchSvcApi[] {testUsers.get(ALT_USER), testUsers.get(ALT_USER2) }) {
			for (int i = 0; i < cleans.length; i++) {
				user.touchedByGift(gifts[cleans[i]].getId());
			}
		}
		// untouch again by one = 5 touches
		testUsers.get(ALT_USER).untouchedByGift(gifts[cleans[0]].getId());
		
		// have admin user remove one of the obscene ones
		testUsers.get(ADMIN_USER).removeObsceneGiftById(gifts[obscens[0]].getId());
		
		// read the notice twice, to check it stays until removed
		for (int i = 0; i < 2; i++) {
			UserNotice notice = testUsers.get(DEFAULT_USER).getNotices();
	
			// totals should be:
			// 6 touches, 1 untouch = 5
			// 1 obscene (maybe 2, but one is removed)
			// 1 inapprop
			// 1 removed

			assertTrue(notice.getTouchCount() == 6);
			assertTrue(notice.getUnTouchCount() == 1);
			assertTrue(notice.getInappropriateCount() == 1 && notice.getUnInappropriateCount() == 0);
			assertTrue(notice.getObsceneCount() == 2 && notice.getUnObsceneCount() == 0);
			assertTrue(notice.getRemovedCount() == 1);
		}
		
		testUsers.get(DEFAULT_USER).removeNotices();
		
		// get the notice and all should be reset
		UserNotice notice = testUsers.get(DEFAULT_USER).getNotices();
		assertTrue(notice == null);
	}

	@Test
	public void supportAndroidTestingUserNotice() throws Exception {
		
		// get the user notice for normal user first (it should reset)
		testUsers.get(DEFAULT_USER).getNotices();
		
		long time = System.currentTimeMillis();
		
		// Create 6 names unique for testing.
		String[] names = new String[] { "Good stuff "+time, "Bad stuff "+time, "Obscene stuff "+time
				, "Good stuff2 "+time, "Bad stuff2 "+time, "Obscene stuff2 "+time  };

		Gift[] gifts = new Gift[names.length];

		for (int i = 0; i < names.length; ++i) {
			gifts[i] = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);
			gifts[i] = testUsers.get(DEFAULT_USER).addGift(gifts[i]);
		}

		// set 1 as inapproriate and 2 as obscene
		int[] inapprops = new int[] { 2 };
		int[] obscens = new int[] { 1, 4 };
		// these are the touched ones
		int[] cleans = new int[] { 0, 3, 5 };

		for (int i = 0; i < inapprops.length; i++) {
			testUsers.get(ALT_USER).inappropriateGift(gifts[inapprops[i]].getId());
		}

		for (int i = 0; i < obscens.length; i++) {
			testUsers.get(ALT_USER).obsceneGift(gifts[obscens[i]].getId());
		}

		// get a touch on 3 cleans by 2 users = 6 touches
		for (PotlatchSvcApi user : new PotlatchSvcApi[] {testUsers.get(ALT_USER), testUsers.get(ALT_USER2) }) {
			for (int i = 0; i < cleans.length; i++) {
				user.touchedByGift(gifts[cleans[i]].getId());
			}
		}
		// untouch again by one = 5 touches
		testUsers.get(ALT_USER).untouchedByGift(gifts[cleans[0]].getId());
		
		// have admin user remove one of the obscene ones
		testUsers.get(ADMIN_USER).removeObsceneGiftById(gifts[obscens[0]].getId());
		
	}

	@Test
	public void testPagingGiftResults() throws Exception {
		final int ROWS = 25;
		final long PAGE_SIZE = 10;
		final int PAGES_LEN = (int) Math.ceil(ROWS / (float)PAGE_SIZE);
		assertTrue(PAGES_LEN == 3);

		clearAllRowsForTests();
		
		ArrayList<Gift> gifts = addRandomGifts(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], testUsers.get(DEFAULT_USER), ROWS);
		Collection<Gift> first5 = gifts.subList(0, 4);
		
		// test getting a page at a time returns the correct size of pages
		// ie. page size until a partial page, and then the last one is % page size
		for (int i = 0; i < PAGES_LEN; i++) {
			PagedCollection<Gift> page = testUsers.get(DEFAULT_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, i, PAGE_SIZE);
			Collection<Gift> pagedGifts = page.getDataList();
			
			if (i == PAGES_LEN - 1) { // last set
				assertTrue(pagedGifts.size() == ROWS % PAGE_SIZE);
				assertTrue(pagedGifts.containsAll(first5));
				
				// test pagination data
				assertTrue(!page.getIsFirstPage());
				assertTrue(page.getIsLastPage());
				assertTrue(page.getPageNum() == PAGES_LEN - 1);
				assertTrue(page.getPageSize() == pagedGifts.size());
				assertTrue(page.getTotalPages() == PAGES_LEN);
				assertTrue(page.getTotalSize() == ROWS);				
			}
			else {
				assertTrue(pagedGifts.size() == PAGE_SIZE);
				
				// test pagination data
				assertTrue((i == 0) == page.getIsFirstPage());
				assertTrue(!page.getIsLastPage());
				assertTrue(page.getPageNum() == i);
				assertTrue(page.getPageSize() == pagedGifts.size());
				assertTrue(page.getTotalPages() == PAGES_LEN);
				assertTrue(page.getTotalSize() == ROWS);				
			}
		}
		
	}

	/**
	 * Tests all the types of searches for the various gift types, they all call the same
	 * method but with different giftSectionType, then title/giftChainId are optional 
	 * @throws Exception
	 */
	@Test
	public void supportGiftTypeSearches() throws Exception {
		// paging may skew results, so work with a big number
		final long PAGE_SIZE = 100;
		final int TEST_ROWS = 30;
		final int TITLE_SUB_LIST_ROWS = TEST_ROWS / 3;
		final int FLAG_SUB_LIST_ROWS = TEST_ROWS / 10;
		final int CHAIN_ROWS = 5, CHAIN_TITLE_ROWS = 2, CHAIN_FLAG_ROWS = 1;
		
		// titles that will match sub sets
		final String TITLE_ONE = "random title one", TITLE_TWO = "pseudo rdm title two", TITLE_MATCHES_ALL = "M Title", TITLE_MATCHES_HALF = "dm title";
		
		// 4 users are used for the tests, the default for most stuff, another normal user for my gifts
		// one more that's always filtering and admin user for the flagged obscene test
		// when filter tests are used, use the filter content user unless need more refined testing
		// in which case, has filter set/unset as needed

		final int MAIN_USR = DEFAULT_USER, ANOTHER = ALT_USER, FILTERER = ALT_USER2;
		final PotlatchSvcApi mainUsr = testUsers.get(MAIN_USR), anotherUsr = testUsers.get(ANOTHER),
				filterUsr = testUsers.get(FILTERER), adminUsr = testUsers.get(ADMIN_USER);

		// for multi runs, these can cause bad request if already set/unset, so wrap and don't worry about it
		try { mainUsr.unfilterContent(); } catch (Exception e) {}
		try { anotherUsr.unfilterContent(); } catch (Exception e) {}
		try { filterUsr.filterContent(); } catch (Exception e) {}
		try { adminUsr.unfilterContent(); } catch (Exception e) {}
		
		clearAllRowsForTests();
		

		// get a bunch of rows with some specific and some random titles for main user
		final ArrayList<Gift> mainUsrGifts = addRandomGifts(PotlatchConstants.TEST_USER_NAMES[MAIN_USR], mainUsr, TEST_ROWS,
				TITLE_ONE, TITLE_SUB_LIST_ROWS); // 10 of them have the title + i
		// another bunch for 2nd user
		final ArrayList<Gift> anotherUsrGifts = addRandomGifts(PotlatchConstants.TEST_USER_NAMES[ANOTHER], anotherUsr, TEST_ROWS,
				TITLE_TWO, TITLE_SUB_LIST_ROWS); // 10 of them have the title + i);
		
		// need some inappropriate, some obscene, take 3 from each end of both sets for inapprop (that way have some titles that match, some not) 
		// make inappropriate sub-list and flag them
		final ArrayList<Gift> inapproGifts = new ArrayList<Gift>();
		safeAddAll(inapproGifts, mainUsrGifts.subList(0, FLAG_SUB_LIST_ROWS));
		safeAddAll(inapproGifts, mainUsrGifts.subList(mainUsrGifts.size() - FLAG_SUB_LIST_ROWS, mainUsrGifts.size()));
		for (Gift g : inapproGifts) adminUsr.inappropriateGift(g.getId()); 

		// same for obscene, choose another set though
		final Collection<Gift> obsceneGifts = new ArrayList<Gift>();
		safeAddAll(obsceneGifts, mainUsrGifts.subList(FLAG_SUB_LIST_ROWS, FLAG_SUB_LIST_ROWS * 2));
		safeAddAll(obsceneGifts, mainUsrGifts.subList(mainUsrGifts.size() - FLAG_SUB_LIST_ROWS * 2, mainUsrGifts.size() - FLAG_SUB_LIST_ROWS));
		for (Gift g : obsceneGifts) filterUsr.obsceneGift(g.getId()); // note not same user flags it, shouldn't matter but anyway
		
		// need some gifts that are in a single chain, some flagged, some not, some with test titles
		final long chainId = mainUsrGifts.get(0).getGiftChainId();
		final ArrayList<Gift> anotherUsrGiftChain = addRandomGifts(PotlatchConstants.TEST_USER_NAMES[ANOTHER], anotherUsr, CHAIN_ROWS,
				TITLE_TWO, CHAIN_TITLE_ROWS, chainId); // 2 of them have the title + i
		final Collection<Gift> inapproChain = anotherUsrGiftChain.subList(0, CHAIN_FLAG_ROWS);
		for (Gift g : inapproChain) adminUsr.inappropriateGift(g.getId()); // only 1 for now
		
		//------------------------ all gifts tests
		
		// using another user, find all
		final int TOTAL_ROWS = mainUsrGifts.size() + anotherUsrGifts.size() + anotherUsrGiftChain.size();
		PagedCollection<Gift> page = anotherUsr.getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, 0L, PAGE_SIZE);
		
		System.out.println("TOTAL_ROWS="+TOTAL_ROWS+" :");
		assertTrue(getGiftsCount(page) == TOTAL_ROWS);

		// title search (all matches)
		final int ALL_TITLE_MATCH_ROWS = TITLE_SUB_LIST_ROWS * 2 + CHAIN_TITLE_ROWS;
		page = anotherUsr.getGifts(GiftsSectionType.ALL_GIFTS.name(), TITLE_MATCHES_ALL, -1L, 0L, PAGE_SIZE);
		
		System.out.println("ALL_TITLE_MATCH_ROWS="+ALL_TITLE_MATCH_ROWS+" :");
		assertTrue(page.getDataList().size() == ALL_TITLE_MATCH_ROWS);
		
		// title search (some matches)
		final int HALF_TITLE_MATCH_ROWS = TITLE_SUB_LIST_ROWS + CHAIN_TITLE_ROWS;
		page = anotherUsr.getGifts(GiftsSectionType.ALL_GIFTS.name(), TITLE_MATCHES_HALF, -1L, 0L, PAGE_SIZE);
		
		System.out.println("HALF_TITLE_MATCH_ROWS="+HALF_TITLE_MATCH_ROWS+" :");
		assertTrue(page.getDataList().size() == HALF_TITLE_MATCH_ROWS);
		
		// filter user, same tests should not include filtered content
		final int TOTAL_NOT_FLAGGED = TOTAL_ROWS - (inapproGifts.size() + obsceneGifts.size() + inapproChain.size());
		page = filterUsr.getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, 0L, PAGE_SIZE);
		
		System.out.println("TOTAL_NOT_FLAGGED="+TOTAL_NOT_FLAGGED+" :");
		assertTrue(page.getDataList().size() == TOTAL_NOT_FLAGGED);
		
		// filter user, title search (all matches)
		final int ALL_TITLE_MATCH_NOT_FLAGGED_ROWS = ALL_TITLE_MATCH_ROWS - ((inapproGifts.size() + obsceneGifts.size()) / 2 + CHAIN_FLAG_ROWS);
		page = filterUsr.getGifts(GiftsSectionType.ALL_GIFTS.name(), TITLE_MATCHES_ALL, -1L, 0L, PAGE_SIZE);
		
		System.out.println("ALL_TITLE_MATCH_NOT_FLAGGED_ROWS="+ALL_TITLE_MATCH_NOT_FLAGGED_ROWS+" :");
		assertTrue(page.getDataList().size() == ALL_TITLE_MATCH_NOT_FLAGGED_ROWS);
		
		//------------------------ my gifts tests
		
		// using another user, find all
		final int MY_ROWS = mainUsrGifts.size();
		page = mainUsr.getGifts(GiftsSectionType.MY_GIFTS.name(), "", -1L, 0L, PAGE_SIZE);
		
		System.out.println("MY_ROWS="+MY_ROWS+" :");
		assertTrue(page.getDataList().size() == MY_ROWS);
		
		// title search (all matches)
		final int MY_TITLE_MATCH_ROWS = TITLE_SUB_LIST_ROWS;
		page = mainUsr.getGifts(GiftsSectionType.MY_GIFTS.name(), TITLE_MATCHES_ALL, -1L, 0L, PAGE_SIZE);
		
		System.out.println("MY_TITLE_MATCH_ROWS="+MY_TITLE_MATCH_ROWS+" :");
		assertTrue(page.getDataList().size() == MY_TITLE_MATCH_ROWS);
		
		// title search (some matches)
		final int MY_HALF_TITLE_MATCH_ROWS = 0;
		page = mainUsr.getGifts(GiftsSectionType.MY_GIFTS.name(), TITLE_MATCHES_HALF, -1L, 0L, PAGE_SIZE);
		
		System.out.println("MY_HALF_TITLE_MATCH_ROWS="+MY_HALF_TITLE_MATCH_ROWS+" :");
		assertTrue(page.getDataList().size() == MY_HALF_TITLE_MATCH_ROWS);
		
		// filter tests, change user
		mainUsr.filterContent();
		
		// filter user, same tests should not include filtered content
		final int MY_NOT_FLAGGED = MY_ROWS - (inapproGifts.size() + obsceneGifts.size());
		page = mainUsr.getGifts(GiftsSectionType.MY_GIFTS.name(), "", -1L, 0L, PAGE_SIZE);
		
		System.out.println("MY_NOT_FLAGGED="+MY_NOT_FLAGGED+" :");
		assertTrue(page.getDataList().size() == MY_NOT_FLAGGED);
		
		// filter user, title search (all matches)
		final int MY_TITLE_MATCH_NOT_FLAGGED_ROWS = MY_TITLE_MATCH_ROWS - ((inapproGifts.size() + obsceneGifts.size()) / 2);
		page = mainUsr.getGifts(GiftsSectionType.MY_GIFTS.name(), TITLE_MATCHES_ALL, -1L, 0L, PAGE_SIZE);
		
		System.out.println("MY_TITLE_MATCH_NOT_FLAGGED_ROWS="+MY_TITLE_MATCH_NOT_FLAGGED_ROWS+" :");
		assertTrue(page.getDataList().size() == MY_TITLE_MATCH_NOT_FLAGGED_ROWS);
		
		// filter tests complete for my gifts, change user
		mainUsr.unfilterContent();


		//------------------------ all gift chains
		
		// using another user, find all
		final int CHAINS_ROWS = mainUsrGifts.size() + anotherUsrGifts.size();
		page = anotherUsr.getGifts(GiftsSectionType.ALL_GIFT_CHAINS.name(), "", -1L, 0L, PAGE_SIZE);
		
		System.out.println("CHAINS_ROWS="+CHAINS_ROWS+" :");
		assertTrue(getGiftsCount(page) == CHAINS_ROWS);
		
		// title search (all matches)
		final int CHAINS_TITLE_MATCH_ROWS = TITLE_SUB_LIST_ROWS * 2;
		page = anotherUsr.getGifts(GiftsSectionType.ALL_GIFT_CHAINS.name(), TITLE_MATCHES_ALL, -1L, 0L, PAGE_SIZE);
		
		System.out.println("CHAINS_TITLE_MATCH_ROWS="+CHAINS_TITLE_MATCH_ROWS+" :");
		assertTrue(page.getDataList().size() == CHAINS_TITLE_MATCH_ROWS);
		
		// filter user, same tests should not include filtered content
		final int CHAINS_NOT_FLAGGED = CHAINS_ROWS - (inapproGifts.size() + obsceneGifts.size());
		page = filterUsr.getGifts(GiftsSectionType.ALL_GIFT_CHAINS.name(), "", -1L, 0L, PAGE_SIZE);
		
		System.out.println("CHAINS_NOT_FLAGGED="+CHAINS_NOT_FLAGGED+" :");
		assertTrue(page.getDataList().size() == CHAINS_NOT_FLAGGED);
		
		// filter user, title search (all matches)
		final int CHAINS_TITLE_MATCH_NOT_FLAGGED_ROWS = CHAINS_TITLE_MATCH_ROWS - ((inapproGifts.size() + obsceneGifts.size()) / 2);
		page = filterUsr.getGifts(GiftsSectionType.ALL_GIFT_CHAINS.name(), TITLE_MATCHES_ALL, -1L, 0L, PAGE_SIZE);
		
		System.out.println("CHAINS_TITLE_MATCH_NOT_FLAGGED_ROWS="+CHAINS_TITLE_MATCH_NOT_FLAGGED_ROWS+" :");
		assertTrue(page.getDataList().size() == CHAINS_TITLE_MATCH_NOT_FLAGGED_ROWS);
		
		//------------------------ obscene (admin only)

		// using another user, find all
		final int OBSCENE_ROWS = obsceneGifts.size();
		page = adminUsr.findFlaggedGifts("", 0L, PAGE_SIZE);
		
		System.out.println("OBSCENE_ROWS="+OBSCENE_ROWS+" :");
		assertTrue(page.getDataList().size() == OBSCENE_ROWS);
		
		// title search (all matches)
		final int OBSCENE_TITLE_MATCH_ROWS = OBSCENE_ROWS / 2;
		page = adminUsr.findFlaggedGifts(TITLE_MATCHES_ALL, 0L, PAGE_SIZE);
		
		System.out.println("OBSCENE_TITLE_MATCH_ROWS="+OBSCENE_TITLE_MATCH_ROWS+" :");
		assertTrue(page.getDataList().size() == OBSCENE_TITLE_MATCH_ROWS);
		
		
		//getGiftsCount(page)
	}

	private void safeAddAll(Collection<Gift> dest, Collection<Gift> src) {
		for (Gift g : src) {
			dest.add(g);
		}
	}
	
	private void clearAllRowsForTests() {
		PagedCollection<Gift> page = testUsers.get(DEFAULT_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, 0L, 1000);		
		ArrayList<Gift> list = (ArrayList<Gift>) page.getDataList();

		for (int i = 0; i < list.size(); i++) {
			Gift g = list.get(i);
			PotlatchSvcApi creator = findUserApi(g);
			if (creator != null) {
				creator.removeGiftById(g.getId());
			}
			else {
				testUsers.get(ADMIN_USER).removeObsceneGiftById(g.getId());
			}
		}
	}

	@Test
	public void supportSetFilterFlags() throws Exception {
		Gift g = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], "inapp defuser only");
		g = testUsers.get(DEFAULT_USER).addGift(g);
		testUsers.get(DEFAULT_USER).inappropriateGift(g.getId());

		g = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[ALT_USER], "obsc alt user only");
		g = testUsers.get(ALT_USER).addGift(g);
		testUsers.get(ALT_USER).obsceneGift(g.getId());

		g = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], "obsc defuser only");
		g = testUsers.get(DEFAULT_USER).addGift(g);
		testUsers.get(DEFAULT_USER).obsceneGift(g.getId());

		g = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[ALT_USER], "iappr alt user only");
		g = testUsers.get(ALT_USER).addGift(g);
		testUsers.get(ALT_USER).inappropriateGift(g.getId());

		g = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], "obsc both users only");
		g = testUsers.get(DEFAULT_USER).addGift(g);
		testUsers.get(DEFAULT_USER).obsceneGift(g.getId());
		testUsers.get(ALT_USER).obsceneGift(g.getId());

		g = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[ALT_USER], "iappr both users only");
		g = testUsers.get(ALT_USER).addGift(g);
		testUsers.get(DEFAULT_USER).inappropriateGift(g.getId());
		testUsers.get(ALT_USER).inappropriateGift(g.getId());

		g = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], "obsc AND ianp everyone");
		g = testUsers.get(DEFAULT_USER).addGift(g);
		testUsers.get(DEFAULT_USER).obsceneGift(g.getId());
		testUsers.get(ALT_USER).obsceneGift(g.getId());
		testUsers.get(DEFAULT_USER).inappropriateGift(g.getId());
		testUsers.get(ALT_USER).inappropriateGift(g.getId());

	}
	
	private PotlatchSvcApi findUserApi(Gift g) {
		for (int i = 0; i < PotlatchConstants.TEST_USER_NAMES.length; i++) {
			if (g.getUserName().equals(PotlatchConstants.TEST_USER_NAMES[i])) {
				return testUsers.get(i);
			}
		}
		
		return null;
	}

	private int getGiftsCount(PagedCollection<Gift> page) {
		ArrayList<Gift> list = (ArrayList<Gift>) page.getDataList();
		int count = 0;
		for (int i = 0; i < list.size(); i++) {
			Gift g = list.get(i);
			System.out.println("\ti="+i+" "+g);
			count++;
		}
		return count;
	}
	
	
	@Test
	public void testUserInterval() throws Exception {
		testUsers.get(DEFAULT_USER).setUpdateInterval(5000 * 60); // set to one min
		long updateInterval = testUsers.get(DEFAULT_USER).getUpdateInterval();
		// default is 5 min
		assertTrue(updateInterval == 5000 * 60);
		
		testUsers.get(DEFAULT_USER).setUpdateInterval(1000 * 60); // set to one min

		updateInterval = testUsers.get(DEFAULT_USER).getUpdateInterval();
		// test has changed
		assertTrue(updateInterval == 1000 * 60);
	}
	
/*	@Test
	public void testTopGivers() throws Exception {

		// in case other tests are run before this or multiple times, make sure there's a dominance
		// for the alt users to score in this test
		ArrayList<Gift> defUserGifts = addRandomGifts(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER], testUsers.get(DEFAULT_USER), 5);
		ArrayList<Gift> altUserGifts = addRandomGifts(PotlatchConstants.TEST_USER_NAMES[ALT_USER], testUsers.get(ALT_USER), 40);
		ArrayList<Gift> altUser2Gifts = addRandomGifts(PotlatchConstants.TEST_USER_NAMES[ALT_USER2], testUsers.get(ALT_USER2), 40);
		
		// each list is liked by an array of users
		touchGifts(defUserGifts, testUsers.get(ALT_USER)); // small list just touched by one user
		touchGifts(altUserGifts, testUsers.get(DEFAULT_USER)); // large list just touched by one user
		touchGifts(altUser2Gifts, testUsers.get(DEFAULT_USER), testUsers.get(ALT_USER), testUsers.get(ADMIN_USER)); // large list touched by many users
		
		// get the list of top givers
		Collection<PotlatchUser> topGivers = testUsers.get(DEFAULT_USER).getTopGivers();
		PotlatchUser[] tga = new PotlatchUser[topGivers.size()];
		topGivers.toArray(tga);
		
		assertTrue(tga.length > 2);
		assertTrue(tga[0].getName().equals(PotlatchConstants.TEST_USER_NAMES[ALT_USER2]));
		assertTrue(tga[0].getTouchCount() >= 120); // run once will produce 120 touches
		assertTrue(tga[1].getName().equals(PotlatchConstants.TEST_USER_NAMES[ALT_USER]));
	}
*/
//	private void touchGifts(ArrayList<Gift> gifts, PotlatchSvcApi ... users) {
//		for (Gift g : gifts) {
//			for (PotlatchSvcApi u : users) {
//				u.touchedByGift(g.getId());
//			}
//		}
//	}

	private ArrayList<Gift> addRandomGifts(String userName, PotlatchSvcApi userSvc, int count) {
		ArrayList<Gift> gifts = new ArrayList<Gift>();
		for (int i = 0; i < count; i++) {
			Gift g = TestData.randomGift(userName);
			g = userSvc.addGift(g);
			gifts.add(g);
		}
		return gifts;
	}

	// like the previous method but the first so many (in title count) don't have a random name
	private ArrayList<Gift> addRandomGifts(String userName, PotlatchSvcApi userSvc, int count,
			String title, int titleCount) {
		ArrayList<Gift> gifts = new ArrayList<Gift>();
		for (int i = 0; i < count; i++) {
			Gift g = i < titleCount ? TestData.randomGift(userName, title+i) : TestData.randomGift(userName);
			g = userSvc.addGift(g);
			gifts.add(g);
		}
		return gifts;
	}

	// like the previous method add ed chain id
	private ArrayList<Gift> addRandomGifts(String userName, PotlatchSvcApi userSvc, int count,
			String title, int titleCount, long chainId) {
		ArrayList<Gift> gifts = new ArrayList<Gift>();
		for (int i = 0; i < count; i++) {
			Gift g = i < titleCount ? TestData.randomGift(userName, title+i) : TestData.randomGift(userName);
			g.setGiftChainId(chainId);
			g = userSvc.addGift(g);
			gifts.add(g);
		}
		return gifts;
	}

	private long getInvalidGiftId() {
		Set<Long> ids = new HashSet<Long>();
		Collection<Gift> stored = testUsers.get(DEFAULT_USER).getGifts(GiftsSectionType.ALL_GIFTS.name(), "", -1L, ALL_ROWS_PAGE_IDX, ALL_ROWS_PAGE_SIZE).getDataList();
		for (Gift v : stored) {
			ids.add(v.getId());
		}

		long nonExistantId = Long.MIN_VALUE;
		while (ids.contains(nonExistantId)) {
			nonExistantId++;
		}
		return nonExistantId;
	}

}
