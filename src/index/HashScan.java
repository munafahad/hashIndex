package index;

import global.GlobalConst;
import global.Minibase;
import global.PageId;
import global.RID;
import global.SearchKey;

/**
 * A HashScan retrieves all records with a given key (via the RIDs of the records).  
 * It is created only through the function openScan() in the HashIndex class. 
 */
public class HashScan implements GlobalConst {

  /** The search key to scan for. */
  protected SearchKey key;

  /** Id of HashBucketPage being scanned. */
  protected PageId curPageId;

  /** HashBucketPage being scanned. */
  protected HashBucketPage curPage;

  /** Current slot to scan from. */
  protected int curSlot;

  // --------------------------------------------------------------------------

  /**
   * Constructs an equality scan by initializing the iterator state.
   */
  protected HashScan(HashIndex index, SearchKey key) {

	  this.key = new SearchKey(key);
	  PageId dirPageId = new PageId(index.headId.pid);
	  HashDirPage dirPage = new HashDirPage();
	  //hashValue: to find the directory page
	  int hashValue = key.getHash(index.DEPTH);
	  
	  
	  for(; hashValue >= HashDirPage.MAX_ENTRIES ; hashValue -= HashDirPage.MAX_ENTRIES) {
		  
		  Minibase.BufferManager.pinPage(dirPageId, dirPage, PIN_DISKIO);
	      PageId nextPageId = dirPage.getNextPage();
	      Minibase.BufferManager.unpinPage(dirPageId, UNPIN_CLEAN);
	      
	      dirPageId = nextPageId;
	  }
	  
	  //hash value < max entries, this page should be the needed directory page
	  Minibase.BufferManager.pinPage(dirPageId, dirPage, PIN_DISKIO);
	  
	  //Get the first page id of bucket page 
	  curPageId = dirPage.getPageId(hashValue);
	  Minibase.BufferManager.unpinPage(dirPageId, UNPIN_CLEAN);
	  curPage = new HashBucketPage();
	  
	  if(curPageId.pid != INVALID_PAGEID) {
		  Minibase.BufferManager.pinPage(curPageId, curPage, PIN_DISKIO);
	      this.curSlot = EMPTY_SLOT;
	  }
	  	  
  } // protected HashScan(HashIndex index, SearchKey key)

  /**
   * Called by the garbage collector when there are no more references to the
   * object; closes the scan if it's still open.
   */
  protected void finalize() throws Throwable {

	  if(curPageId.pid != INVALID_PAGEID) 
		  close();

  } // protected void finalize() throws Throwable

  /**
   * Closes the index scan, releasing any pinned pages.
   */
  public void close() {

	  if (curPageId.pid != INVALID_PAGEID) {
	      Minibase.BufferManager.unpinPage(curPageId, UNPIN_CLEAN);
	      curPageId.pid = INVALID_PAGEID;
	  }

  } // public void close()

   /**
   * Gets the next entry's RID in the index scan.
   * 
   * @throws IllegalStateException if the scan has no more entries
   */
  public RID getNext() {

	  RID rid = null;
	  
	  while (curPageId.pid != INVALID_PAGEID) {
	      
		  //get curSlot to start the scan from it
		  curSlot = curPage.nextEntry(key, curSlot);

	      if (curSlot < 0) {
	    	  
	        PageId nextPageId = curPage.getNextPage();
	        Minibase.BufferManager.unpinPage(curPageId, UNPIN_CLEAN);
	        curPageId = nextPageId;
	        
	        if (curPageId.pid != INVALID_PAGEID)
	          Minibase.BufferManager.pinPage(curPageId, curPage, PIN_DISKIO);
	        
	      } else {
              rid = new RID(curPage.getEntryAt(curSlot).rid);
              break;
          }
	    }
	
	  return rid;
  } // public RID getNext()

} // public class HashScan implements GlobalConst
