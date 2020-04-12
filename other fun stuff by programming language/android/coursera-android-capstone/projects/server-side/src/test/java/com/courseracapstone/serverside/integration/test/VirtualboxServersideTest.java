package com.courseracapstone.serverside.integration.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import retrofit.ErrorHandler;
import retrofit.RetrofitError;
import retrofit.client.ApacheClient;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

import com.courseracapstone.common.PotlatchConstants;
import com.courseracapstone.serverside.client.PotlatchSvcApi;
import com.courseracapstone.serverside.repository.Gift;
import com.courseracapstone.serversidetest.gift.TestData;

/**
 * A test for the gift service on virtual box
 * 
 * @author xxx xxx
 */
public class VirtualboxServersideTest {

	@SuppressWarnings("unused")
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

	private final String TEST_URL = "https://localhost:8888";

	
	private final int ADMIN_USER = PotlatchConstants.ADMIN_TEST_USER_IDX;
	private int DEFAULT_USER = -1, ALT_USER = -1, ALT_USER2 = -1;
	private final String CLIENT_ID = "mobile", ADMIN_CLIEND_ID = "mobileAdmin";
	
	private final ArrayList<PotlatchSvcApi> testUsers;

	public VirtualboxServersideTest() {
		testUsers = new ArrayList<PotlatchSvcApi>();
		
		for (int i = 0; i < PotlatchConstants.TEST_USER_NAMES.length; i++) {
			if (i == PotlatchConstants.ADMIN_TEST_USER_IDX) {
				testUsers.add(new SecuredRestBuilder()
					.setClient(new ApacheClient(UnsafeHttpsClient.createUnsafeClient()))
					.setEndpoint(TEST_URL)
					.setLoginEndpoint(TEST_URL + PotlatchConstants.TOKEN_PATH)
					.setUsername(PotlatchConstants.TEST_USER_NAMES[i]).setPassword(PotlatchConstants.TEST_USER_PWDS[i]).setClientId(ADMIN_CLIEND_ID)
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
					.setUsername(PotlatchConstants.TEST_USER_NAMES[i]).setPassword(PotlatchConstants.TEST_USER_PWDS[i]).setClientId(CLIENT_ID)
					.build().create(PotlatchSvcApi.class));
			}
		}
	}

	private static final File jpgFile = new File("src/test/resources/pic0.jpg");
	private static final File pngFile = new File("src/test/resources/pic1.png");
	private static final File gifFile = new File("src/test/resources/pic2.gif");
	private static final String jpgMime = "image/jpeg", jpgFormat = "jpg";
	private static final String gifMime = "image/gif", gifFormat = "gif";
	private static final String pngMime = "image/png", pngFormat = "png";

	@Test
	public void testAddGetGiftImages() throws Exception {
		// create a gift, add it and then send the image
		Gift gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);
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
		gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);
		gift = testUsers.get(DEFAULT_USER).addGift(gift);
		gift = testUsers.get(DEFAULT_USER).setGiftImageById(gift.getId(), pngFormat, new TypedFile(pngMime, pngFile));
		response = testUsers.get(DEFAULT_USER).getGiftImageData(gift.getId());		
		imageData = response.getBody().in();
		originalFile = IOUtils.toByteArray(new FileInputStream(pngFile));
		retrievedFile = IOUtils.toByteArray(imageData);
		assertTrue(Arrays.equals(originalFile, retrievedFile));
		
		// gif file
		gift = TestData.randomGift(PotlatchConstants.TEST_USER_NAMES[DEFAULT_USER]);
		gift = testUsers.get(DEFAULT_USER).addGift(gift);
		gift = testUsers.get(DEFAULT_USER).setGiftImageById(gift.getId(), gifFormat, new TypedFile(gifMime, gifFile));
		response = testUsers.get(DEFAULT_USER).getGiftImageData(gift.getId());		
		imageData = response.getBody().in();
		originalFile = IOUtils.toByteArray(new FileInputStream(gifFile));
		retrievedFile = IOUtils.toByteArray(imageData);
		assertTrue(Arrays.equals(originalFile, retrievedFile));
		
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
		testUsers.get(ADMIN_USER).removeGiftById(gifts[obscens[0]].getId());
		
	}

}
