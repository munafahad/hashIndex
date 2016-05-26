package index;

import global.Minibase;
import global.PageId;

/**
 * An object in this class is a page in a linked list.
 * The entire linked list is a hash table bucket.
 */
class HashBucketPage extends SortedPage {

  /**
   * Gets the number of entries in this page and later
   * (overflow) pages in the list.
   * <br><br>
   * To find the number of entries in a bucket, apply 
   * countEntries to the primary page of the bucket.
   */
  public int countEntries() {

	  int count = 0;
	  
	  //1. get primary page entry count: SortedPage.getEntryCount
	   count += getEntryCount();
	  
	  //2. count entries in next pages if any: SortedPage.getNextPage >> NEXT_PAGE = 4;
	  PageId pageId = getNextPage();
	  SortedPage nextPage = new SortedPage();
	  
	  while(pageId.pid != INVALID_PAGEID) {
		  
		  //pin and count entries
		  Minibase.BufferManager.pinPage(pageId, nextPage, PIN_DISKIO);
		  count += nextPage.getEntryCount();
		  
		  //move to next page
		  PageId nextPageId = nextPage.getNextPage();
		  Minibase.BufferManager.unpinPage(pageId, UNPIN_CLEAN);
		  pageId = nextPageId;
	  }
	  
	  return count;

  } // public int countEntries()

  /**
   * Inserts a new data entry into this page. If there is no room
   * on this page, recursively inserts in later pages of the list.  
   * If necessary, creates a new page at the end of the list.
   * Does not worry about keeping order between entries in different pages.
   * <br><br>
   * To insert a data entry into a bucket, apply insertEntry to the
   * primary page of the bucket.
   * 
   * @return true if inserting made this page dirty, false otherwise
   */
  public boolean insertEntry(DataEntry entry) {

	  //1. try to insert an entry into the primary page
	  try {
		  super.insertEntry(entry);
		  return true;
	  }
	  //2.1 try to insert in later pages of the list 
	  //OR 2.2 create a new page and insert the data entry into it
	  catch(IllegalStateException ex) {
		  
		  PageId nextPageId = getNextPage();
		  
		  //HashBucketPage object NOT SortedPage object as we can ignore the sorted order
		  HashBucketPage nextPage = new HashBucketPage();
		  
	      if (nextPageId.pid != INVALID_PAGEID)
	      {
	        Minibase.BufferManager.pinPage(nextPageId, nextPage, PIN_DISKIO);
	        boolean dirty = nextPage.insertEntry(entry);
	        Minibase.BufferManager.unpinPage(nextPageId, dirty);
	        return false;
	      }
	      //New page and add the data entry to it 
	      nextPageId = Minibase.BufferManager.newPage(nextPage, 1);
	      setNextPage(nextPageId);
	      boolean dirty = nextPage.insertEntry(entry);
	      Minibase.BufferManager.unpinPage(nextPageId, dirty);
	      return true;
	  }	 

  } // public boolean insertEntry(DataEntry entry)

  /**
   * Deletes a data entry from this page.  If a page in the list 
   * (not the primary page) becomes empty, it is deleted from the list.
   * 
   * To delete a data entry from a bucket, apply deleteEntry to the
   * primary page of the bucket.
   * 
   * @return true if deleting made this page dirty, false otherwise
   * @throws IllegalArgumentException if the entry is not in the list.
   */
  public boolean deleteEntry(DataEntry entry) {

	  //1. try to delete the data entry from the primary page
	  try {
		  super.deleteEntry(entry);
		  return true;
	  }
	  //2. if the entry does not exist in the primary page ==> check next pages in the list
	  catch(IllegalArgumentException ex) {

		  PageId nextPageId = getNextPage();
		  
	      HashBucketPage nextPage = new HashBucketPage();
		  
	      if (nextPageId.pid != INVALID_PAGEID)
	      {
	        Minibase.BufferManager.pinPage(nextPageId, nextPage, PIN_DISKIO);
	        boolean dirty = nextPage.deleteEntry(entry);


	        //check if the page is empty to delete it and set the next page
	        if (nextPage.getEntryCount() < 1)
	        {
	          setNextPage(nextPage.getNextPage());
	          Minibase.BufferManager.unpinPage(nextPageId, dirty);
	          Minibase.BufferManager.freePage(nextPageId);
	          return true;
	        } else {
	        	Minibase.BufferManager.unpinPage(nextPageId, dirty);
	 	        return false;
	        }
	       
	      }
	      
	      throw ex;
	  }


  } // public boolean deleteEntry(DataEntry entry)

} // class HashBucketPage extends SortedPage
