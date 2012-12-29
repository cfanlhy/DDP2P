/* ------------------------------------------------------------------------- */
/*   Copyright (C) 2012 Marius C. Silaghi
		Author: Marius Silaghi: msilaghi@fit.edu
		Florida Tech, Human Decision Support Systems Laboratory
   
       This program is free software; you can redistribute it and/or modify
       it under the terms of the GNU Affero General Public License as published by
       the Free Software Foundation; either the current version of the License, or
       (at your option) any later version.
   
      This program is distributed in the hope that it will be useful,
      but WITHOUT ANY WARRANTY; without even the implied warranty of
      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
      GNU General Public License for more details.
  
      You should have received a copy of the GNU Affero General Public License
      along with this program; if not, write to the Free Software
      Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.              */
/* ------------------------------------------------------------------------- */

package streaming;

import static java.lang.System.out;

import hds.ASNSyncRequest;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Hashtable;

import com.almworks.sqlite4java.SQLiteException;

import config.Application;
import config.DD;
import data.D_Organization;
import data.D_Constituent;
import data.D_FieldValue;
import data.D_Neighborhood;

import registration.ASNConstituentOP;
import util.Util;

import ASN1.Encoder;

public class ConstituentHandling {
	private static final boolean DEBUG = false;
	private static final boolean _DEBUG = true;
	private static final int BIG_LIMIT = 300;
	
	static String sql_get_hashes =
		"SELECT c."+table.constituent.global_constituent_ID_hash+", c."+table.constituent.creation_date+
		" FROM "+table.constituent.TNAME+" as c " +
		//" LEFT JOIN "+table.neighborhood.TNAME+" AS n ON(c."+table.constituent.neighborhood_ID+" = n."+table.neighborhood.neighborhood_ID+") "+
		//" JOIN "+table.organization.TNAME+" AS o ON(c."+table.constituent.organization_ID+" = o."+table.organization.organization_ID+") "+
		" WHERE " 
		+" c."+table.constituent.organization_ID+" = ? "
		+" AND c."+table.constituent.arrival_date+" > ? "
		+" AND c."+table.constituent.arrival_date+" <= ?"
		+" AND c."+table.constituent.sign+" IS NOT NULL "
		+ " AND c."+table.constituent.global_constituent_ID_hash+" IS NOT NULL "
		//+ " AND o."+table.organization.broadcasted+" <> '0' "
		+ " AND c."+table.constituent.broadcasted+" <> '0' "
		+" ORDER BY c."+table.constituent.arrival_date
		;
	/**
	 * 
	 * @param last_sync_date
	 * @param org_id
	 * @param _maxDate
	 * @return
	 * @throws SQLiteException
	 */
	public static Hashtable<String, String> getConstituentHashes(String last_sync_date, String org_id, String[] _maxDate, int BIG_LIMIT) throws SQLiteException {
		String maxDate;
		if(DEBUG) out.println("CostituentHandling:getConstituentHashes: start");
		if((_maxDate==null)||(_maxDate.length<1)||(_maxDate[0]==null)) maxDate = Util.getGeneralizedTime();
		else { maxDate = _maxDate[0]; if((_maxDate!=null)&&(_maxDate.length>0)) _maxDate[0] = maxDate;}
		ArrayList<ArrayList<Object>> result = Application.db.select(sql_get_hashes+" LIMIT "+BIG_LIMIT+";",
				new String[]{org_id, last_sync_date, maxDate}, DEBUG);
		return Util.AL_AL_O_2_HSS_SS(result);
	}
	static String sql_get_const_ops_min_max_no_terminator =
		D_Constituent.sql_get_const +
		" WHERE c."+table.constituent.sign+" IS NOT NULL AND "
		+ " o."+table.organization.broadcasted+" <> '0' "
		+ " AND c."+table.constituent.broadcasted+" <> '0' "
			+" AND c."+table.constituent.organization_ID+" = ? " +
					" AND c."+table.constituent.arrival_date+" > ? AND c."+table.constituent.arrival_date+" <= ?" +
				" GROUP BY c."+table.constituent.arrival_date;
	static String sql_get_const_ops_min_no_terminator =
		D_Constituent.sql_get_const +
		" WHERE c."+table.constituent.sign+" IS NOT NULL AND "
		+ " o."+table.organization.broadcasted+" <> '0' "
		+ " AND c."+table.constituent.broadcasted+" <> '0' "
			+" AND c."+table.constituent.organization_ID+" = ? AND c."+table.constituent.arrival_date+">?";
	/**
	 * get the constituents between two dates
	 * @param asr
	 * @param last_sync_date
	 * @param gid
	 * @param org_id
	 * @param _maxDate
	 * @return
	 * @throws SQLiteException
	 */
	public static ASNConstituentOP[] getConstituentOPs(ASNSyncRequest asr, String last_sync_date, String gid, String org_id, String[] _maxDate) throws SQLiteException {
		if(DEBUG)System.out.println("\n************\nConstituentHandling: getConstituentOPs: from date="+last_sync_date+" orgID="+org_id+" to:"+_maxDate[0]);
		
		ArrayList<ArrayList<Object>> al = Application.db.select(sql_get_const_ops_min_max_no_terminator+" LIMIT "+BIG_LIMIT+";",
				new String[]{org_id, last_sync_date, _maxDate[0]}, DEBUG);
		
		int constits = al.size();
		
		ASNConstituentOP[] result = new ASNConstituentOP[constits];
		for(int k=0; k<result.length; k++) {
			result[k] = new ASNConstituentOP();
			result[k].op = Integer.parseInt(Util.getString(al.get(k).get(table.constituent.CONST_COL_OP)));
			result[k].constituent = D_Constituent.get_WB_Constituent(al.get(k));
			
			if(DEBUG)System.out.println("ConstituentHandling: getConstituentOPs: New Constituent = "+result[k].constituent);		
		}
		if(DEBUG)System.out.println("ConstituentHandling: getConstituentOPs: result["+result.length+"] = "+Util.concat(result, ";"));		
		if(DEBUG)System.out.println("************");
		if(result.length==0) return null;
		return result;
	}
	public static ASNConstituentOP[] getConstituentData(ASNSyncRequest asr, String last_sync_date, String org_gid, String org_id, String[] _maxDate) throws SQLiteException{
		if(_maxDate!=null)
			return ConstituentHandling.getConstituentOPs(asr,last_sync_date, org_gid, org_id, _maxDate);
		else
			return ConstituentHandling.getConstituentsModifs(org_id, last_sync_date);
	}
	/**
	 * Build a list of constituent modifications for a given org from given date
	 * @param local_id
	 * @param last_sync_date
	 * @return
	 * @throws SQLiteException
	 */
	public static ASNConstituentOP[] getConstituentsModifs(String local_id, String last_sync_date) throws SQLiteException {
		if(DEBUG) out.println("\n********\nConstituentHandling:getConstituentsModifs: id="+local_id+" from="+last_sync_date);
		ArrayList<ArrayList<Object>> al = 
			Application.db.select(sql_get_const_ops_min_no_terminator+" LIMIT "+BIG_LIMIT+";", new String[]{local_id, last_sync_date});
		ASNConstituentOP result[] = new ASNConstituentOP[al.size()];
		for(int k=0; k < al.size(); k++) {
			result[k] = new ASNConstituentOP();
			result[k].constituent = D_Constituent.get_WB_Constituent(al.get(k));
		}
		if(DEBUG) out.println("ConstituentHandling:getConstituentsModifs: result="+result);
		if(DEBUG) out.println("**************");
		return result;
	}
	/**
	 * orgs will contain a list of the orgGID available
	 */
	public static String getConstituentOPsDate(String last_sync_date, String maxDate, OrgFilter ofi, HashSet<String> orgs, int limitConstituentLow) throws SQLiteException {
		String result;
		//boolean DEBUG = true;
		if(DEBUG) System.out.println("\n\n************\nConstituentHandling:getConstituentOPsDate:  Unbounded constituents date = "+last_sync_date+" to "+maxDate);
		if(maxDate == null){
			result = getConstituentOPsDate(last_sync_date, ofi, orgs, limitConstituentLow);
			if(DEBUG) System.out.println("\n\n************\nConstituentHandling:getConstituentOPsDate: Unbounded constituents upper bound date after check result = "+result);
			return result;
		}
		String sql_no_filter = "SELECT c."+table.constituent.arrival_date+", COUNT(*) "
			+", o."+table.organization.global_organization_ID +", c."+table.constituent.organization_ID +
				" FROM "+table.constituent.TNAME+" as c "
			+ " LEFT JOIN  "+table.organization.TNAME+" as o ON (o."+table.organization.organization_ID+" = c."+table.constituent.organization_ID+")  "
			+ " WHERE c."+table.constituent.sign+" IS NOT NULL AND "
			+ " o."+table.organization.broadcasted+" <> '0' "
			+ " AND c."+table.constituent.broadcasted+" <> '0' "
			+" AND c."+table.constituent.arrival_date+" > ? AND c."+table.constituent.arrival_date+" <= ? " +
					" GROUP BY c."+table.constituent.arrival_date+" LIMIT "+(1+limitConstituentLow)+";";
		String sql_filter = "SELECT c."+table.constituent.arrival_date+", COUNT(*) "
			+", o."+table.organization.global_organization_ID +", c."+table.constituent.organization_ID +
				" FROM "+table.constituent.TNAME+" as c "
				+ " LEFT JOIN  "+table.organization.TNAME+" as o ON (o."+table.organization.organization_ID+" = c."+table.constituent.organization_ID+")  "
			+ " WHERE c."+table.constituent.sign+" IS NOT NULL AND "
			+ " o."+table.organization.broadcasted+" <> '0' "
			+ " AND c."+table.constituent.broadcasted+" <> '0' "
			+" AND c."+table.constituent.organization_ID+" = ? AND c."+table.constituent.arrival_date+" > ? AND c."+table.constituent.arrival_date+" <= ? " +
					" GROUP BY c."+table.constituent.arrival_date+" LIMIT "+(1+limitConstituentLow)+";";
	
		ArrayList<ArrayList<Object>> constit;
		if(ofi==null){
			constit = Application.db.select(sql_no_filter, new String[]{last_sync_date, maxDate}, DEBUG);			
		}else{
			long orgID = UpdateMessages.getonly_organizationID(ofi.orgGID, ofi.orgGID_hash);
			constit = Application.db.select(sql_filter, new String[]{""+orgID, last_sync_date, maxDate}, DEBUG);
		}
		for (ArrayList<Object> a : constit) {
				orgs.add(Util.getString(a.get(3)));
		}
		if(constit.size()<=limitConstituentLow) result = maxDate; //null;
		else{
			result = Util.getString(constit.get(limitConstituentLow-1).get(0));
			if(DD.WARN_BROADCAST_LIMITS_REACHED) System.out.println("ConstituentHandling: getConstOPsDate: limits reached: "+limitConstituentLow+" date="+result);
		}
		if(DEBUG) System.out.println("\n\n************\nConstituentHandling:getConstituentOPsDate:  Filtered constituents date = "+result);
		return result;
	}
	/**
	 * 
	 * @param last_sync_date
	 * @param ofi
	 * @param orgs is an output with the list of orgGIDs available
	 * @return
	 * @throws SQLiteException
	 */
	public static String getConstituentOPsDate(String last_sync_date, OrgFilter ofi, HashSet<String> orgs, int limitConstituentLow) throws SQLiteException {
		String result;
		//boolean DEBUG = true;
		if(DEBUG) System.out.println("\n\n************\nConstituentHandling:getConstituentOPsDate':  Unbounded constituents date = "+last_sync_date);
		String sql_no_filter =
			"SELECT c."+table.constituent.arrival_date+", COUNT(*) " +", o."+table.organization.global_organization_ID +", c."+table.constituent.organization_ID +
				" FROM "+table.constituent.TNAME+" as c "
				+ " LEFT JOIN "+table.organization.TNAME+" as o ON (o."+table.organization.organization_ID+" = c."+table.constituent.organization_ID+")  "
				+ " WHERE c."+table.constituent.sign+" IS NOT NULL AND "
				+ " o."+table.organization.broadcasted+" <> '0' "
				+ " AND c."+table.constituent.broadcasted+" <> '0' "
			+" AND c."+table.constituent.arrival_date+" > ? GROUP BY c."+table.constituent.arrival_date+" LIMIT "+(1+limitConstituentLow)+";";
		String sql_filter =
			"SELECT c."+table.constituent.arrival_date+", COUNT(*) " +", o."+table.organization.global_organization_ID +", c."+table.constituent.organization_ID +
					" FROM "+table.constituent.TNAME+" as c "
					+ " LEFT JOIN  "+table.organization.TNAME+" as o ON (o."+table.organization.organization_ID+" = c."+table.constituent.organization_ID+")  "
				+ " WHERE c."+table.constituent.sign+" IS NOT NULL AND "
				+ " o."+table.organization.broadcasted+" <> '0' "
				+ " AND c."+table.constituent.broadcasted+" <> '0' "
			+" AND c."+table.constituent.organization_ID+" = ? AND c."+table.constituent.arrival_date+" > ? " +
					" GROUP BY c."+table.constituent.arrival_date+" LIMIT "+(1+limitConstituentLow)+";";
		
		ArrayList<ArrayList<Object>> constit;
		if(ofi==null){
			constit = Application.db.select(sql_no_filter, new String[]{last_sync_date}, DEBUG);			
		}else{
			long orgID = UpdateMessages.getonly_organizationID(ofi.orgGID, ofi.orgGID_hash);
			constit = Application.db.select(sql_filter, new String[]{""+orgID, last_sync_date}, DEBUG);
		}
		/*
		 * Get the list of involved organization, except for the uppmost, if the limit was reached
		 * if the limit was reached, then set the uppmost date in to the next date decremented a millisecond...)
		 * could have used previous date: result = Util.getString(constit.get(limitConstituentLow-1).get(0));
		 */
		if(constit.size()<=limitConstituentLow){
			result = null;
			for (int _a = 0; _a<constit.size(); _a++) {
				ArrayList<Object> a = constit.get(_a);
				orgs.add(Util.getString(a.get(3)));
			}
		}else{
			Calendar cal = Util.getCalendar(Util.getString(constit.get(limitConstituentLow).get(0)));
			cal.add(Calendar.MILLISECOND, -1);
			result = Encoder.getGeneralizedTime(cal);
			for (int _a = 0; _a<limitConstituentLow; _a++) {
				ArrayList<Object> a = constit.get(_a);
				orgs.add(Util.getString(a.get(3)));
			}
		}
		if(DEBUG) System.out.println("\n\n************\nConstituentHandling:getConstituentOPsDate':  Filtered constituents date = "+result);
		return result;
	}
	/**
	 * Integrate the data coming from remote machines
	 * @param constituents
	 * @param id
	 * @param org_local_ID
	 * @param arrival_time
	 * @param orgData
	 * @throws SQLiteException 
	 */
	public static boolean integrateNewData(ASNConstituentOP[] constituents,
			String orgGID, String org_local_ID, String arrival_time, D_Organization orgData, RequestData rq) throws SQLiteException {
		if(constituents == null) return false;
		boolean result = false;
		for(int k=0; k<constituents.length; k++) {
			
			String submit_ID;
			if(constituents[k].constituent.external) {
				if(constituents[k].constituent.submitter_ID !=null) submit_ID = constituents[k].constituent.submitter_ID;
				else submit_ID = D_Constituent.getConstituentLocalID(constituents[k].constituent.global_submitter_id);
				if(submit_ID==null) {
					submit_ID = ""+D_Constituent.insertTemporaryConstituentGID(constituents[k].constituent.global_submitter_id, org_local_ID);
					rq.cons.put(constituents[k].constituent.global_submitter_id, DD.EMPTYDATE);
				}else{
					rq.cons.remove(constituents[k].constituent.global_submitter_id);
				}
				constituents[k].constituent.submitter_ID = submit_ID;
			}			
			String neighborhood_ID;
			if(constituents[k].constituent.neighborhood_ID != null) neighborhood_ID = constituents[k].constituent.neighborhood_ID;
			else neighborhood_ID = D_Neighborhood.getNeighborhoodLocalID(constituents[k].constituent.global_neighborhood_ID);
			
			if((neighborhood_ID == null)&&(constituents[k].constituent.global_neighborhood_ID!=null)) {
				neighborhood_ID = D_Neighborhood.insertTemporaryNeighborhoodGID(constituents[k].constituent.global_neighborhood_ID, org_local_ID);
				rq.neig.add(constituents[k].constituent.global_neighborhood_ID);
			}else{
				//rq.neig.remove(constituents[k].constituent.global_neighborhood_ID);
			}
			constituents[k].constituent.neighborhood_ID = neighborhood_ID;

			result |= integrateNewConstituentOPData(constituents[k],
					orgGID, org_local_ID, arrival_time, orgData);
		}
		return result;
	}
	/**
	 * Integrate one constituent
	 * @param constituent
	 * @param orgGID
	 * @param org_local_ID
	 * @param arrival_time
	 * @param orgData
	 * @throws SQLiteException 
	 */
	private static boolean integrateNewConstituentOPData( ASNConstituentOP constituent, String orgGID,
			String org_local_ID, String arrival_time, D_Organization orgData) throws SQLiteException {
		if(DEBUG) System.out.println("ConstituentHandling:integrateNewConstituentData: start on "+constituent);
		if(constituent == null) return false;
		D_Constituent wc = constituent.constituent;
		boolean result;
		result = null!=integrateNewConstituentData( wc, orgGID, org_local_ID, arrival_time, orgData);
		if(DEBUG) System.out.println("ConstituentHandling:integrateNewConstituentData: exit with "+result);
		return result;
	}
	public static String integrateNewConstituentData( D_Constituent wc, String orgGID,
			String org_local_ID, String arrival_time, D_Organization orgData) throws SQLiteException {
		if(DEBUG) System.out.println("ConstituentHandling:integrateNewConstituentData: start on "+wc);
		String result = null;
		if(wc == null) return result;
		wc.global_organization_ID = orgGID;
		wc.organization_ID = org_local_ID;
		if(!wc.verifySignature(orgGID)){
			if(_DEBUG) System.out.println("ConstituentHandling:integrateNewConstituentData:Signature check failed for "+wc);
			if(!DD.ACCEPT_UNSIGNED_PEERS){
				if(_DEBUG) System.out.println("ConstituentHandling:integrateNewConstituentData: exit");
				return result;
			}
		}
		result = ""+wc.storeVerified(orgGID, org_local_ID, arrival_time);
		if(DEBUG) System.out.println("ConstituentHandling:integrateNewConstituentData: exit");
		return result;
	}
}