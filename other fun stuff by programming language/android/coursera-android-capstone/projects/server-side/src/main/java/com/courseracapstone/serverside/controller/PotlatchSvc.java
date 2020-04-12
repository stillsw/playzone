package com.courseracapstone.serverside.controller;

import static com.courseracapstone.common.PotlatchConstants.CHAIN_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.CLIENT_ID_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.DATA_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.FORMAT_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.GIFTS_LIST_TYPE_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.GIFT_CHAIN_ORDERING_UNDER_THE_TOP;
import static com.courseracapstone.common.PotlatchConstants.GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT;
import static com.courseracapstone.common.PotlatchConstants.GIFT_RESPONSE_TYPE_OBSCENE_CONTENT;
import static com.courseracapstone.common.PotlatchConstants.GIFT_RESPONSE_TYPE_TOUCHED_BY;
import static com.courseracapstone.common.PotlatchConstants.ID_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.PAGE_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.PAGE_SIZE_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.PASSWORD_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_DATA_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_FILTER_CONTENT_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_GET_INTERVAL_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_GIFTS_ADMIN_TYPE_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_GIFTS_USER_TYPES_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_ID_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_INAPPROPRIATE_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_NOT_INAPPROPRIATE_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_NOT_OBSCENE_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_OBSCENE_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_REMOVE_GIFT_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_REMOVE_OWN_GIFT_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_SET_DATA_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_SIGN_UP_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_SVC_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_THUMBNAIL_DATA_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_TOP_GIVERS_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_TOUCH_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_UNFILTER_CONTENT_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_UNTOUCH_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_UPDATE_INTERVAL_PATH;
import static com.courseracapstone.common.PotlatchConstants.POTLATCH_USER_NOTICES_PATH;
import static com.courseracapstone.common.PotlatchConstants.TITLE_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.USER_NAME_PARAMETER;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import com.courseracapstone.common.GiftsSectionType;
import com.courseracapstone.common.PagedCollection;
import com.courseracapstone.common.PotlatchConstants;
import com.courseracapstone.serverside.auth.MyInMemoryUserDetailsManager;
import com.courseracapstone.serverside.auth.User;
import com.courseracapstone.serverside.repository.Gift;
import com.courseracapstone.serverside.repository.GiftRepository;
import com.courseracapstone.serverside.repository.PotlatchUser;
import com.courseracapstone.serverside.repository.PotlatchUsersRepository;
import com.courseracapstone.serverside.repository.UserGiftResponse;
import com.courseracapstone.serverside.repository.UserGiftResponseRepository;
import com.courseracapstone.serverside.repository.UserNotice;
import com.courseracapstone.serverside.repository.UserNoticesRepository;

/**
 * This simple PotlatchSvc allows clients to send HTTP POST requests with gifts
 * that are stored in memory using a list. Clients can send HTTP GET requests to
 * receive a JSON listing of the gifts that have been sent to the controller so
 * far. Stopping the controller will cause it to lose the history of gifts that
 * have been sent to it because they are stored in memory.
 * 
 * The user sign up method just needs basic auth, all the rest use oauth2
 * 
 * @author xxx xxx
 * 
 */

// Tell Spring that this class is a Controller that should
// handle certain HTTP requests for the DispatcherServlet
@Controller
public class PotlatchSvc {

	// exceptions thrown to produce the required error codes
	@SuppressWarnings("serial")
	@ResponseStatus(value = HttpStatus.NOT_FOUND)
	public static class GiftNotFoundException extends RuntimeException {
		public GiftNotFoundException(long giftId) {
			super("Gift with id " + giftId + " not found.");
		}
	}

	@SuppressWarnings("serial")
	@ResponseStatus(value = HttpStatus.NOT_FOUND)
	public static class ResourceNotFoundException extends RuntimeException {
		public ResourceNotFoundException(Exception e) {
			super(e.getMessage());
		}
	}

	@SuppressWarnings("serial")
	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	public static class RepeatedActionException extends RuntimeException {
		public RepeatedActionException() {
			super("Action already completed by this user.");
		}
	}

	@SuppressWarnings("serial")
	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	public static class NoPreviousActionException extends RuntimeException {
		public NoPreviousActionException() {
			super("Action not previously completed by this user.");
		}
	}

	@SuppressWarnings("serial")
	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	public static class InvalidParamException extends RuntimeException {
		public InvalidParamException(String param, String value) {
			super("parameter "+param+" is invalid ("+value+")");
		}
	}

	@SuppressWarnings("serial")
	@ResponseStatus(value = HttpStatus.UNAUTHORIZED)
	public static class NoPermissionException extends RuntimeException {
		public NoPermissionException() {
			super("requested action is not allowed for this user");
		}
	}

	private static Sort TOP_GIVERS_SORT = new Sort(
			new Sort.Order(Sort.Direction.DESC, "touchCount"),
			new Sort.Order(Sort.Direction.ASC, "name"));

	private static Sort NORMAL_GIFTS_SORT = new Sort(
			new Sort.Order(Sort.Direction.DESC, "id")); // matches the android sort rather than user created time

	private static Sort ONE_CHAIN_GIFTS_SORT = new Sort(
			new Sort.Order(Sort.Direction.ASC, "forceOrderForChainTop"), // chainTop at the top, then id order
			new Sort.Order(Sort.Direction.DESC, "id")); 

	// the autowired repositories
	@Autowired
	private GiftRepository gifts;
	@Autowired
	private UserGiftResponseRepository userGiftResponses;
	@Autowired
	private PotlatchUsersRepository users;
	@Autowired
	private UserNoticesRepository userNotices;

	/**
	 * Just needs basic authentication, this is how the user signs up
	 * 
	 * @param username
	 * @param password
	 * @param clientId
	 * @return
	 */
	@RequestMapping(value = POTLATCH_SIGN_UP_PATH, method = RequestMethod.POST) 
	public @ResponseBody Void createUser(
			@RequestParam(USER_NAME_PARAMETER) String username,
			@RequestParam(PASSWORD_PARAMETER) String password,
			@RequestParam(CLIENT_ID_PARAMETER) String clientId) {
		
		MyInMemoryUserDetailsManager userMgr = MyInMemoryUserDetailsManager.getInstance();
		if (userMgr.userExists(username)) {
			throw new RepeatedActionException();
		}
		
		if (PotlatchConstants.ADMIN_CLIENT_ID.equals(clientId)) {
			userMgr.appCreateUser(User.create(username, password, "ADMIN", "USER"));
		}
		else {
			userMgr.appCreateUser(User.create(username, password, "USER"));
		}
		
		return null;
	}
	
	/**
	 * quite a complex set of possible gifts, each by the 3 types that the parameter
	 * contains, then also title search and filtered or not
	 * 
	 * @param giftsListType
	 * @param title
	 * @param chainId
	 * @param pageIdx
	 * @param pageSize
	 * @param principal
	 * @return
	 */
	@RequestMapping(value = POTLATCH_GIFTS_USER_TYPES_PATH, method = RequestMethod.GET)
	public @ResponseBody PagedCollection<Gift> getGifts(
			@RequestParam(GIFTS_LIST_TYPE_PARAMETER) String giftsListType,
			@RequestParam(TITLE_PARAMETER) String title,
			@RequestParam(CHAIN_PARAMETER) long chainId,
			@PathVariable(PAGE_PARAMETER) long pageIdx,
			@PathVariable(PAGE_SIZE_PARAMETER) long pageSize,
			Principal principal) {

		GiftsSectionType type = GiftsSectionType.valueOf(giftsListType);
		if (type == null) {
			throw new InvalidParamException(GIFTS_LIST_TYPE_PARAMETER, giftsListType);
		}
		
		boolean isFiltered = getUser(principal.getName()).isFilterContent();
		boolean hasTitle = title != null && !title.isEmpty();
		Pageable page = new PageRequest((int)pageIdx, (int)pageSize,
				type == GiftsSectionType.ONE_CHAIN
					? ONE_CHAIN_GIFTS_SORT : NORMAL_GIFTS_SORT);

		Page<Gift> giftsPage = null;		
		switch (type) {
		case ALL_GIFTS : {
			giftsPage = 
				isFiltered 
					? hasTitle
						// filtered and title
						? gifts.findByTitleContainingIgnoreCaseAndInappropriateCountIsAndObsceneCountIs(
								title, 0, 0, page)
						// filtered no title
						: gifts.findByInappropriateCountIsAndObsceneCountIs(0, 0, page)
					: hasTitle
						// title no filter
						? gifts.findByTitleContainingIgnoreCase(title, page)
						// no title no filter
						: gifts.findAll(page);
			break;
		}
		case MY_GIFTS : {
			String uname = principal.getName();
			giftsPage = 
				isFiltered 
					? hasTitle
						// filtered and title
						? gifts.findByUserNameIsAndTitleContainingIgnoreCaseAndInappropriateCountIsAndObsceneCountIs(uname, 
								title, 0, 0, page)
						// filtered no title
						: gifts.findByUserNameIsAndInappropriateCountIsAndObsceneCountIs(uname, 0, 0, page)
					: hasTitle
						// title no filter
						? gifts.findByUserNameIsAndTitleContainingIgnoreCase(uname, title, page)
						// no title no filter
						: gifts.findByUserNameIs(uname, page);
			break;
		}
		case ONE_CHAIN : {
			giftsPage = 
				isFiltered 
					? hasTitle
						// filtered and title
						? gifts.findByGiftChainIdIsAndTitleContainingIgnoreCaseAndInappropriateCountIsAndObsceneCountIs(chainId, 
								title, 0, 0, page)
						// filtered no title
						: gifts.findByGiftChainIdIsAndInappropriateCountIsAndObsceneCountIs(chainId, 0, 0, page)
					: hasTitle
						// title no filter
						? gifts.findByGiftChainIdIsAndTitleContainingIgnoreCase(chainId, title, page)
						// no title no filter
						: gifts.findByGiftChainIdIs(chainId, page);
			break;
		}
		case ALL_GIFT_CHAINS : {
			giftsPage = 
				isFiltered 
					? hasTitle
						// filtered and title
						? gifts.findByChainTopTrueAndTitleContainingIgnoreCaseAndInappropriateCountIsAndObsceneCountIs(
								title, 0, 0, page)
						// filtered no title
						: gifts.findByChainTopTrueAndInappropriateCountIsAndObsceneCountIs(0, 0, page)
					: hasTitle
						// title no filter
						? gifts.findByChainTopTrueAndTitleContainingIgnoreCase(title, page)
						// no title no filter
						: gifts.findByChainTopTrue(page);
			break;
		}
		default : // presume obscene, that's priv only (see next method)
			throw new InvalidParamException(GIFTS_LIST_TYPE_PARAMETER, giftsListType);
		}
		
		return getGiftCollection(principal, giftsPage);
	}
	
	/**
 	 * 2 possible methods to get back obscene gifts, with or w/o title search
 	 * note only admin users can call this
	 * 
	 * @param title
	 * @param pageIdx
	 * @param pageSize
	 * @param principal
	 * @return
	 */
	@RequestMapping(value = POTLATCH_GIFTS_ADMIN_TYPE_PATH, method = RequestMethod.GET)
	public @ResponseBody PagedCollection<Gift> findFlaggedGifts(
			@RequestParam(TITLE_PARAMETER) String title,
			@PathVariable(PAGE_PARAMETER) long pageIdx,
			@PathVariable(PAGE_SIZE_PARAMETER) long pageSize,
			Principal principal) {
		
		Pageable pageSpec = new PageRequest((int)pageIdx, (int)pageSize, NORMAL_GIFTS_SORT);
		PagedCollection<Gift> col = getGiftCollection(principal, 
				title == null || title.isEmpty()
				? gifts.findByObsceneCountNot(0L, pageSpec)
				: gifts.findByTitleContainingIgnoreCaseAndObsceneCountNot(title, 0L, pageSpec)
				);
		
		return col;
	}
	
	@RequestMapping(value = POTLATCH_ID_PATH, method = RequestMethod.GET)
	public @ResponseBody Gift getGiftById(@PathVariable(ID_PARAMETER) long id, Principal principal) {
		
		Gift gift = gifts.findOne(id);
		if (gift != null) {
			// set the flags
			setUserGiftFlags(principal, gift);
			return gift;
		} 
		else {
			throw new GiftNotFoundException(id);
		}
	}

	@RequestMapping(value = POTLATCH_DATA_PATH, method = RequestMethod.GET)
	public void getGiftImageData(@PathVariable(ID_PARAMETER) long id
			, OutputStream out) throws IOException {

		Gift gift = gifts.findOne(id);
		if (gift != null) {
			getData(out, gift, gift.getImagePath());
		} 
		else {
			throw new GiftNotFoundException(id);
		}
	}

	@RequestMapping(value = POTLATCH_THUMBNAIL_DATA_PATH, method = RequestMethod.GET)
	public void getGiftThumbnailData(@PathVariable(ID_PARAMETER) long id
			, OutputStream out) throws IOException {

		Gift gift = gifts.findOne(id);
		if (gift != null) {
			getData(out, gift, gift.getThumbnailPath());
		} 
		else {
			throw new GiftNotFoundException(id);
		}
	}

	@RequestMapping(value = POTLATCH_REMOVE_OWN_GIFT_PATH, method = RequestMethod.POST)
	public @ResponseBody Void removeGiftById(@PathVariable(ID_PARAMETER) long id, Principal principal) {

		Gift gift = gifts.findOne(id);
		if (gift != null) {
			if (gift.getUserName().equals(principal.getName())) {
				deleteGiftRelationships(gift, false);
				gifts.delete(gift);
				return null;
			}
			else {
				throw new NoPermissionException();
			}
		} else {
			throw new GiftNotFoundException(id);
		}
	}

	@RequestMapping(value = POTLATCH_REMOVE_GIFT_PATH, method = RequestMethod.POST)
	public @ResponseBody Void removeObsceneGiftById(@PathVariable(ID_PARAMETER) long id, Principal principal) {

		Gift gift = gifts.findOne(id);
		if (gift != null) {
			// only admin users can remove other people's gifts
			// ... removing for obscenity
			deleteGiftRelationships(gift, true);
			gifts.delete(gift);
			return null;
		} else {
			throw new GiftNotFoundException(id);
		}
	}

	// Receives POST requests to /potlatch
	@RequestMapping(value = POTLATCH_SVC_PATH, method = RequestMethod.POST)
	public @ResponseBody Gift addGift(@RequestBody Gift g, Principal principal) {

		// save once to get the id
		gifts.save(g);

		// allowing for the user to set the gift responses themselves too
		if (g.getUserInappropriateFlag()) {
			g.setInappropriateCount(1);
			this.setUserResponseOn(g.getId(), principal,
					GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT);
		}

		if (g.getUserObsceneFlag()) {
			g.setObsceneCount(1);
			this.setUserResponseOn(g.getId(), principal,
					GIFT_RESPONSE_TYPE_OBSCENE_CONTENT);
		}

		if (g.getUserTouchedBy()) {
			PotlatchUser user = getUser(principal.getName());
			user.setTouchCount(1);
			users.save(user);
			
			g.setTouchCount(1);
			this.setUserResponseOn(g.getId(), principal,
					GIFT_RESPONSE_TYPE_TOUCHED_BY);
		}

		// chain not set, refer to self
		if (g.getGiftChainId() < 0L) {
			g.setGiftChainId(g.getId());
			g.setChainTop(true);
		}
		else {
			// set the ordering to always be under the chain top 
			// (only used for viewing a single chain)
			g.setForceOrderForChainTop(GIFT_CHAIN_ORDERING_UNDER_THE_TOP);
			
			// increment the top of the chain's count
			Gift chainTop = gifts.findOne(g.getGiftChainId());
			
			// might decide to not remove the whole chain when delete the top one
			// so test for it being there
			if (chainTop != null) {
				chainTop.setChainCount(chainTop.getChainCount() +1);
			}
		}

		// save again to save any other changes
		gifts.save(g);

		return g;
	}

	@RequestMapping(value = POTLATCH_SET_DATA_PATH, method = RequestMethod.POST)
	public @ResponseBody Gift setGiftImageById(@PathVariable(ID_PARAMETER) long id
			, @PathVariable(FORMAT_PARAMETER) String format
			, @RequestParam(DATA_PARAMETER) MultipartFile imageData) throws IOException {

		Gift gift = gifts.findOne(id);
		if (gift != null) {
			// save the image and thumbnail and update the paths on the gift
			ImageFileManager.get().saveGiftData(gift, format, imageData.getInputStream());
			
			gifts.save(gift);

			return gift;
		} else {
			throw new GiftNotFoundException(id);
		}

	}

	@RequestMapping(value = POTLATCH_TOUCH_PATH, method = RequestMethod.POST)
	public @ResponseBody Gift touchedByGift(@PathVariable(ID_PARAMETER) long id, Principal principal) {
		return setUserResponseOn(id, principal, GIFT_RESPONSE_TYPE_TOUCHED_BY);
	}

	@RequestMapping(value = POTLATCH_UNTOUCH_PATH, method = RequestMethod.POST)
	public @ResponseBody Gift untouchedByGift(@PathVariable(ID_PARAMETER) long id,
			Principal principal) {
		return setUserResponseOff(id, principal, GIFT_RESPONSE_TYPE_TOUCHED_BY);
	}

	@RequestMapping(value = POTLATCH_INAPPROPRIATE_PATH, method = RequestMethod.POST)
	public @ResponseBody Gift inappropriateGift(@PathVariable(ID_PARAMETER) long id,
			Principal principal) {
		return setUserResponseOn(id, principal,
				GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT);
	}

	@RequestMapping(value = POTLATCH_NOT_INAPPROPRIATE_PATH, method = RequestMethod.POST)
	public @ResponseBody Gift notInappropriateGift(@PathVariable(ID_PARAMETER) long id,
			Principal principal) {
		return setUserResponseOff(id, principal,
				GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT);
	}

	@RequestMapping(value = POTLATCH_OBSCENE_PATH, method = RequestMethod.POST)
	public @ResponseBody Gift obsceneGift(@PathVariable(ID_PARAMETER) long id, Principal principal) {
		return setUserResponseOn(id, principal,
				GIFT_RESPONSE_TYPE_OBSCENE_CONTENT);
	}

	@RequestMapping(value = POTLATCH_NOT_OBSCENE_PATH, method = RequestMethod.POST)
	public @ResponseBody Gift notObsceneGift(@PathVariable(ID_PARAMETER) long id, Principal principal) {
		return setUserResponseOff(id, principal,
				GIFT_RESPONSE_TYPE_OBSCENE_CONTENT);
	}

	@RequestMapping(value = POTLATCH_FILTER_CONTENT_PATH, method = RequestMethod.GET)
	public @ResponseBody Boolean isUserFilterContent(Principal principal) {
		return getUser(principal.getName()).isFilterContent();
	}

	@RequestMapping(value = POTLATCH_FILTER_CONTENT_PATH, method = RequestMethod.POST)
	public @ResponseBody Void filterContent(Principal principal) {
		// check not already filtering
		PotlatchUser user = getUser(principal.getName());
		if (user.isFilterContent()) {
			throw new RepeatedActionException();
		}

		user.setFilterContent(true);
		users.save(user);

		return null;
	}

	@RequestMapping(value = POTLATCH_UNFILTER_CONTENT_PATH, method = RequestMethod.POST)
	public @ResponseBody Void unfilterContent(Principal principal) {
		// check is filtering
		PotlatchUser user = getUser(principal.getName());
		if (!user.isFilterContent()) {
			throw new NoPreviousActionException();
		}

		user.setFilterContent(false);
		
		users.save(user);

		return null;
	}

	@RequestMapping(value = POTLATCH_GET_INTERVAL_PATH, method = RequestMethod.GET)
	public @ResponseBody long getUpdateInterval(Principal principal) {
		
		PotlatchUser user = getUser(principal.getName());
		return user.getUpdateInterval();
	}

	@RequestMapping(value = POTLATCH_UPDATE_INTERVAL_PATH, method = RequestMethod.GET)
	public @ResponseBody Void setUpdateInterval(@PathVariable long millis,
			Principal principal) {
		
		PotlatchUser user = getUser(principal.getName());
		user.setUpdateInterval(millis);
		users.save(user);
		
		return null;
	}

	@RequestMapping(value = POTLATCH_USER_NOTICES_PATH, method = RequestMethod.GET)
	public @ResponseBody UserNotice getNotices(Principal principal) {
		return userNotices.findOne(principal.getName());
	}

	@RequestMapping(value = POTLATCH_USER_NOTICES_PATH, method = RequestMethod.POST)
	public @ResponseBody Void removeNotices(Principal principal) {
		UserNotice un = userNotices.findOne(principal.getName());
		if (un != null) {
			userNotices.delete(un);
		}
		return null;
	}

	@RequestMapping(value = POTLATCH_TOP_GIVERS_PATH, method = RequestMethod.GET)
	public @ResponseBody PagedCollection<PotlatchUser> getTopGivers(
			@PathVariable(PAGE_PARAMETER) long pageIdx,
			@PathVariable(PAGE_SIZE_PARAMETER) long pageSize) {

		Pageable page = new PageRequest((int)pageIdx, (int)pageSize, TOP_GIVERS_SORT);

		Page<PotlatchUser> list = users.findAll(page);
		
		if (list.hasContent()) {

			return new PagedCollection<PotlatchUser>(list.getContent(),
					list.getNumber(),
					list.getNumberOfElements(),
					list.getTotalElements(),
					list.getTotalPages(),
					list.isFirstPage(),
					list.isLastPage());
		}
		else {
			return new PagedCollection<PotlatchUser>(new ArrayList<PotlatchUser>());
		}

	}

	/**
	 * Used by 2 methods that need images (regular and thumbnail)
	 * @param out
	 * @param gift
	 * @param pathStr
	 */
	private void getData(OutputStream out, Gift gift, String pathStr) {
		try {
			ImageFileManager.get().copyPathData(pathStr, out);
		}
		catch (Exception e) {
			throw new ResourceNotFoundException(e);
		}
	}

	/**
	 * Handles the 3 kinds of responses the user can set to on
	 * 
	 * @param id
	 * @param principal
	 * @param giftResponseType
	 * @return
	 */
	private Gift setUserResponseOn(long id, Principal principal,
			long giftResponseType) {
		Gift gift = gifts.findOne(id);
		if (gift == null) {
			throw new GiftNotFoundException(id);
		}

		Collection<UserGiftResponse> giftUserResp = userGiftResponses
				.findByUserNameAndGiftIdAndResponseType(principal.getName(),
						id, giftResponseType);

		// check the user hasn't already touched this gift
		if (!giftUserResp.isEmpty()) {
			throw new RepeatedActionException();
		}

		userGiftResponses.save(new UserGiftResponse(principal.getName(), id,
				giftResponseType));

		// update the gift
		adjustGiftCount(giftResponseType, gift, 1);

		gifts.save(gift);

		return gift;
	}

	/**
	 * Handles the 3 kinds of responses the user can set to off
	 * 
	 * @param id
	 * @param principal
	 * @param giftResponseType
	 * @return
	 */
	private Gift setUserResponseOff(long id, Principal principal,
			long giftResponseType) {
		Gift gift = gifts.findOne(id);
		if (gift == null) {
			throw new GiftNotFoundException(id);
		}

		Collection<UserGiftResponse> vl = userGiftResponses
				.findByUserNameAndGiftIdAndResponseType(principal.getName(),
						id, giftResponseType);

		// check the user has already liked the video
		if (vl.isEmpty()) {
			throw new NoPreviousActionException();
		}

		// unlike the video
		userGiftResponses.delete(vl);

		// update the gift
		adjustGiftCount(giftResponseType, gift, -1);

		gifts.save(gift);

		return gift;
	}

	/**
	 * A couple of aggregations to other tables.
	 * 1) TouchCount on User (which remains forever)
	 * 2) Each flag count on User Notice (which is periodically reset)
	 * @param giftResponseType
	 * @param gift
	 * @param adjust
	 */
	private void adjustGiftCount(long giftResponseType, Gift gift, int adjust) {
		String giftCreator = gift.getUserName();
		UserNotice un = getUserNotice(giftCreator);
		
		switch ((int) giftResponseType) {
		case GIFT_RESPONSE_TYPE_TOUCHED_BY:
			if (adjust > 0) un.setTouchCount(un.getTouchCount() + 1);
			else un.setUnTouchCount(un.getUnTouchCount() + 1);
			
			gift.setTouchCount(gift.getTouchCount() + adjust);
			
			// and the creator's touchcount
			PotlatchUser user = getUser(giftCreator);
			user.adjustTouchCount(adjust);
			users.save(user);
			
			break;
		case GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT:
			if (adjust > 0) un.setInappropriateCount(un.getInappropriateCount() + 1);
			else un.setUnInappropriateCount(un.getUnInappropriateCount() + 1);
			
			gift.setInappropriateCount(gift.getInappropriateCount() + adjust);
			break;
		case GIFT_RESPONSE_TYPE_OBSCENE_CONTENT:
			if (adjust > 0) un.setObsceneCount(un.getObsceneCount() + 1);
			else un.setUnObsceneCount(un.getUnObsceneCount() + 1);
			
			gift.setObsceneCount(gift.getObsceneCount() + adjust);
			break;
		}

		userNotices.save(un);
	}

	private UserNotice getUserNotice(String username) {
		UserNotice un = userNotices.findOne(username);
		if (un == null) {
			un = new UserNotice(username);
			userNotices.save(un);
		}
		return un;
	}

	/**
	 * Called from the methods that return lists (all gifts and search by title)
	 * 
	 * @param giftsCol
	 * @param principal
	 * @return
	 */
	private Collection<Gift> setUserFlags(Collection<Gift> giftsCol, Principal principal) {

		for (Gift gift : giftsCol) {
			setUserGiftFlags(principal, gift);
		}

		return giftsCol;
	}

	private void setUserGiftFlags(Principal principal, Gift gift) {
		// did THIS user set it as inappropriate?
		gift.setUserInappropriateFlag(!userGiftResponses
				.findByUserNameAndGiftIdAndResponseType(principal.getName(),
						gift.getId(), GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT)
				.isEmpty());

		// did THIS user set it as obscene?
		gift.setUserObsceneFlag(!userGiftResponses
				.findByUserNameAndGiftIdAndResponseType(principal.getName(),
						gift.getId(), GIFT_RESPONSE_TYPE_OBSCENE_CONTENT)
				.isEmpty());

		// did THIS user touch this gift
		gift.setUserTouchedBy(!userGiftResponses
				.findByUserNameAndGiftIdAndResponseType(principal.getName(),
						gift.getId(), GIFT_RESPONSE_TYPE_TOUCHED_BY).isEmpty());
	}

	private PotlatchUser getUser(String username) {
		PotlatchUser user = users.findOne(username);
		if (user == null) {
			user = new PotlatchUser(username);
			users.save(user);
		}
		return user;
	}

	/**
	 * Recursive to one level because of the chain of gifts, 
	 * but only want to adjust the user touch count for the one gift that generated
	 * the obscene flag that caused the removal
	 * @param gift
	 * @param isForObscenity
	 */
	private void deleteGiftRelationships(Gift gift, boolean isForObscenity) {
		
		// update the gift creator's stats
		String giftCreator = gift.getUserName();

		if (isForObscenity) {
			UserNotice un = getUserNotice(giftCreator);
			
			un.setRemovedCount(un.getRemovedCount() + 1);
			userNotices.save(un);
			
			PotlatchUser user = getUser(gift.getUserName());
			user.adjustTouchCount(gift.getTouchCount() * -1);
		}

		// delete responses
		Collection<UserGiftResponse> urs = userGiftResponses.findByGiftId(gift.getId());
		if (!urs.isEmpty()) {
			userGiftResponses.delete(urs);
		}
		
		// if top of a chain, remove the chain too
		if (gift.getChainTop()) {
			
			// gonna try it this way, delete top one doesn't remove the others
//			Collection<Gift> gc = gifts.findByGiftChainId(gift.getId());
//			
//			// first the responses for each gift in the chain
//			if (!gc.isEmpty()) {
//				for (Gift chainGift : gc) {
//					// ignore the gift calling with, or it will go on forever
//					if (chainGift.getId() != gift.getId()) {
//						deleteGiftRelationships(chainGift, false);
//					}
//				}
//				
//				// and then all the gifts too
//				gifts.delete(gc);
//			}
		}
		else {
			// not the top of the chain, decrement the top of the chain's count
			Gift chainTop = gifts.findOne(gift.getGiftChainId());
			
			// might decide to not remove the whole chain when delete the top one
			// so test for it being there
			if (chainTop != null) {
				chainTop.setChainCount(chainTop.getChainCount() -1);
			}
		}
		
		// delete image files
		try {
			ImageFileManager.get().deleteGiftImages(gift);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Used from the 3 places that generate gifts lists in paginated form, handles
	 * testing for content and returning the results with the user's flags set
	 * @param principal
	 * @param page
	 * @return
	 */
	private PagedCollection<Gift> getGiftCollection(Principal principal, Page<Gift> page) {

		if (page.hasContent()) {
			setUserFlags(page.getContent(), principal);
			return new PagedCollection<Gift>(page.getContent(),
					page.getNumber(),
					page.getNumberOfElements(),
					page.getTotalElements(),
					page.getTotalPages(),
					page.isFirstPage(),
					page.isLastPage());
		}
		else {
			return new PagedCollection<Gift>(new ArrayList<Gift>());
		}
	}
}
