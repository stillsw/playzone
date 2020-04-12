package com.courseracapstone.serverside.client;

import static com.courseracapstone.common.PotlatchConstants.CHAIN_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.CLIENT_ID_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.DATA_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.FORMAT_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.GIFTS_LIST_TYPE_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.ID_PARAMETER;
import static com.courseracapstone.common.PotlatchConstants.MILLIS_PARAMETER;
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
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.http.Streaming;
import retrofit.mime.TypedFile;

import com.courseracapstone.common.PagedCollection;
import com.courseracapstone.serverside.repository.Gift;
import com.courseracapstone.serverside.repository.PotlatchUser;
import com.courseracapstone.serverside.repository.UserNotice;

/**
 * This interface defines an API for a PotlatchSvc. The
 * interface is used to provide a contract for client/server
 * interactions. The interface is annotated with Retrofit
 * annotations so that clients can automatically convert the
 * interface into a client capable of sending the appropriate
 * HTTP requests.
 * 
 * @author xxx xxx - modified from VideoSvcApi by Jules White
 *
 */
public interface PotlatchSvcApi {

	
	@POST(POTLATCH_SIGN_UP_PATH) 
	public Void createUser(
			@Query(USER_NAME_PARAMETER) String username,
			@Query(PASSWORD_PARAMETER) String password,
			@Query(CLIENT_ID_PARAMETER) String clientId);
	
	@GET(POTLATCH_GIFTS_USER_TYPES_PATH)
	public PagedCollection<Gift> getGifts(
			@Query(GIFTS_LIST_TYPE_PARAMETER) String giftsListType,
			@Query(TITLE_PARAMETER) String title,
			@Query(CHAIN_PARAMETER) long chainId,
			@Path(PAGE_PARAMETER) long pageIdx, 
			@Path(PAGE_SIZE_PARAMETER) long pageSize);
	
	@GET(POTLATCH_GIFTS_ADMIN_TYPE_PATH)
	public PagedCollection<Gift> findFlaggedGifts(
			@Query(TITLE_PARAMETER) String title,
			@Path(PAGE_PARAMETER) long pageIdx, 
			@Path(PAGE_SIZE_PARAMETER) long pageSize);
	
	@GET(POTLATCH_ID_PATH)
	public Gift getGiftById(@Path(ID_PARAMETER) long id);
	
	@Streaming
    @GET(POTLATCH_DATA_PATH)
	public Response getGiftImageData(@Path(ID_PARAMETER) long id);	

	@Streaming
    @GET(POTLATCH_THUMBNAIL_DATA_PATH)
	public Response getGiftThumbnailData(@Path(ID_PARAMETER) long id);	

	@POST(POTLATCH_REMOVE_OWN_GIFT_PATH)
	public Void removeGiftById(@Path(ID_PARAMETER) long id);
	
	@POST(POTLATCH_REMOVE_GIFT_PATH)
	public Void removeObsceneGiftById(@Path(ID_PARAMETER) long id);
	
	@POST(POTLATCH_SVC_PATH)
	public Gift addGift(@Body Gift g);

	@Multipart
	@POST(POTLATCH_SET_DATA_PATH)
	public Gift setGiftImageById(
			@Path(ID_PARAMETER) long id, 
			@Path(FORMAT_PARAMETER) String format,
			@Part(DATA_PARAMETER) TypedFile imageData);	
	
	@POST(POTLATCH_TOUCH_PATH)
	public Gift touchedByGift(@Path(ID_PARAMETER) long id);
	
	@POST(POTLATCH_UNTOUCH_PATH)
	public Gift untouchedByGift(@Path(ID_PARAMETER) long id);
	
	@POST(POTLATCH_INAPPROPRIATE_PATH)
	public Gift inappropriateGift(@Path(ID_PARAMETER) long id);
	
	@POST(POTLATCH_NOT_INAPPROPRIATE_PATH)
	public Gift notInappropriateGift(@Path(ID_PARAMETER) long id);
	
	@POST(POTLATCH_OBSCENE_PATH)
	public Gift obsceneGift(@Path(ID_PARAMETER) long id);
	
	@POST(POTLATCH_NOT_OBSCENE_PATH)
	public Gift notObsceneGift(@Path(ID_PARAMETER) long id);
	
	@POST(POTLATCH_FILTER_CONTENT_PATH)
	public Void filterContent();
	
	@GET(POTLATCH_FILTER_CONTENT_PATH)
	public Boolean isUserFilterContent();
	
	@POST(POTLATCH_UNFILTER_CONTENT_PATH)
	public Void unfilterContent();
	
	@GET(POTLATCH_UPDATE_INTERVAL_PATH)
	public Void setUpdateInterval(@Path(MILLIS_PARAMETER) long millis);
	
	@GET(POTLATCH_GET_INTERVAL_PATH)
	public long getUpdateInterval();
	
	@GET(POTLATCH_USER_NOTICES_PATH)
	public UserNotice getNotices();

	@POST(POTLATCH_USER_NOTICES_PATH)
	public Void removeNotices();

	@GET(POTLATCH_TOP_GIVERS_PATH)
	public PagedCollection<PotlatchUser> getTopGivers(
			@Path(PAGE_PARAMETER) long pageIdx, 
			@Path(PAGE_SIZE_PARAMETER) long pageSize);
	
}
