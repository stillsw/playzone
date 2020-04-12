package com.courseracapstone.common;

import java.util.Collection;

/**
 * A wrapper for a Collection (only used for Gift initially), that allows pagination data
 * to be sent along with it to client from server
 *
 * @param <T>
 */
public class PagedCollection<T> {

	private Collection<T> dataList;
	private int pageNum;
	private int pageSize;
	private long totalSize;
	private int totalPages;
	private boolean isFirstPage;
	private boolean isLastPage;

	// allows retrofit to create it 
	public PagedCollection() {}
	
	/**
	 * Used when the list is empty
	 * @param dataList
	 */
	public PagedCollection(Collection<T> dataList) {
		this(dataList, 0, 0, 0, 1, true, true);
		assert(dataList.isEmpty());
	}

	/**
	 * Used to pass in data from a spring Page
	 * @param dataList
	 * @param pageNum
	 * @param pageSize
	 * @param totalSize
	 * @param totalPages
	 * @param isFirstPage
	 * @param isLastPage
	 */
	public PagedCollection(Collection<T> dataList, int pageNum, int pageSize,
			long totalSize, int totalPages, boolean isFirstPage, boolean isLastPage) {

		this.dataList = dataList;
		this.pageNum = pageNum;
		this.pageSize = pageSize;
		this.totalSize = totalSize;
		this.totalPages = totalPages;
		this.isFirstPage = isFirstPage;
		this.isLastPage = isLastPage;
	}

	public Collection<T> getDataList() {
		return dataList;
	}

	public void setDataList(Collection<T> dataList) {
		this.dataList = dataList;
	}

	public int getPageNum() {
		return pageNum;
	}

	public int getPageSize() {
		return pageSize;
	}

	public long getTotalSize() {
		return totalSize;
	}

	public int getTotalPages() {
		return totalPages;
	}

	public boolean getIsFirstPage() {
		return isFirstPage;
	}

	public boolean getIsLastPage() {
		return isLastPage;
	}

	public void setPageNum(int pageNum) {
		this.pageNum = pageNum;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public void setTotalSize(long totalSize) {
		this.totalSize = totalSize;
	}

	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
	}

	public void setFirstPage(boolean isFirstPage) {
		this.isFirstPage = isFirstPage;
	}

	public void setLastPage(boolean isLastPage) {
		this.isLastPage = isLastPage;
	}
	
}
