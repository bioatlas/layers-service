package org.ala.spatial.analysis.index;

import java.io.Serializable;

/**
 * record for indexing of a species name (or other level such as genus)
 * against sorted records file
 *
 * @author adam
 *
 */
public class IndexedRecord implements Serializable{
	
	static final long serialVersionUID = 238184888719362147L;
	
	/**
	 * String value of this record
	 */
	public String name;
	
	/**
	 * first char position in sorted records file
	 */
	public long file_start;
	
	/**
	 * last char position in sorted records file
	 */
	public long file_end;
	
	/**
	 * first record position in sorted records file
	 */
	public int record_start;
	
	/**
	 * last record position in sorted records file
	 */
	public int record_end;
	
	/**
	 * indexed record type, arbitary
	 * 
	 * current usage as sorted records file column index
	 */
	public byte type;	
	
	/**
	 * constructor for new IndexedRecord
	 * @param _name value of this record as String
	 * @param _file_start first char position in sorted records file
	 * @param _file_end last char position in sorted records file
	 * @param _record_start first record position in sorted records file
	 * @param _record_end last record position in sorted records file
	 * @param _type type as byte, current usage as sorted records file 
	 * column index
	 */
	public IndexedRecord(String _name, long _file_start, long _file_end,
			int _record_start, int _record_end, byte _type){
		name = _name;
		file_start = _file_start;
		file_end = _file_end;
		record_start = _record_start;
		record_end = _record_end;
		type = _type;
	}	
}