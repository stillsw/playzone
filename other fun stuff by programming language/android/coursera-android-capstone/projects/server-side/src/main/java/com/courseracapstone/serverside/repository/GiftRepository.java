package com.courseracapstone.serverside.repository;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

/**
 * An interface for a repository that can store Gift
 * objects and allow them to be searched by title/chainId/and content flags per user.
 * 
 * @author xxx xxx
 */
public interface GiftRepository extends PagingAndSortingRepository<Gift, Long>{

	// all the methods that support paged/sorted retrieval of Gifts of the different types
	// that's why need a method for each type combo, otherwise could do it easier in the java
	// but this way the repo handles both sorting and paging
	
	// Note, every type allows filter by obscene/inappropriate and also title
	// so there's 4 methods for each kind, except obscene (only 2)
	
	// the methods are ugly, but they work and the advantage of handling this complexity
	// here is the api can be very simple, it's the controller that interprets the
	// call and handles the complexity

	//-------- all gifts
	
	public Page<Gift> findAll(Pageable page);
	
	public Page<Gift> findByInappropriateCountIsAndObsceneCountIs(
			@Param(value="inappropriateCount") long inappropriateCount, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	public Page<Gift> findByTitleContainingIgnoreCase(
			@Param(value="title") String title, 
			Pageable page);

	public Page<Gift> findByTitleContainingIgnoreCaseAndInappropriateCountIsAndObsceneCountIs(
			@Param(value="title") String title, 
			@Param(value="inappropriateCount") long inappropriateCount, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	//-------- my gifts
	
	public Page<Gift> findByUserNameIs(
			@Param(value="userName") String userName, 
			Pageable page);
	
	public Page<Gift> findByUserNameIsAndInappropriateCountIsAndObsceneCountIs(
			@Param(value="userName") String userName, 
			@Param(value="inappropriateCount") long inappropriateCount, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	public Page<Gift> findByUserNameIsAndTitleContainingIgnoreCase(
			@Param(value="userName") String userName, 
			@Param(value="title") String title, 
			Pageable page);

	public Page<Gift> findByUserNameIsAndTitleContainingIgnoreCaseAndInappropriateCountIsAndObsceneCountIs(
			@Param(value="userName") String userName, 
			@Param(value="title") String title, 
			@Param(value="inappropriateCount") long inappropriateCount, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	//-------- all gift chains
	
	public Page<Gift> findByChainTopTrue(
			Pageable page);
	
	public Page<Gift> findByChainTopTrueAndInappropriateCountIsAndObsceneCountIs(
			@Param(value="inappropriateCount") long inappropriateCount, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	public Page<Gift> findByChainTopTrueAndTitleContainingIgnoreCase(
			@Param(value="title") String title, 
			Pageable page);

	public Page<Gift> findByChainTopTrueAndTitleContainingIgnoreCaseAndInappropriateCountIsAndObsceneCountIs(
			@Param(value="title") String title, 
			@Param(value="inappropriateCount") long inappropriateCount, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	//-------- one gift chain
	
	public Page<Gift> findByGiftChainIdIs(
			@Param(value="chainId") long chainId, 
			Pageable page);
	
	public Page<Gift> findByGiftChainIdIsAndInappropriateCountIsAndObsceneCountIs(
			@Param(value="chainId") long chainId, 
			@Param(value="inappropriateCount") long inappropriateCount, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	public Page<Gift> findByGiftChainIdIsAndTitleContainingIgnoreCase(
			@Param(value="chainId") long chainId, 
			@Param(value="title") String title, 
			Pageable page);

	public Page<Gift> findByGiftChainIdIsAndTitleContainingIgnoreCaseAndInappropriateCountIsAndObsceneCountIs(
			@Param(value="chainId") long chainId, 
			@Param(value="title") String title, 
			@Param(value="inappropriateCount") long inappropriateCount, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	//-------- obscene gifts : only 2 of these, since they always have obscene count set
	
	public Page<Gift> findByObsceneCountNot(
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	public Page<Gift> findByTitleContainingIgnoreCaseAndObsceneCountNot(
			@Param(value="title") String title, 
			@Param(value="obsceneCount") long obsceneCount, 
			Pageable page);

	
	// no ordering here, only used to find the ones to delete
	public Collection<Gift> findByGiftChainId(long chainId);
	
	
}
