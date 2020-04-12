package com.courseracapstone.serverside.repository;

import java.util.Collection;

import org.springframework.data.repository.CrudRepository;

/**
 */
//@RepositoryRestResource(path = PotlatchSvcApi.POTLATCH_SVC_PATH+"/userresponse")
public interface UserGiftResponseRepository extends CrudRepository<UserGiftResponse, Long>{

	public Collection<UserGiftResponse> findByUserNameAndGiftIdAndResponseType(
			String UserName, long giftId, long responseType);
	
	public Collection<UserGiftResponse> findByGiftIdAndResponseType(
			long giftId, long responseType);
	
	public Collection<UserGiftResponse> findByGiftId(long giftId);
	
}
