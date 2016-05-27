package index;

import global.GlobalConst;
import global.Minibase;
import global.PageId;
import global.RID;
import global.SearchKey;

/**
 * <h3>Minibase Hash Index</h3>
 * This unclustered index implements static hashing as described on pages 371 to
 * 373 of the textbook (3rd edition).  The index file is a stored as a heapfile.  
 */
public class HashIndex implements GlobalConst {

  /** File name of the hash index. */
  protected String fileName;

  /** Page id of the directory. */
  protected PageId headId;
  
  //Log2 of the number of buckets - fixed for this simple index
  protected final int  DEPTH = 7;

  // --------------------------------------------------------------------------

  /**
   * Opens an index file given its name, or creates a new index file if the name
   * doesn't exist; a null name produces a temporary index file which requires
   * no file library entry and whose pages are freed when there are no more
   * references to it.
   * The file's directory contains the locations of the 128 primary bucket pages.
   * You will need to decide on a structure for the directory.
   * The library entry contains the name of the index file and the pageId of the
   * file's directory.
   */
  public HashIndex(String fileName) {

	  this.fileName=fileName;
	  
	  //1. check fileName
	  //2.1 get the index file if it is exist by setting the headId
	  boolean exist = false;
	  
	  if(fileName != null) {
		  headId = Minibase.DiskManager.get_file_entry(fileName);
		  if(headId != null)
			  exist = true;  
	  } 
	  //2.2 create a new index file if the file with the provided name does not exist 
	  if(!exist){
		  HashDirPage hDirPage = new HashDirPage();
		  headId = Minibase.BufferManager.newPage(hDirPage, 1);
		  Minibase.BufferManager.unpinPage(headId, UNPIN_DIRTY);
		  
		  //add the index file to the library
		  if(fileName!=null){
			  Minibase.DiskManager.add_file_entry(fileName, headId);
		  }
	  }

  } // public HashIndex(String fileName)

  /**
   * Called by the garbage collector when there are no more references to the
   * object; deletes the index file if it's temporary.
   */
  protected void finalize() throws Throwable {

	  if (fileName==null)
		  deleteFile();

  } // protected void finalize() throws Throwable

   /**
   * Deletes the index file from the database, freeing all of its pages.
   */
  public void deleteFile() {
	  
	  //PageId, HashDirPage, and HashBucketPage objects
	  PageId dirId = new PageId(headId.pid);
	  HashDirPage hDirPage = new HashDirPage();
	  SortedPage hBucketPage = new SortedPage();
	  
	  //1. delete pages
	  while (dirId.pid != INVALID_PAGEID) {
		  
		  Minibase.BufferManager.pinPage(dirId, hDirPage, PIN_DISKIO);
 
		  //loop through the hashDirPages
		  int count = hDirPage.getEntryCount();
		  for (int i = 0 ; i < count ; ++i) {
			  PageId dataId = hDirPage.getPageId(i);
			  
			  //loop through the (HashBucketPage\sorted pages) AND 
			  //deallocate all pages in the bucket
			  while(dataId.pid != INVALID_PAGEID) {
				  
				  Minibase.BufferManager.pinPage(dataId, hBucketPage, PIN_DISKIO);
				  PageId nextPageId = hBucketPage.getNextPage();
				  Minibase.BufferManager.unpinPage(dataId, UNPIN_CLEAN);
				  Minibase.BufferManager.freePage(dataId);
				  dataId = nextPageId;
			  }
		  }
		  
		  //2. deallocate the Page from the hash directory and move to next page
		  PageId nextPageId = hDirPage.getNextPage();
		  
		  Minibase.BufferManager.unpinPage(dirId, UNPIN_CLEAN);
		  Minibase.BufferManager .freePage(dirId);
		  
		  dirId = nextPageId;
	  }
	  
	  //3. delete the index file from the library
	  if (fileName!=null){
		  Minibase.DiskManager.delete_file_entry(fileName);
	  }

  } // public void deleteFile()

  /**
   * Inserts a new data entry into the index file.
   * 
   * @throws IllegalArgumentException if the entry is too large
   */
  public void insertEntry(SearchKey key, RID rid) {

	  //1.data entry checking
	  DataEntry entry = new DataEntry (key, rid);
	  
	  if (entry.getLength() > SortedPage.MAX_ENTRY_SIZE){
		  throw new IllegalArgumentException("The data entry is too large!");
	  }
	  
	  PageId dirPageId = new PageId(headId.pid);
	  HashDirPage hDirPage = new HashDirPage();
	  HashBucketPage hdataPage = new HashBucketPage();
	  
	  int hashValue = key.getHash(DEPTH);
	  
	  //to check and use correct hash value
	  for (; hashValue >= HashDirPage.MAX_ENTRIES ; hashValue -= HashDirPage.MAX_ENTRIES){
		  Minibase.BufferManager.pinPage(dirPageId, hdataPage, PIN_DISKIO);
		  PageId nextId = hdataPage.getNextPage();
		  Minibase.BufferManager.unpinPage(dirPageId, UNPIN_CLEAN);
		  dirPageId = nextId;
		  
	  }
	  Minibase.BufferManager.pinPage(dirPageId, hDirPage, PIN_DISKIO); 
	    
	  PageId dataId = hDirPage.getPageId(hashValue);
	  
	  //2. insert to exist pageId or create a new one and insert the record to it
	  if (dataId.pid != INVALID_PAGEID){
		  Minibase.BufferManager.pinPage(dataId, hdataPage, PIN_DISKIO);
		  Minibase.BufferManager.unpinPage(dirPageId, UNPIN_CLEAN);
		  
	  } else {
		  dataId = Minibase.BufferManager.newPage(hdataPage, 1);
		  hDirPage.setPageId(hashValue, dataId);
		  Minibase.BufferManager.unpinPage(dirPageId, UNPIN_DIRTY);
	  }
	  
	  boolean dirty = hdataPage.insertEntry(entry);
	  Minibase.BufferManager.unpinPage(dataId, dirty);
	
  } // public void insertEntry(SearchKey key, RID rid)

  /**
   * Deletes the specified data entry from the index file.
   * 
   * @throws IllegalArgumentException if the entry doesn't exist
   */
  public void deleteEntry(SearchKey key, RID rid) {

	  
	  DataEntry entry = new DataEntry(key, rid);
	  PageId dirId= new PageId(headId.pid);
	  HashDirPage hDirPage = new HashDirPage();
	  HashBucketPage hDataPage = new HashBucketPage();
	  int hashValue = key.getHash(DEPTH);
	  
	  //check the correctness of hashValue
	  while (hashValue >= HashDirPage.MAX_ENTRIES){
		  Minibase.BufferManager.pinPage(dirId, hDirPage, PIN_DISKIO);
		  PageId nextId = hDirPage.getNextPage();
		  Minibase.BufferManager.unpinPage(dirId, UNPIN_CLEAN);
		  dirId = nextId;
		  hashValue -= HashDirPage.MAX_ENTRIES;
	  }
	  
	  //1. get pageId from the HashDirPage
	  Minibase.BufferManager.pinPage(dirId, hDirPage, PIN_DISKIO);
	  PageId dataId = hDirPage.getPageId(hashValue);
	  Minibase.BufferManager.unpinPage(dirId, UNPIN_CLEAN);
	  
	  
	  //2. check pageId and delete or throw an exception
	  if (dataId.pid != INVALID_PAGEID){
		  
		  Minibase.BufferManager.pinPage(dataId, hDataPage, PIN_DISKIO);
		  try {
			  boolean dirty = hDataPage.deleteEntry(entry);
			  Minibase.BufferManager.unpinPage(dataId, dirty);
		  } catch(IllegalArgumentException exc) {
			  Minibase.BufferManager.unpinPage(dataId, UNPIN_CLEAN);
			  throw exc;
		  }
		  
	  } else {
		  throw new IllegalArgumentException("The entry does not exist!");
	  }
	  
	  
	  
  } // public void deleteEntry(SearchKey key, RID rid)

  /**
   * Initiates an equality scan of the index file.
   */
  public HashScan openScan(SearchKey key) {
    return new HashScan(this, key);
  }

  /**
   * Returns the name of the index file.
   */
  public String toString() {
    return fileName;
  }

  /**
   * Prints a high-level view of the directory, namely which buckets are
   * allocated and how many entries are stored in each one. Sample output:
   * 
   * <pre>
   * IX_Customers
   * ------------
   * 0000000 : 35
   * 0000001 : null
   * 0000010 : 27
   * ...
   * 1111111 : 42
   * ------------
   * Total : 1500
   * </pre>
   */
  public void printSummary() {

	  String name = "temp file";
	  if(fileName != null) 
		  name = this.fileName;
	  
	  System.out.println(name);
	  
	  for (int i = 0; i < name.length(); ++i){
		  System.out.print("-");		  
	  }
	  System.out.println();
	  
	  
	  int total = 0;
	  
	  PageId dirId = new PageId(headId.pid);
	  HashDirPage dirPage = new HashDirPage();
	  HashBucketPage dataPage = new HashBucketPage();
	  
	  while (dirId.pid != INVALID_PAGEID) {
		  Minibase.BufferManager.pinPage(dirId, dirPage, PIN_DISKIO);
		  
		  int count = dirPage.getEntryCount();
		  for (int i = 0 ; i < count ; ++i){
			  String hash = Integer.toString(i,2);
			  for (int j=0 ; j < DEPTH - hash.length() ; ++j){
				  System.out.print('0');
			  }
			  System.out.print(hash + " : ");
			  
			  PageId dataId = dirPage.getPageId(i);
			  
			  if (dataId.pid != INVALID_PAGEID) {
				  Minibase.BufferManager.pinPage(dataId, dataPage, PIN_DISKIO);
				  int bkcnt = dataPage.countEntries();
				  System.out.println(bkcnt);
				  total += bkcnt;
				  Minibase.BufferManager.unpinPage(dataId, UNPIN_CLEAN);		  
			  } else {
				  System.out.println("null");
			  }
		  }
		  
		  PageId nextId = dirPage.getNextPage();
		  Minibase.BufferManager.unpinPage(dirId, UNPIN_CLEAN);
		  dirId = nextId;
		  
	  }
	  
	  for (int i = 0 ; i < name.length() ; ++i) {
		  System.out.print('-');
	  }
	  
	  System.out.println();
	  System.out.println("Total : "+ total);

  } // public void printSummary()

} // public class HashIndex implements GlobalConst
